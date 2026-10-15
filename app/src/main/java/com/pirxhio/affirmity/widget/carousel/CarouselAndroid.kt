package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.data.DefaultGroups
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogAffirmationRef
import com.pirxhio.affirmity.data.remote.FirestoreAdUnlockRepository
import com.pirxhio.affirmity.data.remote.FirestoreAffirmationRepository
import com.pirxhio.affirmity.data.remote.FirestoreEntitlementRepository
import com.pirxhio.affirmity.data.repository.LocalFreeEntitlementRepository
import com.pirxhio.affirmity.data.repository.RoomAdUnlockRepository

/** [CarouselStateStore] over Glance's per-widget preferences store (design D3). */
class GlanceCarouselStateStore(
    private val context: Context,
    private val glanceId: GlanceId,
) : CarouselStateStore {

    override suspend fun update(transform: (CarouselState) -> CarouselState): CarouselState {
        var previous = CarouselState()
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs: Preferences ->
            previous = CarouselStateCodec.read(prefs)
            prefs.toMutablePreferences().also { CarouselStateCodec.write(it, transform(previous)) }
        }
        return previous
    }
}

/** Current state without changing it. */
suspend fun CarouselStateStore.read(): CarouselState = update { it }

/**
 * Production [CarouselDataPorts]: Room for the catalog, favorites and collections; owned
 * affirmations from Room when signed out and from Firestore (cache-first) when signed in (R6).
 */
class AppCarouselPorts(
    private val database: AffirmityDatabase,
    private val firestore: FirebaseFirestore?,
    private val uid: String?,
) : CarouselDataPorts {

    override suspend fun favoriteIds(): List<String> = database.favoriteAffirmationDao().getFavoriteIds()

    override suspend fun ownedAffirmations(): List<AffirmationEntity> =
        if (uid != null && firestore != null) {
            FirestoreAffirmationRepository(firestore, uid).observeAllWithSource().firstTrustworthyOwned()
        } else {
            database.affirmationDao().getAll()
        }

    override suspend fun collectionMembers(): Map<String, List<String>> =
        DefaultGroups.all.associate { it.id to it.affirmationIds } +
            database.userCollectionDao().getAll().associate { it.collection.id to it.affirmationIds }

    override suspend fun catalogRefsByGroup(groupId: String): List<CatalogAffirmationRef> =
        database.catalogAffirmationDao().getRefsByGroupId(groupId)

    override suspend fun allCatalogRefs(): List<CatalogAffirmationRef> =
        database.catalogAffirmationDao().getAllRefs()

    override suspend fun catalogRow(id: String): CatalogAffirmationEntity? =
        database.catalogAffirmationDao().getById(id)
}

/** Access reader wired to Firestore when signed in, or to Free plus Room grants when signed out. */
fun carouselAccessReader(database: AffirmityDatabase, firestore: FirebaseFirestore?, uid: String?): CarouselAccessReader =
    if (uid != null && firestore != null) {
        CarouselAccessReader(FirestoreEntitlementRepository(firestore, uid), FirestoreAdUnlockRepository(firestore, uid))
    } else {
        CarouselAccessReader(
            LocalFreeEntitlementRepository(),
            RoomAdUnlockRepository(database.adUnlockDao(), database.timedAdUnlockDao()),
        )
    }
