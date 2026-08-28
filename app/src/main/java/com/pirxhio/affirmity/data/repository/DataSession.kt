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
    val entitlements: EntitlementRepository

    // No default on either Local or Remote: unlike `entitlements` (Local's Free default is
    // intentional), a default here would silently drop durable ad-unlock grant data -- the exact
    // bug class this comment on `entitlements` warns against, reused deliberately (design §4a).
    val adUnlocks: AdUnlockRepository

    /** Per-user overrides on shared catalog rows (design D9, revised). Defaulted to
     *  [NoOpCatalogOverrideRepository] on both [Local] and [Remote] -- unlike [adUnlocks], an
     *  empty override map is a safe, forward-compatible default: the bundled v1.0.0 catalog has
     *  no bracket tokens to override (D11), so no test fixture that omits this argument silently
     *  loses meaningful data. */
    val catalogOverrides: CatalogOverrideRepository

    class Local(
        override val affirmations: AffirmationRepository,
        override val completions: DailyCompletionRepository,
        override val moods: DailyMoodRepository,
        override val healerUses: StreakHealerRepository,
        override val meditation: MeditationPreferencesRepository,
        override val notifications: NotificationSettingsRepository,
        override val entitlements: EntitlementRepository = LocalFreeEntitlementRepository(),
        override val adUnlocks: AdUnlockRepository,
        override val catalogOverrides: CatalogOverrideRepository = NoOpCatalogOverrideRepository,
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
        override val entitlements: EntitlementRepository get() = local.entitlements
        override val adUnlocks: AdUnlockRepository get() = local.adUnlocks
        override val catalogOverrides: CatalogOverrideRepository get() = local.catalogOverrides
    }

    class Remote(
        val uid: String,
        override val affirmations: AffirmationRepository,
        override val completions: DailyCompletionRepository,
        override val moods: DailyMoodRepository,
        override val healerUses: StreakHealerRepository,
        override val meditation: MeditationPreferencesRepository,
        override val notifications: NotificationSettingsRepository,
        // No default: a signed-in Remote session must never silently fall back to Free -- that
        // would mask a real wiring bug instead of failing loudly at the `rememberAffirmityAppState`
        // call site (unlike Local, where "signed-out = Free" is the correct, intentional default).
        override val entitlements: EntitlementRepository,
        override val adUnlocks: AdUnlockRepository,
        override val catalogOverrides: CatalogOverrideRepository = NoOpCatalogOverrideRepository,
    ) : DataSession
}
