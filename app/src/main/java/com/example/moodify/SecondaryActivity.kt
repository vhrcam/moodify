package com.example.moodify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class SecondaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secondary)

        // Pull the song data passed in via the Intent from MainActivity
        val title = intent.getStringExtra("SONG_TITLE")
        val artist = intent.getStringExtra("SONG_ARTIST")
        val albumArtUrl = intent.getStringExtra("ALBUM_ART_URL")
        val spotifyUrl = intent.getStringExtra("SPOTIFY_URL")

        val albumArt: ImageView = findViewById(R.id.imageAlbumArtLarge)
        val titleText: TextView = findViewById(R.id.textSongTitleLarge)
        val artistText: TextView = findViewById(R.id.textArtistName)
        val openInSpotifyButton: Button = findViewById(R.id.buttonOpenSpotify)
        val backButton: TextView = findViewById(R.id.buttonBack)

        backButton.setOnClickListener { finish() }

        Glide.with(this)
            .load(albumArtUrl)
            .placeholder(R.drawable.placeholder_album_art)
            .error(R.drawable.placeholder_album_art)
            .centerCrop()
            .into(albumArt)

        titleText.text = title
        artistText.text = artist

        // Opens this exact track in the Spotify app (or the browser if
        // Spotify isn't installed)
        openInSpotifyButton.setOnClickListener {
            spotifyUrl?.let {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
            }
        }
    }
}