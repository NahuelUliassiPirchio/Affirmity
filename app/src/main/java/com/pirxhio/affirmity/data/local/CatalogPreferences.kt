package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.catalogDataStore by preferencesDataStore(name = "catalog_prefs")

/**
 * Narrow contract extracted so `CatalogSeeder` can be unit-tested with a fake, without needing an
 * Android [Context] (design D13). The seed marker: `null` means "never seeded".
 */
interface CatalogPreferences {
    fun observeSeededCatalogVersion(): Flow<String?>
    suspend fun saveSeededCatalogVersion(version: String)
}

/**
 * DataStore-backed seed marker (design D13). Written AFTER the seeding transaction commits: a
 * crash between them costs one redundant re-seed on next launch (the transaction is a full
 * replace, so re-running is a no-op by construction), never a half-populated catalog.
 */
class AndroidCatalogPreferences(private val context: Context) : CatalogPreferences {

    override fun observeSeededCatalogVersion(): Flow<String?> =
        context.catalogDataStore.data.map { it[SEEDED_CATALOG_VERSION] }

    override suspend fun saveSeededCatalogVersion(version: String) {
        context.catalogDataStore.edit { it[SEEDED_CATALOG_VERSION] = version }
    }

    private companion object {
        val SEEDED_CATALOG_VERSION = stringPreferencesKey("seeded_catalog_version")
    }
}
