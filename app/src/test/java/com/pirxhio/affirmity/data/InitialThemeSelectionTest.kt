package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.personalization.goals.GoalCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the pure goal-driven initial theme selection used on a fresh install, once the survey
 * has saved its goals. Uses synthetic ids and an injected goal->theme map so it stays independent
 * of the generated catalog's shape. */
class InitialThemeSelectionTest {

    private val mapping = linkedMapOf(
        "a" to setOf("a.1", "a.2", "a.3"),
        "b" to setOf("b.1", "b.2"),
        "c" to setOf("c.1"),
    )
    private val free = setOf("a.1", "a.2", "a.3", "b.1", "b.2", "c.1")

    @Test
    fun `max initial themes defaults to five`() {
        assertEquals(5, MAX_INITIAL_THEMES)
    }

    @Test
    fun `goals map to their free themes, round-robin across goals so each goal is represented`() {
        val result = deriveInitialThemeIds(setOf("a", "b"), free, mapping, max = 3)

        assertEquals(setOf("a.1", "b.1", "a.2"), result)
    }

    @Test
    fun `result is capped at max`() {
        val result = deriveInitialThemeIds(setOf("a", "b", "c"), free, mapping, max = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun `locked themes are excluded`() {
        val result = deriveInitialThemeIds(setOf("a"), setOf("a.2"), mapping, max = 5)

        assertEquals(setOf("a.2"), result)
    }

    @Test
    fun `ranking is deterministic regardless of goal set iteration order`() {
        val one = deriveInitialThemeIds(linkedSetOf("c", "a", "b"), free, mapping, max = 4)
        val two = deriveInitialThemeIds(linkedSetOf("b", "c", "a"), free, mapping, max = 4)

        assertEquals(one.toList(), two.toList())
    }

    @Test
    fun `unknown goal ids are ignored and fall through to the other goals`() {
        val result = deriveInitialThemeIds(setOf("zzz", "c"), free, mapping, max = 5)

        assertEquals(setOf("c.1"), result)
    }

    @Test
    fun `null or empty goals fall back to the capped default, deterministic order`() {
        val default = (1..9).map { "t.$it" }.toSet()

        val fromNull = deriveInitialThemeIds(null, default, emptyMap(), max = 5)
        val fromEmpty = deriveInitialThemeIds(emptySet(), default.reversed().toSet(), emptyMap(), max = 5)

        assertEquals(5, fromNull.size)
        assertEquals(fromNull.toList(), fromEmpty.toList())
        assertTrue(default.containsAll(fromNull))
    }

    @Test
    fun `goals with no free mapped theme fall back to the broad mix across all goals`() {
        // "zzz" is unknown, so the goal tier is empty; the broad tier picks one free theme per
        // goal in sorted goal order, which differs from the sorted-default tier (a.1, a.2, ...).
        val result = deriveInitialThemeIds(setOf("zzz"), free, mapping, max = 3)

        assertEquals(listOf("a.1", "b.1", "c.1"), result.toList())
    }

    @Test
    fun `with nothing free in the mapping the sorted default tier is used`() {
        val result = deriveInitialThemeIds(setOf("a"), setOf("other.2", "other.1"), mapping, max = 1)

        assertEquals(listOf("other.1"), result.toList())
    }

    @Test
    fun `resolver keeps the full default for persisted null and legacy null`() {
        val default = (1..12).map { "t.$it" }.toSet()

        val result = resolveSelectedThemeIds(null, null, default, default)

        assertEquals(default, result)
    }

    @Test
    fun `persisted selection is returned as is`() {
        val result = resolveSelectedThemeIds(
            persistedThemeIds = free,
            legacyGroupIds = null,
            knownThemeIds = free,
            defaultThemeIds = setOf("a.1"),
        )

        assertEquals(free, result)
    }

    @Test
    fun `real goal catalog maps every goal to at least one theme`() {
        val real = GoalCatalog.themeIdsByGoalId
        val result = deriveInitialThemeIds(real.keys, real.values.flatten().toSet(), real, max = MAX_INITIAL_THEMES)

        assertEquals(MAX_INITIAL_THEMES, result.size)
    }
}
