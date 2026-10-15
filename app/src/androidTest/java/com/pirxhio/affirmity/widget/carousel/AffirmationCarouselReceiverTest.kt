package com.pirxhio.affirmity.widget.carousel

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Receiver lifecycle, manifest and Glance state cleanup (S2.1, S2.4, S4.3, S4.4, S11.1). Emulator only. */
class AffirmationCarouselReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    private fun workInfos() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(CarouselRotationSchedule.UNIQUE_WORK_NAME).get()

    @Test
    fun onEnabled_schedulesTheRotation() {
        AffirmationCarouselReceiver().onEnabled(context)

        assertEquals(WorkInfo.State.ENQUEUED, workInfos().single().state)
    }

    @Test
    fun onDisabled_cancelsTheRotation() {
        val receiver = AffirmationCarouselReceiver()
        receiver.onEnabled(context)

        receiver.onDisabled(context)

        assertEquals(WorkInfo.State.CANCELLED, workInfos().single().state)
    }

    @Test
    fun manifest_declaresTheReceiverNotExportedWithAppWidgetUpdate() {
        val component = ComponentName(context, AffirmationCarouselReceiver::class.java)
        val info = context.packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA)

        assertFalse(info.exported)
        assertNotNull(info.metaData?.get(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
        val resolved = context.packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName),
            0,
        )
        assertTrue(resolved.any { it.activityInfo.name == AffirmationCarouselReceiver::class.java.name })
    }

    @Test
    fun onDeleted_removesOnlyTheDeletedInstancesState() = runBlocking {
        val one = AppWidgetId(9001)
        val two = AppWidgetId(9002)
        listOf(one, two).forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().also {
                    CarouselStateCodec.write(it, CarouselState(source = CarouselSource.Favorites, order = listOf("a")))
                }
            }
        }

        AffirmationCarouselReceiver().onDeleted(context, intArrayOf(9001))

        var removed = false
        for (attempt in 0 until 50) {
            val state = CarouselStateCodec.read(getAppWidgetState(context, PreferencesGlanceStateDefinition, one))
            if (state.source == null) {
                removed = true
                break
            }
            delay(100)
        }
        assertTrue("state(9001) should be removed", removed)
        val kept = CarouselStateCodec.read(getAppWidgetState(context, PreferencesGlanceStateDefinition, two))
        assertEquals(CarouselSource.Favorites, kept.source)
        // Clean up the surviving fixture.
        AffirmationCarouselReceiver().onDeleted(context, intArrayOf(9002))
    }

    @Test
    fun onDeleted_forAnIdWithoutStateDoesNotFail() {
        // Must simply not throw.
        AffirmationCarouselReceiver().onDeleted(context, intArrayOf(424242))
    }
}
