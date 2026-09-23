package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.ui.groups.CatalogTheme
import com.pirxhio.affirmity.ui.groups.catalogThemesById
import com.pirxhio.affirmity.ui.groups.isThemeToggleable

/**
 * Detail sheet opened from a [DiscoverySurfaceCard] tap (design §4): "Suggested for you"
 * ([SurfaceUiModel.recommendedThemeIds], checkmark reflects [draftThemeIds] membership -- never
 * auto-checked), "More in {surface}" (the remaining toggleable themes), and "Go deeper" (themes
 * where [accessDecisionFor] resolves locked -- PRO badge, routes to upgrade). Each section renders
 * its themes as wrapping [CatalogThemeChip]s in a [FlowRow] -- the same toggle-chip visual
 * language [SeeAllThemesScreen] and [CurrentFeedSection] already use, replacing the earlier
 * full-width `ThemeSelectionRow`/`PremiumThemeRow` rows (now deleted) with a denser, scannable
 * grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurfaceDetailBottomSheet(
    surface: SurfaceUiModel,
    draftThemeIds: Set<String>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    onToggleTheme: (themeId: String) -> Unit,
    onUpgradeClick: () -> Unit,
    onEvent: (AnalyticsEvent) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
) {
    val themesById = catalogThemesById()
    val recommendedIds = surface.recommendedThemeIds.toSet()
    val toggleableIds = surface.themeIds.filter { isThemeToggleable(accessDecisionFor(it)) }
    val lockedIds = surface.themeIds - toggleableIds.toSet()
    val suggested = surface.themeIds.filter { it in recommendedIds && it in toggleableIds }
    val more = toggleableIds.filterNot { it in recommendedIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(surface.titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (suggested.isNotEmpty()) {
                SectionHeader(stringResource(R.string.your_feed_suggested_for_you))
                ThemeChipFlowRow(
                    themeIds = suggested,
                    themesById = themesById,
                    draftThemeIds = draftThemeIds,
                    accessDecisionFor = accessDecisionFor,
                    onToggleTheme = onToggleTheme,
                    onUpgradeClick = onUpgradeClick,
                    onEvent = onEvent,
                )
            }
            if (more.isNotEmpty()) {
                SectionHeader(stringResource(R.string.your_feed_more_in, stringResource(surface.titleRes)))
                ThemeChipFlowRow(
                    themeIds = more,
                    themesById = themesById,
                    draftThemeIds = draftThemeIds,
                    accessDecisionFor = accessDecisionFor,
                    onToggleTheme = onToggleTheme,
                    onUpgradeClick = onUpgradeClick,
                    onEvent = onEvent,
                )
            }
            if (lockedIds.isNotEmpty()) {
                SectionHeader(stringResource(R.string.your_feed_go_deeper))
                ThemeChipFlowRow(
                    themeIds = lockedIds,
                    themesById = themesById,
                    draftThemeIds = draftThemeIds,
                    accessDecisionFor = accessDecisionFor,
                    onToggleTheme = onToggleTheme,
                    onUpgradeClick = onUpgradeClick,
                    onEvent = onEvent,
                )
            }
            Column(modifier = Modifier.padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeChipFlowRow(
    themeIds: List<String>,
    themesById: Map<String, CatalogTheme>,
    draftThemeIds: Set<String>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    onToggleTheme: (themeId: String) -> Unit,
    onUpgradeClick: () -> Unit,
    onEvent: (AnalyticsEvent) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        themeIds.forEach { themeId ->
            val theme = themesById[themeId] ?: return@forEach
            CatalogThemeChip(
                theme = theme,
                checked = themeId in draftThemeIds,
                decision = accessDecisionFor(themeId),
                onToggleTheme = onToggleTheme,
                onUpgradeClick = onUpgradeClick,
                onEvent = onEvent,
            )
        }
    }
}
