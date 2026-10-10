package com.pirxhio.affirmity.data

import org.junit.Test
import org.junit.Assert.assertEquals

class NotificationLocaleTokenTest {
    @Test fun englishMapsToEn() = assertEquals("en", notificationLocaleToken("en"))
    @Test fun englishRegionMapsToEn() = assertEquals("en", notificationLocaleToken("en-US"))
    @Test fun spanishMapsToEs() = assertEquals("es", notificationLocaleToken("es"))
    @Test fun unsupportedFallsBackToEs() = assertEquals("es", notificationLocaleToken("fr"))
    @Test fun nullFallsBackToEs() = assertEquals("es", notificationLocaleToken(null))
    @Test fun englishGbMapsToEn() = assertEquals("en", notificationLocaleToken("en-GB"))
    @Test fun frenchRegionFallsBackToEs() = assertEquals("es", notificationLocaleToken("fr-CA"))
}
