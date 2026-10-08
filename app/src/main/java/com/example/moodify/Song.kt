package com.example.moodify

/**
 * Simple data model for a song shown in the list.
 *
 * NOTE: albumArtResId is a placeholder local drawable for now.
 * Once the real Spotify Search API call is wired in (a later part of the
 * project), this will be replaced with an album art URL loaded over the
 * network using an image-loading library such as Glide.
 */
data class Song(
    val title: String,
    val artist: String,
    val albumArtResId: Int,
    val previewUrl: String
)
