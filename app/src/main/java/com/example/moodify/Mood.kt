package com.example.moodify

/**
 * Represents one selectable mood: its display name, emoji, and the
 * genre string it maps to for the (future) Spotify Search API query.
 */
data class Mood(
    val name: String,
    val emoji: String,
    val genre: String
)
