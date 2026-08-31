package com.pirxhio.affirmity.meditation.customization

/** The `Map<String, String>` produced by every [CustomizationField]'s spec-declared default,
 * flattened per [storageKey]. This is what a meditation launches with when the user has never
 * customized it (and what a saved customization overlays on top of via [resolvedValues]). */
fun defaultValues(fields: List<CustomizationField>, parentKey: String? = null): Map<String, String> =
    fields.fold(emptyMap()) { acc, field ->
        acc + when (field) {
            is CustomizationField.IntSlider -> mapOf(field.storageKey(parentKey) to field.default.toString())
            is CustomizationField.Options<*> -> mapOf(field.storageKey(parentKey) to encodeOption(field))
            is CustomizationField.Toggle -> mapOf(field.storageKey(parentKey) to field.default.toString())
            is CustomizationField.FreeText ->
                field.default?.let { mapOf(field.storageKey(parentKey) to it) } ?: emptyMap()
            is CustomizationField.MultiSelect ->
                mapOf(field.storageKey(parentKey) to encodeMultiSelect(field.default))
            is CustomizationField.Group -> defaultValues(field.fields, field.storageKey(parentKey))
        }
    }

/** Saved values win over defaults for keys the current field list still declares; a saved key
 * belonging to a field that no longer exists (e.g. content authoring changed) is dropped rather
 * than leaking a stale entry into the config map a definition builder will read.
 *
 * A saved [CustomizationField.IntSlider] value is clamped into `[min, max]` (and a non-numeric
 * saved value falls back to the field's default) before it reaches this map. This defends against
 * a persisted row from a previous app version whose declared range has since narrowed -- without
 * this, an out-of-range value could flow into a definition builder's `require(...)` guard (e.g.
 * `rounds > 0`) and crash the session on launch, since the customization Slider only enforces the
 * *current* range at input time, not for values already on disk. */
fun resolvedValues(fields: List<CustomizationField>, saved: Map<String, String>): Map<String, String> {
    val defaults = defaultValues(fields)
    val intSliders = flattenIntSliders(fields)
    return defaults.mapValues { (key, default) ->
        val savedValue = saved[key] ?: return@mapValues default
        val slider = intSliders[key] ?: return@mapValues savedValue
        val parsed = savedValue.toIntOrNull() ?: return@mapValues default
        parsed.coerceIn(slider.min, slider.max).toString()
    }
}

private fun flattenIntSliders(
    fields: List<CustomizationField>,
    parentKey: String? = null,
): Map<String, CustomizationField.IntSlider> =
    fields.fold(emptyMap()) { acc, field ->
        acc + when (field) {
            is CustomizationField.IntSlider -> mapOf(field.storageKey(parentKey) to field)
            is CustomizationField.Group -> flattenIntSliders(field.fields, field.storageKey(parentKey))
            else -> emptyMap()
        }
    }

fun decodeMultiSelect(encoded: String): Set<String> =
    encoded.split(CustomizationField.MultiSelect.SEPARATOR).filter { it.isNotBlank() }.toSet()

private fun encodeMultiSelect(values: Set<String>): String =
    values.joinToString(separator = CustomizationField.MultiSelect.SEPARATOR)

@Suppress("UNCHECKED_CAST")
private fun encodeOption(field: CustomizationField.Options<*>): String =
    (field.encode as (Any) -> String).invoke(field.default)
