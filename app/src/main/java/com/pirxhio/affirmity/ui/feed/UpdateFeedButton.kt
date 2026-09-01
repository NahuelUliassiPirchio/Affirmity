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

/** Sticky "Update my feed" CTA, enabled only when [isDirty] AND [isValid] -- adapted from the old
 *  selector sheet's Aplicar button, which gated on the same minimum-selection invariant rather
 *  than dirtiness alone (a dirty-but-empty draft must not look tappable, since committing it
 *  would silently no-op). */
@Composable
fun UpdateFeedButton(
    isDirty: Boolean,
    isValid: Boolean,
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
            enabled = isDirty && isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.your_feed_update_button))
        }
    }
}
