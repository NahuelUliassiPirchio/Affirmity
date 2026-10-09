package com.pirxhio.affirmity.ui.affirmations

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.pirxhio.affirmity.data.Affirmation
import kotlinx.coroutines.flow.filter

/**
 * Round detection shared by every looping player (the feed and the group player): every settle (the
 * initial page included, hence no drop(1)) is reported with the CURRENT ids. snapshotFlow also
 * re-emits when [affirmations] itself changes under a stationary pager; the tracker then resets (it
 * never completes on a list change) and counts the card the user is sitting on. Nothing is reported
 * while a scroll is still in progress. [onRoundCompleted] receives the size of the walked list.
 */
@Composable
internal fun RoundCompletionEffect(
    pagerState: PagerState,
    affirmations: List<Affirmation>,
    onRoundCompleted: (Int) -> Unit,
) {
    val currentAffirmations by rememberUpdatedState(affirmations)
    val currentOnRoundCompleted by rememberUpdatedState(onRoundCompleted)
    val roundTracker = remember { RoundTracker() }
    LaunchedEffect(pagerState) {
        snapshotFlow {
            Triple(pagerState.settledPage, currentAffirmations.map { it.id }, pagerState.isScrollInProgress)
        }
            .filter { (_, _, scrolling) -> !scrolling }
            .collect { (page, ids, _) ->
                if (roundTracker.onSettled(ids, page)) currentOnRoundCompleted(ids.size)
            }
    }
}
