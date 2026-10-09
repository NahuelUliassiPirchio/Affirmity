package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R

/** Sticky "Update my feed" CTA, enabled only when [isDirty]. An empty draft is a valid selection
 *  (no catalog themes), so there is no separate validity gate. */
@Composable
fun UpdateFeedButton(
    isDirty: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp, top = 8.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = isDirty,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.your_feed_update_button))
        }
    }
}
