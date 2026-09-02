package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartAmbient
import com.pirxhio.affirmity.meditation.StopAmbient
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXACT_TOLERANCE_MILLIS = 30_000L
private const val GATED_TOLERANCE_MILLIS = 90_000L

/**
 * Covers `meditationCatalog()`/`findMeditationCatalogEntry` (REQ-4.6, REQ-4.11) with the 12
 * numbered assertions from spec §11 — the primary correctness gate for PR2. Each assertion is
 * traceable to its own spec acceptance criterion; see the KDoc on each test.
 */
class MeditationCatalogTest {

    private object NoOpCommandExecutor : MeditationCommandExecutor {
        override fun execute(command: MeditationCommand) = Unit
    }

    private data class SimulationResult(
        val ceilingMillis: Long,
        val manualPhases: Int,
        val gatedPhases: Int,
        val maxIterationCounts: Map<String, Int>,
    )

    /**
     * Drives a real [MeditationEngine] over [definition] with [NoOpCommandExecutor], advancing via
     * [MeditationEvent.Next] (which forces a phase to exit regardless of its [PhaseDuration] or
     * `exitEvents`) until the session leaves [SessionStatus.Running] (design §8.1). Because AR-1
     * (no `AudioCompleted` gating) and AR-2 (no `PhaseDuration.Manual`) hold for all 7 entries,
     * [SimulationResult.ceilingMillis] is each entry's *exact* real runtime, not merely an upper
     * bound.
     */
    private fun simulate(definition: MeditationDefinition): SimulationResult {
        val phasesById = collectPhases(definition.root).associateBy(Phase::id)
        val engine = MeditationEngine(definition, NoOpCommandExecutor)
        engine.send(MeditationEvent.Start)
        var total = 0L
        var manual = 0
        var gated = 0
        val maxCounts = mutableMapOf<String, Int>()
        while (engine.state.value.status == SessionStatus.Running) {
            val current = engine.state.value
            current.iterationCounts.forEach { (repeatId, iteration) ->
                maxCounts[repeatId] = maxOf(maxCounts[repeatId] ?: 0, iteration)
            }
            val phase = phasesById.getValue(requireNotNull(current.currentPhaseId))
            when (val duration = phase.duration) {
                is PhaseDuration.Fixed -> total += duration.millis
                PhaseDuration.Manual -> manual++
            }
            if (MeditationEvent.AudioCompleted::class in phase.exitEvents) gated++
            engine.send(MeditationEvent.Next)
        }
        return SimulationResult(total, manual, gated, maxCounts)
    }

    // --- 1. Exactly 7 entries, in §4.6 order, with the exact ids and access companions ----------

    @Test
    fun `1 - exactly 38 entries, REQ-4-6's 7 first in order, then the breathing-technique batch, then the mindfulness-silence-movement-mantra batch, then the prayer-contemplation batch, then the body-mind batch, then the visualization-and-christian-prayer batch, then the breathing-affirmations hybrid, with the exact ids and access companions`() {
        val catalog = meditationCatalog()
        assertEquals(38, catalog.size)
        assertEquals(
            listOf(
                "reset_rapido" to ContentAccess.Free,
                "calma" to ContentAccess.Free,
                "enfoque" to ContentAccess.ProOrAdPerUse,
                "dormir" to ContentAccess.ProOrAdTrial,
                "body_scan" to ContentAccess.Pro,
                "autocompasion_5" to ContentAccess.Free,
                "autocompasion_10" to ContentAccess.Pro,
                "box_breathing" to ContentAccess.Free,
                "breathing_4_7_8" to ContentAccess.Free,
                "extended_exhale" to ContentAccess.ProOrAdPerUse,
                "coherent_breathing" to ContentAccess.Pro,
                "nadi_shodhana" to ContentAccess.Pro,
                "bhramari" to ContentAccess.ProOrAdTrial,
                "kapalabhati" to ContentAccess.Pro,
                "breath_of_fire" to ContentAccess.ProOrAdTrial,
                "anapanasati" to ContentAccess.Free,
                "vipassana" to ContentAccess.Pro,
                "metta" to ContentAccess.ProOrAdPerUse,
                "zazen" to ContentAccess.Pro,
                "walking_meditation" to ContentAccess.ProOrAdTrial,
                "mantra_meditation" to ContentAccess.Free,
                "om_meditation" to ContentAccess.ProOrAdTrial,
                "so_hum" to ContentAccess.Pro,
                "dhikr" to ContentAccess.Free,
                "muraqabah" to ContentAccess.ProOrAdPerUse,
                "hitbodedut" to ContentAccess.Pro,
                "self_compassion_break" to ContentAccess.ProOrAdTrial,
                "trataka" to ContentAccess.ProOrAdPerUse,
                "yoganidra" to ContentAccess.Pro,
                "open_awareness" to ContentAccess.Free,
                "noting" to ContentAccess.ProOrAdTrial,
                "progressive_muscle_relaxation" to ContentAccess.Pro,
                "visualization" to ContentAccess.ProOrAdPerUse,
                "gratitude_meditation" to ContentAccess.Free,
                "centering_prayer" to ContentAccess.Pro,
                "jesus_prayer" to ContentAccess.ProOrAdTrial,
                "lectio_divina" to ContentAccess.Pro,
                "breathing_affirmations" to ContentAccess.Pro,
            ),
            catalog.map { it.id to it.access },
        )
    }

