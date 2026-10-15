package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogAffirmationRef

internal const val FREE_COLLECTION = "body_energy_wellbeing.body_acceptance.respect_my_body_today"
internal const val PRO_COLLECTION = "body_energy_wellbeing.body_confidence.take_physical_space"
internal const val UNIVERSE = "body_energy_wellbeing"

internal fun catalogEntity(
    id: String,
    collectionId: String = FREE_COLLECTION,
    text: String = "text $id",
    subtitle: String = "sub $id",
) = CatalogAffirmationEntity(
    id = id,
    text = text,
    subtitle = subtitle,
    groupId = UNIVERSE,
    themeId = "$UNIVERSE.theme",
    collectionId = collectionId,
    sortOrder = 0,
)

internal fun CatalogAffirmationEntity.toRef() = CatalogAffirmationRef(id, groupId, collectionId)

internal fun ownedEntity(
    id: String,
    title: String = "owned $id",
    subtitle: String = "",
    overrides: Map<String, String> = emptyMap(),
) = AffirmationEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    backgroundType = "color",
    backgroundValue = "#000000",
    overrides = overrides,
)

/** In-memory [CarouselDataPorts] with call tracking; override lambdas to simulate failures. */
internal class FakeCarouselPorts(
    val catalog: List<CatalogAffirmationEntity> = emptyList(),
    val owned: List<AffirmationEntity> = emptyList(),
    val favorites: List<String> = emptyList(),
    val collections: Map<String, List<String>> = emptyMap(),
    val ownedRead: suspend () -> List<AffirmationEntity> = { owned },
) : CarouselDataPorts {
    var ownedReads = 0
    var groupReads = mutableListOf<String>()

    override suspend fun favoriteIds() = favorites
    override suspend fun ownedAffirmations(): List<AffirmationEntity> {
        ownedReads++
        return ownedRead()
    }

    override suspend fun collectionMembers() = collections
    override suspend fun catalogRefsByGroup(groupId: String): List<CatalogAffirmationRef> {
        groupReads += groupId
        return catalog.filter { it.groupId == groupId }.map { it.toRef() }
    }

    override suspend fun allCatalogRefs() = catalog.map { it.toRef() }
    override suspend fun catalogRow(id: String) = catalog.firstOrNull { it.id == id }
}
