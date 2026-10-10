package com.pirxhio.affirmity.res

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

/** Guard: every default-groups string exists in the ES default and EN locale files with real text. */
class DefaultGroupStringsTest {
    private val keys = listOf(
        "default_group_everyday_essentials_name",
        "default_group_morning_start_name",
        "groups_action_duplicate",
        "groups_duplicate_copy_suffix",
        "groups_duplicate_copy_suffix_numbered",
        "groups_action_in_feed",
        "collection_name_error_read_only",
        "collection_name_error_catalog_not_ready",
    )

    private fun load(path: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val e = nodes.item(it) as Element
            e.getAttribute("name") to e.textContent
        }
    }

    private val es by lazy { load("src/main/res/values/strings.xml") }
    private val en by lazy { load("src/main/res/values-en/strings.xml") }

    @Test
    fun `every default-groups key is defined and non-blank in both locales`() {
        for (key in keys) {
            assertNotNull("$key missing in ES", es[key])
            assertNotNull("$key missing in EN", en[key])
            assertEquals("$key blank in ES", false, es.getValue(key).isBlank())
            assertEquals("$key blank in EN", false, en.getValue(key).isBlank())
        }
    }

    @Test
    fun `the numbered copy suffix keeps its number placeholder in both locales`() {
        assertEquals("(copia %1\$d)", es["groups_duplicate_copy_suffix_numbered"])
        assertEquals("(copy %1\$d)", en["groups_duplicate_copy_suffix_numbered"])
        assertEquals("(copia)", es["groups_duplicate_copy_suffix"])
        assertEquals("(copy)", en["groups_duplicate_copy_suffix"])
    }

    @Test
    fun `default group names are the product names`() {
        assertEquals("Esenciales del día", es["default_group_everyday_essentials_name"])
        assertEquals("Buen comienzo", es["default_group_morning_start_name"])
        assertEquals("Everyday Essentials", en["default_group_everyday_essentials_name"])
        assertEquals("Morning Start", en["default_group_morning_start_name"])
    }
}
