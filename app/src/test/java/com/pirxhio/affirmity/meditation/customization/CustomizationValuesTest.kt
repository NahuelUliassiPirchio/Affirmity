package com.pirxhio.affirmity.meditation.customization

import com.pirxhio.affirmity.R
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomizationValuesTest {

    private val rounds = CustomizationField.IntSlider(
        key = "rounds",
        labelRes = R.string.app_name,
        default = 6,
        min = 3,
        max = 30,
    )
    private val pace = CustomizationField.Options(
        key = "pace",
        labelRes = R.string.app_name,
        default = "natural",
        options = listOf("natural", "slow", "very_slow"),
        optionLabelRes = { R.string.app_name },
    )
    private val retention = CustomizationField.Toggle(
        key = "retention",
        labelRes = R.string.app_name,
        default = false,
    )
    private val sacredWord = CustomizationField.FreeText(
        key = "sacredWord",
        labelRes = R.string.app_name,
        default = null,
    )
    private val labels = CustomizationField.MultiSelect(
        key = "labels",
        labelRes = R.string.app_name,
        default = setOf("thinking", "hearing"),
        options = listOf("thinking", "hearing", "feeling"),
        optionLabelRes = { R.string.app_name },
    )
    private val minutesPerStage = CustomizationField.Group(
        key = "minutesPerStage",
        labelRes = R.string.app_name,
        fields = listOf(
            CustomizationField.IntSlider("lectio", R.string.app_name, default = 3, min = 1, max = 10),
            CustomizationField.IntSlider("oratio", R.string.app_name, default = 3, min = 1, max = 10),
        ),
    )

    @Test
    fun `defaultValues flattens a flat field list using each field's default`() {
        val defaults = defaultValues(listOf(rounds, pace, retention))

        assertEquals(
            mapOf("rounds" to "6", "pace" to "natural", "retention" to "false"),
            defaults,
        )
    }

    @Test
    fun `defaultValues omits a null FreeText default`() {
        val defaults = defaultValues(listOf(sacredWord))

        assertEquals(emptyMap<String, String>(), defaults)
    }

    @Test
    fun `defaultValues encodes MultiSelect as separator-joined options`() {
        val defaults = defaultValues(listOf(labels))

        assertEquals("thinking|hearing", defaults.getValue("labels"))
    }

    @Test
    fun `defaultValues namespaces Group children under the group key`() {
        val defaults = defaultValues(listOf(minutesPerStage))

        assertEquals(
            mapOf("minutesPerStage.lectio" to "3", "minutesPerStage.oratio" to "3"),
            defaults,
        )
    }

    @Test
    fun `resolvedValues overlays saved values on top of defaults`() {
        val saved = mapOf("rounds" to "12")

        val resolved = resolvedValues(listOf(rounds, pace), saved)

        assertEquals(mapOf("rounds" to "12", "pace" to "natural"), resolved)
    }

    @Test
    fun `resolvedValues ignores a saved key that no longer matches any field`() {
        val saved = mapOf("rounds" to "12", "stale_key" to "x")

        val resolved = resolvedValues(listOf(rounds), saved)

        assertEquals(mapOf("rounds" to "12"), resolved)
    }

    @Test
    fun `resolvedValues clamps a saved IntSlider value below min up to min`() {
        val saved = mapOf("rounds" to "-5")

        val resolved = resolvedValues(listOf(rounds), saved)

        assertEquals(mapOf("rounds" to "3"), resolved)
    }

    @Test
    fun `resolvedValues clamps a saved IntSlider value above max down to max`() {
        val saved = mapOf("rounds" to "9999")

        val resolved = resolvedValues(listOf(rounds), saved)

        assertEquals(mapOf("rounds" to "30"), resolved)
    }

    @Test
    fun `resolvedValues clamps a saved IntSlider value inside a Group`() {
        val saved = mapOf("minutesPerStage.lectio" to "999")

        val resolved = resolvedValues(listOf(minutesPerStage), saved)

        assertEquals(
            mapOf("minutesPerStage.lectio" to "10", "minutesPerStage.oratio" to "3"),
            resolved,
        )
    }

    @Test
    fun `resolvedValues falls back to default for a saved IntSlider value that is not a number`() {
        val saved = mapOf("rounds" to "not-a-number")

        val resolved = resolvedValues(listOf(rounds), saved)

        assertEquals(mapOf("rounds" to "6"), resolved)
    }

    @Test
    fun `decodeMultiSelect splits on the separator and drops blanks`() {
        assertEquals(setOf("thinking", "hearing"), decodeMultiSelect("thinking|hearing"))
        assertEquals(emptySet<String>(), decodeMultiSelect(""))
    }
}
