package com.pirxhio.affirmity.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverridesConvertersTest {

    private val converters = OverridesConverters()

    @Test
    fun `round-trips a map through fromOverrides and toOverrides`() {
        val original = mapOf("title:0:10k" to "20k", "title:1:mes" to "año")

        val column = converters.fromOverrides(original)
        val result = converters.toOverrides(column)

        assertEquals(original, result)
    }

    @Test
    fun `output is deterministic regardless of map insertion order`() {
        val a = linkedMapOf("title:1:mes" to "año", "title:0:10k" to "20k")
        val b = linkedMapOf("title:0:10k" to "20k", "title:1:mes" to "año")

        assertEquals(converters.fromOverrides(a), converters.fromOverrides(b))
    }

    @Test
    fun `special characters in keys and values survive round-trip`() {
        val original = mapOf("title:0:\"quoted\"" to "back\\slash{brace}:colon")

        val column = converters.fromOverrides(original)
        val result = converters.toOverrides(column)

        assertEquals(original, result)
    }

    @Test
    fun `null column value yields an empty map`() {
        assertTrue(converters.toOverrides(null).isEmpty())
    }

    @Test
    fun `empty string column value yields an empty map`() {
        assertTrue(converters.toOverrides("").isEmpty())
    }

    @Test
    fun `garbage column value yields an empty map instead of throwing`() {
        assertTrue(converters.toOverrides("garbage").isEmpty())
    }

    @Test
    fun `blank values are filtered out on read`() {
        val column = """{"title:0:10k":"   "}"""

        val result = converters.toOverrides(column)

        assertTrue(result.isEmpty())
    }
}
