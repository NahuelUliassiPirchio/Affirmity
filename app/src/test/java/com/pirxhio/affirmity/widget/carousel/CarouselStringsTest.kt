package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resource coverage for the carousel notice copy, checked on the XML so no Android runtime is needed. */
class CarouselStringsTest {

    private fun strings(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("missing ${file.absolutePath}", file.exists())
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val es = strings("values")
    private val en = strings("values-en")

    private fun resourceName(reason: FallbackReason) = "carousel_notice_${reason.name.lowercase()}"

    @Test
    fun `every fallback reason has a non-blank notice in both locales`() {
        FallbackReason.entries.forEach { reason ->
            val name = resourceName(reason)
            assertTrue("es missing $name", !es[name].isNullOrBlank())
            assertTrue("en missing $name", !en[name].isNullOrBlank())
        }
    }

    @Test
    fun `every fallback reason maps to its own string resource`() {
        FallbackReason.entries.forEach { reason ->
            val expected = R.string::class.java.getField(resourceName(reason)).getInt(null)
            assertEquals(reason.name, expected, noticeStringRes(reason))
        }
    }

    @Test
    fun `placeholder and widget description exist in both locales`() {
        listOf("carousel_placeholder", "carousel_widget_description").forEach { name ->
            assertTrue("es missing $name", !es[name].isNullOrBlank())
            assertTrue("en missing $name", !en[name].isNullOrBlank())
        }
    }

    @Test
    fun `the spanish and english notices differ`() {
        FallbackReason.entries.forEach { reason ->
            assertTrue(es.getValue(resourceName(reason)) != en.getValue(resourceName(reason)))
        }
    }
}
