package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.affirmationThemesDataStore by preferencesDataStore(name = "affirmation_theme_prefs")

/**
 * Narrow contract extracted so [com.pirxhio.affirmity.data.AffirmityAppState] can be unit-tested
 * with a fake, without needing an Android [Context] -- same shape as the group-level
 * `GroupSelectionPreferences` this replaces ("Your feed" refactor §2).
 */
interface ThemeSelectionPreferences {
    /** Null means "no theme selection has ever been persisted" (first-ever launch, or an install
     * that predates this change and has never resolved the legacy migration) — distinct from an
     * empty set, which would mean the user explicitly cleared everything. */
    fun observeSelectedThemeIds(): Flow<Set<String>?>

    suspend fun saveSelectedThemeIds(ids: Set<String>)
}

/**
 * Device-local theme-selection preference, replacing `AffirmationGroupPreferences` — deliberately
 * NOT part of the `DataSession`/Firestore sync surface, mirroring its predecessor.
 */
class AffirmationThemePreferences(private val context: Context) : ThemeSelectionPreferences {

    override fun observeSelectedThemeIds(): Flow<Set<String>?> =
        context.affirmationThemesDataStore.data.map { it[SELECTED_THEME_IDS] }

    override suspend fun saveSelectedThemeIds(ids: Set<String>) {
        context.affirmationThemesDataStore.edit { it[SELECTED_THEME_IDS] = ids }
    }

    private companion object {
        val SELECTED_THEME_IDS = stringSetPreferencesKey("selected_theme_ids")
    }
}

/** The now-deleted `AffirmationGroupPreferences`' DataStore file/key, kept alive here as a
 * READ-ONLY path (scope decision #4): [AffirmityAppState][com.pirxhio.affirmity.data.AffirmityAppState]'s
 * one-time legacy migration needs to read a pre-existing install's `selected_group_ids` exactly
 * once, even though nothing writes to this file anymore. */
private val Context.legacyAffirmationGroupsDataStore by preferencesDataStore(name = "affirmation_group_prefs")
private val LEGACY_SELECTED_GROUP_IDS = stringSetPreferencesKey("selected_group_ids")

/** One-shot read of the legacy group-id selection, for [com.pirxhio.affirmity.data.resolveSelectedThemeIds]'s
 * one-time migration path only. `null` means either a fresh install, or an install that already
 * migrated (irrelevant either way once the theme-prefs store has its own persisted value). */
suspend fun readLegacySelectedGroupIds(context: Context): Set<String>? =
    context.legacyAffirmationGroupsDataStore.data.first()[LEGACY_SELECTED_GROUP_IDS]
