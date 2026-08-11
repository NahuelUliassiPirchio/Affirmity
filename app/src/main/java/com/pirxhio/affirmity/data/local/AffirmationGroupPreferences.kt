package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.affirmationGroupsDataStore by preferencesDataStore(name = "affirmation_group_prefs")

/**
 * Narrow contract extracted so [com.pirxhio.affirmity.data.AffirmityAppState] can be unit-tested
 * with a fake, without needing an Android [Context] (design risk #4).
 */
interface GroupSelectionPreferences {
    /** Null means "no selection has ever been persisted" (first-ever launch) — distinct from an
     * empty set, which would mean the user explicitly cleared everything. */
    fun observeSelectedGroupIds(): Flow<Set<String>?>

    suspend fun saveSelectedGroupIds(ids: Set<String>)
}

/**
 * Device-local group-selection preference — deliberately NOT part of the `DataSession`/Firestore
 * sync surface (no repository interface, no migrator entry), mirroring how meditation duration
 * worked before it was synced.
 */
class AffirmationGroupPreferences(private val context: Context) : GroupSelectionPreferences {

    override fun observeSelectedGroupIds(): Flow<Set<String>?> =
        context.affirmationGroupsDataStore.data.map { it[SELECTED_GROUP_IDS] }

    override suspend fun saveSelectedGroupIds(ids: Set<String>) {
        context.affirmationGroupsDataStore.edit { it[SELECTED_GROUP_IDS] = ids }
    }

    private companion object {
        val SELECTED_GROUP_IDS = stringSetPreferencesKey("selected_group_ids")
    }
}
