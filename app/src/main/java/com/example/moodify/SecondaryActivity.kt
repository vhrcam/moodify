package com.example.moodify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SecondaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secondary)

        // Pull the song data passed in via the Intent from MainActivity
        val title = intent.getStringExtra("SONG_TITLE")
        val artist = intent.getStringExtra("SONG_ARTIST")
        val albumArtResId = intent.getIntExtra("ALBUM_ART_RES_ID", R.drawable.placeholder_album_art)
        val previewUrl = intent.getStringExtra("PREVIEW_URL")

        val albumArt: ImageView = findViewById(R.id.imageAlbumArtLarge)
        val titleText: TextView = findViewById(R.id.textSongTitleLarge)
        val artistText: TextView = findViewById(R.id.textArtistName)
        val openInSpotifyButton: Button = findViewById(R.id.buttonOpenSpotify)
        val backButton: TextView = findViewById(R.id.buttonBack)

        backButton.setOnClickListener { finish() }

        albumArt.setImageResource(albumArtResId)
        titleText.text = title
        artistText.text = artist

        openInSpotifyButton.setOnClickListener {
            previewUrl?.let {
                val openLink = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                startActivity(openLink)
            }
        }
    }
}