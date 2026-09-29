package com.pirxhio.affirmity.notifications

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class NeutralSpanishResourcesTest {
    // Keep this detector aligned with functions/test/seedCopyCatalog.test.ts.
    private val voseoWords = Regex(
        """(?<![\p{L}])(anclate|asentate|dejate|abrí|acomodate|activá|agregá|alterná|ampliá|anotá|armá|boludo|cambiá|caminá|cerrá|che|completá|considerá|contemplá|continuá|contá|cultivá|dale|decidí|dejalo|dejá|desbloqueá|descansá|deslizá|detenete|elegí|empezá|encontrá|entrá|escribí|esperá|establecé|exhalá|expandí|explorá|extendé|guardalo|guardá|hablá|hacelo|hacé|imaginate|imaginá|inhalá|iniciá|intentá|laburo|leé|llevá|llevátela|mantené|meditá|miralo|mirá|nombrá|notá|observá|ofrecete|orá|parpadeá|pedí|pensá|permanecé|ponete|practicá|preparate|probá|quedate|reconocé|recordá|recorré|recuperá|reflexioná|registrá|regulá|relajá|repetí|respirá|respondete|respondé|seguí|sentate|sentí|sincronizá|soltá|sos|sostené|tensá|tocá|tomate|usalo|usá|visualizá|volvé|vos|zumbá)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )
    private val voseoEnding = Regex("""(?<![\p{L}])\p{L}+(ás|és|ís)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val neutralEndings = setOf("demás", "estás", "más", "además", "después", "país", "atrás", "detrás", "quizás", "jamás", "través", "interés", "inglés", "francés", "hablarás", "tendrás", "podrás", "serás", "estarás", "harás", "dirás", "vendrás", "saldrás", "querrás", "sabrás", "pondrás", "valdrás", "habrás", "irás")

    private fun usesVoseo(text: String): Boolean =
        voseoWords.containsMatchIn(text) || voseoEnding.findAll(text).any {
            val word = it.value.lowercase(Locale.ROOT)
            // Only reviewed neutral words are exempt; mirás and estirás are voseo.
            word !in neutralEndings
        }

    @Test
    fun `all default Spanish resource text uses neutral Spanish`() {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml")).documentElement
        // Scan strings plus array/plural items. No resource or affirmation exclusions are needed.
        for (tag in listOf("string", "item")) {
            val nodes = root.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                val name = element.getAttribute("name").ifEmpty {
                    (element.parentNode as Element).getAttribute("name")
                }
                assertFalse("$name uses voseo/regional forms: ${element.textContent}", usesVoseo(element.textContent))
            }
        }
    }

    @Test
    fun `detector distinguishes voseo from neutral Spanish`() {
        for (sample in listOf(
            "Llevás 5 días", "Vos sabes", "Todavía llegás", "Ya venís", "Seguís sumando",
            "Tenés tiempo", "Sos genial", "Hacelo hoy", "Usalo hoy", "Mantené la racha",
            "Leé una hoy", "Sentate un rato", "Dale que va", "Creés que puedes", "Acomodate",
            "Respondete", "Mirá aquí", "Inhalá", "Quedate", "PreparATE", "Imaginate", "GUARDALO", "Mirás", "Estirás", "Anclate", "Asentate", "Dejate",
        )) assertTrue(sample, usesVoseo(sample))
        for (sample in listOf(
            "Llevas 5 días meditando", "Lee una hoy", "Siéntate un momento", "Sigues sumando",
            "Aún estás a tiempo", "Tienes tiempo", "Después de las prácticas", "Hablarás después",
            "Tendrás tiempo", "Darle espacio", "Date un momento", "Más interés en el país", "Creo que puedes", "Las demás prácticas",
        )) assertFalse(sample, usesVoseo(sample))
    }
}
