package com.romannepali.keyboard.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.romannepali.keyboard.HapticFeedbackManager
import com.romannepali.keyboard.Prefs
import com.romannepali.keyboard.R
import com.romannepali.keyboard.clipboard.ClipboardManager
import com.romannepali.keyboard.dictionary.room.NepaliSeedData
import com.romannepali.keyboard.suggestion.SuggestionEngine
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val engine by lazy { SuggestionEngine(this) }
    private val clipboardManager by lazy { ClipboardManager.get(this) }
    private val hapticManager by lazy { HapticFeedbackManager(this) }

    private enum class DictTab { SAVED, LEARNED, ROOM }
    private var currentDictTab = DictTab.SAVED

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            if (Prefs.darkTheme(this)) R.style.Theme_MeroType_Dark
            else R.style.Theme_MeroType_Light
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        setupStatusAndWizard()
        setupPlayground()
        setupTypingPreferences()
        setupHapticsAndAudio()
        setupLayoutAndAppearance()
        setupDictionary()
        setupClipboard()
        setupAbout()
    }

    override fun onResume() {
        super.onResume()
        updateKeyboardStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun setupStatusAndWizard() {
        findViewById<MaterialButton>(R.id.btn_enable_keyboard).setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.btn_switch_keyboard).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        updateKeyboardStatus()
    }

    private fun updateKeyboardStatus() {
        val badge = findViewById<TextView>(R.id.text_status_badge)
        val isEnabled = isKeyboardEnabled()
        val isDefault = isKeyboardDefault()

        when {
            isEnabled && isDefault -> {
                badge.text = getString(R.string.status_active)
                badge.setBackgroundResource(R.drawable.bg_suggestion_chip_dark_selected)
                badge.setTextColor(Color.WHITE)
            }
            isEnabled -> {
                badge.text = getString(R.string.status_enabled)
                badge.setBackgroundResource(R.drawable.bg_suggestion_chip_dark)
                badge.setTextColor(Color.parseColor("#FFFF6B4A"))
            }
            else -> {
                badge.text = getString(R.string.status_inactive)
                badge.setBackgroundResource(R.drawable.bg_suggestion_chip_dark)
                badge.setTextColor(Color.parseColor("#FFFF8A72"))
            }
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isKeyboardDefault(): Boolean {
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return defaultIme?.contains(packageName) == true
    }

    private fun setupPlayground() {
        val input = findViewById<EditText>(R.id.playground_input)
        findViewById<MaterialButton>(R.id.btn_clear_playground).setOnClickListener {
            input.text?.clear()
        }

        findViewById<TextView>(R.id.chip_sample_namaste).setOnClickListener {
            input.append("namaste ")
        }
        findViewById<TextView>(R.id.chip_sample_kasto).setOnClickListener {
            input.append("kasto cha ")
        }
        findViewById<TextView>(R.id.chip_sample_nepal).setOnClickListener {
            input.append("mero desh ")
        }
    }

    private fun setupTypingPreferences() {
        val devanagari = findViewById<SwitchMaterial>(R.id.switch_devanagari)
        devanagari.isChecked = Prefs.devanagari(this)
        devanagari.setOnCheckedChangeListener { _, checked ->
            Prefs.setDevanagari(this, checked)
        }

        val suggestions = findViewById<SwitchMaterial>(R.id.switch_suggestions)
        suggestions.isChecked = Prefs.suggestions(this)
        suggestions.setOnCheckedChangeListener { _, checked ->
            Prefs.setSuggestions(this, checked)
        }

        val swipeDelete = findViewById<SwitchMaterial>(R.id.switch_swipe_delete)
        swipeDelete.isChecked = Prefs.swipeDeleteWord(this)
        swipeDelete.setOnCheckedChangeListener { _, checked ->
            Prefs.setSwipeDeleteWord(this, checked)
        }

        val autoCap = findViewById<SwitchMaterial>(R.id.switch_auto_cap)
        autoCap.isChecked = Prefs.autoCapitalization(this)
        autoCap.setOnCheckedChangeListener { _, checked ->
            Prefs.setAutoCapitalization(this, checked)
        }

        val doubleSpace = findViewById<SwitchMaterial>(R.id.switch_double_space)
        doubleSpace.isChecked = Prefs.doubleSpacePeriod(this)
        doubleSpace.setOnCheckedChangeListener { _, checked ->
            Prefs.setDoubleSpacePeriod(this, checked)
        }
    }

    private fun setupHapticsAndAudio() {
        val vibration = findViewById<SwitchMaterial>(R.id.switch_vibration)
        val intensityLayout = findViewById<View>(R.id.layout_vibration_intensity)
        vibration.isChecked = Prefs.vibration(this)
        intensityLayout.visibility = if (vibration.isChecked) View.VISIBLE else View.GONE
        vibration.setOnCheckedChangeListener { _, checked ->
            Prefs.setVibration(this, checked)
            intensityLayout.visibility = if (checked) View.VISIBLE else View.GONE
        }

        val slider = findViewById<Slider>(R.id.slider_vibration_intensity)
        val valueLabel = findViewById<TextView>(R.id.text_vibration_intensity_value)
        val initialIntensity = Prefs.vibrationIntensity(this)
        slider.value = initialIntensity.toFloat()
        valueLabel.text = intensityLabel(initialIntensity)
        slider.addOnChangeListener { _, value, _ ->
            val intVal = value.toInt()
            Prefs.setVibrationIntensity(this, intVal)
            valueLabel.text = intensityLabel(intVal)
        }

        findViewById<MaterialButton>(R.id.btn_test_vibration).setOnClickListener {
            hapticManager.testVibration()
        }

        val keyAudio = findViewById<SwitchMaterial>(R.id.switch_key_audio)
        keyAudio.isChecked = Prefs.sound(this)
        keyAudio.setOnCheckedChangeListener { _, checked ->
            Prefs.setSound(this, checked)
        }
    }

    private fun intensityLabel(intensity: Int): String = when {
        intensity < 35 -> "$intensity% (Subtle)"
        intensity > 70 -> "$intensity% (Firm)"
        else -> "$intensity% (Balanced)"
    }

    private fun setupLayoutAndAppearance() {
        val dark = findViewById<SwitchMaterial>(R.id.switch_dark_theme)
        dark.isChecked = Prefs.darkTheme(this)
        dark.setOnCheckedChangeListener { _, checked ->
            Prefs.setDarkTheme(this, checked)
            recreate()
        }

        val sizeSlider = findViewById<Slider>(R.id.slider_keyboard_size)
        val sizeValue = findViewById<TextView>(R.id.text_keyboard_size_value)
        sizeSlider.value = Prefs.keyboardSize(this).toFloat()
        sizeValue.text = getString(keyboardSizeLabel(Prefs.keyboardSize(this)))
        sizeSlider.addOnChangeListener { _, value, _ ->
            Prefs.setKeyboardSize(this, value.toInt())
            sizeValue.text = getString(keyboardSizeLabel(value.toInt()))
        }

        val numberRow = findViewById<SwitchMaterial>(R.id.switch_number_row)
        numberRow.isChecked = Prefs.showNumberRow(this)
        numberRow.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowNumberRow(this, checked)
        }
    }

    private fun keyboardSizeLabel(size: Int): Int = when {
        size < 35 -> R.string.keyboard_size_small
        size > 65 -> R.string.keyboard_size_large
        else -> R.string.keyboard_size_normal
    }

    private fun setupDictionary() {
        val tabSaved = findViewById<TextView>(R.id.tab_saved)
        val tabLearned = findViewById<TextView>(R.id.tab_learned)
        val tabRoom = findViewById<TextView>(R.id.tab_room)

        fun updateTabsUi() {
            tabSaved.setBackgroundResource(if (currentDictTab == DictTab.SAVED) R.drawable.bg_suggestion_chip_dark_selected else R.drawable.bg_suggestion_chip_dark)
            tabLearned.setBackgroundResource(if (currentDictTab == DictTab.LEARNED) R.drawable.bg_suggestion_chip_dark_selected else R.drawable.bg_suggestion_chip_dark)
            tabRoom.setBackgroundResource(if (currentDictTab == DictTab.ROOM) R.drawable.bg_suggestion_chip_dark_selected else R.drawable.bg_suggestion_chip_dark)
            tabSaved.setTextColor(if (currentDictTab == DictTab.SAVED) Color.WHITE else Color.GRAY)
            tabLearned.setTextColor(if (currentDictTab == DictTab.LEARNED) Color.WHITE else Color.GRAY)
            tabRoom.setTextColor(if (currentDictTab == DictTab.ROOM) Color.WHITE else Color.GRAY)
            renderDictionaryTab()
        }

        tabSaved.setOnClickListener {
            currentDictTab = DictTab.SAVED
            updateTabsUi()
        }
        tabLearned.setOnClickListener {
            currentDictTab = DictTab.LEARNED
            updateTabsUi()
        }
        tabRoom.setOnClickListener {
            currentDictTab = DictTab.ROOM
            updateTabsUi()
        }

        val input = findViewById<EditText>(R.id.input_new_word)
        findViewById<MaterialButton>(R.id.btn_add_word).setOnClickListener {
            val word = input.text.toString().trim()
            when {
                word.isEmpty() -> Toast.makeText(this, R.string.empty_word, Toast.LENGTH_SHORT).show()
                !engine.addPersonalWord(word) -> Toast.makeText(this, R.string.word_invalid, Toast.LENGTH_SHORT).show()
                else -> {
                    input.text?.clear()
                    Toast.makeText(this, getString(R.string.word_added, word), Toast.LENGTH_SHORT).show()
                    currentDictTab = DictTab.SAVED
                    updateTabsUi()
                }
            }
        }

        findViewById<MaterialButton>(R.id.btn_clear_learned).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_learned)
                .setMessage("Clear all words learned from your typing?")
                .setPositiveButton("Clear") { _, _ ->
                    val cleared = engine.getLearnedWords().toMap()
                    engine.clearLearnedWords()
                    renderDictionaryTab()
                    snackbar(getString(R.string.clear_learned_confirmed)) {
                        engine.restoreLearned(cleared)
                        renderDictionaryTab()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updateTabsUi()
    }

    private fun renderDictionaryTab() {
        val container = findViewById<LinearLayout>(R.id.dict_items_container)
        val empty = findViewById<TextView>(R.id.dict_empty)
        val clearBtn = findViewById<View>(R.id.btn_clear_learned)
        container.removeAllViews()

        when (currentDictTab) {
            DictTab.SAVED -> {
                clearBtn.visibility = View.GONE
                val words = engine.getSavedWords()
                empty.text = getString(R.string.no_saved_words)
                empty.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
                words.forEach { word ->
                    container.addView(createWordRow(word, subtitle = "Personal") {
                        engine.removePersonalWord(word)
                        renderDictionaryTab()
                        snackbar(getString(R.string.word_removed_confirmed, word)) {
                            engine.addPersonalWord(word)
                            renderDictionaryTab()
                        }
                    })
                }
            }

            DictTab.LEARNED -> {
                val words = engine.getLearnedWords()
                clearBtn.visibility = if (words.isNotEmpty()) View.VISIBLE else View.GONE
                empty.text = getString(R.string.no_learned_words)
                empty.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
                words.forEach { (word, count) ->
                    container.addView(createWordRow(word, subtitle = "Typed $count times") {
                        val backup = engine.getLearnedWords().firstOrNull { it.first == word }
                        engine.removeLearnedWord(word)
                        renderDictionaryTab()
                        snackbar(getString(R.string.word_removed_confirmed, word)) {
                            backup?.let { engine.restoreLearned(mapOf(it)) }
                            renderDictionaryTab()
                        }
                    })
                }
            }

            DictTab.ROOM -> {
                clearBtn.visibility = View.GONE
                val seedWords = NepaliSeedData.COMMON_NEPALI_WORDS.take(30)
                empty.visibility = View.GONE
                seedWords.forEach { (roman, devanagari, freq) ->
                    container.addView(createWordRow(roman, subtitle = "$devanagari · Rank $freq", canDelete = false) {})
                }
            }
        }
    }

    private fun createWordRow(
        word: String,
        subtitle: String = "",
        canDelete: Boolean = true,
        onDelete: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(this).apply {
            text = word
            textSize = 15f
            setTextColor(Color.parseColor(if (Prefs.darkTheme(this@SettingsActivity)) "#E8EAED" else "#202124"))
        }
        textLayout.addView(title)

        if (subtitle.isNotEmpty()) {
            val sub = TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor(if (Prefs.darkTheme(this@SettingsActivity)) "#9AA0A6" else "#5F6368"))
            }
            textLayout.addView(sub)
        }
        row.addView(textLayout)

        if (canDelete) {
            val deleteBtn = TextView(this).apply {
                text = "✕"
                textSize = 18f
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setTextColor(Color.parseColor("#FFFF6B4A"))
                setOnClickListener { onDelete() }
            }
            row.addView(deleteBtn)
        }

        return row
    }

    private fun setupClipboard() {
        findViewById<MaterialButton>(R.id.btn_clear_clipboard).setOnClickListener {
            val cleared = clipboardManager.getHistory()
            if (cleared.isEmpty()) return@setOnClickListener
            clipboardManager.clearHistory()
            refreshClipboard()
            snackbar(getString(R.string.clear_clipboard_confirmed)) {
                cleared.reversed().forEach { clipboardManager.copy(it.text) }
                refreshClipboard()
            }
        }
        refreshClipboard()
    }

    private fun refreshClipboard() {
        val container = findViewById<LinearLayout>(R.id.clipboard_list)
        val empty = findViewById<TextView>(R.id.clipboard_empty)
        container.removeAllViews()

        val clips = clipboardManager.getHistory().take(6)
        empty.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE

        clips.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }

            val text = TextView(this).apply {
                this.text = item.text
                textSize = 14f
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(Color.parseColor(if (Prefs.darkTheme(this@SettingsActivity)) "#E8EAED" else "#202124"))
            }

            val remove = TextView(this).apply {
                this.text = "✕"
                textSize = 18f
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setTextColor(Color.parseColor("#FFFF6B4A"))
                setOnClickListener {
                    clipboardManager.removeByText(item.text)
                    refreshClipboard()
                    snackbar(getString(R.string.clip_removed_confirmed)) {
                        clipboardManager.copy(item.text)
                        refreshClipboard()
                    }
                }
            }

            row.addView(text)
            row.addView(remove)
            container.addView(row)
        }
    }

    private fun setupAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1.18"
        } catch (_: Exception) {
            "1.1.18"
        }
        findViewById<TextView>(R.id.about_version).text = "MeroType v$version · Fast, Offline & Private"
    }

    private fun snackbar(message: String, onUndo: () -> Unit) {
        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { onUndo() }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
