package com.pirxhio.affirmity.data.remote

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

    fun settingsPreferencesDoc(uid: String): String = "users/$uid/settings/preferences"

    fun migratedMarkerDoc(uid: String): String = "users/$uid/meta/migrated"
}
