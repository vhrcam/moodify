package com.example.moodify

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Display-only screen. The only interaction is the back arrow.
 */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        val backButton: TextView = findViewById(R.id.buttonBack)
        backButton.setOnClickListener { finish() }
    }
}