package com.pirxhio.affirmity.widget.carousel

import android.appwidget.AppWidgetManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselConfigureValidationTest {

    private val ours = "com.pirxhio.affirmity/com.pirxhio.affirmity.widget.carousel.AffirmationCarouselReceiver"

    @Test
    fun `a widget bound to our provider is accepted`() {
        assertTrue(isOwnCarouselWidget(12, ours, ours))
    }

    @Test
    fun `a widget bound to another provider is rejected`() {
        assertFalse(isOwnCarouselWidget(12, "other.pkg/other.pkg.Receiver", ours))
    }

    @Test
    fun `an unknown widget id with no provider info is rejected`() {
        assertFalse(isOwnCarouselWidget(12, null, ours))
    }

    @Test
    fun `the invalid widget id is rejected even if a provider matches`() {
        assertFalse(isOwnCarouselWidget(AppWidgetManager.INVALID_APPWIDGET_ID, ours, ours))
    }
}
