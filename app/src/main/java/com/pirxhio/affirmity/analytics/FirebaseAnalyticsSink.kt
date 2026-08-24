package com.pirxhio.affirmity.analytics

/**
 * Pure-Kotlin, Android-free SDK boundary (design D6). `android.os.Bundle` is a stubbed framework
 * class on plain JVM JUnit (this project has no Robolectric), so the mapper below is expressed
 * against this interface instead -- the `Bundle` is built only in
 * `analytics/firebase/AndroidFirebaseAnalyticsSink.kt`, one layer below this boundary.
 */
interface FirebaseAnalyticsSink {
    fun logEvent(name: String, params: List<AnalyticsParamValue>)
}

/**
 * The one place a raw `String` legitimately exists in this whole feature -- and it is BELOW the
 * [AnalyticsEvent] boundary. [Text.value] is only ever populated from an enum `wireName` or
 * [AnalyticsId.value], never from event-declared free text (D2/D6). The REQ-6.3.2
 * no-PII-representable test deliberately excludes this type; see its own scope note.
 */
sealed interface AnalyticsParamValue {
    val key: String

    data class Text(override val key: String, val value: String) : AnalyticsParamValue
    data class Number(override val key: String, val value: Long) : AnalyticsParamValue
    data class Flag(override val key: String, val value: Boolean) : AnalyticsParamValue
}
