package com.example.moodify

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class PreferencesActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "moodify_prefs"
        const val KEY_DEFAULT_MOOD = "default_mood"       // -1 = Random, otherwise mood index
        const val KEY_ALLOW_EXPLICIT = "allow_explicit"
        const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        const val KEY_RECENT_MOODS = "recent_moods"        // comma-separated mood indices, most recent first

        const val DEFAULT_MOOD_RANDOM = -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val backButton: TextView = findViewById(R.id.buttonBack)
        val radioGroup: RadioGroup = findViewById(R.id.radioGroupDefaultMood)
        val switchExplicit: SwitchCompat = findViewById(R.id.switchExplicit)

        backButton.setOnClickListener { finish() }

        // Build one radio button per option: "Random" first, then each mood.
        val labels = listOf("\uD83C\uDFB2 Random") + ALL_MOODS.map { "${it.emoji} ${it.name}" }
        labels.forEach { label ->
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 16f
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.parseColor("#8B5CF6"))
                setPadding(16, 20, 16, 20)
            }
            radioGroup.addView(radio)
        }

        // Show the saved value (defaults to the first mood, Chill, the first time)
        val savedMood = prefs.getInt(KEY_DEFAULT_MOOD, 0)
        val savedIndex = savedMood + 1 // +1 because "Random" sits at position 0
        radioGroup.check(radioGroup.getChildAt(savedIndex).id)

        switchExplicit.isChecked = prefs.getBoolean(KEY_ALLOW_EXPLICIT, true)

        // Preferences save automatically as soon as they change.
        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val position = group.indexOfChild(group.findViewById(checkedId))
            prefs.edit().putInt(KEY_DEFAULT_MOOD, position - 1).apply()
        }

        switchExplicit.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ALLOW_EXPLICIT, isChecked).apply()
        }
    }
}