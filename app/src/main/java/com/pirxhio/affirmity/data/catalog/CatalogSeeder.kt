package com.pirxhio.affirmity.data.catalog

import com.pirxhio.affirmity.data.local.CatalogAffirmationDao
import com.pirxhio.affirmity.data.local.CatalogPreferences
import com.pirxhio.affirmity.ui.groups.catalogCollectionsById
import kotlinx.coroutines.flow.first

/**
 * Bundled-asset-first seeding (design D2/D13). Runs off the main thread at app start.
 * Marker AFTER the transaction: a crash between them costs one redundant re-seed, never a
 * half-populated catalog -- [dao.replaceAll] is a full replace, so re-running is a no-op.
 */
class CatalogSeeder(
    private val assetReader: CatalogAssetReader,
    private val dao: CatalogAffirmationDao,
    private val prefs: CatalogPreferences,
    private val knownCollectionIds: () -> Set<String> = { catalogCollectionsById().keys },
) {

    /** No-op when `prefs.observeSeededCatalogVersion() == bundled version` AND the table holds
     * rows. Idempotent by full replace otherwise. */
    suspend fun seedIfNeeded() {
        val bundled = CatalogAssetParser.parse(assetReader.readCatalogJson(), knownCollectionIds())
        val seededVersion = prefs.observeSeededCatalogVersion().first()
        // The marker lives in DataStore, which Auto Backup restores, while the Room DB is excluded
        // from backup. A matching marker over an empty table therefore means "restored/cleared
        // DB", not "already seeded" -- trusting the marker alone leaves the catalog empty forever.
        if (seededVersion == bundled.version && dao.count() > 0) return

        dao.replaceAll(bundled.affirmations)
        // MARKER LAST (design D13): if this throws, the rows are already committed and the next
        // call simply re-seeds (a harmless, idempotent replace), never leaving a half-seeded state.
        prefs.saveSeededCatalogVersion(bundled.version)
    }
}
