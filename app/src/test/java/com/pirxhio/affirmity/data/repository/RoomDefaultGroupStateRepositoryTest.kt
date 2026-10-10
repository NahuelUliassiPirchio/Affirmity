package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.DefaultGroupState
import com.pirxhio.affirmity.data.local.DefaultGroupStateDao
import com.pirxhio.affirmity.data.local.DefaultGroupStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomDefaultGroupStateRepositoryTest {
    private class FakeDao : DefaultGroupStateDao {
        val rows = MutableStateFlow<List<DefaultGroupStateEntity>>(emptyList())
        val calls = mutableListOf<String>()

        override fun observeAll(): Flow<List<DefaultGroupStateEntity>> = rows
        override suspend fun getAll(): List<DefaultGroupStateEntity> = rows.value
        override suspend fun upsert(entity: DefaultGroupStateEntity) {
            rows.value = rows.value.filterNot { it.id == entity.id } + entity
        }

        override suspend fun enable(id: String, nowMillis: Long) {
            calls += "enable:$id:$nowMillis"
            upsert(DefaultGroupStateEntity(id, true, nowMillis))
        }

        override suspend fun disable(id: String) {
            calls += "disable:$id"
            rows.value = rows.value.map { if (it.id == id) it.copy(enabled = false) else it }
        }
    }

    @Test
    fun setEnabled_trueRoutesToEnableWithTimestamp_falseRoutesToDisable() = runBlocking {
        val dao = FakeDao()
        val repository = RoomDefaultGroupStateRepository(dao)

        repository.setEnabled("default.morning_start", enabled = true, nowMillis = 42L)
        repository.setEnabled("default.morning_start", enabled = false, nowMillis = 99L)

        assertEquals(listOf("enable:default.morning_start:42", "disable:default.morning_start"), dao.calls)
    }

    @Test
    fun states_mapRowsById_andEmptyWhenNoRows() = runBlocking {
        val dao = FakeDao()
        val repository = RoomDefaultGroupStateRepository(dao)
        assertEquals(emptyMap<String, DefaultGroupState>(), repository.getStates())

        dao.rows.value = listOf(
            DefaultGroupStateEntity("default.morning_start", true, 10L),
            DefaultGroupStateEntity("default.everyday_essentials", false, 3L),
        )

        val expected = mapOf(
            "default.morning_start" to DefaultGroupState(true, 10L),
            "default.everyday_essentials" to DefaultGroupState(false, 3L),
        )
        assertEquals(expected, repository.getStates())
        assertEquals(expected, repository.observeStates().first())
    }
}
