package com.example.moodify

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    // The six moods the app supports, each mapped to a genre string.
    // This mapping is the core "mood-to-genre" logic described in the
    // design document, and is what will drive the real Spotify Search API
    // query once networking is wired in during a later part.
    private val moods = ALL_MOODS

    // Saved user preferences (set in PreferencesActivity)
    private val prefs by lazy {
        getSharedPreferences(PreferencesActivity.PREFS_NAME, MODE_PRIVATE)
    }

    // Songs marked explicit in the placeholder data. The explicit-content
    // preference filters these out when it is turned off.
    private val explicitTitles = setOf("Go Hard", "No Mercy", "Burn It Down", "Alone Again")

    private var currentMoodIndex = 0

    // Placeholder song pools, one per mood, standing in for real Spotify
    // Search API results until that networking is added later.
    private val moodSongPools = mutableMapOf<String, MutableList<Song>>()

    private lateinit var layoutMoodPicker: LinearLayout
    private lateinit var layoutResults: LinearLayout
    private lateinit var layoutEmojiRow: LinearLayout

    private lateinit var textBigEmoji: TextView
    private lateinit var textMoodName: TextView
    private lateinit var textGenreTag: TextView
    private lateinit var textResultsHeader: TextView
    private lateinit var textResultsGenreTag: TextView
    private lateinit var textSliderLabelStart: TextView
    private lateinit var textSliderLabelEnd: TextView
    private lateinit var seekBarMood: SeekBar

    private lateinit var recyclerViewSongs: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private var currentSongs = mutableListOf<Song>()

    private var updatingSeekBarProgrammatically = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buildPlaceholderPools()

        layoutMoodPicker = findViewById(R.id.layoutMoodPicker)
        layoutResults = findViewById(R.id.layoutResults)
        layoutEmojiRow = findViewById(R.id.layoutEmojiRow)

        textBigEmoji = findViewById(R.id.textBigEmoji)
        textMoodName = findViewById(R.id.textMoodName)
        textGenreTag = findViewById(R.id.textGenreTag)
        textResultsHeader = findViewById(R.id.textResultsHeader)
        textResultsGenreTag = findViewById(R.id.textResultsGenreTag)
        textSliderLabelStart = findViewById(R.id.textSliderLabelStart)
        textSliderLabelEnd = findViewById(R.id.textSliderLabelEnd)
        seekBarMood = findViewById(R.id.seekBarMood)

        recyclerViewSongs = findViewById(R.id.recyclerViewSongs)
        recyclerViewSongs.layoutManager = LinearLayoutManager(this)

        val buttonFindSongs: Button = findViewById(R.id.buttonFindSongs)
        val buttonShuffleAgain: Button = findViewById(R.id.buttonShuffleAgain)
        val tabMoodSlider: TextView = findViewById(R.id.tabMoodSlider)
        val tabSpin: TextView = findViewById(R.id.tabSpin)
        val tabRecent: TextView = findViewById(R.id.tabRecent)

        buildEmojiRow()

        textSliderLabelStart.text = moods.first().name
        textSliderLabelEnd.text = moods.last().name
        seekBarMood.max = moods.size - 1

        seekBarMood.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    selectMood(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Start on the user's default mood (or a random one if they chose Random)
        val savedMood = prefs.getInt(
            PreferencesActivity.KEY_DEFAULT_MOOD, 0
        )
        selectMood(if (savedMood in moods.indices) savedMood else Random.nextInt(moods.size))

        val buttonMenu: TextView = findViewById(R.id.buttonMenu)
        buttonMenu.setOnClickListener { showMenu(buttonMenu) }

        buttonFindSongs.setOnClickListener { showResultsForCurrentMood() }
        buttonShuffleAgain.setOnClickListener { shuffleSongs() }

        // Spin and Recent tabs are planned for a later part of the project
        tabSpin.setOnClickListener {
            Toast.makeText(this, "Spin wheel coming in a later update", Toast.LENGTH_SHORT).show()
        }
        tabRecent.setOnClickListener {
            Toast.makeText(this, "Recent moods coming in a later update", Toast.LENGTH_SHORT).show()
        }
        tabMoodSlider.setOnClickListener {
            // Already on this tab; if results are showing, go back to the picker
            layoutResults.visibility = View.GONE
            layoutMoodPicker.visibility = View.VISIBLE
        }
    }

    private fun buildEmojiRow() {
        layoutEmojiRow.removeAllViews()
        moods.forEachIndexed { index, mood ->
            val emojiView = TextView(this).apply {
                text = mood.emoji
                textSize = 26f
                setPadding(20, 8, 20, 8)
                gravity = Gravity.CENTER
                setOnClickListener { selectMood(index) }
            }
            layoutEmojiRow.addView(emojiView)
        }
    }

    private fun selectMood(index: Int) {
        currentMoodIndex = index
        val mood = moods[index]

        textBigEmoji.text = mood.emoji
        textMoodName.text = mood.name
        textGenreTag.text = mood.genre

        if (!updatingSeekBarProgrammatically) {
            updatingSeekBarProgrammatically = true
            seekBarMood.progress = index
            updatingSeekBarProgrammatically = false
        }
    }

    private fun showResultsForCurrentMood() {
        val mood = moods[currentMoodIndex]
        val pool = moodSongPools.getValue(mood.name)

        currentSongs = pickSongs(pool)

        songAdapter = SongAdapter(currentSongs) { song ->
            // Tapping a song opens the Secondary Activity via an Intent,
            // passing that song's data along as extras.
            val intent = Intent(this, SecondaryActivity::class.java).apply {
                putExtra("SONG_TITLE", song.title)
                putExtra("SONG_ARTIST", song.artist)
                putExtra("ALBUM_ART_RES_ID", song.albumArtResId)
                putExtra("PREVIEW_URL", song.previewUrl)
            }
            startActivity(intent)
        }
        recyclerViewSongs.adapter = songAdapter

        textResultsHeader.text = "${mood.emoji} ${mood.name} Picks"
        textResultsGenreTag.text = mood.genre

        layoutMoodPicker.visibility = View.GONE
        layoutResults.visibility = View.VISIBLE
    }

    private fun shuffleSongs() {
        val mood = moods[currentMoodIndex]
        val pool = moodSongPools.getValue(mood.name)

        // Placeholder shuffle: reshuffle this mood's pool and take a new
        // batch of 5. Once real Spotify data is wired in, this will instead
        // re-query the Search API with a randomized offset.
        pool.shuffle()
        currentSongs.clear()
        currentSongs.addAll(pickSongs(pool))
        songAdapter.notifyDataSetChanged()
    }

    // Takes up to 5 songs from the pool, skipping explicit songs when the
    // user has turned explicit content off in Preferences.
    private fun pickSongs(pool: List<Song>): MutableList<Song> {
        val allowExplicit = prefs.getBoolean(PreferencesActivity.KEY_ALLOW_EXPLICIT, true)
        return pool.filter { allowExplicit || !it.explicit }.take(5).toMutableList()
    }

    // Menu in the top-right corner: opens Preferences or Help via an Intent
    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Preferences")
        popup.menu.add(0, 2, 1, "Help")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, PreferencesActivity::class.java))
                2 -> startActivity(Intent(this, HelpActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun buildPlaceholderPools() {
        moodSongPools["Chill"] = buildPool(
            "Sunset Drive" to "Lo-Fi Collective",
            "Golden Hour" to "Chill Beats Co.",
            "Night Bloom" to "Acoustic Dreams",
            "Slow Motion" to "The Quiet Hour",
            "Paper Clouds" to "Soft Static",
            "Afterglow" to "Wavelength"
        )

        moodSongPools["Hype"] = buildPool(
            "Turn It Up" to "Bass Riot",
            "Overdrive" to "Neon Pulse",
            "Adrenaline" to "Volt Crew",
            "Higher" to "Skyline",
            "Electric" to "Pulse Nation",
            "Go Hard" to "Riot Squad"
        )

        moodSongPools["Happy"] = buildPool(
            "Sunny Days" to "The Brightsides",
            "Good Vibes" to "Daylight",
            "Feel Good" to "Citrus",
            "Smile On" to "Honeybeat",
            "Up and Away" to "Skylark",
            "Bright Side" to "Golden Hour Kids"
        )

        moodSongPools["Sad"] = buildPool(
            "Rainy Window" to "Blue Hour",
            "Empty Room" to "Hollow Sky",
            "Fading Light" to "Grey November",
            "Quiet Tears" to "Nocturne",
            "Heavy Heart" to "Solace",
            "Alone Again" to "Midnight Ivy"
        )

        moodSongPools["Angry"] = buildPool(
            "Breaking Point" to "Riot Line",
            "No Mercy" to "Iron Grip",
            "Static Rage" to "Voltage",
            "Burn It Down" to "Fault Line",
            "Last Straw" to "Grit",
            "Full Throttle" to "Warhead"
        )

        moodSongPools["Focused"] = buildPool(
            "Deep Work" to "Neural",
            "Clarity" to "Mind Palace",
            "Flow State" to "Studybeats",
            "Quiet Focus" to "Minimal",
            "Steady Hands" to "Calibrate",
            "Concentration" to "Signal"
        )
    }

    // Convenience method: pass title-artist pairs to quickly build a
    // placeholder song pool.
    private fun buildPool(vararg titleArtistPairs: Pair<String, String>): MutableList<Song> {
        return titleArtistPairs.map { (title, artist) ->
            val previewUrl = "https://open.spotify.com/search/${title.replace(" ", "%20")}"
            Song(title, artist, R.drawable.placeholder_album_art, previewUrl, title in explicitTitles)
        }.toMutableList()
    }
}