package com.example.moodify

/**
 * One song returned from Spotify's Search API.
 *
 * albumArtUrl  - link to the album cover image (loaded with Glide)
 * spotifyUrl   - link that opens this exact track in the Spotify app
 * explicit     - Spotify's own explicit flag, used by the explicit-content preference
 */
data class Song(
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val spotifyUrl: String,
    val explicit: Boolean = false
)
