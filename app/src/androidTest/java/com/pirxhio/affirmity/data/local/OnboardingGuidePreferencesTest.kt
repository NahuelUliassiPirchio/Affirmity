package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers task 1.1: [OnboardingGuidePreferences] persistence contract (spec R2.1-R2.3, design D1).
 * Mirrors [AdUnlockDaoTest]'s in-app-context/`@RunWith(AndroidJUnit4::class)` convention. Requires
 * a connected device/emulator (`connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class OnboardingGuidePreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun observeHasSeenGuide_emitsNullWhenKeyAbsent() = runBlocking {
        val prefs = OnboardingGuidePreferences(context)

        val value = prefs.observeHasSeenGuide().first()

        assertNull(value)
    }

    @Test
    fun arm_writesFalse() = runBlocking {
        val prefs = OnboardingGuidePreferences(context)

        prefs.arm()

        assertEquals(false, prefs.observeHasSeenGuide().first())
    }

    @Test
    fun markSeen_writesTrue() = runBlocking {
        val prefs = OnboardingGuidePreferences(context)

        prefs.markSeen()

        assertEquals(true, prefs.observeHasSeenGuide().first())
    }

    @Test
    fun markSeen_afterArm_overwritesFalseWithTrue() = runBlocking {
        val prefs = OnboardingGuidePreferences(context)

        prefs.arm()
        prefs.markSeen()

        assertEquals(true, prefs.observeHasSeenGuide().first())
    }
}
