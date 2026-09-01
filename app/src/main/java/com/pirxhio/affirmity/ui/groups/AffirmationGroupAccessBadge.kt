package com.pirxhio.affirmity.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R

/** Relocated from the now-deleted `AffirmationGroupSelectorSheet.kt` -- still used by
 *  [com.pirxhio.affirmity.ui.meditation.MeditationScreen]'s locked rows, and by the new
 *  `ui/feed` theme selection rows. Caller derives [badge] via [deriveBadge]/[deriveCatalogBadge]. */
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
