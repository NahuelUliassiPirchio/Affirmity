package com.pirxhio.affirmity.widget.carousel

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
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
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Receiver lifecycle, manifest and Glance state cleanup. Emulator only. */
@SuppressLint("RestrictedApi") // AppWidgetId stands in for a bound widget id in tests
class AffirmationCarouselReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    private fun workInfos() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(CarouselRotationSchedule.UNIQUE_WORK_NAME).get()

    private val host by lazy { AppWidgetHost(context, 9201) }

    /** Deleting through the host makes the system deliver a real ACTION_APPWIDGET_DELETED. */
    private fun deleteViaHost(appWidgetId: Int) = host.deleteAppWidgetId(appWidgetId)

    @Test
    fun onEnabled_schedulesTheRotation() {
        AffirmationCarouselReceiver().onEnabled(context)

        // The test executor may start the job immediately, so any live (non-finished) state is a pass.
        val state = workInfos().single().state
        assertTrue("expected a live state but was $state", state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING)
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
        val oneId = bindRealCarouselWidgetId(context, host)
        val twoId = bindRealCarouselWidgetId(context, host)
        val one = AppWidgetId(oneId)
        val two = AppWidgetId(twoId)
        listOf(one, two).forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().also {
                    CarouselStateCodec.write(it, CarouselState(source = CarouselSource.Favorites, order = listOf("a")))
                }
            }
        }

        val file0 = File(context.filesDir, "datastore/appWidget-$oneId.preferences_pb")
        assertTrue("fixture state should exist before deletion", file0.exists())
        deleteViaHost(oneId)

        // Reading the deleted store through Glance would race its own store teardown (two DataStores
        // on one file), so assert on the backing file that Glance's deleteStore removes.
        val file = File(context.filesDir, "datastore/appWidget-$oneId.preferences_pb")
        var removed = false
        for (attempt in 0 until 50) {
            if (!file.exists()) {
                removed = true
                break
            }
            delay(100)
        }
        assertTrue("state of the deleted instance should be removed", removed)
        val kept = CarouselStateCodec.read(getAppWidgetState(context, PreferencesGlanceStateDefinition, two))
        assertEquals(CarouselSource.Favorites, kept.source)
        // Clean up the surviving fixture.
        deleteViaHost(twoId)
    }

    @Test
    fun onDeleted_forAnIdWithoutStateDoesNotFail() {
        // Must simply not crash the process; delivered through the system so goAsync has a PendingResult.
        deleteViaHost(bindRealCarouselWidgetId(context, host))
        Thread.sleep(500)
    }
}
