package com.pirxhio.affirmity.ui.onboarding.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import kotlinx.coroutines.launch

/**
 * The post-survey onboarding guide -- a 4-slide swipeable carousel (spec R3/R4, design D5/D6).
 * Used for BOTH the auto-show gate and the manual Settings re-entry gate (design's Data Flow);
 * [onDismiss] is the one exit path both Skip, the last slide's primary action, and system back all
 * funnel into (R4.2/R4.3/R4.5) -- the caller decides what "dismiss" means (mark seen vs. close).
 */
@Composable
fun OnboardingGuideScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    slides: List<OnboardingGuideSlide>? = null,
) {
    val defaultSlides = remember { onboardingGuideSlides() }
    val resolvedSlides = slides ?: defaultSlides
    val pagerState = rememberPagerState(pageCount = { resolvedSlides.size })
    val scope = rememberCoroutineScope()
    val isLastPage by remember(pagerState, resolvedSlides) {
        derivedStateOf { pagerState.currentPage == resolvedSlides.lastIndex }
    }

    // R4.5: back is intercepted and behaves exactly like Skip -- dismiss + mark seen, never
    // resumable at a later slide.
    BackHandler(onBack = onDismiss)

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            // R4.2: Skip is available on EVERY slide, regardless of current page.
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.onboarding_guide_skip_button))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            OnboardingGuideSlideContent(slide = resolvedSlides[page])
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OnboardingGuideDots(
                pageCount = resolvedSlides.size,
                currentPage = pagerState.settledPage,
            )

            Button(
                onClick = {
                    if (isLastPage) {
                        // R4.3: completing the last slide is behaviorally equivalent to Skip in
                        // its persistence outcome (dismiss + mark seen).
                        onDismiss()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            ) {
                Text(
                    // R4.1: the last slide's primary action reads as a completion action, never
                    // "Next".
                    text = stringResource(
                        if (isLastPage) {
                            R.string.onboarding_guide_get_started_button
                        } else {
                            R.string.onboarding_guide_next_button
                        },
                    ),
                )
            }
        }
    }
}
