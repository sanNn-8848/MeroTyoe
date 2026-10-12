package com.romannepali.keyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    private const val NAME = "merotype_prefs"

    private const val KEY_VIBRATION = "key_vibration"
    private const val KEY_SOUND = "key_sound"
    private const val KEY_SUGGESTIONS = "word_suggestions"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_KEYBOARD_SIZE = "keyboard_size"
    private const val KEY_DEVANAGARI = "devanagari_suggestions"
    private const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
    private const val KEY_SWIPE_DELETE = "swipe_delete_word"
    private const val KEY_AUTO_CAPITALIZATION = "auto_capitalization"
    private const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"
    private const val KEY_NUMBER_ROW = "show_number_row"

    fun vibration(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATION, true)

    fun vibrationIntensity(context: Context): Int =
        prefs(context).getInt(KEY_VIBRATION_INTENSITY, 60)

    fun sound(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND, true)

    fun suggestions(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUGGESTIONS, true)

    fun devanagari(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVANAGARI, true)

    fun darkTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_THEME, true)

    fun keyboardSize(context: Context): Int =
        prefs(context).getInt(KEY_KEYBOARD_SIZE, 50)

    fun swipeDeleteWord(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SWIPE_DELETE, true)

    fun autoCapitalization(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CAPITALIZATION, true)

    fun doubleSpacePeriod(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DOUBLE_SPACE_PERIOD, true)

    fun showNumberRow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NUMBER_ROW, false)

    fun setVibration(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun setVibrationIntensity(context: Context, intensity: Int) {
        prefs(context).edit().putInt(KEY_VIBRATION_INTENSITY, intensity.coerceIn(0, 100)).apply()
    }

    fun setSound(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setSuggestions(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUGGESTIONS, enabled).apply()
    }

    fun setDevanagari(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEVANAGARI, enabled).apply()
    }

    fun setDarkTheme(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun setKeyboardSize(context: Context, size: Int) {
        prefs(context).edit().putInt(KEY_KEYBOARD_SIZE, size.coerceIn(0, 100)).apply()
    }

    fun setSwipeDeleteWord(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SWIPE_DELETE, enabled).apply()
    }

    fun setAutoCapitalization(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CAPITALIZATION, enabled).apply()
    }

    fun setDoubleSpacePeriod(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DOUBLE_SPACE_PERIOD, enabled).apply()
    }

    fun setShowNumberRow(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NUMBER_ROW, enabled).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}