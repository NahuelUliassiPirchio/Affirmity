package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.MeditationCustomizationDao
import com.pirxhio.affirmity.data.local.MeditationCustomizationEntity

/**
 * Per-meditation pre-session customization values (spec: meditation-customization). Deliberately
 * NOT a [com.pirxhio.affirmity.data.DataSession] member like [MeditationPreferencesRepository] --
 * these are local, per-device knob positions (rounds, pace, a typed sacred word), not account data
 * that should follow a sign-in. Local-only for this foundation stage; revisit if a future need for
 * cross-device sync emerges.
 */
class RoomMeditationCustomizationRepository(
    private val dao: MeditationCustomizationDao,
) {
    suspend fun getValues(meditationId: String): Map<String, String> =
        dao.getById(meditationId)?.values.orEmpty()

    suspend fun saveValues(meditationId: String, values: Map<String, String>) {
        dao.upsert(MeditationCustomizationEntity(meditationId, values))
    }
}
