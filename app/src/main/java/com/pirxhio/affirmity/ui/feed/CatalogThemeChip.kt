package com.pirxhio.affirmity.ui.feed

import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.analytics.AnalyticsContentType
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.provenance
import com.pirxhio.affirmity.ui.groups.AffirmationGroupAccessBadge
import com.pirxhio.affirmity.ui.groups.CatalogTheme
import com.pirxhio.affirmity.ui.groups.GroupBadge
import com.pirxhio.affirmity.ui.groups.isThemeToggleable

/** One theme as a chip: a selectable [InputChip] when unlocked (tap toggles it in/out of the
 *  draft feed), or a muted [AssistChip] when locked -- tapping a locked chip fires
 *  [AnalyticsEvent.ContentLockedTapped], then routes to upgrade. No trailing close icon here
 *  (unlike [CurrentFeedSection]'s chips): this is add/remove-by-tap on the whole chip, not a
 *  dedicated remove action.
 *
 *  [showBadge] trails the shared [AffirmationGroupAccessBadge] on the chip itself (default): needed
 *  wherever locked and unlocked chips are interleaved (e.g. [SeeAllThemesScreen]'s browse grid), so
 *  a locked one is identifiable at a glance without a grouping header to carry that signal. Callers
 *  that already segregate every locked theme under one PRO-only section header -- e.g.
 *  [SurfaceDetailBottomSheet]'s "Go deeper" -- pass `false` and show the badge once, on the header,
 *  instead of once per chip.
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
    showBadge: Boolean = true,
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
            trailingIcon = if (showBadge) {
                { AffirmationGroupAccessBadge(decision.lockedThemeBadge()) }
            } else {
                null
            },
        )
    }
}

/** [ThemeAccessPolicy][com.pirxhio.affirmity.ui.groups.ThemeAccessPolicy]'s kdoc notes v1's "Go
 *  deeper" mockup only ever shows a PRO badge (no ad CTA for themes yet), but this still branches
 *  on [AccessDecision.LockedAdUnlockable] rather than assuming PREMIUM -- the sealed type already
 *  distinguishes them, so a future ad-unlockable theme needs no change here. */
private fun AccessDecision.lockedThemeBadge(): GroupBadge = when (this) {
    is AccessDecision.LockedAdUnlockable -> GroupBadge.AD_UNLOCK
    else -> GroupBadge.PREMIUM
}
