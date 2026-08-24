package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingGuideDataStore by preferencesDataStore(name = "onboarding_guide_prefs")

/**
 * Whether the post-survey onboarding guide has been seen on this device (spec R2, design D1).
 * Own DataStore file/prefs class, independent of [OnboardingPreferences] -- survey-completed and
 * guide-seen are distinct lifecycles (D1 rationale).
 *
 * Tri-state: `null` means the key has never been written (legacy install, see
 * [com.pirxhio.affirmity.data.resolveGuideBackfill] for the migration-default resolution), `false`
 * means armed (guide owed), `true` means seen.
 */
class OnboardingGuidePreferences(private val context: Context) {

    fun observeHasSeenGuide(): Flow<Boolean?> =
        context.onboardingGuideDataStore.data.map { it[HAS_SEEN_ONBOARDING_GUIDE] }

    /** Called from `completeOnboarding()` -- arms the auto-show gate (writes `false`). */
    suspend fun arm() {
        context.onboardingGuideDataStore.edit { it[HAS_SEEN_ONBOARDING_GUIDE] = false }
    }

    /** Called on Skip/complete/manual-close -- commits "seen" (writes `true`). */
    suspend fun markSeen() {
        context.onboardingGuideDataStore.edit { it[HAS_SEEN_ONBOARDING_GUIDE] = true }
    }

    private companion object {
        val HAS_SEEN_ONBOARDING_GUIDE = booleanPreferencesKey("has_seen_onboarding_guide")
    }
}
