package com.pirxhio.affirmity.personalization.goals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserGoalsPreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun absentEmptyAndOnboardingValuesRemainDistinctAcrossStoreRecreation() = runBlocking {
        // This is the only test that opens this process-wide preferencesDataStore delegate, so
        // removing its file before first access gives the contract a deterministic fresh install.
        context.filesDir.resolve("datastore/user_goals_prefs.preferences_pb").delete()
        val firstStore = UserGoalsPreferences(context)

        assertNull(firstStore.observeGoalIds().first())

        firstStore.saveGoalIds(emptySet())
        val afterExplicitEmpty = UserGoalsPreferences(context)
        assertEquals(emptySet<String>(), afterExplicitEmpty.observeGoalIds().first())

        firstStore.saveOnboardingAnswers(
            OnboardingAnswers(
                goalIds = setOf("calm", "direction"),
                preferredTone = "gentle",
                usageMoments = setOf("morning", "before_sleep"),
            ),
        )

        val recreatedStore = UserGoalsPreferences(context)

        assertEquals(setOf("calm", "direction"), recreatedStore.observeGoalIds().first())
        assertEquals("gentle", recreatedStore.observePreferredTone().first())
        assertEquals(setOf("morning", "before_sleep"), recreatedStore.observeUsageMoments().first())

        recreatedStore.saveOnboardingAnswers(
            OnboardingAnswers(
                goalIds = setOf("change"),
                preferredTone = null,
                usageMoments = null,
            ),
        )
        val afterNullableAnswers = UserGoalsPreferences(context)
        assertEquals(setOf("change"), afterNullableAnswers.observeGoalIds().first())
        assertNull(afterNullableAnswers.observePreferredTone().first())
        assertNull(afterNullableAnswers.observeUsageMoments().first())
    }
}
