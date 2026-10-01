package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.ui.affirmations.FavoriteGesture
import com.pirxhio.affirmity.ui.affirmations.AffirmationCard

/**
 * Full-screen player for one group: the feed's vertical swipe and card look, none of its chrome
 * (no save button, status overlay, gestures or editing). Back and the close button both dismiss.
 * Not looping: it stops at the first and last affirmation.
 */
@Composable
internal fun GroupPlayerDialog(
    affirmations: List<Affirmation>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pagerState = rememberPagerState(pageCount = { affirmations.size })
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AffirmationCard(
                    affirmation = affirmations[page],
                    isFavorite = false,
                    onToggleFavorite = {},
                    onOverrideCommitted = { _, _ -> },
                    onHide = {},
                    onShared = {},
                    favoriteGesture = FavoriteGesture.DOUBLE_TAP,
                    isCleanScreen = false,
                    onEnterCleanScreen = {},
                    onAddToCollection = null,
                    isInAnyGroup = false,
                    readOnly = true,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.groups_player_close),
                    tint = Color.White,
                )
            }
        }
    }
}
