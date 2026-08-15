package com.pirxhio.affirmity.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class FirestorePathsTest {

    @Test
    fun `affirmation doc path is scoped under the user's affirmations collection`() {
        assertEquals("users/uid-1/affirmations/aff-1", FirestorePaths.affirmationDoc("uid-1", "aff-1"))
    }

    @Test
    fun `daily completion doc path uses the stringified epochDay as the doc id`() {
        assertEquals("users/uid-1/dailyCompletions/19876", FirestorePaths.dailyCompletionDoc("uid-1", 19_876L))
    }

    @Test
    fun `settings preferences doc path is a single shared document`() {
        assertEquals("users/uid-1/settings/preferences", FirestorePaths.settingsPreferencesDoc("uid-1"))
    }

    @Test
    fun `migrated marker doc path`() {
        assertEquals("users/uid-1/meta/migrated", FirestorePaths.migratedMarkerDoc("uid-1"))
    }

    @Test
    fun `fcm tokens collection path is scoped under the user`() {
        assertEquals("users/uid-1/fcmTokens", FirestorePaths.fcmTokensCollection("uid-1"))
    }

    @Test
    fun `fcm token doc path uses the token as the doc id`() {
        assertEquals("users/uid-1/fcmTokens/token-abc", FirestorePaths.fcmTokenDoc("uid-1", "token-abc"))
    }

    @Test
    fun `streak healer uses collection path is scoped under the user`() {
        assertEquals("users/uid-1/streakHealerUses", FirestorePaths.streakHealerUsesCollection("uid-1"))
    }

    @Test
    fun `streak healer use doc path uses the stringified healedEpochDay as the doc id`() {
        assertEquals("users/uid-1/streakHealerUses/19876", FirestorePaths.streakHealerUseDoc("uid-1", 19_876L))
    }

    @Test
    fun `entitlements collection path is scoped under the user`() {
        assertEquals("users/uid-1/entitlements", FirestorePaths.entitlementsCollection("uid-1"))
    }

    @Test
    fun `entitlement doc path is the single shared 'current' document`() {
        assertEquals("users/uid-1/entitlements/current", FirestorePaths.entitlementDoc("uid-1"))
    }
}
