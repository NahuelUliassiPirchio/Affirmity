package com.pirxhio.affirmity.ui.meditation.customization

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.meditation.customization.CustomizationField
import com.pirxhio.affirmity.meditation.customization.decodeMultiSelect
import com.pirxhio.affirmity.meditation.customization.storageKey
import com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry
import kotlin.math.roundToInt

/**
 * Pre-session customization step (spec: meditation-customization). Renders one control per
 * [MeditationCatalogEntry.customizationFields] field, seeded from [initialValues] (the caller's
 * job to resolve saved-vs-default via `resolvedValues`), and hands the edited map to [onStart]
 * only when the user confirms — nothing is written back to the caller before that.
 *
 * An entry with no fields still composes correctly (just the title + Start button) since the
 * launch-time wiring may transiently show this screen before the empty-fields fast path applies.
 */
@Composable
fun MeditationCustomizationScreen(
    entry: MeditationCatalogEntry,
    initialValues: Map<String, String>,
    onStart: (Map<String, String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var values by remember(entry.id) { mutableStateOf(initialValues) }

    // Item 5 fix: this route previously had no system-Back path of its own -- only the in-content
    // Cancel button below -- so Back could fall through past this screen entirely (potentially
    // exiting the app) instead of returning to Discover. Wired to the exact same callback Cancel
    // uses, so there is exactly one cancel path, mirroring GuidedMeditationScreen's own
    // single-BackHandler convention. No top app bar exists on this screen, so this is sufficient.
    BackHandler(onBack = onCancel)

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(entry.titleRes))

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(entry.customizationFields, key = { it.key }) { field ->
                CustomizationFieldRow(
                    field = field,
                    values = values,
                    onValuesChange = { values = it },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.meditation_customization_cancel))
            }
            Button(onClick = { onStart(values) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.meditation_customization_start))
            }
        }
    }
}

@Composable
private fun CustomizationFieldRow(
    field: CustomizationField,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
    parentKey: String? = null,
) {
    val key = field.storageKey(parentKey)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(field.labelRes))

        when (field) {
            is CustomizationField.IntSlider -> {
                val current = values[key]?.toIntOrNull() ?: field.default
                Slider(
                    value = current.toFloat(),
                    onValueChange = { onValuesChange(values + (key to (it.roundToInt()).toString())) },
                    valueRange = field.min.toFloat()..field.max.toFloat(),
                    steps = ((field.max - field.min) / field.step - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(),
                )
                Text(current.toString())
            }

            is CustomizationField.Options<*> -> {
                @Suppress("UNCHECKED_CAST")
                val typedField = field as CustomizationField.Options<Any>
                val current = values[key] ?: typedField.encode(typedField.default)
                // Item 6 fix: was a plain non-scrollable Row -- a field with enough options (e.g.
                // affirmationUniverse, one per affirmation group) overflowed the screen width and
                // left its later chips unreachable. FlowRow wraps instead of clipping.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    typedField.options.forEach { option ->
                        val encoded = typedField.encode(option)
                        FilterChip(
                            selected = current == encoded,
                            onClick = { onValuesChange(values + (key to encoded)) },
                            label = { Text(stringResource(typedField.optionLabelRes(option))) },
                        )
                    }
                }
            }

            is CustomizationField.Toggle -> {
                val current = values[key]?.toBooleanStrictOrNull() ?: field.default
                Switch(
                    checked = current,
                    onCheckedChange = { onValuesChange(values + (key to it.toString())) },
                )
            }

            is CustomizationField.FreeText -> {
                val current = values[key] ?: field.default.orEmpty()
                OutlinedTextField(
                    value = current,
                    onValueChange = { onValuesChange(values + (key to it)) },
                    placeholder = field.placeholderRes?.let { { Text(stringResource(it)) } }
                        ?: { Text(stringResource(R.string.meditation_customization_free_text_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is CustomizationField.MultiSelect -> {
                val current = values[key]?.let(::decodeMultiSelect) ?: field.default
                // Item 6 fix: same overflow issue as the Options branch above -- FlowRow instead
                // of a non-scrollable Row.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = option in current,
                            onClick = {
                                val next = if (option in current) current - option else current + option
                                onValuesChange(
                                    values + (key to next.joinToString(separator = CustomizationField.MultiSelect.SEPARATOR)),
                                )
                            },
                            label = { Text(stringResource(field.optionLabelRes(option))) },
                        )
                    }
                }
            }

            is CustomizationField.Group -> {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    field.fields.forEach { child ->
                        CustomizationFieldRow(
                            field = child,
                            values = values,
                            onValuesChange = onValuesChange,
                            parentKey = key,
                        )
                    }
                }
            }
        }
    }
}
