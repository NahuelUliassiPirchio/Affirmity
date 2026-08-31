package com.pirxhio.affirmity.meditation.customization

import androidx.annotation.StringRes

/**
 * One adjustable knob on a meditation's pre-session customization screen. A
 * [com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry] declares a
 * `List<CustomizationField>`; the generic customization screen renders one control per field
 * (recursing into [Group]s) and hands the confirmed choices back as a `Map<String, String>` keyed
 * by [key] -- the same shape `MeditationCatalogEntry.definition` now takes, and the same shape
 * persisted by `MeditationCustomizationRepository`.
 *
 * Encoding convention (every value round-trips through a single `String` column, mirroring
 * [com.pirxhio.affirmity.data.local.CatalogOverrideEntity]'s `Map<String, String>` precedent):
 * - [IntSlider] -> the chosen `Int`, e.g. `"6"`.
 * - [Options] -> [Options.encode] of the chosen option (defaults to `toString()`).
 * - [Toggle] -> `"true"` / `"false"`.
 * - [FreeText] -> the raw string as typed, or the key is absent from the map if left blank.
 * - [MultiSelect] -> the chosen options, each passed through [MultiSelect.encode], joined with
 *   [MultiSelect.SEPARATOR]. An empty selection is `""`, not an absent key.
 * - [Group] -> not itself a map key; each child's effective key is `"${group.key}.${child.key}"`,
 *   so a group's children coexist in the same flat map as top-level fields.
 *
 * [key] must be unique within one entry's field list (children of different [Group]s may reuse a
 * child [key] since the group prefix disambiguates them).
 */
sealed interface CustomizationField {
    val key: String
    @get:StringRes val labelRes: Int

    data class IntSlider(
        override val key: String,
        @StringRes override val labelRes: Int,
        val default: Int,
        val min: Int,
        val max: Int,
        val step: Int = 1,
    ) : CustomizationField {
        init {
            require(min < max) { "IntSlider($key): min ($min) must be < max ($max)" }
            require(step > 0) { "IntSlider($key): step ($step) must be > 0" }
            require(default in min..max) { "IntSlider($key): default ($default) outside [$min, $max]" }
        }
    }

    /** A discrete choice among [options]. [encode]/[decode] default to `toString()`/no-op-lookup,
     * overridable for types (e.g. `Float`) whose `toString()` isn't a stable round-trip key. */
    data class Options<T : Any>(
        override val key: String,
        @StringRes override val labelRes: Int,
        val default: T,
        val options: List<T>,
        @StringRes val optionLabelRes: (T) -> Int,
        val encode: (T) -> String = { it.toString() },
    ) : CustomizationField {
        init {
            require(options.isNotEmpty()) { "Options($key): options must not be empty" }
            require(default in options) { "Options($key): default ($default) not in options" }
        }
    }

    data class Toggle(
        override val key: String,
        @StringRes override val labelRes: Int,
        val default: Boolean,
    ) : CustomizationField

    data class FreeText(
        override val key: String,
        @StringRes override val labelRes: Int,
        val default: String? = null,
        @StringRes val placeholderRes: Int? = null,
    ) : CustomizationField

    data class MultiSelect(
        override val key: String,
        @StringRes override val labelRes: Int,
        val default: Set<String>,
        val options: List<String>,
        @StringRes val optionLabelRes: (String) -> Int,
    ) : CustomizationField {
        init {
            require(default.all { it in options }) {
                "MultiSelect($key): default $default contains values outside options $options"
            }
        }

        companion object {
            const val SEPARATOR = "|"
        }
    }

    /** Groups a fixed set of child fields under one shared label (e.g. "minutes per stage"),
     * namespacing their storage keys as `"$key.${child.key}"`. Not itself a map entry. */
    data class Group(
        override val key: String,
        @StringRes override val labelRes: Int,
        val fields: List<CustomizationField>,
    ) : CustomizationField {
        init {
            require(fields.isNotEmpty()) { "Group($key): fields must not be empty" }
            require(fields.none { it is Group }) { "Group($key): nested groups are not supported" }
        }
    }
}

/** Effective storage key for a field that may be nested inside a [CustomizationField.Group]. */
fun CustomizationField.storageKey(parentKey: String? = null): String =
    if (parentKey == null) key else "$parentKey.$key"
