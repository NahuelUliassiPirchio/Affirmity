package com.pirxhio.affirmity.widget.carousel

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/**
 * Allocates a widget id and binds it to the carousel provider so tests exercise the same id
 * validation as production. Deleting it through [AppWidgetHost.deleteAppWidgetId] makes the system
 * deliver a real ACTION_APPWIDGET_DELETED (that action is protected, so tests cannot send it).
 */
internal fun bindRealCarouselWidgetId(context: Context, host: AppWidgetHost): Int {
    val id = host.allocateAppWidgetId()
    val provider = ComponentName(context, AffirmationCarouselReceiver::class.java)
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
    try {
        assertTrue("failed to bind a real widget id", AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id, provider))
    } finally {
        automation.dropShellPermissionIdentity()
    }
    return id
}
