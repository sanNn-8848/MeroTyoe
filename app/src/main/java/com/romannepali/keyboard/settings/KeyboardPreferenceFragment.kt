package com.romannepali.keyboard.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.romannepali.keyboard.Prefs
import com.romannepali.keyboard.R

/**
 * PreferenceFragmentCompat rendering the standard Android PreferenceScreen.
 */
class KeyboardPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<SwitchPreferenceCompat>("dark_theme")?.setOnPreferenceChangeListener { _, newValue ->
            val checked = newValue as Boolean
            context?.let { Prefs.setDarkTheme(it, checked) }
            activity?.recreate()
            true
        }
    }
}
