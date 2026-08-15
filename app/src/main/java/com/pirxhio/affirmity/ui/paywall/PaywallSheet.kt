package com.pirxhio.affirmity.ui.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R

/**
 * Inline bottom-sheet paywall (design.md D7, same structural pattern as `MoodDayDetailSheet`) --
 * not a separate navigation destination (spec's "Upgrade CTA opens inline paywall"). Only ever
 * opened for a signed-in user; the signed-out routing decision (sign-in instead of this sheet)
 * happens at the call site (design.md D7).
 *
 * Prices are never hardcoded (spec's "Product catalog and purchase flow") -- [monthlyPriceLabel]
 * and [annualPriceLabel] are `null` until `BillingService`'s product-details query resolves, in
 * which case both CTAs stay disabled and [R.string.paywall_unavailable] is shown instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(
    monthlyPriceLabel: String?,
    annualPriceLabel: String?,
    onSelectMonthly: () -> Unit,
    onSelectAnnual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val plansAvailable = monthlyPriceLabel != null && annualPriceLabel != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.paywall_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!plansAvailable) {
                Text(
                    text = stringResource(R.string.paywall_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onSelectMonthly,
                enabled = monthlyPriceLabel != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (monthlyPriceLabel != null) {
                        stringResource(R.string.paywall_monthly_cta, monthlyPriceLabel)
                    } else {
                        stringResource(R.string.paywall_monthly_cta, "…")
                    },
                )
            }
            OutlinedButton(
                onClick = onSelectAnnual,
                enabled = annualPriceLabel != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (annualPriceLabel != null) {
                        stringResource(R.string.paywall_annual_cta, annualPriceLabel)
                    } else {
                        stringResource(R.string.paywall_annual_cta, "…")
                    },
                )
            }
        }
    }
}
