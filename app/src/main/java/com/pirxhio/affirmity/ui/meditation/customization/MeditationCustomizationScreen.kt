package com.pirxhio.affirmity.ui.meditation.customization

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * Every top-level field (including a [CustomizationField.Group], which renders its children
 * inline rather than as nested cards) gets its own tonal [Card] for visual grouping, mirroring
 * this app's existing settings-card idiom (see `ui/settings/SettingsScreen.kt`'s
 * `ChannelSettingsCard`).
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(entry.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entry.customizationFields, key = { it.key }) { field ->
                CustomizationFieldCard(
                    field = field,
                    values = values,
                    onValuesChange = { values = it },
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
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
    }
}

/** One top-level field, wrapped in its own tonal card. [CustomizationField.Group] renders its
 * children as rows inside this same card rather than nesting another card per child. */
@Composable
private fun CustomizationFieldCard(
    field: CustomizationField,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CustomizationFieldBody(field = field, values = values, onValuesChange = onValuesChange)
        }
    }
}

@Composable
private fun CustomizationFieldBody(
    field: CustomizationField,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
    parentKey: String? = null,
) {
    val key = field.storageKey(parentKey)

    when (field) {
        is CustomizationField.IntSlider -> {
            val current = values[key]?.toIntOrNull() ?: field.default
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(field.labelRes), style = MaterialTheme.typography.titleMedium)
                ValueBadge(text = current.toString())
            }
            Slider(
                value = current.toFloat(),
                onValueChange = { onValuesChange(values + (key to (it.roundToInt()).toString())) },
                valueRange = field.min.toFloat()..field.max.toFloat(),
                steps = ((field.max - field.min) / field.step - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        is CustomizationField.Options<*> -> {
            @Suppress("UNCHECKED_CAST")
            val typedField = field as CustomizationField.Options<Any>
            val current = values[key] ?: typedField.encode(typedField.default)
            Text(stringResource(field.labelRes), style = MaterialTheme.typography.titleMedium)
            // Item 6 fix: was a plain non-scrollable Row -- a field with enough options (e.g.
            // affirmationUniverse, one per affirmation group) overflowed the screen width and
            // left its later chips unreachable. FlowRow wraps instead of clipping.
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                typedField.options.forEach { option ->
                    val encoded = typedField.encode(option)
                    FilterChip(
                        selected = current == encoded,
                        onClick = { onValuesChange(values + (key to encoded)) },
                        label = { Text(stringResource(typedField.optionLabelRes(option))) },
                        colors = customizationChipColors(),
                    )
                }
            }
        }

        is CustomizationField.Toggle -> {
            val current = values[key]?.toBooleanStrictOrNull() ?: field.default
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(field.labelRes), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = current,
                    onCheckedChange = { onValuesChange(values + (key to it.toString())) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        is CustomizationField.FreeText -> {
            val current = values[key] ?: field.default.orEmpty()
            Text(stringResource(field.labelRes), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = current,
                onValueChange = { onValuesChange(values + (key to it)) },
                placeholder = field.placeholderRes?.let { { Text(stringResource(it)) } }
                    ?: { Text(stringResource(R.string.meditation_customization_free_text_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }

        is CustomizationField.MultiSelect -> {
            val current = values[key]?.let(::decodeMultiSelect) ?: field.default
            Text(stringResource(field.labelRes), style = MaterialTheme.typography.titleMedium)
            // Item 6 fix: same overflow issue as the Options branch above -- FlowRow instead
            // of a non-scrollable Row.
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                        colors = customizationChipColors(),
                    )
                }
            }
        }

        is CustomizationField.Group -> {
            Text(stringResource(field.labelRes), style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                field.fields.forEach { child ->
                    CustomizationFieldBody(
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

@Composable
private fun customizationChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/** Small rounded pill echoing an [CustomizationField.IntSlider]'s current value next to its
 * label, so the number reads as a live readout rather than trailing plain text under the slider. */
@Composable
private fun ValueBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
