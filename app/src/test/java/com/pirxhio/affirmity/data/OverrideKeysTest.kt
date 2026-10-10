package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideKeysTest {

    private val counts = mapOf(TemplateField.TITLE to 1, TemplateField.SUBTITLE to 1)

    // --- tokenKey ---

    @Test
    fun `tokenKey is field colon ordinal with no original`() {
        assertEquals("title:0", AffirmationTemplateParser.tokenKey(TemplateField.TITLE, 0))
        assertEquals("subtitle:2", AffirmationTemplateParser.tokenKey(TemplateField.SUBTITLE, 2))
    }

    // --- displayView (REQ-OVR-1, 2) ---

    @Test
    fun `legacy key shows at its ordinal`() {
        val view = OverrideKeys.displayView(mapOf("title:0:Foo" to "Ana"))

        assertEquals(mapOf("title:0" to "Ana"), view)
    }

    @Test
    fun `stale legacy key still applies to the ordinal`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "Hola [NewText]")
        val token = template.segments.filterIsInstance<TemplateSegment.Token>().single()

        val view = OverrideKeys.displayView(mapOf("title:0:OldText" to "Ana"))

        assertEquals("Hola Ana", template.render(view))
        assertEquals("title:0", token.key)
    }

    @Test
    fun `canonical key wins over legacy for the same slot`() {
        val view = OverrideKeys.displayView(
            mapOf("title:0:Foo" to "legacy", "title:0" to "canonical"),
        )

        assertEquals(mapOf("title:0" to "canonical"), view)
    }

    @Test
    fun `two legacy keys for one slot pick the smallest full key`() {
        val view = OverrideKeys.displayView(
            mapOf("title:0:b" to "second", "title:0:a" to "first"),
        )

        assertEquals(mapOf("title:0" to "first"), view)
    }

    @Test
    fun `legacy original containing a colon is accepted`() {
        val view = OverrideKeys.displayView(mapOf("subtitle:1:a:b:c" to "v"))

        assertEquals(mapOf("subtitle:1" to "v"), view)
    }

    @Test
    fun `unknown keys are hidden from the display view`() {
        val stored = mapOf(
            "foo:0" to "x",
            "title:x" to "x",
            "title:00" to "x",
            "title:+0" to "x",
            "title:-1" to "x",
            "title" to "x",
            "title:00:orig" to "x",
        )

        assertTrue(OverrideKeys.displayView(stored).isEmpty())
    }

    @Test
    fun `displayView is pure and leaves the stored map unchanged`() {
        val stored = mapOf("title:0:Foo" to "Ana", "weird" to "z")
        val snapshot = stored.toMap()

        OverrideKeys.displayView(stored)

        assertEquals(snapshot, stored)
    }

    // --- tokenCounts helper ---

    @Test
    fun `tokenCounts counts non-empty tokens per field`() {
        val result = OverrideKeys.tokenCounts("A [x] [y]", "B [] c")

        assertEquals(mapOf(TemplateField.TITLE to 2, TemplateField.SUBTITLE to 0), result)
    }

    // --- applyEdit (REQ-OVR-3, 4, 5) ---

    @Test
    fun `edit on another slot migrates the winning legacy key and loses nothing`() {
        val stored = mapOf(
            "title:0:Foo" to "Ana",
            "subtitle:0:Bar" to "Luis",
            "foo:bar" to "unknown",
        )

        val next = OverrideKeys.applyEdit(stored, TemplateField.SUBTITLE, 0, "Eva", counts)

        assertEquals(
            mapOf("title:0" to "Ana", "subtitle:0" to "Eva", "subtitle:0:Bar" to "Luis", "foo:bar" to "unknown"),
            next,
        )
    }

    @Test
    fun `extra legacy keys of a migrated slot are retained`() {
        val stored = mapOf("title:0:a" to "first", "title:0:b" to "second")

        val next = OverrideKeys.applyEdit(stored, TemplateField.SUBTITLE, 0, "X", counts)

        assertEquals(
            mapOf("title:0" to "first", "title:0:b" to "second", "subtitle:0" to "X"),
            next,
        )
    }

    @Test
    fun `canonical plus legacy keeps the legacy key`() {
        val stored = mapOf("title:0" to "canonical", "title:0:a" to "legacy")

        val next = OverrideKeys.applyEdit(stored, TemplateField.SUBTITLE, 0, "X", counts)

        assertEquals(stored + ("subtitle:0" to "X"), next)
    }

    @Test
    fun `out of range legacy key is not migrated and is retained`() {
        val stored = mapOf("title:5:old" to "kept")

        val next = OverrideKeys.applyEdit(stored, TemplateField.TITLE, 0, "X", counts)

        assertEquals(mapOf("title:5:old" to "kept", "title:0" to "X"), next)
    }

    @Test
    fun `prune removes only out of range canonical keys`() {
        val stored = mapOf(
            "title:0" to "keep",
            "title:3" to "drop",
            "subtitle:9:legacy" to "stay",
            "weird" to "stay",
        )

        val next = OverrideKeys.applyEdit(stored, TemplateField.TITLE, 0, "keep2", counts)

        assertEquals(
            mapOf("title:0" to "keep2", "subtitle:9:legacy" to "stay", "weird" to "stay"),
            next,
        )
    }

    @Test
    fun `revert removes canonical and all legacy keys of that slot only`() {
        val stored = mapOf(
            "title:0" to "c",
            "title:0:a" to "l1",
            "title:0:b" to "l2",
            "subtitle:0:z" to "other",
            "foo" to "unknown",
        )

        val next = OverrideKeys.applyEdit(stored, TemplateField.TITLE, 0, null, counts)

        assertEquals(mapOf("subtitle:0" to "other", "foo" to "unknown"), next)
    }

    @Test
    fun `es to en to es round trip keeps overrides with an edit in en`() {
        // Set in es (legacy key written by an old build), then an en edit on another slot.
        val esStored = mapOf("title:0:dinero" to "Ana")
        // en display shows the override at ordinal 0 without any write.
        assertEquals(mapOf("title:0" to "Ana"), OverrideKeys.displayView(esStored))

        val afterEnEdit = OverrideKeys.applyEdit(esStored, TemplateField.SUBTITLE, 0, "Bob", counts)

        // Back in es: both slots resolve to the values.
        assertEquals(
            mapOf("title:0" to "Ana", "subtitle:0" to "Bob"),
            OverrideKeys.displayView(afterEnEdit),
        )
    }
}
