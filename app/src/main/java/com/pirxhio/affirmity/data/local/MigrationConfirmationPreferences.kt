package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val STORE_NAME = "migration_confirmations"

private val Context.migrationConfirmationDataStore by preferencesDataStore(name = STORE_NAME)

/**
 * Device-local record of which uids are known to have their server-side legacy-migration marker.
 * It lives in its own DataStore file so it can be excluded from Auto Backup: a restored install has
 * no business trusting a confirmation made on another device, so it must re-check the server once.
 * Keyed strictly by uid: confirming one account never covers another.
 */
class MigrationConfirmationPreferences(private val context: Context) {

    /** An unreadable or corrupt file reads as "nothing confirmed": this store is an optional cache. */
    suspend fun isConfirmed(uid: String): Boolean {
        val prefs = context.migrationConfirmationDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        return uid in (prefs[CONFIRMED_UIDS] ?: emptySet())
    }

    suspend fun confirm(uid: String) {
        context.migrationConfirmationDataStore.edit { prefs ->
            prefs[CONFIRMED_UIDS] = (prefs[CONFIRMED_UIDS] ?: emptySet()) + uid
        }
    }

    companion object {
        /** `filesDir`-relative path of the DataStore file; the backup rules must exclude exactly this. */
        const val BACKUP_EXCLUDED_PATH = "datastore/$STORE_NAME.preferences_pb"
        private val CONFIRMED_UIDS = stringSetPreferencesKey("confirmed_uids")
    }
}
