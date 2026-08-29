package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.access.ContentKey

/**
 * Pure Firestore path builders for the `users/{uid}` schema (design.md's "Per-User Collection
 * Schema" section). No Firebase/Android dependency — unit-tested directly.
 */
object FirestorePaths {
    fun affirmationsCollection(uid: String): String = "users/$uid/affirmations"

    fun affirmationDoc(uid: String, id: String): String = "${affirmationsCollection(uid)}/$id"

    fun dailyCompletionsCollection(uid: String): String = "users/$uid/dailyCompletions"

    fun dailyCompletionDoc(uid: String, epochDay: Long): String =
        "${dailyCompletionsCollection(uid)}/$epochDay"

    fun dailyMoodsCollection(uid: String): String = "users/$uid/dailyMoods"

    fun dailyMoodDoc(uid: String, epochDay: Long): String =
        "${dailyMoodsCollection(uid)}/$epochDay"

    fun settingsPreferencesDoc(uid: String): String = "users/$uid/settings/preferences"

    fun migratedMarkerDoc(uid: String): String = "users/$uid/meta/migrated"

    fun onboardingMarkerDoc(uid: String): String = "users/$uid/meta/onboarded"

    fun fcmTokensCollection(uid: String): String = "users/$uid/fcmTokens"

    fun fcmTokenDoc(uid: String, token: String): String = "${fcmTokensCollection(uid)}/$token"

    fun streakHealerUsesCollection(uid: String): String = "users/$uid/streakHealerUses"

    fun streakHealerUseDoc(uid: String, healedEpochDay: Long): String =
        "${streakHealerUsesCollection(uid)}/$healedEpochDay"

    fun entitlementsCollection(uid: String): String = "users/$uid/entitlements"

    fun entitlementDoc(uid: String): String = "${entitlementsCollection(uid)}/current"

    fun adUnlocksCollection(uid: String): String = "users/$uid/adUnlocks"

    fun adUnlockDoc(uid: String, key: ContentKey): String = "${adUnlocksCollection(uid)}/${key.storageKey}"

    /** TIMED_REPEATABLE grants (design D16). A SIBLING of [adUnlocksCollection], never a reuse of
     *  it: this one permits overwrite and [adUnlocksCollection] must never. */
    fun timedUnlocksCollection(uid: String): String = "users/$uid/timedUnlocks"

    fun timedUnlockDoc(uid: String, key: ContentKey): String =
        "${timedUnlocksCollection(uid)}/${key.storageKey}"
}
