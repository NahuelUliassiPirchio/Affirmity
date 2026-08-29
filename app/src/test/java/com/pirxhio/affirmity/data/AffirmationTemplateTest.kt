package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AffirmationTemplateTest {

    // --- Parsing ---

    @Test
    fun `affirmation with tokens is parsed into alternating segments in source order`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k] [dolares] al [mes]")

        assertEquals(
            listOf(
                TemplateSegment.Literal("Gano "),
                TemplateSegment.Token(AffirmationTemplateParser.tokenKey(TemplateField.TITLE, 0, "10k"), "10k"),
                TemplateSegment.Literal(" "),
                TemplateSegment.Token(AffirmationTemplateParser.tokenKey(TemplateField.TITLE, 1, "dolares"), "dolares"),
                TemplateSegment.Literal(" al "),
                TemplateSegment.Token(AffirmationTemplateParser.tokenKey(TemplateField.TITLE, 2, "mes"), "mes"),
            ),
            template.segments,
        )
    }

    @Test
    fun `text with no brackets parses as a single literal segment`() {
        val text = "No tokens here at all"

        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, text)

        assertEquals(listOf(TemplateSegment.Literal(text)), template.segments)
        assertEquals(text, template.render(emptyMap()))
    }

    @Test
    fun `render with no overrides is byte-identical to original text when no brackets present`() {
        val text = "Plain affirmation text."

        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, text)

        assertEquals(text, template.render(emptyMap()))
    }

    @Test
    fun `unpaired opening bracket stays literal`() {
        val text = "Gano [10k mucho"

        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, text)

        assertEquals(listOf(TemplateSegment.Literal(text)), template.segments)
    }

    @Test
    fun `unpaired closing bracket stays literal`() {
        val text = "Gano 10k] mucho"

        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, text)

        assertEquals(listOf(TemplateSegment.Literal(text)), template.segments)
    }

    @Test
    fun `nested-looking bracket resolves to literal prefix plus inner token`() {
        val text = "[a[b]"

        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, text)

        assertEquals(
            listOf(
                TemplateSegment.Literal("[a"),
                TemplateSegment.Token(AffirmationTemplateParser.tokenKey(TemplateField.TITLE, 0, "b"), "b"),
            ),
            template.segments,
        )
    }

    @Test
    fun `blank bracket content is demoted to a literal empty-bracket segment`() {
        val text = "Gano [] al mes"

        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, text)

        assertEquals(
            listOf(TemplateSegment.Literal("Gano [] al mes")),
            template.segments,
        )
    }

    @Test
    fun `bracket content is captured verbatim without trimming`() {
        val text = "Gano [ 10k ] al mes"

        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, text)

        val token = template.segments.filterIsInstance<TemplateSegment.Token>().single()
        assertEquals(" 10k ", token.original)
    }

    @Test
    fun `token key format is field colon ordinal colon content`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "[10k]")

        val token = template.segments.filterIsInstance<TemplateSegment.Token>().single()
        assertEquals("title:0:10k", token.key)
    }

    @Test
    fun `repeated identical bracket content gets distinct ordinals`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "[hoy] y [hoy]")

        val tokens = template.segments.filterIsInstance<TemplateSegment.Token>()
        assertEquals(listOf("title:0:hoy", "title:1:hoy"), tokens.map { it.key })
    }

    @Test
    fun `title and subtitle keys never collide for identical content`() {
        val title = AffirmationTemplateParser.parse(TemplateField.TITLE, "[hoy]")
        val subtitle = AffirmationTemplateParser.parse(TemplateField.SUBTITLE, "[hoy]")

        val titleKey = title.segments.filterIsInstance<TemplateSegment.Token>().single().key
        val subtitleKey = subtitle.segments.filterIsInstance<TemplateSegment.Token>().single().key

        assertEquals("title:0:hoy", titleKey)
        assertEquals("subtitle:0:hoy", subtitleKey)
        assertTrue(titleKey != subtitleKey)
    }

    // --- Resolution ---

    @Test
    fun `override value wins when non-blank`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k]")
        val token = template.segments.filterIsInstance<TemplateSegment.Token>().single()

        val value = template.valueOf(token, mapOf(token.key to "20k"))

        assertEquals("20k", value)
    }

    @Test
    fun `blank override loses to the original value`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k]")
        val token = template.segments.filterIsInstance<TemplateSegment.Token>().single()

        val value = template.valueOf(token, mapOf(token.key to "   "))

        assertEquals("10k", value)
    }

    @Test
    fun `unknown override key is ignored and never throws`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k]")
        val token = template.segments.filterIsInstance<TemplateSegment.Token>().single()

        val value = template.valueOf(token, mapOf("title:99:unknown" to "999"))

        assertEquals("10k", value)
    }

    @Test
    fun `render with empty overrides equals the original values with brackets stripped`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k] al [mes]")

        assertEquals("Gano 10k al mes", template.render(emptyMap()))
    }

    @Test
    fun `render applies overrides in place of original token values`() {
        val template = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k] al [mes]")
        val tokens = template.segments.filterIsInstance<TemplateSegment.Token>()

        val rendered = template.render(mapOf(tokens[0].key to "20k"))

        assertEquals("Gano 20k al mes", rendered)
    }

    // --- Prune ---

    @Test
    fun `editing token content drops the stale override key`() {
        val before = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k]")
        val staleKey = before.segments.filterIsInstance<TemplateSegment.Token>().single().key
        val overrides = mapOf(staleKey to "15k")

        val pruned = AffirmationTemplateParser.pruneOverrides("Gano [20k]", "", overrides)

        assertTrue(pruned.isEmpty())
    }

    @Test
    fun `surrounding text only edit keeps the override key`() {
        val before = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k]")
        val key = before.segments.filterIsInstance<TemplateSegment.Token>().single().key
        val overrides = mapOf(key to "15k")

        val pruned = AffirmationTemplateParser.pruneOverrides("Gano mucho [10k] al mes", "", overrides)

        assertEquals(overrides, pruned)
    }

    @Test
    fun `inserting a token before shifts ordinals and drops downstream keys`() {
        val before = AffirmationTemplateParser.parse(TemplateField.TITLE, "Gano [10k] al [mes]")
        val tokens = before.segments.filterIsInstance<TemplateSegment.Token>()
        val overrides = mapOf(tokens[0].key to "20k", tokens[1].key to "año")

        val pruned = AffirmationTemplateParser.pruneOverrides("Gano [ahora] [10k] al [mes]", "", overrides)

        // tokens[1] ("mes") shifted from ordinal 1 to ordinal 2, so its old key no longer matches.
        assertTrue(pruned.isEmpty())
    }

    // --- normalizeOverrideValue ---

    @Test
    fun `blank or whitespace normalizes to null`() {
        assertNull(AffirmationTemplateParser.normalizeOverrideValue(""))
        assertNull(AffirmationTemplateParser.normalizeOverrideValue("   "))
    }

    @Test
    fun `value within the limit normalizes to the trimmed value`() {
        val result = AffirmationTemplateParser.normalizeOverrideValue("  20k  ")

        assertEquals("20k", result)
    }

    @Test
    fun `value longer than the max length is truncated`() {
        val raw = "a".repeat(MAX_OVERRIDE_VALUE_LENGTH + 50)

        val result = AffirmationTemplateParser.normalizeOverrideValue(raw)

        assertEquals(MAX_OVERRIDE_VALUE_LENGTH, result!!.length)
        assertEquals("a".repeat(MAX_OVERRIDE_VALUE_LENGTH), result)
    }
}
