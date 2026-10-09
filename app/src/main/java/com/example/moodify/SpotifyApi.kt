package com.example.moodify

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

class SpotifyException(message: String, val code: Int = 0) : Exception(message)

/**
 * Everything that talks to Spotify lives here.
 *
 * 1. Gets an app access token using the Client Credentials flow
 *    (Client ID + Client Secret from local.properties, via BuildConfig).
 * 2. Calls the Search API for tracks in a mood's genre, starting at a
 *    random offset so every Find Songs / Shuffle Again gives a new set.
 */
object SpotifyApi {

    private const val TAG = "Moodify"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private const val SEARCH_URL = "https://api.spotify.com/v1/search"

    // Spotify allows at most 10 results per search for Development Mode apps,
    // and offset + limit can't go past 1000.
    private const val PAGE_SIZE = 10
    private const val MAX_OFFSET = 990

    private var accessToken: String? = null
    private var tokenExpiresAt = 0L

    // Remembers how many results each search has, so random offsets stay in range
    private val knownTotals = mutableMapOf<String, Int>()

    /**
     * Returns up to 10 tracks for a genre. Tries Spotify's genre filter first,
     * then falls back to a plain keyword search if the genre filter finds nothing.
     */
    suspend fun searchTracksForGenre(genre: String): List<Song> = withContext(Dispatchers.IO) {
        var songs = searchAtRandomOffset("genre:\"$genre\"")
        if (songs.isEmpty()) {
            songs = searchAtRandomOffset(genre)
        }
        songs
    }

    private fun searchAtRandomOffset(query: String): List<Song> {
        val total = knownTotals[query]
        val maxOffset = if (total != null) minOf(total - PAGE_SIZE, MAX_OFFSET) else 100
        val offset = if (maxOffset > 0) Random.nextInt(0, maxOffset + 1) else 0

        var songs = search(query, offset)
        // A random offset can land past the last result; start over from the top if so
        if (songs.isEmpty() && offset > 0) {
            songs = search(query, 0)
        }
        return songs
    }

    private fun search(query: String, offset: Int): List<Song> {
        val url = SEARCH_URL +
                "?q=" + URLEncoder.encode(query, "UTF-8") +
                "&type=track&limit=$PAGE_SIZE&offset=$offset"

        val json = JSONObject(authorizedGet(url))
        val tracks = json.getJSONObject("tracks")
        knownTotals[query] = tracks.optInt("total", 0)

        val items = tracks.optJSONArray("items") ?: return emptyList()
        val songs = mutableListOf<Song>()

        for (i in 0 until items.length()) {
            val track = items.optJSONObject(i) ?: continue

            val title = track.optString("name")
            val spotifyUrl = track.optJSONObject("external_urls")?.optString("spotify").orEmpty()
            if (title.isEmpty() || spotifyUrl.isEmpty()) continue

            val artistsArray = track.optJSONArray("artists")
            val artistNames = mutableListOf<String>()
            if (artistsArray != null) {
                for (j in 0 until artistsArray.length()) {
                    artistsArray.optJSONObject(j)?.optString("name")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { artistNames.add(it) }
                }
            }

            // Spotify lists album images largest first
            val albumArtUrl = track.optJSONObject("album")
                ?.optJSONArray("images")
                ?.optJSONObject(0)
                ?.optString("url")
                ?.takeIf { it.isNotEmpty() }

            songs.add(
                Song(
                    title = title,
                    artist = artistNames.joinToString(", "),
                    albumArtUrl = albumArtUrl,
                    spotifyUrl = spotifyUrl,
                    explicit = track.optBoolean("explicit", false)
                )
            )
        }
        return songs
    }

    // GET request with the access token. If the token has expired (401),
    // get a fresh one and try once more.
    private fun authorizedGet(url: String): String {
        return try {
            get(url, getAccessToken())
        } catch (e: SpotifyException) {
            if (e.code == 401) {
                accessToken = null
                get(url, getAccessToken())
            } else {
                throw e
            }
        }
    }

    private fun get(url: String, token: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return readResponse(connection)
    }

    private fun getAccessToken(): String {
        val cached = accessToken
        if (cached != null && System.currentTimeMillis() < tokenExpiresAt) {
            return cached
        }

        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw SpotifyException("Spotify keys are missing. Add them to local.properties and rebuild.")
        }

        val credentials = Base64.encodeToString(
            "$clientId:$clientSecret".toByteArray(),
            Base64.NO_WRAP
        )

        val connection = URL(TOKEN_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Basic $credentials")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.outputStream.use { it.write("grant_type=client_credentials".toByteArray()) }

        val json = JSONObject(readResponse(connection))
        val token = json.getString("access_token")
        val expiresInSeconds = json.optInt("expires_in", 3600)

        accessToken = token
        // Refresh a minute early so a request never goes out with an expired token
        tokenExpiresAt = System.currentTimeMillis() + (expiresInSeconds - 60) * 1000L
        return token
    }

    private fun readResponse(connection: HttpURLConnection): String {
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                Log.e(TAG, "Spotify request failed ($code): $body")
                val message = when (code) {
                    400, 401 -> "Spotify rejected the app's keys (error $code). Double-check local.properties."
                    403 -> "Spotify blocked this request (error 403). Check that your Premium account owns the developer app."
                    429 -> "Too many requests to Spotify. Wait a minute and try again."
                    else -> "Spotify error $code. Try again."
                }
                throw SpotifyException(message, code)
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}