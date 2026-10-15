package com.pirxhio.affirmity.widget.carousel

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Configure flow (S1.1-S1.4). Emulator only; Compose UI interaction is covered by the pure seams. */
class CarouselConfigureActivityTest {

    private lateinit var context: Context
    private val appWidgetId = 9100

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    private fun launchIntent() = Intent(context, CarouselConfigureActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun storedState() = runBlocking {
        CarouselStateCodec.read(getAppWidgetState(context, PreferencesGlanceStateDefinition, AppWidgetId(appWidgetId)))
    }

    @Test
    fun backingOut_returnsCanceledWithTheWidgetIdAndStoresNothing() {
        ActivityScenario.launchActivityForResult<CarouselConfigureActivity>(launchIntent()).use { scenario ->
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
            assertEquals(appWidgetId, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        }
        assertNull(storedState().source)
    }

    @Test
    fun launchedWithoutAWidgetId_finishesCanceled() {
        val intent = Intent(context, CarouselConfigureActivity::class.java)

        ActivityScenario.launchActivityForResult<CarouselConfigureActivity>(intent).use { scenario ->
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
    }

    @Test
    fun confirmingASource_storesItAndSchedulesRotation() = runBlocking {
        val source = CarouselSource.Theme("self_worth")

        confirmCarouselSource(context, appWidgetId, source)

        assertEquals(source, storedState().source)
        val info = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CarouselRotationSchedule.UNIQUE_WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }

    @Test
    fun resultIntentCarriesTheWidgetId() {
        assertEquals(
            appWidgetId,
            carouselConfigureResult(appWidgetId).getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
        )
    }

    // --- S1.4: provider XML ---

    private val providerInfo
        get() = AppWidgetManager.getInstance(context).installedProviders.firstOrNull {
            it.provider == ComponentName(context, AffirmationCarouselReceiver::class.java)
        }

    @Test
    fun providerDeclaresTheConfigureActivityAndIsNotReconfigurable() {
        val info = providerInfo
        assertNotNull("carousel provider should be installed", info)

        assertEquals(ComponentName(context, CarouselConfigureActivity::class.java), info!!.configure)
        assertEquals(0, info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE)
    }
}
