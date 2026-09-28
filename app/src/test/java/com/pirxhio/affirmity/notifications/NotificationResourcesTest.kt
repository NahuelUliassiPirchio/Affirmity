package com.pirxhio.affirmity.notifications

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Resource-level guards for the custom notification layouts, run on the JVM by parsing the XML
 * directly (Gradle's unit-test working directory is the `app` module).
 */
class NotificationResourcesTest {

    private val res = File("src/main/res")
    private val android = "http://schemas.android.com/apk/res/android"

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).documentElement

    private fun colors(values: String): Map<String, String> {
        val nodes = parse(File(res, "$values/colors.xml")).getElementsByTagName("color")
        return (0 until nodes.length).associate {
            val e = nodes.item(it) as Element
            e.getAttribute("name") to e.textContent.trim()
        }
    }

    private fun luminance(argb: String): Double {
        val hex = argb.removePrefix("#").takeLast(6)
        fun channel(i: Int): Double {
            val c = hex.substring(i, i + 2).toInt(16) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
    }

    private fun contrast(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun palette(mode: String): Map<String, String> =
        colors("values") + if (mode == "values-night") colors("values-night") else emptyMap()

    @Test
    fun `small text on every notification surface meets WCAG AA in light and night`() {
        for (mode in listOf("values", "values-night")) {
            val p = palette(mode)
            listOf(
                "notif_streak_text" to "notif_streak_bg",
                "notif_reflection_text" to "notif_reflection_bg",
                "notif_reflection_accent" to "notif_reflection_bg",
                "notif_mood_text" to "notif_mood_bg",
                "notif_streak_chip_text" to "notif_streak_chip_bg",
            ).forEach { (fg, bg) ->
                val ratio = contrast(p.getValue(fg), p.getValue(bg))
                assertTrue("$mode $fg on $bg = %.2f".format(ratio), ratio >= 4.5)
            }
        }
    }

    @Test
    fun `large streak count accent meets the 3 to 1 large-text ratio in light and night`() {
        for (mode in listOf("values", "values-night")) {
            val p = palette(mode)
            val ratio = contrast(p.getValue("notif_streak_accent"), p.getValue("notif_streak_bg"))
            assertTrue("$mode streak accent = %.2f".format(ratio), ratio >= 3.0)
        }
    }

    private fun layout(name: String) = parse(File(res, "layout/$name.xml"))

    private fun textViews(root: Element): List<Element> {
        val nodes = root.getElementsByTagName("TextView")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun `streak and reflection layouts expose a root id so the whole card is one tap target`() {
        listOf(
            "notification_streak_collapsed" to "streak_root",
            "notification_streak_expanded" to "streak_root",
            "notification_reflection_collapsed" to "reflection_root",
            "notification_reflection_expanded" to "reflection_root",
        ).forEach { (file, id) ->
            assertEquals(file, "@+id/$id", layout(file).getAttributeNS(android, "id"))
        }
    }

    @Test
    fun `collapsed layouts autosize every sp text so large font scales do not clip`() {
        listOf(
            "notification_streak_collapsed",
            "notification_reflection_collapsed",
            "notification_mood_collapsed",
        ).forEach { file ->
            textViews(layout(file)).forEach { tv ->
                assertEquals(
                    "$file ${tv.getAttributeNS(android, "id")}",
                    "uniform",
                    tv.getAttributeNS(android, "autoSizeTextType"),
                )
            }
        }
    }

    @Test
    fun `mood layouts are text-only with a root tap target and no emoji views`() {
        listOf("notification_mood_collapsed", "notification_mood_expanded").forEach { file ->
            val root = layout(file)
            assertEquals(file, "@+id/mood_root", root.getAttributeNS(android, "id"))
            textViews(root).forEach { tv ->
                val id = tv.getAttributeNS(android, "id")
                assertTrue("$file $id", !id.contains("emoji") && !id.contains("face"))
            }
        }
    }
}
