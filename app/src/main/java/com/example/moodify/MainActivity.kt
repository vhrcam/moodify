package com.example.moodify

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

class MainActivity : AppCompatActivity(), OnboardingFragment.OnboardingListener {

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

    private lateinit var layoutRecent: LinearLayout
    private lateinit var layoutRecentList: LinearLayout
    private lateinit var textRecentEmpty: TextView
    private lateinit var tabMoodSlider: TextView
    private lateinit var tabSpin: TextView
    private lateinit var tabRecent: TextView

    // Last 5 mood selections, most recent first. Loaded from and saved
    // to SharedPreferences so the history survives an app restart.
    private val recentMoodIndices = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buildPlaceholderPools()

        layoutMoodPicker = findViewById(R.id.layoutMoodPicker)
        layoutResults = findViewById(R.id.layoutResults)
        layoutRecent = findViewById(R.id.layoutRecent)
        layoutRecentList = findViewById(R.id.layoutRecentList)
        textRecentEmpty = findViewById(R.id.textRecentEmpty)
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
        tabMoodSlider = findViewById(R.id.tabMoodSlider)
        tabSpin = findViewById(R.id.tabSpin)
        tabRecent = findViewById(R.id.tabRecent)

        loadRecentMoods()

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

        setActiveTab(tabMoodSlider)

        val buttonMenu: TextView = findViewById(R.id.buttonMenu)
        buttonMenu.setOnClickListener { showMenu(buttonMenu) }

        buttonFindSongs.setOnClickListener { showResultsForCurrentMood() }
        buttonShuffleAgain.setOnClickListener { shuffleSongs() }

        // The Spin wheel is a possible future addition; not required for the app to work
        tabSpin.setOnClickListener {
            Toast.makeText(this, "Spin wheel coming in a later update", Toast.LENGTH_SHORT).show()
        }
        tabRecent.setOnClickListener { showRecentMoods() }
        tabMoodSlider.setOnClickListener { showMoodPicker() }

        showOnboardingIfNeeded()
    }

    private fun showMoodPicker() {
        setActiveTab(tabMoodSlider)
        layoutResults.visibility = View.GONE
        layoutRecent.visibility = View.GONE
        layoutMoodPicker.visibility = View.VISIBLE
    }

    private fun showRecentMoods() {
        setActiveTab(tabRecent)
        layoutMoodPicker.visibility = View.GONE
        layoutResults.visibility = View.GONE
        layoutRecent.visibility = View.VISIBLE
        buildRecentMoodsList()
    }

    // Highlights whichever tab is active and resets the other two to plain text,
    // matching the gradient-pill style used for the active tab.
    private fun setActiveTab(activeTab: TextView) {
        for (tab in listOf(tabMoodSlider, tabSpin, tabRecent)) {
            val isActive = tab == activeTab
            tab.setBackgroundResource(if (isActive) R.drawable.bg_gradient_pill else android.R.color.transparent)
            tab.setTextColor(android.graphics.Color.parseColor(if (isActive) "#FFFFFF" else "#9CA3AF"))
        }
    }

    // ===================== Onboarding =====================

    private fun showOnboardingIfNeeded() {
        val hasSeenOnboarding = prefs.getBoolean(PreferencesActivity.KEY_HAS_SEEN_ONBOARDING, false)
        if (!hasSeenOnboarding) {
            val container: View = findViewById(R.id.fragmentContainerOnboarding)
            container.visibility = View.VISIBLE
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerOnboarding, OnboardingFragment())
                .commit()
        }
    }

    // Called by OnboardingFragment once the user finishes both pages
    override fun onOnboardingFinished() {
        supportFragmentManager.findFragmentById(R.id.fragmentContainerOnboarding)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
        findViewById<View>(R.id.fragmentContainerOnboarding).visibility = View.GONE
    }

    // ===================== Recent Moods =====================

    private fun loadRecentMoods() {
        val saved = prefs.getString(PreferencesActivity.KEY_RECENT_MOODS, "") ?: ""
        recentMoodIndices.clear()
        if (saved.isNotEmpty()) {
            saved.split(",").forEach { token ->
                token.toIntOrNull()?.let { index ->
                    if (index in moods.indices) recentMoodIndices.add(index)
                }
            }
        }
    }

    private fun saveRecentMood(index: Int) {
        recentMoodIndices.remove(index) // avoid duplicate entries for the same mood
        recentMoodIndices.add(0, index)
        while (recentMoodIndices.size > 5) {
            recentMoodIndices.removeAt(recentMoodIndices.size - 1)
        }
        prefs.edit()
            .putString(PreferencesActivity.KEY_RECENT_MOODS, recentMoodIndices.joinToString(","))
            .apply()
    }

    private fun buildRecentMoodsList() {
        layoutRecentList.removeAllViews()

        if (recentMoodIndices.isEmpty()) {
            textRecentEmpty.visibility = View.VISIBLE
            return
        }
        textRecentEmpty.visibility = View.GONE

        recentMoodIndices.forEach { index ->
            val mood = moods[index]
            val row = TextView(this).apply {
                text = "${mood.emoji}  ${mood.name}  \u2022  ${mood.genre}"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 16f
                setPadding(24, 28, 24, 28)
                setBackgroundResource(R.drawable.bg_pill)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 12
                layoutParams = params
                setOnClickListener {
                    selectMood(index)
                    showResultsForCurrentMood()
                }
            }
            layoutRecentList.addView(row)
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

        saveRecentMood(currentMoodIndex)

        layoutMoodPicker.visibility = View.GONE
        layoutRecent.visibility = View.GONE
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

    // Menu in the top-right corner: opens Preferences or Help via an Intent.
    // Uses a custom PopupWindow (instead of the system PopupMenu) so the
    // two options can be styled as rounded purple-gradient bubbles that
    // match the rest of the app, rather than the default white dropdown.
    private fun showMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 12f

        val itemPreferences: TextView = popupView.findViewById(R.id.menuItemPreferences)
        val itemHelp: TextView = popupView.findViewById(R.id.menuItemHelp)

        itemPreferences.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, PreferencesActivity::class.java))
        }
        itemHelp.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, HelpActivity::class.java))
        }

        // Measure the bubbles first so the popup can be shifted left,
        // keeping it from running off the right edge of the screen since
        // the menu button sits in the top-right corner.
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val xOffset = anchor.width - popupView.measuredWidth
        popupWindow.showAsDropDown(anchor, xOffset, -12)
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