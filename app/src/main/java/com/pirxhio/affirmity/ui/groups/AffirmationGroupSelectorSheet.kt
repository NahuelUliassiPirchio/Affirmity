package com.pirxhio.affirmity.ui.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R

/**
 * Persistent, non-dismissible sheet docked under the affirmations feed. Peek row is always
 * visible; expanded content shows every selectable group plus the Aplicar action.
 */
@Composable
fun AffirmationGroupSelectorSheet(
    groups: List<AffirmationGroup>,
    selectedIds: Set<String>,
    isValid: Boolean,
    isExpanded: Boolean,
    onToggle: (AffirmationGroup) -> Unit,
    onApply: () -> Unit,
    onPeekClick: () -> Unit,
    onAddCustomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight(0.85f)) {
        // While expanded, tapping the peek/handle row collapses the sheet the same way Aplicar
        // does (commits the draft, subject to the same validation gate) -- otherwise tapping it
        // only ever expanded, so there was no way to collapse by tapping this row.
        GroupSelectorPeekRow(
            isExpanded = isExpanded,
            onClick = if (isExpanded) onApply else onPeekClick,
        )

        if (isExpanded) {
            Text(
                text = stringResource(R.string.affirmation_group_selector_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(groups, key = { it.id }) { group ->
                    AffirmationGroupSelectableRow(
                        group = group,
                        checked = group.id in selectedIds,
                        onToggle = { onToggle(group) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                item {
                    AddCustomAffirmationsCard(
                        onClick = onAddCustomClick,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, top = 8.dp),
            ) {
                if (!isValid) {
                    Text(
                        text = stringResource(R.string.affirmation_group_selector_min_selection_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = onApply,
                    enabled = isValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.affirmation_group_selector_apply))
                }
            }
        }
    }
}

@Composable
private fun GroupSelectorPeekRow(isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The handle itself IS the up/down chevron -- pointing up ("can expand") when
            // collapsed, down ("can collapse") when expanded -- replacing the plain drag-handle
            // dash so the affordance direction is unambiguous at a glance.
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(
                    if (isExpanded) {
                        R.string.affirmation_group_selector_collapse
                    } else {
                        R.string.affirmation_group_selector_expand
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AffirmationGroupSelectableRow(
    group: AffirmationGroup,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleable = !group.alwaysSelected && group.access == AffirmationGroupAccess.FREE
    // alwaysSelected groups (personalizadas) can show a non-FREE access badge purely as a
    // monetization preview without being visually/functionally locked -- see PERSONALIZADAS_GROUP.
    val locked = group.access != AffirmationGroupAccess.FREE && !group.alwaysSelected
    val showsAccessBadge = group.access != AffirmationGroupAccess.FREE
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (toggleable) {
                Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            } else {
                Modifier
            }
        )
        .let { if (locked) it.alpha(0.6f) else it }

    Card(
        modifier = rowModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = stringResource(group.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(group.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showsAccessBadge) {
                    AffirmationGroupAccessBadge(group.access)
                }
            }

            when {
                locked -> Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(
                        R.string.affirmation_group_locked_a11y,
                        stringResource(group.titleRes),
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                group.alwaysSelected -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.affirmation_group_always_on_a11y),
                    tint = MaterialTheme.colorScheme.primary,
                )
                checked -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun AddCustomAffirmationsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.affirmation_group_add_custom),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Relocated from the now-deleted `AffirmationGroupsScreen.kt` (design D8) — still used by the
 * selector's locked rows. */
@Composable
fun AffirmationGroupAccessBadge(access: AffirmationGroupAccess) {
    when (access) {
        AffirmationGroupAccess.FREE -> Unit
        AffirmationGroupAccess.PREMIUM -> AccessBadge(
            icon = Icons.Filled.WorkspacePremium,
            label = stringResource(R.string.affirmation_group_badge_premium),
            containerColor = PremiumBadgeColor.copy(alpha = 0.2f),
            contentColor = PremiumBadgeColor,
            borderColor = PremiumBadgeColor.copy(alpha = 0.3f),
        )
        AffirmationGroupAccess.AD_SUPPORTED -> AccessBadge(
            icon = Icons.Filled.PlayCircle,
            label = stringResource(R.string.affirmation_group_badge_ad),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun AccessBadge(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
) {
    Row(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private val PremiumBadgeColor = Color(0xFFED9A68)
