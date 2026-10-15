package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogAffirmationRef
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID

/**
 * One-shot reads the carousel needs, abstracted from Room/Firestore so loading is testable.
 * Owned affirmations come from Room when signed out and Firestore when signed in (R6).
 */
interface CarouselDataPorts {
    suspend fun favoriteIds(): List<String>
    suspend fun ownedAffirmations(): List<AffirmationEntity>

    /** Member ids per user-created or default collection. */
    suspend fun collectionMembers(): Map<String, List<String>>
    suspend fun catalogRefsByGroup(groupId: String): List<CatalogAffirmationRef>
    suspend fun allCatalogRefs(): List<CatalogAffirmationRef>
    suspend fun catalogRow(id: String): CatalogAffirmationEntity?
}

/** A fully loaded snapshot for one render: pool inputs, access, and owned rows for text. */
data class CarouselLoad(
    val snapshot: CarouselSnapshot,
    val access: CarouselAccess,
    val ownedById: Map<String, AffirmationEntity>,
)

class CarouselDataLoader(
    private val ports: CarouselDataPorts,
    private val readAccess: suspend () -> CarouselAccess,
    private val timeoutMillis: Long = CAROUSEL_READ_TIMEOUT_MILLIS,
) {
    suspend fun load(source: CarouselSource?): CarouselLoad {
        val access = readAccess()
        val allRefs = ports.allCatalogRefs()

        val needsOwned = when (source) {
            null -> false
            CarouselSource.Favorites, is CarouselSource.Collection -> true
            is CarouselSource.Theme -> source.groupId == PERSONALIZADAS_GROUP_ID
        }
        var ownedRows: List<AffirmationEntity> = emptyList()
        val owned = if (needsOwned) {
            readOwned(timeoutMillis) { ports.ownedAffirmations().also { ownedRows = it }.map { it.id } }
        } else {
            OwnedRead.Available(emptyList())
        }

        val favoriteIds = if (source == CarouselSource.Favorites) ports.favoriteIds() else emptyList()
        val collections = if (source is CarouselSource.Collection) ports.collectionMembers() else emptyMap()
        val memberIds: Set<String> = when (source) {
            CarouselSource.Favorites -> favoriteIds.toSet()
            is CarouselSource.Collection -> collections[source.collectionId].orEmpty().toSet()
            else -> emptySet()
        }
        val catalogRefs = when (source) {
            is CarouselSource.Theme ->
                if (source.groupId == PERSONALIZADAS_GROUP_ID) emptyList() else ports.catalogRefsByGroup(source.groupId)
            null -> emptyList()
            else -> allRefs.filter { it.id in memberIds }
        }

        return CarouselLoad(
            snapshot = CarouselSnapshot(
                favoriteIds = favoriteIds,
                owned = owned,
                collections = collections,
                catalogRefs = catalogRefs,
                fallbackRefs = allRefs,
            ),
            access = access,
            ownedById = ownedRows.associateBy { it.id },
        )
    }
}
