package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.ui.groups.AffirmationGroup
import com.pirxhio.affirmity.ui.groups.CatalogTheme
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups

/** Theme chips shown collapsed before a group offers a "+N more" expander -- keeps a large
 *  selection scannable at a glance instead of always paying its full wrapped height upfront. */
private const val COLLAPSED_CHIP_COUNT = 3

/** Group cards shown before the section offers a "Show all N groups" expander -- this section sits
 *  above the Discovery grid, the Favorites/Mine toggles and the update button on the feed screen,
 *  so a selection spanning many surfaces must not push all of that far down by default. */
private const val COLLAPSED_GROUP_COUNT = 3

/**
 * "Feeding you now" section (design §4): the current draft selection, grouped by its parent
 * surface (universe) into a vertically-stacked list of group cards. Each group shows a header
 * (icon, title, theme count, remove-all control) and its themes as wrapped chips, collapsed to
 * [COLLAPSED_CHIP_COUNT] by default with a "+N more" expander -- replaces the earlier single-row
 * horizontally-scrolling capsule strip, which made surfaces with many selected themes unreadable
 * without side-scrolling and offered no way to clear a whole surface at once. Only the first
 * [COLLAPSED_GROUP_COUNT] groups show by default, with a "Show all N groups" toggle for the rest.
 */
@Composable
fun CurrentFeedSection(
    selectedThemes: List<CatalogTheme>,
    totalUniqueAffirmationCount: Int,
    onRemoveTheme: (themeId: String) -> Unit,
    onRemoveGroup: (universeId: String) -> Unit,
    onSeeAllThemes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.your_feed_feeding_you_now),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.your_feed_total_affirmation_count, totalUniqueAffirmationCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        val themesByUniverse = selectedThemes.groupBy { it.universeId }
        val groups = catalogUniverseGroups().mapNotNull { group ->
            themesByUniverse[group.id]?.takeIf { it.isNotEmpty() }?.let { group to it }
        }

        // Local UI state only -- never committed to app state, reset is fine on process death.
        val expandedGroupIds = remember { mutableStateMapOf<String, Boolean>() }
        var showAllGroups by remember { mutableStateOf(false) }
        val visibleGroups = if (showAllGroups) groups else groups.take(COLLAPSED_GROUP_COUNT)

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            visibleGroups.forEach { (group, themes) ->
                FeedThemeGroupCard(
                    group = group,
                    themes = themes,
                    isExpanded = expandedGroupIds[group.id] == true,
                    onToggleExpanded = { expandedGroupIds[group.id] = !(expandedGroupIds[group.id] == true) },
                    onRemoveTheme = onRemoveTheme,
                    onRemoveGroup = { onRemoveGroup(group.id) },
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (groups.size > COLLAPSED_GROUP_COUNT) {
                    AssistChip(
                        onClick = { showAllGroups = !showAllGroups },
                        label = {
                            Text(
                                if (showAllGroups) {
                                    stringResource(R.string.your_feed_show_fewer_groups)
                                } else {
                                    stringResource(R.string.your_feed_show_all_groups, groups.size)
                                },
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showAllGroups) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = onSeeAllThemes,
                    label = { Text(stringResource(R.string.your_feed_see_all_themes)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.your_feed_add_theme_a11y),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}

/** One surface's selected themes: a header (icon, title, count, remove-all) above the themes
 *  wrapped in a [FlowRow], collapsed to [COLLAPSED_CHIP_COUNT] chips plus a "+N more" expander
 *  until [isExpanded] -- keeps the section's default height bounded while still letting every
 *  theme wrap onto its own line instead of overflowing off-screen. */
@Composable
private fun FeedThemeGroupCard(
    group: AffirmationGroup,
    themes: List<CatalogTheme>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRemoveTheme: (themeId: String) -> Unit,
    onRemoveGroup: () -> Unit,
) {
    val title = stringResource(group.titleRes)
    val visibleThemes = if (isExpanded) themes else themes.take(COLLAPSED_CHIP_COUNT)
    val hiddenCount = themes.size - visibleThemes.size

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = if (isExpanded) {
                            stringResource(R.string.your_feed_collapse_group_a11y, title)
                        } else {
                            stringResource(R.string.your_feed_expand_group_a11y, title)
                        },
                        role = Role.Button,
                        onClick = onToggleExpanded,
                    ),
            ) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.your_feed_group_theme_count, title, themes.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f, fill = true),
                )
                IconButton(onClick = onRemoveGroup) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.your_feed_remove_group_a11y, title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                visibleThemes.forEach { theme ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveTheme(theme.id) },
                        label = { Text(theme.label) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.your_feed_remove_theme_a11y, theme.label),
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
                if (hiddenCount > 0) {
                    AssistChip(
                        onClick = onToggleExpanded,
                        label = { Text(stringResource(R.string.your_feed_more_chip, hiddenCount)) },
                    )
                }
            }
        }
    }
}
