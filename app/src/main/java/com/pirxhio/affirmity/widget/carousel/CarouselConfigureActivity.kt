package com.pirxhio.affirmity.widget.carousel

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.DefaultGroups
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.ui.groups.AffirmationGroupAccessBadge
import com.pirxhio.affirmity.ui.groups.partiallyLockedGroupIds
import com.pirxhio.affirmity.ui.theme.AffirmityTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stores the chosen [source] for one widget instance, starts rotation and requests the first
 * render. The initial queue is built by that first `provideGlance`, not here. Kept free of UI so it can be tested directly.
 */
suspend fun confirmCarouselSource(context: Context, glanceId: GlanceId, source: CarouselSource) {
    GlanceCarouselStateStore(context, glanceId).update { it.reconfigured(source) }
    CarouselRotationWorker.enqueue(context)
    AffirmationCarouselWidget().update(context, glanceId)
}

/**
 * The activity is exported, so the widget id in its intent is untrusted. It is accepted only when
 * the id is valid and the system says it is bound to our own provider (flattened component names).
 */
internal fun isOwnCarouselWidget(appWidgetId: Int, providerFlattened: String?, expectedFlattened: String): Boolean =
    appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && providerFlattened == expectedFlattened

/** Result intent carrying the widget id, required for both RESULT_OK and RESULT_CANCELED. */
internal fun carouselConfigureResult(appWidgetId: Int): Intent =
    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

/**
 * Launched by the system when a carousel widget is placed. The result is `RESULT_CANCELED`
 * from the start so backing out never adds a widget; only a confirmed pick flips it to OK. There
 * is no reconfiguration after placement in v1.
 */
class CarouselConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, carouselConfigureResult(appWidgetId))
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        val expected = ComponentName(this, AffirmationCarouselReceiver::class.java)
        if (!isOwnCarouselWidget(appWidgetId, provider?.flattenToString(), expected.flattenToString())) {
            finish()
            return
        }

        setContent {
            AffirmityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CarouselConfigureScreen(onPick = ::confirm, onCancel = ::finish)
                }
            }
        }
    }

    private fun confirm(source: CarouselSource) {
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
                confirmCarouselSource(applicationContext, glanceId, source)
                setResult(Activity.RESULT_OK, carouselConfigureResult(appWidgetId))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                setResult(Activity.RESULT_CANCELED, carouselConfigureResult(appWidgetId))
            }
            finish()
        }
    }
}

@Composable
private fun CarouselConfigureScreen(onPick: (CarouselSource) -> Unit, onCancel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val options by produceState<SourceOptions?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadSourceOptions(context) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.carousel_configure_title), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCancel) { Text(stringResource(R.string.carousel_configure_cancel)) }
            }
        }
        options?.let { loaded ->
            item { SectionHeader(stringResource(R.string.your_feed_source_favorites)) }
            item { OptionRow(loaded.favorites, onPick) }
            item { SectionHeader(stringResource(R.string.carousel_configure_section_themes)) }
            items(loaded.themes, key = { CarouselStateCodec.encodeSource(it.source) }) { OptionRow(it, onPick) }
            item { SectionHeader(stringResource(R.string.groups_section_title)) }
            items(loaded.collections, key = { CarouselStateCodec.encodeSource(it.source) }) { OptionRow(it, onPick) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun OptionRow(option: SourceOption, onPick: (CarouselSource) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(option.source) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val title = when (val t = option.title) {
            is SourceTitle.Res -> stringResource(t.id)
            is SourceTitle.Text -> t.value
        }
        Text(title, style = MaterialTheme.typography.bodyLarge)
        option.badge?.let { AffirmationGroupAccessBadge(it) }
    }
    HorizontalDivider()
}

/** Loads collections and the current tier, so the partial-lock badge hides for Pro users. */
private suspend fun loadSourceOptions(context: Context): SourceOptions {
    val database = AffirmityDatabase.getInstance(context)
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val firestore = if (uid != null) FirebaseFirestore.getInstance() else null
    val access = carouselAccessReader(database, firestore, uid).read()
    return buildSourceOptions(
        partiallyLockedGroupIds = partiallyLockedGroupIds(access.tier, access.grants, System.currentTimeMillis()),
        defaultGroups = DefaultGroups.all,
        userCollections = database.userCollectionDao().getAll().map { it.collection },
    )
}
