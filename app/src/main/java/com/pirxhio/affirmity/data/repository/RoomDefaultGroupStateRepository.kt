package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.DefaultGroupState
import com.pirxhio.affirmity.data.local.DefaultGroupStateDao
import com.pirxhio.affirmity.data.local.DefaultGroupStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDefaultGroupStateRepository(
    private val dao: DefaultGroupStateDao,
) : DefaultGroupStateRepository {
    override fun observeStates(): Flow<Map<String, DefaultGroupState>> =
        dao.observeAll().map { it.toStateMap() }

    override suspend fun getStates(): Map<String, DefaultGroupState> = dao.getAll().toStateMap()

    override suspend fun setEnabled(id: String, enabled: Boolean, nowMillis: Long) {
        if (enabled) dao.enable(id, nowMillis) else dao.disable(id)
    }
}

private fun List<DefaultGroupStateEntity>.toStateMap(): Map<String, DefaultGroupState> =
    associate { it.id to DefaultGroupState(enabled = it.enabled, lastUsedAtMillis = it.lastUsedAtMillis) }
