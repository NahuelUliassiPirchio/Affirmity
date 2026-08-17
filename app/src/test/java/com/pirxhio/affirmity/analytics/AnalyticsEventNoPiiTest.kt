package com.pirxhio.affirmity.analytics

import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-6.3.2 (no-PII-representable, structural). No [AnalyticsEvent] subtype may expose an
 * unbounded `String` property; the only allowed property types are [AnalyticsId], [Int], [Long],
 * [Boolean], and enum types (design D2). Scope note: [AnalyticsParamValue.Text] (D6, added PR7)
 * deliberately lives BELOW this boundary and is out of scope for this test — its only producers
 * are enum wireNames and [AnalyticsId.value].
 */
class AnalyticsEventNoPiiTest {

    @Test
    fun `no AnalyticsEvent subtype declares a raw String property`() {
        val allowedNonEnumTypes = setOf(AnalyticsId::class, Int::class, Long::class, Boolean::class)

        AnalyticsEvent::class.sealedSubclasses.forEach { subclass ->
            subclass.declaredMemberProperties.forEach { prop ->
                val type = prop.returnType.jvmErasure
                val isAllowed = type in allowedNonEnumTypes || type.java.isEnum
                assertTrue(
                    "${subclass.simpleName}.${prop.name} has disallowed type ${type.simpleName} " +
                        "(only AnalyticsId/Int/Long/Boolean/enum are allowed)",
                    isAllowed,
                )
                assertFalse(
                    "${subclass.simpleName}.${prop.name} must not be a raw String",
                    type == String::class,
                )
            }
        }
    }

    @Test
    fun `AnalyticsId constructor is private and only reachable via typed factories`() {
        val ctor = AnalyticsId::class.primaryConstructor
        assertEquals(KVisibility.PRIVATE, ctor?.visibility)
    }
}
