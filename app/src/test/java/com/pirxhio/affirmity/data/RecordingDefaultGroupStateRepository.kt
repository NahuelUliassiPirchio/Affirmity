package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.repository.DefaultGroupStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [DefaultGroupStateRepository] that records every call in [events]. */
internal class RecordingDefaultGroupStateRepository(
    initial: Map<String, DefaultGroupState> = emptyMap(),
    val events: MutableList<String> = mutableListOf(),
) : DefaultGroupStateRepository {
    private val state = MutableStateFlow(initial)

    val current: Map<String, DefaultGroupState> get() = state.value

    override fun observeStates(): Flow<Map<String, DefaultGroupState>> = state

    override suspend fun getStates(): Map<String, DefaultGroupState> = state.value

    override suspend fun setEnabled(id: String, enabled: Boolean, nowMillis: Long) {
        events += "setEnabled:$id:$enabled:$nowMillis"
        val previous = state.value[id]
        state.value = state.value + (
            id to DefaultGroupState(
                enabled = enabled,
                lastUsedAtMillis = if (enabled) nowMillis else previous?.lastUsedAtMillis ?: 0L,
            )
            )
    }
}
