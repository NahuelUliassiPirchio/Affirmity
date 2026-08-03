package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

/** Whether the first-launch onboarding flow has been completed on this device. */
class OnboardingPreferences(private val context: Context) {

    fun observeHasCompletedOnboarding(): Flow<Boolean> =
        context.onboardingDataStore.data.map { it[HAS_COMPLETED_ONBOARDING] ?: false }

    suspend fun setCompleted() {
        context.onboardingDataStore.edit { it[HAS_COMPLETED_ONBOARDING] = true }
    }

    private companion object {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    }
}