    // --- 2. Ids unique; ContentKey round-trips through ContentKey.parse for all 7 ---------------

    @Test
    fun `2 - ids are unique and ContentKey storageKey round-trips through ContentKey-parse for all 7`() {
        val ids = meditationCatalog().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            val key = ContentKey(ContentType.MEDITATION, id)
            assertEquals(key, ContentKey.parse(key.storageKey))
        }
    }

    // --- 3. All 4 ContentAccess companions appear at least once (REQ-4.11.8) --------------------

    @Test
    fun `3 - all four ContentAccess companions appear at least once`() {
        val accessSet = meditationCatalog().map { it.access }.toSet()
        assertTrue(ContentAccess.Free in accessSet)
        assertTrue(ContentAccess.ProOrAdPerUse in accessSet)
        assertTrue(ContentAccess.ProOrAdTrial in accessSet)
        assertTrue(ContentAccess.Pro in accessSet)
    }

    // --- 4. Every definition builds without throwing; unique definition.id per entry ------------

    @Test
    fun `4 - every entry's definition builds without throwing and has a unique definition id`() {
        val definitionIds = meditationCatalog().map { it.definition(emptyMap()).id }
        assertEquals(
            listOf(
                "quickreset", "calm", "focus", "sleep", "bodyscan", "selfcompassion5", "selfcompassion10",
                "boxbreathing", "breathing478", "extendedexhale", "coherentbreathing", "nadishodhana",
                "bhramari", "kapalabhati", "breathoffire",
                "anapanasati", "vipassana", "metta", "zazen", "walking", "mantra",
                "om", "sohum",
                "dhikr", "muraqabah", "hitbodedut", "selfcompassionbreak",
                "trataka", "yoganidra", "openawareness", "noting", "progressivemusclerelaxation",
                "visualization", "gratitudemeditation", "centeringprayer", "jesusprayer", "lectiodivina",
                "breathingaffirmations",
            ),
            definitionIds,
        )
        assertEquals(definitionIds.size, definitionIds.toSet().size)
    }

    // --- 5. Phase-id uniqueness within each entry's own tree (REQ-4.8 precondition) --------------

    @Test
    fun `5 - phase ids are globally unique within each entry's own tree`() {
        meditationCatalog().forEach { entry ->
            val phaseIds = collectPhases(entry.definition(emptyMap()).root).map { it.id }
            assertEquals("${entry.id}: duplicate phase ids in $phaseIds", phaseIds.size, phaseIds.toSet().size)
        }
    }

    // --- 6. Presentation completeness -------------------------------------------------------------

    /** Entries whose reachable [ShowText] ids depend on customization choices (a config-conditional
     * phase tree, e.g. a toggle that inserts extra phases) -- assertion 6 unions the reachable set
     * across every config listed here on top of the `emptyMap()` baseline, instead of the single
     * default tree every other entry is checked against. */
    private val configVariantsForReachability: Map<String, List<Map<String, String>>> = mapOf(
        "nadi_shodhana" to listOf(mapOf("retention" to "true")),
        "hitbodedut" to listOf(
            mapOf("guidedPrompts" to "false"),
            mapOf("promptTopics" to "gratitude|concerns|requests|relationships|purpose|forgiveness"),
        ),
        "centering_prayer" to listOf(mapOf("openingGuidance" to "false")),
        // visualization: `scenario` picks which VisualizationText id the VISUALIZATION phase shows
        // (visualizationCueFor) -- "nature" is the emptyMap() default, the other four scenarios
        // each reach a distinct textId not covered by any single config.
        "visualization" to listOf(
            mapOf("scenario" to "safe_place"),
            mapOf("scenario" to "goal"),
            mapOf("scenario" to "performance"),
            mapOf("scenario" to "custom"),
        ),
        // breathing_affirmations: HOLD is only emitted by the box_breathing technique branch, and
        // the affirmation-unavailable fallback (already reachable at the emptyMap() baseline, since
        // this test never populates "affirmationText.0"..) is the only affirmation-phase cue this
        // static check can ever exercise -- the real per-affirmation ShowLiteralText content is
        // runtime-fetched, never a textResources-mapped id, so it's outside this assertion's scope.
        "breathing_affirmations" to listOf(mapOf("breathingTechnique" to "box_breathing")),
    )

    @Test
    fun `6 - every reachable ShowText id has a textResources mapping, and textResources has no unused keys`() {
        meditationCatalog().forEach { entry ->
            val configs = listOf(emptyMap<String, String>()) + configVariantsForReachability[entry.id].orEmpty()
            val reachableTextIds = configs
                .flatMap { config -> collectCommands(entry.definition(config).root) }
                .filterIsInstance<ShowText>()
                .map { it.textId }
                .toSet()
            assertEquals(
                "${entry.id}: textResources must cover exactly the reachable ShowText ids",
                reachableTextIds,
                entry.presentation.textResources.keys,
            )
        }
    }

    // --- 7. Counter consistency -------------------------------------------------------------------

    @Test
    fun `7 - each declared counter total minus 1 equals the real engine-driven max iteration for its repeat id`() {
        meditationCatalog().forEach { entry ->
            val result = simulate(entry.definition(emptyMap()))
            entry.presentation.counters.forEach { counter ->
                assertEquals(
                    "${entry.id}/${counter.repeatId}",
                    counter.total - 1,
                    result.maxIterationCounts[counter.repeatId],
                )
            }
        }
    }

    // --- 8. Duration tolerance + AR-1 (gatedPhases==0) + AR-2 (manualPhases==0) ------------------

    @Test
    fun `8 - duration tolerance plus AR-1 and AR-2 hold for every entry`() {
        meditationCatalog().forEach { entry ->
            val result = simulate(entry.definition(emptyMap()))
            assertEquals("${entry.id}: manual phases (AR-2)", 0, result.manualPhases)
            assertEquals("${entry.id}: gated phases (AR-1)", 0, result.gatedPhases)
            val declared = entry.approxDurationMinutes * 60_000L
            assertTrue(
                "${entry.id}: ceiling ${result.ceilingMillis} vs declared $declared",
                abs(result.ceilingMillis - declared) <= EXACT_TOLERANCE_MILLIS,
            )
            // Standing (currently vacuous) branch, kept for the day AR-1's precondition is met and
            // gating turns on for a future entry.
            if (result.gatedPhases > 0) {
                assertTrue(result.ceilingMillis >= declared)
                assertTrue(result.ceilingMillis - declared <= GATED_TOLERANCE_MILLIS)
            }
        }
    }

    // --- 9. DC-1 guard: command-vocabulary subset -------------------------------------------------

    @Test
    fun `9 - DC-1 guard - the union of all commands across all entries is a subset of the whitelist`() {
        val whitelist =
            setOf(ShowText::class, PlayVoice::class, StartAmbient::class, StopAmbient::class, PlayAudio::class)
        meditationCatalog().forEach { entry ->
            collectCommands(entry.definition(emptyMap()).root).forEach { command ->
                assertTrue("${entry.id}: unexpected command ${command::class}", command::class in whitelist)
            }
        }
    }

    // --- 10. AR-3 guard: terminal fade -------------------------------------------------------------

    @Test
    fun `10 - AR-3 guard - a terminal StopAmbient fade is at most 5s and strictly shorter than its Fixed phase`() {
        meditationCatalog().forEach { entry ->
            val lastPhase = collectPhases(entry.definition(emptyMap()).root).last()
            val stopAmbient = lastPhase.onEnter.filterIsInstance<StopAmbient>().firstOrNull() ?: return@forEach
            assertTrue("${entry.id}: fade must be <= 5000ms", stopAmbient.fadeOutMillis <= 5_000L)
            val duration = lastPhase.duration
            assertTrue("${entry.id}: last phase must be Fixed", duration is PhaseDuration.Fixed)
            assertTrue(
                "${entry.id}: fade must be strictly shorter than its phase",
                stopAmbient.fadeOutMillis < (duration as PhaseDuration.Fixed).millis,
            )
        }
    }

    // --- 11. audioResources.isEmpty() tripwire ------------------------------------------------------

    @Test
    fun `11 - audioResources is empty for all entries`() {
        meditationCatalog().forEach { entry ->
            assertTrue("${entry.id}: audioResources must be empty", entry.presentation.audioResources.isEmpty())
        }
    }

    // --- 12. findMeditationCatalogEntry correctness -------------------------------------------------

    @Test
    fun `12 - findMeditationCatalogEntry returns the correct entry for each id, and null for an unknown id`() {
        meditationCatalog().forEach { entry ->
            assertEquals(entry, findMeditationCatalogEntry(entry.id))
        }
        assertNull(findMeditationCatalogEntry("does_not_exist"))
    }
}
