package com.example.moodify

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

/**
 * Shown only on first launch. Three short pages: a splash screen, a single
 * explicit-content question, then a quick "how it works" explainer before
 * handing off to the normal mood picker. Saves directly to the same
 * SharedPreferences file used by PreferencesActivity, so the choice made
 * here shows up there too.
 */
class OnboardingFragment : Fragment() {

    interface OnboardingListener {
        fun onOnboardingFinished()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pageSplash: LinearLayout = view.findViewById(R.id.pageSplash)
        val pageExplicit: LinearLayout = view.findViewById(R.id.pageExplicit)
        val pageIntro: LinearLayout = view.findViewById(R.id.pageIntro)

        val buttonGetStarted: Button = view.findViewById(R.id.buttonGetStarted)
        val buttonExplicitContinue: Button = view.findViewById(R.id.buttonExplicitContinue)
        val buttonIntroContinue: Button = view.findViewById(R.id.buttonIntroContinue)
        val switchExplicit: SwitchCompat = view.findViewById(R.id.switchExplicitOnboarding)

        // Page 1 -> Page 2
        buttonGetStarted.setOnClickListener {
            pageSplash.visibility = View.GONE
            pageExplicit.visibility = View.VISIBLE
        }

        // Page 2 -> Page 3 (save the explicit-content choice along the way)
        buttonExplicitContinue.setOnClickListener {
            val prefs = requireActivity()
                .getSharedPreferences(PreferencesActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(PreferencesActivity.KEY_ALLOW_EXPLICIT, switchExplicit.isChecked)
                .apply()

            pageExplicit.visibility = View.GONE
            pageIntro.visibility = View.VISIBLE
        }

        // Page 3 -> done
        buttonIntroContinue.setOnClickListener {
            val prefs = requireActivity()
                .getSharedPreferences(PreferencesActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(PreferencesActivity.KEY_HAS_SEEN_ONBOARDING, true)
                .apply()

            (activity as? OnboardingListener)?.onOnboardingFinished()
        }
    }
}

