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
import androidx.compose.runtime.LaunchedEffect
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
import com.pirxhio.affirmity.ui.affirmations.RoundCompletionEffect

/**
 * Full-screen player for one group: the feed's vertical swipe and card look, none of its chrome
 * (no save button, status overlay, gestures or editing). Back and the close button both dismiss.
 * Loops infinitely in both directions over the feed's virtual-page model (a single item does not
 * loop and cannot be swiped), opens on the first affirmation, and reports a completed round through
 * [onRoundCompleted] with the same semantics as the feed. Progress lives in memory only, so
 * reopening the player starts over.
 */
@Composable
internal fun GroupPlayerDialog(
    affirmations: List<Affirmation>,
    onRoundCompleted: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val itemCount = affirmations.size
    val pageCount = groupPlayerPageCount(itemCount)
    // Hoisted above the Dialog so the position is saved with the host's state and survives rotation.
    val pagerState = rememberPagerState(
        initialPage = groupPlayerStartPage(itemCount),
        pageCount = { pageCount },
    )
    // The group can change size while the player is open (e.g. 1 -> N leaves the pager on page 0,
    // where a backward swipe would be dead). Re-center onto the same item so the card does not change.
    LaunchedEffect(pageCount) {
        groupPlayerRecenterPageOrNull(pagerState.currentPage, itemCount)
            ?.let { pagerState.scrollToPage(it) }
    }
    RoundCompletionEffect(pagerState, affirmations, onRoundCompleted)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // No key: virtual pages repeat ids, so page identity must not depend on the id.
                userScrollEnabled = groupPlayerLoops(itemCount),
            ) { page ->
                AffirmationCard(
                    affirmation = affirmations[page % itemCount],
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
