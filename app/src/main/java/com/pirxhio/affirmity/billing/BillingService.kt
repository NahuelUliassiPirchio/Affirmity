package com.pirxhio.affirmity.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.google.firebase.auth.FirebaseAuth
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** A purchasable subscription offer with its Play Console-sourced formatted price -- decoupled
 * from [ProductDetails] so `PaywallSheet` (UI layer) never needs a Play Billing Library import. */
data class PricedSubscriptionOffer(val offerToken: String, val formattedPrice: String)

/**
 * Owns the Play Billing Library client lifecycle for the single Pro subscription product
 * (monthly + annual base plans -- prices come from Play Console, never hardcoded here), and the
 * post-purchase sync to the server (design.md D7). Android-scoped, no Compose dependency.
 *
 * Acknowledgement is **client-side** (`acknowledgePurchase`), then this immediately calls the
 * `syncEntitlement` Cloud Function so the server-authoritative entitlement doc catches up without
 * waiting on RTDN delivery latency. Design D7 explicitly rejected server-side acknowledgement from
 * the RTDN function: that would put RTDN's delivery latency inside Play's 3-day auto-refund
 * window, and the app is already foregrounded at the moment of purchase.
 *
 * [optimisticProUntilMillis] implements D7's optimistic-unlock window: set to `now + 10min` right
 * after a purchase is acknowledged, so the UI can render Pro immediately instead of showing a dead
 * gap while `syncEntitlement`/RTDN catch up. Cleared the instant a real Pro entitlement doc
 * arrives (the caller -- `AffirmityAppState` -- is responsible for clearing it, since only it
 * observes the entitlement repository).
 */
class BillingService(
    private val context: Context,
    private val proSubscriptionProductId: String,
    private val syncEntitlementUrl: String,
    private val scope: CoroutineScope,
) {
    private val _optimisticProUntilMillis = MutableStateFlow<Long?>(null)
    val optimisticProUntilMillis: StateFlow<Long?> = _optimisticProUntilMillis

    private val _monthlyOffer = MutableStateFlow<PricedSubscriptionOffer?>(null)
    val monthlyOffer: StateFlow<PricedSubscriptionOffer?> = _monthlyOffer

    private val _annualOffer = MutableStateFlow<PricedSubscriptionOffer?>(null)
    val annualOffer: StateFlow<PricedSubscriptionOffer?> = _annualOffer

    private var billingClient: BillingClient? = null
    private var lastProductDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase -> scope.launch { handlePurchase(purchase) } }
        } else {
            Log.d(TAG, "purchases update: responseCode=${result.responseCode}")
        }
    }

    /** Starts the `BillingClient` connection; retries on disconnect (Play's documented pattern).
     * Queries the product catalog and any already-owned purchases once connected. */
    fun connect() {
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().build())
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        refreshProductDetails()
                        queryExistingPurchases()
                    }
                } else {
                    Log.e(TAG, "billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                client.startConnection(this)
            }
        })
    }

    private suspend fun refreshProductDetails() {
        val client = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(proSubscriptionProductId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull()
        lastProductDetails = details
        // Base plan ids "monthly"/"annual" per Play Console setup (design.md D7's product catalog
        // decision) -- prices always come from here, never hardcoded.
        _monthlyOffer.value = details?.pricedOfferOrNull("monthly")
        _annualOffer.value = details?.pricedOfferOrNull("annual")
    }

    private fun ProductDetails.pricedOfferOrNull(basePlanId: String): PricedSubscriptionOffer? {
        val offer = subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlanId } ?: return null
        val price = offer.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice ?: return null
        return PricedSubscriptionOffer(offerToken = offer.offerToken, formattedPrice = price)
    }

    /** Re-checks already-owned purchases on launch (e.g. a purchase acknowledged on another
     * device, or a purchase that never made it through `handlePurchase` on this one). */
    private suspend fun queryExistingPurchases() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val (_, purchases) = client.queryPurchasesAsync(params)
        purchases.forEach { purchase -> handlePurchase(purchase) }
    }

    /** Launches the purchase flow for [offerToken] (one of [ProductDetails]'s subscription
     * offers -- monthly or annual, selected by the caller). No-op if the client isn't connected or
     * product details haven't resolved yet. */
    fun launchPurchaseFlow(activity: Activity, offerToken: String) {
        val client = billingClient ?: return
        val details = lastProductDetails ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            val client = billingClient ?: return
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val ackResult = client.acknowledgePurchase(ackParams)
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "acknowledgePurchase failed: ${ackResult.debugMessage}")
                return
            }
        }

        // Optimistic unlock (design D7): rendered immediately while syncEntitlement/RTDN catch up.
        _optimisticProUntilMillis.value = System.currentTimeMillis() + OPTIMISTIC_WINDOW_MILLIS
        syncEntitlement(purchase.purchaseToken)
    }

    /** Calls the `syncEntitlement` Cloud Function (design D2) with the caller's Firebase ID token
     * and the purchase token, so the server-authoritative entitlement doc is re-verified against
     * the Play Developer API without waiting on RTDN delivery. Best-effort: a failure here is not
     * fatal, since RTDN is still expected to arrive and this device already has the optimistic
     * grant from [handlePurchase]. */
    private suspend fun syncEntitlement(purchaseToken: String) = withContext(Dispatchers.IO) {
        runCatching {
            val idToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: return@runCatching
            val connection = (URL(syncEntitlementUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { stream ->
                OutputStreamWriter(stream).use { writer ->
                    writer.write("""{"purchaseToken":"$purchaseToken"}""")
                }
            }
            connection.responseCode
            connection.disconnect()
        }.onFailure { error -> Log.e(TAG, "syncEntitlement failed", error) }
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
    }

    private companion object {
        const val TAG = "BillingService"
        const val OPTIMISTIC_WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
