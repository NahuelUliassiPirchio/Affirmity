package com.pirxhio.affirmity.analytics.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.pirxhio.affirmity.analytics.AnalyticsParamValue
import com.pirxhio.affirmity.analytics.FirebaseAnalyticsSink

/**
 * The one place `android.os.Bundle` and `FirebaseAnalytics` are touched (design D6). Zero
 * branching, `FirebaseAnalytics.getInstance` is a static factory -- accepted as untested on plain
 * JVM JUnit (this project has no Robolectric), the same explicit precedent Spec 5's
 * `GoogleRewardedAdGateway` established.
 */
class AndroidFirebaseAnalyticsSink(private val firebaseAnalytics: FirebaseAnalytics) : FirebaseAnalyticsSink {
    override fun logEvent(name: String, params: List<AnalyticsParamValue>) {
        val bundle = Bundle()
        params.forEach { param ->
            when (param) {
                is AnalyticsParamValue.Text -> bundle.putString(param.key, param.value)
                is AnalyticsParamValue.Number -> bundle.putLong(param.key, param.value)
                is AnalyticsParamValue.Flag -> bundle.putBoolean(param.key, param.value)
            }
        }
        firebaseAnalytics.logEvent(name, bundle)
    }
}
