package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.data.local.CatalogAffirmationRef
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.ui.groups.AffirmationGroup
import com.pirxhio.affirmity.ui.groups.CatalogCollection
import com.pirxhio.affirmity.ui.groups.PERSONALIZADAS_GROUP
import com.pirxhio.affirmity.ui.groups.catalogAccessDecision
import com.pirxhio.affirmity.ui.groups.catalogCollectionsById
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups

/**
 * Pure pool resolution with Pro gating and free fallback. Rows are gated
 * one by one through [catalogAccessDecision]; the free fallback is used only when nothing
 * survives the gate. Unknown groups are never allowed (fail closed).
 */
object CarouselPoolBuilder {

    private val defaultGroups: Map<String, AffirmationGroup> by lazy {
        (catalogUniverseGroups() + PERSONALIZADAS_GROUP).associateBy { it.id }
    }

    fun build(
        source: CarouselSource?,
        snapshot: CarouselSnapshot,
        access: CarouselAccess,
        nowMillis: Long,
        groups: Map<String, AffirmationGroup> = defaultGroups,
        collectionsById: Map<String, CatalogCollection> = catalogCollectionsById(),
    ): PoolResult {
        val gate = Gate(groups, collectionsById)
        fun fallback(reason: FallbackReason?) = PoolResult.Fallback(
            reason,
            snapshot.fallbackRefs
                .filter { gate.isFreeTier(it) }
                .map { it.toRef(gate) },
        )
        if (source == null) return fallback(null)

        return when (source) {
            CarouselSource.Favorites ->
                fromMembers(snapshot.favoriteIds, snapshot, access, nowMillis, gate, ::fallback)

            is CarouselSource.Collection -> {
                val members = snapshot.collections[source.collectionId] ?: return fallback(FallbackReason.SOURCE_MISSING)
                fromMembers(members, snapshot, access, nowMillis, gate, ::fallback)
            }

            is CarouselSource.Theme ->
                if (source.groupId == PERSONALIZADAS_GROUP_ID) {
                    fromOwned(snapshot.owned, ::fallback)
                } else {
                    fromTheme(source.groupId, snapshot, access, nowMillis, gate, groups, ::fallback)
                }
        }
    }

    private fun fromOwned(owned: OwnedRead, fallback: (FallbackReason?) -> PoolResult): PoolResult =
        when (owned) {
            OwnedRead.Unavailable -> fallback(FallbackReason.DATA_UNAVAILABLE)
            is OwnedRead.Available ->
                if (owned.ids.isEmpty()) fallback(FallbackReason.SOURCE_EMPTY)
                else PoolResult.Resolved(owned.ids.map { ownedRef(it) })
        }

    private fun fromTheme(
        groupId: String,
        snapshot: CarouselSnapshot,
        access: CarouselAccess,
        nowMillis: Long,
        gate: Gate,
        groups: Map<String, AffirmationGroup>,
        fallback: (FallbackReason?) -> PoolResult,
    ): PoolResult {
        if (groupId !in groups) return fallback(FallbackReason.SOURCE_MISSING)
        val rows = snapshot.catalogRefs.filter { it.groupId == groupId }
        if (rows.isEmpty()) return fallback(FallbackReason.SOURCE_EMPTY)
        val allowed = rows.filter { gate.isAllowed(it, access.tier, access.grants, nowMillis) }
        if (allowed.isEmpty()) return fallback(FallbackReason.SOURCE_LOCKED)
        return PoolResult.Resolved(allowed.map { it.toRef(gate) })
    }

    private fun fromMembers(
        memberIds: List<String>,
        snapshot: CarouselSnapshot,
        access: CarouselAccess,
        nowMillis: Long,
        gate: Gate,
        fallback: (FallbackReason?) -> PoolResult,
    ): PoolResult {
        if (memberIds.isEmpty()) return fallback(FallbackReason.SOURCE_EMPTY)
        val catalogById = snapshot.catalogRefs.associateBy { it.id }
        val ownedIds = (snapshot.owned as? OwnedRead.Available)?.ids?.toSet().orEmpty()
        var ownedUnverifiable = false
        val candidates = memberIds.distinct().mapNotNull { id ->
            when {
                id in catalogById -> catalogById.getValue(id).let { Candidate.Catalog(it) }
                id in ownedIds -> Candidate.Owned(id)
                snapshot.owned is OwnedRead.Unavailable -> {
                    ownedUnverifiable = true
                    null
                }
                else -> null
            }
        }
        if (candidates.isEmpty()) {
            return fallback(if (ownedUnverifiable) FallbackReason.DATA_UNAVAILABLE else FallbackReason.SOURCE_EMPTY)
        }
        val allowed = candidates.filter {
            it is Candidate.Owned || gate.isAllowed((it as Candidate.Catalog).row, access.tier, access.grants, nowMillis)
        }
        if (allowed.isEmpty()) return fallback(FallbackReason.SOURCE_LOCKED)
        return PoolResult.Resolved(
            allowed.map {
                when (it) {
                    is Candidate.Owned -> ownedRef(it.id)
                    is Candidate.Catalog -> it.row.toRef(gate)
                }
            },
        )
    }

    private sealed interface Candidate {
        data class Owned(val id: String) : Candidate
        data class Catalog(val row: CatalogAffirmationRef) : Candidate
    }

    private fun ownedRef(id: String) = CarouselRef(id, PERSONALIZADAS_GROUP_ID, null, isFreeTier = true)

    private fun CatalogAffirmationRef.toRef(gate: Gate) =
        CarouselRef(id, groupId, collectionId, isFreeTier = gate.isFreeTier(this))

    private class Gate(
        private val groups: Map<String, AffirmationGroup>,
        private val collectionsById: Map<String, CatalogCollection>,
    ) {
        fun isAllowed(
            row: CatalogAffirmationRef,
            tier: AccessTier,
            grants: AdUnlockState,
            nowMillis: Long,
        ): Boolean {
            val group = groups[row.groupId] ?: return false
            return catalogAccessDecision(group, collectionsById[row.collectionId], tier, grants, nowMillis).isUnlocked
        }

        /** Free for everyone: resolved under FREE with no grants. */
        fun isFreeTier(row: CatalogAffirmationRef): Boolean =
            isAllowed(row, AccessTier.FREE, AdUnlockState(), nowMillis = 0L)
    }
}
