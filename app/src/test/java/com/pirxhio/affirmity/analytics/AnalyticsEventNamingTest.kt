package com.pirxhio.affirmity.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-4.5 / REQ-6.4: every event and parameter name is Firebase-legal, not eyeballed. */
class AnalyticsEventNamingTest {

    private val reservedPrefixes = listOf("firebase_", "google_", "ga_")
    private val snakeCase = Regex("^[a-z][a-z0-9_]*$")

    @Test
    fun `all 19 event names are declared`() {
        assertEquals(19, AnalyticsEventName.entries.size)
    }

    @Test
    fun `event names are snake_case, under 40 chars, no reserved prefix`() {
        AnalyticsEventName.entries.forEach { event ->
            val wire = event.wireName
            assertTrue("'$wire' must be snake_case", snakeCase.matches(wire))
            assertTrue("'$wire' must be <= 40 chars", wire.length <= 40)
            reservedPrefixes.forEach { prefix ->
                assertFalse("'$wire' must not start with reserved prefix '$prefix'", wire.startsWith(prefix))
            }
        }
    }

    @Test
    fun `param names are snake_case and under 40 chars`() {
        AnalyticsParam.entries.forEach { param ->
            val wire = param.wireName
            assertTrue("'$wire' must be snake_case", snakeCase.matches(wire))
            assertTrue("'$wire' must be <= 40 chars", wire.length <= 40)
        }
    }

    @Test
    fun `each event declares 25 or fewer parameters`() {
        AnalyticsEvent::class.sealedSubclasses.forEach { subclass ->
            val paramCount = subclass.members.count { member ->
                member.name != "name" && member.name != "toString" && member.name != "hashCode" &&
                    member.name != "equals" && member.name != "copy"
            }
            assertTrue("${subclass.simpleName} must declare <= 25 params", paramCount <= 25)
        }
    }
}
