package com.pirxhio.affirmity.personalization.goals

import kotlinx.coroutines.flow.Flow

/** Answers collected by the onboarding survey's explicit confirmation action. */
data class OnboardingAnswers(
    val goalIds: Set<String>,
    val preferredTone: String?,
    val usageMoments: Set<String>?,
)

/** Context-free contract for device-local personalization preferences. */
interface UserGoalsStore {
    /** Null means that goal choices have never been persisted on this device. */
    fun observeGoalIds(): Flow<Set<String>?>

    fun observePreferredTone(): Flow<String?>

    fun observeUsageMoments(): Flow<Set<String>?>

    /** Explicit user actions only; observing or inference must never call this method. */
    suspend fun saveGoalIds(ids: Set<String>)

    suspend fun saveOnboardingAnswers(answers: OnboardingAnswers)
}
