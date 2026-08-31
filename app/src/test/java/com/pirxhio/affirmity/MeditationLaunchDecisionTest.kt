package com.pirxhio.affirmity

import com.pirxhio.affirmity.ui.meditation.catalog.findMeditationCatalogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime coverage for [decideMeditationLaunchStep] (spec: meditation-customization, REQ-5.4) --
 * the pure decision extracted out of [AffirmityApp]'s guided-session route so the "show
 * customization vs skip straight to the session, and what values/config to hand each destination"
 * branch can be driven directly by a plain JUnit test, without a Compose test harness (this repo
 * has none; no `createComposeRule` usage exists anywhere in the codebase).
 */
class MeditationLaunchDecisionTest {

    // "calma" has an empty customizationFields list (no pre-session knobs).
    private val noFieldsEntry = requireNotNull(findMeditationCatalogEntry("calma"))

    // "body_scan" declares durationMinutes (default 10), direction (default "feet_to_head"), and
    // detailLevel (default "standard") customization fields.
    private val fieldsEntry = requireNotNull(findMeditationCatalogEntry("body_scan"))

    @Test
    fun `skips straight to StartSession with an empty config for a no-fields entry`() {
        val step = decideMeditationLaunchStep(
            entry = noFieldsEntry,
            confirmedCustomization = null,
            savedValues = emptyMap(),
        )

        assertEquals(MeditationLaunchStep.StartSession(emptyMap()), step)
    }

    @Test
    fun `shows customization for a fields entry with no confirmed value yet`() {
        val step = decideMeditationLaunchStep(
            entry = fieldsEntry,
            confirmedCustomization = null,
            savedValues = emptyMap(),
        )

        assertTrue(step is MeditationLaunchStep.ShowCustomization)
    }

    @Test
    fun `seeds the customization screen from each field's spec default when nothing was saved`() {
        val step = decideMeditationLaunchStep(
            entry = fieldsEntry,
            confirmedCustomization = null,
            savedValues = emptyMap(),
        ) as MeditationLaunchStep.ShowCustomization

        assertEquals("10", step.seedValues["durationMinutes"])
        assertEquals("feet_to_head", step.seedValues["direction"])
        assertEquals("standard", step.seedValues["detailLevel"])
    }

    @Test
    fun `seeds the customization screen with saved values, filling in defaults for any missing key`() {
        val step = decideMeditationLaunchStep(
            entry = fieldsEntry,
            confirmedCustomization = null,
            savedValues = mapOf("direction" to "head_to_feet"),
        ) as MeditationLaunchStep.ShowCustomization

        // Saved value wins for the key that was persisted...
        assertEquals("head_to_feet", step.seedValues["direction"])
        // ...and every other declared field still falls back to its own default.
        assertEquals("10", step.seedValues["durationMinutes"])
        assertEquals("standard", step.seedValues["detailLevel"])
    }

    @Test
    fun `starts the session with the confirmed config once customization has been confirmed`() {
        val confirmed = mapOf("durationMinutes" to "20", "direction" to "head_to_feet", "detailLevel" to "standard")

        val step = decideMeditationLaunchStep(
            entry = fieldsEntry,
            confirmedCustomization = confirmed,
            savedValues = emptyMap(),
        )

        assertEquals(MeditationLaunchStep.StartSession(confirmed), step)
    }
}
