package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.repository.UserCollectionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written in-memory [UserCollectionRepository] that mirrors the real chip ordering and the
 * "enable bumps lastUsedAt, disable never does" rule, and records every call in [events] so tests
 * can assert both outcome and write order. [createGate], when set, suspends `create` after its
 * `create:start` event so Mutex serialisation can be observed.
 */
internal class RecordingUserCollectionRepository(
    initial: List<UserCollection> = emptyList(),
    private val createGate: CompletableDeferred<Unit>? = null,
    val events: MutableList<String> = mutableListOf(),
) : UserCollectionRepository {
    private val state = MutableStateFlow(sorted(initial))

    val current: List<UserCollection> get() = state.value

    override fun observeCollections(): Flow<List<UserCollection>> = state

    override suspend fun getCollections(): List<UserCollection> = state.value

    override suspend fun create(
        id: String,
        name: String,
        nowMillis: Long,
        initialAffirmationId: String?,
        highlightId: String,
    ) {
        events += "create:start:$id"
        createGate?.await()
        events += "create:$id:$name:$nowMillis:${initialAffirmationId ?: "-"}"
        update {
            it + UserCollection(
                id = id,
                name = name,
                createdAtMillis = nowMillis,
                enabled = true,
                lastUsedAtMillis = nowMillis,
                affirmationIds = listOfNotNull(initialAffirmationId),
                highlightId = highlightId,
            )
        }
    }

    override suspend fun rename(id: String, name: String) {
        events += "rename:$id:$name"
        update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
    }

    override suspend fun delete(id: String) {
        events += "delete:$id"
        update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun addItem(userCollectionId: String, affirmationId: String, nowMillis: Long) {
        events += "addItem:$userCollectionId:$affirmationId"
        // Models the real FK: inserting an item for a missing collection throws (IGNORE does not cover it).
        check(state.value.any { it.id == userCollectionId }) { "FOREIGN KEY constraint failed: $userCollectionId" }
        update { list ->
            list.map {
                if (it.id == userCollectionId && affirmationId !in it.affirmationIds) {
                    it.copy(affirmationIds = it.affirmationIds + affirmationId)
                } else {
                    it
                }
            }
        }
    }

    override suspend fun removeItem(userCollectionId: String, affirmationId: String) {
        events += "removeItem:$userCollectionId:$affirmationId"
        update { list ->
            list.map {
                if (it.id == userCollectionId) it.copy(affirmationIds = it.affirmationIds - affirmationId) else it
            }
        }
    }

    override suspend fun enable(id: String, nowMillis: Long) {
        events += "enable:$id:$nowMillis"
        update { list ->
            list.map { if (it.id == id) it.copy(enabled = true, lastUsedAtMillis = nowMillis) else it }
        }
    }

    override suspend fun disable(id: String) {
        events += "disable:$id"
        update { list -> list.map { if (it.id == id) it.copy(enabled = false) else it } }
    }

    override suspend fun removeAffirmations(affirmationIds: Collection<String>) {
        events += "removeAffirmations:${affirmationIds.toList()}"
        val ids = affirmationIds.toSet()
        update { list -> list.map { it.copy(affirmationIds = it.affirmationIds.filterNot { id -> id in ids }) } }
    }

    private fun update(transform: (List<UserCollection>) -> List<UserCollection>) {
        state.value = sorted(transform(state.value))
    }

    private companion object {
        fun sorted(list: List<UserCollection>) = list.sortedWith(
            compareByDescending<UserCollection> { it.lastUsedAtMillis }
                .thenByDescending { it.createdAtMillis }
                .thenBy { it.id },
        )
    }
}
