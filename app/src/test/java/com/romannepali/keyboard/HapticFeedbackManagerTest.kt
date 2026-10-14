package com.romannepali.keyboard

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HapticFeedbackManagerTest {

    private lateinit var context: Context
    private lateinit var hapticManager: HapticFeedbackManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        hapticManager = HapticFeedbackManager(context)
        Prefs.setVibration(context, true)
        Prefs.setVibrationIntensity(context, 70)
    }

    @Test
    fun performKeyHaptic_executesWithoutErrorForAllTypes() {
        val view = View(context)

        HapticFeedbackManager.FeedbackType.entries.forEach { type ->
            hapticManager.performKeyHaptic(view, type)
            hapticManager.performKeyHaptic(null, type)
        }
    }

    @Test
    fun performKeyHaptic_respectsVibrationDisabledSetting() {
        Prefs.setVibration(context, false)
        val view = View(context)

        // Should return early and not crash
        hapticManager.performKeyHaptic(view, HapticFeedbackManager.FeedbackType.KEY_LETTER)
        hapticManager.performHaptic(HapticFeedbackManager.FeedbackType.KEY_SPACE)
    }

    @Test
    fun performKeyHaptic_respectsZeroIntensitySetting() {
        Prefs.setVibrationIntensity(context, 0)
        val view = View(context)

        hapticManager.performKeyHaptic(view, HapticFeedbackManager.FeedbackType.KEY_LETTER)
    }

    @Test
    fun testVibration_executesWithoutError() {
        hapticManager.testVibration()
        assertNotNull(hapticManager)
    }
}
