package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AffirmationImportTest {

    @Test
    fun `valid array with both color and image types parses all fields`() {
        val json = """
            [
              {"title": "I am capable of change", "subtitle": "Growth starts with a single choice", "background": {"type": "color", "value": "#2A9D8F"}},
              {"title": "Second one", "subtitle": "Second subtitle", "background": {"type": "image", "value": "https://example.com/pic.jpg"}}
            ]
        """.trimIndent()

        val result = parseAffirmationsJson(json)

        assertEquals(2, result.size)
        assertEquals(ParsedAffirmation("I am capable of change", "Growth starts with a single choice", "color", "#2A9D8F"), result[0])
        assertEquals(ParsedAffirmation("Second one", "Second subtitle", "image", "https://example.com/pic.jpg"), result[1])
    }

    @Test
    fun `empty array is valid and yields zero affirmations`() {
        val result = parseAffirmationsJson("[]")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `invalid JSON syntax throws`() {
        val error = assertThrows("[{\"title\": ") // malformed
        assertTrue(error.message!!.contains("no es un JSON válido"))
    }

    @Test
    fun `JSON object instead of array throws`() {
        val error = assertThrows("""{"title": "x"}""")
        assertTrue(error.message!!.contains("no es un JSON válido"))
    }

    @Test
    fun `missing title throws`() {
        val json = """[{"subtitle": "s", "background": {"type": "color", "value": "#FFFFFF"}}]"""

        val error = assertThrows(json)
        assertTrue(error.message!!.contains("title"))
    }

    @Test
    fun `missing subtitle throws`() {
        val json = """[{"title": "t", "background": {"type": "color", "value": "#FFFFFF"}}]"""

        val error = assertThrows(json)
        assertTrue(error.message!!.contains("subtitle"))
    }

    @Test
    fun `invalid background type throws`() {
        val json = """[{"title": "t", "subtitle": "s", "background": {"type": "video", "value": "x"}}]"""

        val error = assertThrows(json)
        assertTrue(error.message!!.contains("background.type"))
    }

    @Test
    fun `missing background value throws`() {
        val json = """[{"title": "t", "subtitle": "s", "background": {"type": "color", "value": ""}}]"""

        val error = assertThrows(json)
        assertTrue(error.message!!.contains("background.value"))
    }

    private fun assertThrows(json: String): IllegalArgumentException {
        try {
            parseAffirmationsJson(json)
        } catch (e: IllegalArgumentException) {
            return e
        }
        fail("Expected IllegalArgumentException for input: $json")
        error("unreachable")
    }
}
