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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.analytics.AnalyticsContentType
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.provenance

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
    accessDecisionFor: (AffirmationGroup) -> AccessDecision,
    onToggle: (AffirmationGroup) -> Unit,
    onApply: () -> Unit,
    onPeekClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onAddCustomClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onWatchAd: (AffirmationGroup, AdUnlockPolicy) -> Unit = { _, _ -> },
    adInFlightFor: (AffirmationGroup) -> Boolean = { false },
    anyAdInFlight: Boolean = false,
    /** Spec 6 emit surface (REQ-5.4) -- fires `content_locked_tapped` from a locked row's CTA. */
    onEvent: (AnalyticsEvent) -> Unit = {},
    /** Which universes read as partially locked FOR THIS USER (design D19). Defaulted so previews
     * and existing tests compile untouched. */
    partiallyLockedIds: Set<String> = emptySet(),
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
                        decision = accessDecisionFor(group),
                        isPartiallyLocked = group.id in partiallyLockedIds,
                        onToggle = { onToggle(group) },
                        onUpgradeClick = onUpgradeClick,
                        onWatchAd = onWatchAd,
                        adInFlight = adInFlightFor(group),
                        anyAdInFlight = anyAdInFlight,
                        onEvent = onEvent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                item {
                    FavoritesEntryCard(
                        onClick = onFavoritesClick,
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
            .height(64.dp)
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
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AffirmationGroupSelectableRow(
    group: AffirmationGroup,
    checked: Boolean,
    decision: AccessDecision,
    isPartiallyLocked: Boolean = false,
    onToggle: () -> Unit,
    onUpgradeClick: () -> Unit,
    onWatchAd: (AffirmationGroup, AdUnlockPolicy) -> Unit = { _, _ -> },
    adInFlight: Boolean = false,
    anyAdInFlight: Boolean = false,
    onEvent: (AnalyticsEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Single source of lock/toggle truth (design §6) -- `alwaysSelected` short-circuits lock
    // status first, so `PERSONALIZADAS_GROUP` is never locked at either tier. It IS toggleable
    // (TEMPORARY dogfooding relaxation of the old "never toggleable" behavior -- see
    // GroupAccessPolicy.isToggleable's KDoc).
    val toggleable = isToggleable(group, decision)
    val locked = isLocked(decision)
    // MUST be checked before `locked` below -- LockedAdUnlockable satisfies both `locked` and
    // `adUnlockable` (hard constraint, §6.2): an ad-unlockable row is not just "any locked row".
    val adUnlockable = canWatchAdToUnlock(decision)
    val badge = deriveCatalogBadge(group, decision, isPartiallyLocked)
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            when {
                toggleable -> Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                adUnlockable && !anyAdInFlight -> Modifier.clickable {
                    onWatchAd(group, (decision as AccessDecision.LockedAdUnlockable).policy)
                }
                else -> Modifier
            },
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
                if (badge != null) {
                    AffirmationGroupAccessBadge(badge)
                }
            }

            when {
                // MUST precede `locked` below -- an ad-unlockable row also satisfies `locked`,
                // but it must render the play CTA, not the dead lock icon (§6.2, hard constraint).
                adInFlight -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                adUnlockable -> IconButton(
                    onClick = { onWatchAd(group, (decision as AccessDecision.LockedAdUnlockable).policy) },
                    enabled = !anyAdInFlight,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = stringResource(
                            R.string.ad_unlock_cta_a11y,
                            stringResource(group.titleRes),
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Locked rows expose an actionable upgrade CTA (spec's "Upgrade CTA opens inline
                // paywall") instead of a dead lock icon -- tapping it opens the paywall sheet.
                // REQ-5.4: content_locked_tapped fires here, AFTER the adUnlockable branch above,
                // so an ad-unlockable row never reports a locked tap.
                locked -> IconButton(
                    onClick = {
                        onEvent(
                            AnalyticsEvent.ContentLockedTapped(
                                AnalyticsId.of(group), AnalyticsContentType.AFFIRMATION_GROUP, decision.provenance(),
                            ),
                        )
                        onUpgradeClick()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(
                            R.string.affirmation_group_locked_a11y,
                            stringResource(group.titleRes),
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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
 * selector's locked rows. Caller derives [badge] via `GroupAccessPolicy.deriveBadge` (spec §0). */
@Composable
fun AffirmationGroupAccessBadge(badge: GroupBadge) {
    when (badge) {
        GroupBadge.PREMIUM -> AccessBadge(
            icon = Icons.Filled.WorkspacePremium,
            label = stringResource(R.string.affirmation_group_badge_premium),
            containerColor = PremiumBadgeColor.copy(alpha = 0.2f),
            contentColor = PremiumBadgeColor,
            borderColor = PremiumBadgeColor.copy(alpha = 0.3f),
        )
        GroupBadge.AD_UNLOCK -> AccessBadge(
            icon = Icons.Filled.PlayCircle,
            label = stringResource(R.string.affirmation_group_badge_ad),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        GroupBadge.PARTIALLY_LOCKED -> AccessBadge(
            icon = Icons.Filled.LockOpen,
            label = stringResource(R.string.affirmation_group_badge_partial),
            containerColor = PremiumBadgeColor.copy(alpha = 0.08f),
            contentColor = PremiumBadgeColor,
            borderColor = PremiumBadgeColor.copy(alpha = 0.3f),
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
