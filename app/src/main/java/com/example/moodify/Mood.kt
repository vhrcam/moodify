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

// Single source of truth for the moods, shared by the Main and
// Preferences activities.
val ALL_MOODS = listOf(
    Mood("Chill", "\uD83D\uDE0C", "lofi"),
    Mood("Hype", "\uD83D\uDD25", "hip hop"),
    Mood("Happy", "\uD83D\uDE0A", "pop"),
    Mood("Sad", "\uD83D\uDE22", "blues"),
    Mood("Angry", "\uD83D\uDE24", "rock"),
    Mood("Focused", "\uD83C\uDFAF", "instrumental")
)