package com.pirxhio.affirmity.ui.onboarding.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R

/**
 * Dot-style progress indicator (spec R4.4), driven by [currentPage] -- the settled pager page, not
 * an in-flight drag offset. Individual dots are decorative (`clearAndSetSemantics {}`, design D6's
 * testing note); the Row itself carries the a11y summary so a screen reader announces "slide N of
 * total" once instead of N separate unlabeled dots.
 */
@Composable
fun OnboardingGuideDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val progressDescription = stringResource(
            R.string.onboarding_guide_progress_content_description,
            currentPage + 1,
            pageCount,
        )
        repeat(pageCount) { index ->
            val color = if (index == currentPage) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Row(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
                    .clearAndSetSemantics {
                        if (index == currentPage) contentDescription = progressDescription
                    },
            ) {}
        }
    }
}
