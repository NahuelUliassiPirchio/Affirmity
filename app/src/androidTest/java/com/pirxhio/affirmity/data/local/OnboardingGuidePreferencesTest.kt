package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.io.File
import org.junit.After
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storeFile = File.createTempFile("onboarding_guide_prefs_test", ".preferences_pb", context.cacheDir)

    // Hermetic: every test gets its own DataStore file instead of the app's shared singleton, so
    // results never depend on test order or on state left by other classes.
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { storeFile }

    @After
    fun tearDown() {
        scope.cancel()
        storeFile.delete()
    }

    @Test
    fun observeHasSeenGuide_emitsNullWhenKeyAbsent() = runBlocking {
        val prefs = OnboardingGuidePreferences(dataStore)

        val value = prefs.observeHasSeenGuide().first()

        assertNull(value)
    }

    @Test
    fun arm_writesFalse() = runBlocking {
        val prefs = OnboardingGuidePreferences(dataStore)

        prefs.arm()

        assertEquals(false, prefs.observeHasSeenGuide().first())
    }

    @Test
    fun markSeen_writesTrue() = runBlocking {
        val prefs = OnboardingGuidePreferences(dataStore)

        prefs.markSeen()

        assertEquals(true, prefs.observeHasSeenGuide().first())
    }

    @Test
    fun markSeen_afterArm_overwritesFalseWithTrue() = runBlocking {
        val prefs = OnboardingGuidePreferences(dataStore)

        prefs.arm()
        prefs.markSeen()

        assertEquals(true, prefs.observeHasSeenGuide().first())
    }
}
