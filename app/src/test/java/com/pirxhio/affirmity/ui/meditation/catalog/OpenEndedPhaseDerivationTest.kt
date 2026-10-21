package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.wimhof.WimHofPhaseIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenEndedPhaseDerivationTest {

    @Test
    fun `across the whole catalog only the wim hof exhale hold is open-ended`() {
        val found = meditationCatalog().flatMap { entry ->
            openEndedPhaseIds(entry.definition(emptyMap())).map { entry.id to it }
        }
        assertEquals(listOf("wim_hof" to WimHofPhaseIds.EXHALE_HOLD), found)
    }

    @Test
    fun `wim hof recovery hold is not open-ended`() {
        val wimHof = meditationCatalog().first { it.id == "wim_hof" }.definition(emptyMap())
        assertTrue(WimHofPhaseIds.RECOVERY_HOLD !in openEndedPhaseIds(wimHof))
    }
}
