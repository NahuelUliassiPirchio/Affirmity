package com.pirxhio.affirmity.ui.groups

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogThemeTitlesTest {
    private fun resName(themeId: String) = "catalog_theme_" + themeId.replace('.', '_')

    private fun loadStrings(path: String): Map<String, String> {
        val file = File(path)
        assertTrue("Missing ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val node = nodes.item(it)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private val spanish by lazy { loadStrings("src/main/res/values/strings.xml") }
    private val english by lazy { loadStrings("src/main/res/values-en/strings.xml") }

    @Test
    fun everyThemeHasATitleResourceAndNoUnknownIdsExist() {
        val ids = catalogThemes().map { it.id }.toSet()
        assertEquals(ids, catalogThemeTitleRes.keys)
    }

    @Test
    fun catalogThemesCarryTheirTitleRes() {
        catalogThemes().forEach { assertEquals(catalogThemeTitleRes[it.id], it.titleRes) }
    }

    @Test
    fun everyThemeStringExistsInBothLocalesAndEnglishMatchesTheHumanizedSlug() {
        catalogThemes().forEach { theme ->
            val name = resName(theme.id)
            assertTrue("$name missing in es", spanish.containsKey(name))
            assertEquals(humanizeSlug(theme.id.substringAfterLast('.')), english[name])
            assertEquals(theme.label, english[name])
        }
    }

    @Test
    fun spanishTitlesAreNotBlank() {
        catalogThemes().forEach { assertTrue(resName(it.id), !spanish[resName(it.id)].isNullOrBlank()) }
    }
}
