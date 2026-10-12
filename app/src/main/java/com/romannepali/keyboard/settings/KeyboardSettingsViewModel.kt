package com.romannepali.keyboard.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.romannepali.keyboard.HapticFeedbackManager
import com.romannepali.keyboard.Prefs
import com.romannepali.keyboard.dictionary.room.NepaliDictionaryRepository
import com.romannepali.keyboard.dictionary.room.NepaliPhraseEntity
import com.romannepali.keyboard.dictionary.room.NepaliPhraseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing keyboard preferences and predictive text phrase data.
 */
class KeyboardSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val hapticManager = HapticFeedbackManager(context)
    val phraseRepository = NepaliPhraseRepository(context)
    val dictionaryRepository = NepaliDictionaryRepository(context)

    val phrases: Flow<List<NepaliPhraseEntity>> = phraseRepository.allPhrases

    private val _vibrationEnabled = MutableStateFlow(Prefs.vibration(context))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _vibrationIntensity = MutableStateFlow(Prefs.vibrationIntensity(context))
    val vibrationIntensity: StateFlow<Int> = _vibrationIntensity.asStateFlow()

    private val _soundEnabled = MutableStateFlow(Prefs.sound(context))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _suggestionsEnabled = MutableStateFlow(Prefs.suggestions(context))
    val suggestionsEnabled: StateFlow<Boolean> = _suggestionsEnabled.asStateFlow()

    private val _devanagariEnabled = MutableStateFlow(Prefs.devanagari(context))
    val devanagariEnabled: StateFlow<Boolean> = _devanagariEnabled.asStateFlow()

    private val _swipeDeleteEnabled = MutableStateFlow(Prefs.swipeDeleteWord(context))
    val swipeDeleteEnabled: StateFlow<Boolean> = _swipeDeleteEnabled.asStateFlow()

    private val _autoCapEnabled = MutableStateFlow(Prefs.autoCapitalization(context))
    val autoCapEnabled: StateFlow<Boolean> = _autoCapEnabled.asStateFlow()

    private val _doubleSpaceEnabled = MutableStateFlow(Prefs.doubleSpacePeriod(context))
    val doubleSpaceEnabled: StateFlow<Boolean> = _doubleSpaceEnabled.asStateFlow()

    private val _darkThemeEnabled = MutableStateFlow(Prefs.darkTheme(context))
    val darkThemeEnabled: StateFlow<Boolean> = _darkThemeEnabled.asStateFlow()

    private val _keyboardSize = MutableStateFlow(Prefs.keyboardSize(context))
    val keyboardSize: StateFlow<Int> = _keyboardSize.asStateFlow()

    private val _numberRowEnabled = MutableStateFlow(Prefs.showNumberRow(context))
    val numberRowEnabled: StateFlow<Boolean> = _numberRowEnabled.asStateFlow()

    fun setVibration(enabled: Boolean) {
        Prefs.setVibration(context, enabled)
        _vibrationEnabled.value = enabled
    }

    fun setVibrationIntensity(intensity: Int) {
        Prefs.setVibrationIntensity(context, intensity)
        _vibrationIntensity.value = intensity
    }

    fun testVibration() {
        hapticManager.testVibration()
    }

    fun setSound(enabled: Boolean) {
        Prefs.setSound(context, enabled)
        _soundEnabled.value = enabled
    }

    fun setSuggestions(enabled: Boolean) {
        Prefs.setSuggestions(context, enabled)
        _suggestionsEnabled.value = enabled
    }

    fun setDevanagari(enabled: Boolean) {
        Prefs.setDevanagari(context, enabled)
        _devanagariEnabled.value = enabled
    }

    fun setSwipeDelete(enabled: Boolean) {
        Prefs.setSwipeDeleteWord(context, enabled)
        _swipeDeleteEnabled.value = enabled
    }

    fun setAutoCapitalization(enabled: Boolean) {
        Prefs.setAutoCapitalization(context, enabled)
        _autoCapEnabled.value = enabled
    }

    fun setDoubleSpacePeriod(enabled: Boolean) {
        Prefs.setDoubleSpacePeriod(context, enabled)
        _doubleSpaceEnabled.value = enabled
    }

    fun setDarkTheme(enabled: Boolean) {
        Prefs.setDarkTheme(context, enabled)
        _darkThemeEnabled.value = enabled
    }

    fun setKeyboardSize(size: Int) {
        Prefs.setKeyboardSize(context, size)
        _keyboardSize.value = size
    }

    fun setNumberRow(enabled: Boolean) {
        Prefs.setShowNumberRow(context, enabled)
        _numberRowEnabled.value = enabled
    }

    fun addPhrase(phrase: String, devanagari: String, category: String = "general") {
        viewModelScope.launch {
            phraseRepository.addPhrase(phrase, devanagari, category)
        }
    }

    fun deletePhrase(phrase: String) {
        viewModelScope.launch {
            phraseRepository.deletePhrase(phrase)
        }
    }
}
