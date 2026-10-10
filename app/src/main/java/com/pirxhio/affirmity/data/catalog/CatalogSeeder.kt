package com.pirxhio.affirmity.data.catalog

import com.pirxhio.affirmity.data.local.CatalogAffirmationDao
import com.pirxhio.affirmity.data.local.CatalogPreferences
import com.pirxhio.affirmity.ui.groups.catalogCollectionsById
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bundled-asset-first seeding (design D2/D13). Runs off the main thread at app start.
 * Marker AFTER the transaction: a crash between them costs one redundant re-seed, never a
 * half-populated catalog -- [dao.replaceAll] is a full replace, so re-running is a no-op.
 */
class CatalogSeeder(
    private val assetReader: CatalogAssetReader,
    private val dao: CatalogAffirmationDao,
    private val prefs: CatalogPreferences,
    /** Effective catalog locale, consulted ONLY by the no-arg [seedIfNeeded] and only INSIDE the lock. */
    private val currentLocale: () -> CatalogLocale = { CatalogLocale.resolve() },
    private val knownCollectionIds: () -> Set<String> = { catalogCollectionsById().keys },
) {

    /**
     * No-op when the stored marker equals `"<bundled version>|<locale tag>"` AND the table holds
     * rows. Otherwise a single transactional full replace, then the marker (REQ-LOC-4/7).
     *
     * Everything -- locale resolution, asset read, replace, marker -- runs inside one process-wide
     * lock (design D4): a no-arg call always resolves the locale AFTER any in-flight seed finishes,
     * so the last no-arg seed after a locale change decides the final table. Only the Settings
     * pre-seed passes an explicit [locale].
     */
    suspend fun seedIfNeeded(locale: CatalogLocale? = null) = mutex.withLock {
        val target = locale ?: currentLocale()
        val bundled = CatalogAssetParser.parse(assetReader.readCatalogJson(target), knownCollectionIds())
        val marker = "${bundled.version}|${target.tag}"
        val seededMarker = prefs.observeSeededCatalogVersion().first()
        // The marker lives in DataStore, which Auto Backup restores, while the Room DB is excluded
        // from backup. A matching marker over an empty table therefore means "restored/cleared
        // DB", not "already seeded" -- trusting the marker alone leaves the catalog empty forever.
        if (seededMarker == marker && dao.count() > 0) return@withLock

        dao.replaceAll(bundled.affirmations)
        // MARKER LAST (design D13): if this throws, the rows are already committed and the next
        // call simply re-seeds (a harmless, idempotent replace), never leaving a half-seeded state.
        prefs.saveSeededCatalogVersion(marker)
    }

    private companion object {
        val mutex = Mutex()
    }
}
