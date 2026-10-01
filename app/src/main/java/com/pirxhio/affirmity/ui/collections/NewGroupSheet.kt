package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.ui.theme.GroupHighlight

private val PreviewCoverSize = 124.dp
private val PreviewCoverCorner = 18.dp
private val PreviewNameSize = 21.sp

// Mirrors the Cancel text button's width so the centred title stays centred.
private val CancelButtonWidth = 72.dp
private val SwatchSize = 44.dp
private val SwatchInset = 3.dp
private const val UnavailableAlpha = 0.5f

// Write my own is the only start the business logic supports (see [GroupStartOption]).
private val SelectedStart = GroupStartOption.WriteOwn

/**
 * "New group" sheet (design 7b): live cover preview, name, highlight colour and the starting
 * point. [onCreate] is authoritative like [CollectionNameDialog]'s submit: Ok dismisses the sheet,
 * any other verdict (a duplicate that lost a race, the free-tier limit) is shown inline.
 *
 * Only "Write my own" is selectable: see [GroupStartOption]. [showStartWith] hides that section
 * for entry points that already seed the group ("Add to collection" on an affirmation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewGroupSheet(
    canCreate: Boolean,
    onCreate: suspend (name: String, highlightId: String) -> CollectionNameResult,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    showStartWith: Boolean = true,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var highlightId by rememberSaveable { mutableStateOf(GroupHighlight.Default.id) }
    val submitState = rememberNameSubmitState()
    val highlight = GroupHighlight.fromId(highlightId)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.collection_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = stringResource(R.string.groups_new_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Box(modifier = Modifier.size(width = CancelButtonWidth, height = 1.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                GroupCover(
                    name = name.trim().ifEmpty { null },
                    highlight = highlight,
                    size = PreviewCoverSize,
                    cornerRadius = PreviewCoverCorner,
                    nameSize = PreviewNameSize,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                TextField(
                    value = name,
                    onValueChange = {
                        name = clampGroupName(it)
                        submitState.clearError()
                    },
                    placeholder = { Text(stringResource(R.string.groups_new_name_hint)) },
                    suffix = {
                        Text(
                            text = nameCounter(name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    isError = submitState.errorRes != null,
                    supportingText = submitState.errorRes?.let { res -> { Text(stringResource(res)) } },
                    shape = RoundedCornerShape(13.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )

                SectionLabel(R.string.groups_new_highlight_title)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    GroupHighlight.entries.forEach { option ->
                        HighlightSwatch(
                            option = option,
                            selected = option == highlight,
                            onSelect = { highlightId = option.id },
                        )
                    }
                }

                if (showStartWith) {
                    SectionLabel(R.string.groups_new_start_title)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp)),
                    ) {
                        GroupStartOption.entries.forEach { option ->
                            StartOptionRow(option = option, selected = option == SelectedStart)
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
                if (!canCreate) CollectionLimitNotice(onUpgrade = onUpgrade)
                Button(
                    enabled = canCreate && canSubmitNewGroup(name) && !submitState.submitting,
                    onClick = { submitState.submit(action = { onCreate(name, highlight.id) }, onDone = onDismiss) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        text = stringResource(R.string.groups_new_create),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(textRes: Int) {
    Text(
        text = stringResource(textRes).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun HighlightSwatch(option: GroupHighlight, selected: Boolean, onSelect: () -> Unit) {
    val label = stringResource(R.string.groups_new_highlight_a11y, stringResource(option.labelRes()))
    // 48dp touch target around the swatch; the selection ring sits outside the dot.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Box(
            modifier = Modifier
                .size(SwatchSize)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                )
                .padding(SwatchInset)
                .background(option.color, CircleShape)
                .semantics { contentDescription = label },
        )
    }
}

@Composable
private fun StartOptionRow(option: GroupStartOption, selected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (option.available) 1f else UnavailableAlpha)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Icon(option.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stringResource(option.titleRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(option.hintRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun GroupStartOption.icon(): ImageVector = when (this) {
    GroupStartOption.WriteOwn -> Icons.Filled.Edit
    GroupStartOption.FromFavorites -> Icons.Filled.Favorite
    GroupStartOption.RemixTheme -> Icons.Filled.Tune
}

private fun GroupStartOption.titleRes(): Int = when (this) {
    GroupStartOption.WriteOwn -> R.string.groups_new_start_write_own
    GroupStartOption.FromFavorites -> R.string.groups_new_start_favorites
    GroupStartOption.RemixTheme -> R.string.groups_new_start_remix
}

private fun GroupStartOption.hintRes(): Int = when (this) {
    GroupStartOption.WriteOwn -> R.string.groups_new_start_write_own_hint
    GroupStartOption.FromFavorites -> R.string.groups_new_start_favorites_hint
    GroupStartOption.RemixTheme -> R.string.groups_new_start_remix_hint
}
