package com.pirxhio.affirmity.data.repository

/**
 * Bundles the four store-agnostic repositories so a swap is atomic — see design.md's "Swap
 * granularity" decision: a bundle cannot be half-swapped (e.g. affirmations on Firestore,
 * completions still on Room).
 *
 * - [Local]: signed-out session, backed by Room/DataStore.
 * - [Migrating]: transient state during the one-time migration for [uid]; reads delegate to the
 *   [local] session so the UI keeps showing current data with no flicker, while writers suspend
 *   until the session leaves this state (see `AffirmityAppState`'s `ready()` in stage 3).
 * - [Remote]: signed-in, migrated session for [uid], backed exclusively by Firestore.
 */
sealed interface DataSession {
    val affirmations: AffirmationRepository
    val completions: DailyCompletionRepository
    val moods: DailyMoodRepository
    val healerUses: StreakHealerRepository
    val meditation: MeditationPreferencesRepository
    val notifications: NotificationSettingsRepository

    class Local(
        override val affirmations: AffirmationRepository,
        override val completions: DailyCompletionRepository,
        override val moods: DailyMoodRepository,
        override val healerUses: StreakHealerRepository,
        override val meditation: MeditationPreferencesRepository,
        override val notifications: NotificationSettingsRepository,
    ) : DataSession

    class Migrating(
        val uid: String,
        private val local: Local,
    ) : DataSession {
        override val affirmations: AffirmationRepository get() = local.affirmations
        override val completions: DailyCompletionRepository get() = local.completions
        override val moods: DailyMoodRepository get() = local.moods
        override val healerUses: StreakHealerRepository get() = local.healerUses
        override val meditation: MeditationPreferencesRepository get() = local.meditation
        override val notifications: NotificationSettingsRepository get() = local.notifications
    }

    class Remote(
        val uid: String,
        override val affirmations: AffirmationRepository,
        override val completions: DailyCompletionRepository,
        override val moods: DailyMoodRepository,
        override val healerUses: StreakHealerRepository,
        override val meditation: MeditationPreferencesRepository,
        override val notifications: NotificationSettingsRepository,
    ) : DataSession
}
