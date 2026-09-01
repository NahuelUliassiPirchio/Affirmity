package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.ui.groups.CatalogTheme

/**
 * "Your feed" top composable (design §4, scope decision #3): current selection shown as
 * removable chips, a personalized 2-column grid of discovery surfaces, "See all themes", and the
 * sticky "Update my feed" CTA. Surface/theme detail navigation ([SurfaceDetailBottomSheet],
 * [SeeAllThemesScreen]) is owned by the caller (mirroring the old group-selector sheet's
 * MainActivity-level orchestration) -- this composable only ever reports intent via
 * [onOpenSurface]/[onSeeAllThemes].
 */
@Composable
fun YourFeedScreen(
    draftThemeIds: Set<String>,
    isDirty: Boolean,
    isValid: Boolean,
    catalogThemesById: Map<String, CatalogTheme>,
    recommendedSurfaces: List<SurfaceUiModel>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    onRemoveTheme: (themeId: String) -> Unit,
    onOpenSurface: (surfaceId: String) -> Unit,
    onSeeAllThemes: () -> Unit,
    onUpdateFeed: () -> Unit,
    onDone: () -> Unit,
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedThemes = draftThemeIds.mapNotNull { catalogThemesById[it] }.sortedBy { it.label }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.your_feed_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.your_feed_done),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            CurrentFeedSection(
                selectedThemes = selectedThemes,
                onRemoveTheme = onRemoveTheme,
                onSeeAllThemes = onSeeAllThemes,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            DiscoverySurfaceGrid(
                surfaces = recommendedSurfaces,
                draftThemeIds = draftThemeIds,
                onOpenSurface = onOpenSurface,
            )
            SeeAllThemesLink(
                onClick = onSeeAllThemes,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            FavoritesEntryCard(
                onClick = onFavoritesClick,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            )
        }

        UpdateFeedButton(isDirty = isDirty, isValid = isValid, onClick = onUpdateFeed)
    }
}

/** "See all themes" as its own bottom link (feedback: it belongs at the bottom of "Your feed",
 *  not only as the "+" quick-add chip in [CurrentFeedSection]) -- matches the exported design's
 *  centered, bordered "See all themes →" row below the discovery grid. */
@Composable
private fun SeeAllThemesLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.your_feed_see_all_themes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Relocated from `SeeAllThemesScreen` (feedback: favorites belongs at the bottom of "Your feed",
 *  next to "See all themes", not buried inside the exhaustive theme browser). */
@Composable
private fun FavoritesEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.affirmation_group_open_favorites),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Docked bottom-sheet content for "Your feed" -- [BottomSheetScaffold]'s `sheetContent`.
 * Restores the old group-selector sheet's persistent peek/expand-collapse pattern instead of a
 * separate full-screen entry point: the peek row is always visible under the affirmations feed,
 * and tapping it toggles between collapsed and [YourFeedScreen] expanded, exactly like the old
 * sheet's Aplicar-on-peek-tap behavior.
 */
@Composable
fun YourFeedSheetContent(
    isExpanded: Boolean,
    draftThemeIds: Set<String>,
    isDirty: Boolean,
    isValid: Boolean,
    catalogThemesById: Map<String, CatalogTheme>,
    recommendedSurfaces: List<SurfaceUiModel>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    onRemoveTheme: (themeId: String) -> Unit,
    onOpenSurface: (surfaceId: String) -> Unit,
    onSeeAllThemes: () -> Unit,
    onUpdateFeed: () -> Unit,
    onDone: () -> Unit,
    onPeekClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight(0.85f)) {
        FeedSelectorPeekRow(
            isExpanded = isExpanded,
            // Tapping the peek strip while expanded commits the draft, same as "Update my feed"
            // -- mirrors the old selector sheet's GroupSelectorPeekRow, which called onApply here.
            onClick = if (isExpanded) onUpdateFeed else onPeekClick,
        )
        if (isExpanded) {
            YourFeedScreen(
                draftThemeIds = draftThemeIds,
                isDirty = isDirty,
                isValid = isValid,
                catalogThemesById = catalogThemesById,
                recommendedSurfaces = recommendedSurfaces,
                accessDecisionFor = accessDecisionFor,
                onRemoveTheme = onRemoveTheme,
                onOpenSurface = onOpenSurface,
                onSeeAllThemes = onSeeAllThemes,
                onUpdateFeed = onUpdateFeed,
                onDone = onDone,
                onFavoritesClick = onFavoritesClick,
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }
}

@Composable
private fun FeedSelectorPeekRow(isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(
                    if (isExpanded) R.string.your_feed_collapse else R.string.your_feed_expand,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
