package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.analytics.AnalyticsContentType
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.provenance
import com.pirxhio.affirmity.ui.groups.CatalogTheme
import com.pirxhio.affirmity.ui.groups.isThemeToggleable

/** One theme as a chip: a selectable [InputChip] when unlocked (tap toggles it in/out of the
 *  draft feed), or a muted, lock-icon [AssistChip] when locked -- tapping a locked chip fires
 *  [AnalyticsEvent.ContentLockedTapped], then routes to upgrade. No trailing close icon here
 *  (unlike [CurrentFeedSection]'s chips): this is add/remove-by-tap on the whole chip, not a
 *  dedicated remove action.
 *
 *  Shared across [SeeAllThemesScreen] (browse grid) and [SurfaceDetailBottomSheet] (per-surface
 *  detail sheet) -- extracted from a private copy in `SeeAllThemesScreen.kt` once the detail sheet
 *  needed the same toggle-chip visual language (design §4).
 */
@Composable
internal fun CatalogThemeChip(
    theme: CatalogTheme,
    checked: Boolean,
    decision: AccessDecision,
    onToggleTheme: (themeId: String) -> Unit,
    onUpgradeClick: () -> Unit,
    onEvent: (AnalyticsEvent) -> Unit,
) {
    if (isThemeToggleable(decision)) {
        InputChip(
            selected = checked,
            onClick = { onToggleTheme(theme.id) },
            label = { Text(theme.label) },
        )
    } else {
        AssistChip(
            onClick = {
                onEvent(
                    AnalyticsEvent.ContentLockedTapped(
                        AnalyticsId.of(theme),
                        AnalyticsContentType.AFFIRMATION_GROUP,
                        decision.provenance(),
                    ),
                )
                onUpgradeClick()
            },
            label = { Text(theme.label) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.your_feed_locked_theme_a11y),
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
