package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.ui.groups.CatalogTheme
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups

/**
 * "Feeding you now" section (design §4): the current draft selection, grouped by its parent
 * surface (universe) into a single-row, horizontally-scrolling strip of tinted "capsules" -- a
 * full grouped/wrapped layout took too much vertical space (feedback), so grouping here is a
 * visual cue (capsule background + surface label) rather than a layout change: still one row,
 * same height as before. Plus a trailing `+` chip that opens "See all themes".
 */
@Composable
fun CurrentFeedSection(
    selectedThemes: List<CatalogTheme>,
    onRemoveTheme: (themeId: String) -> Unit,
    onSeeAllThemes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.your_feed_feeding_you_now),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        val themesByUniverse = selectedThemes.groupBy { it.universeId }
        val groups = catalogUniverseGroups().mapNotNull { group ->
            themesByUniverse[group.id]?.takeIf { it.isNotEmpty() }?.let { group to it }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(groups, key = { (group, _) -> group.id }) { (group, themes) ->
                FeedThemeCapsule(
                    label = stringResource(group.titleRes),
                    themes = themes,
                    onRemoveTheme = onRemoveTheme,
                )
            }
            item {
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

/** One surface's selected themes clustered into a single tinted pill -- the surface label sits
 *  above its row of themes (rather than inline), each with its own inline remove affordance, all
 *  sharing the capsule's background instead of each theme being its own bordered chip (keeps a
 *  large selection compact and scannable). */
@Composable
private fun FeedThemeCapsule(
    label: String,
    themes: List<CatalogTheme>,
    onRemoveTheme: (themeId: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            themes.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        text = theme.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.your_feed_remove_theme_a11y, theme.label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClickLabel = null,
                                role = Role.Button,
                                onClick = { onRemoveTheme(theme.id) },
                            ),
                    )
                }
            }
        }
    }
}
