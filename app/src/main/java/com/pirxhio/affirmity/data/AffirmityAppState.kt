package com.pirxhio.affirmity.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.pirxhio.affirmity.auth.AuthError
import com.pirxhio.affirmity.auth.AuthException
import com.pirxhio.affirmity.auth.AuthProviderId
import com.pirxhio.affirmity.auth.AuthRepository
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.auth.FirebaseAuthRepository
import com.pirxhio.affirmity.auth.GoogleIdAuthProvider
import com.pirxhio.affirmity.auth.SignInCancelledException
import com.pirxhio.affirmity.data.local.AffirmationDao
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.AffirmationImageStore
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionDao
import com.pirxhio.affirmity.data.local.DailyViewCount
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationLogEntry
import com.pirxhio.affirmity.data.local.NotificationPreferences
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.NotificationScheduler
import com.pirxhio.affirmity.notifications.Notifier
import com.pirxhio.affirmity.widget.WeeklyTrackerWidget
import androidx.glance.appwidget.updateAll
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Background for an affirmation card: a solid color, or a locally-cached downloaded image. */
sealed class AffirmationBackground {
    data class Color(val value: String) : AffirmationBackground()
    data class Image(val localPath: String) : AffirmationBackground()
}

data class Affirmation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val background: AffirmationBackground,
)

/** Mon..Sun completion flags for a weekly habit tracker. */
data class WeeklyStreak(
    val completedDays: List<Boolean>,
    val streakDays: Int,
)

/** Solid-color background, or the placeholder tint shown behind an image while it decodes. */
fun Affirmation.backgroundColor(): Color =
    when (val bg = background) {
        is AffirmationBackground.Color -> runCatching {
            Color(android.graphics.Color.parseColor(bg.value))
        }.getOrDefault(Color(0xFF00696F))

        is AffirmationBackground.Image -> Color(0xFF00696F)
    }

private fun AffirmationEntity.toAffirmation(): Affirmation = Affirmation(
    id = id,
    title = title,
    subtitle = subtitle,
    background = if (backgroundType == "image") {
        AffirmationBackground.Image(backgroundValue)
    } else {
        AffirmationBackground.Color(backgroundValue)
    },
)

private fun Affirmation.toEntity(): AffirmationEntity = AffirmationEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    backgroundType = when (background) {
        is AffirmationBackground.Image -> "image"
        is AffirmationBackground.Color -> "color"
    },
    backgroundValue = when (val bg = background) {
        is AffirmationBackground.Image -> bg.localPath
        is AffirmationBackground.Color -> bg.value
    },
)

/**
 * Shared in-memory state for the whole app, backed by Room (affirmations) and DataStore
 * (trackers) — see README "Decisions". Screens read plain [Affirmation]/[WeeklyStreak] state;
 * this class owns translating that to/from the persisted shapes.
 */
class AffirmityAppState(
    private val scope: CoroutineScope,
    private val affirmationDao: AffirmationDao,
    private val dailyCompletionDao: DailyCompletionDao,
    private val trackerPreferences: TrackerPreferences,
    private val imageStore: AffirmationImageStore,
    private val notificationPreferences: NotificationPreferences,
    private val notificationScheduler: NotificationScheduler,
    private val notificationDebugLog: NotificationDebugLog,
    private val notifier: Notifier,
    private val widgetUpdater: WidgetUpdater,
    private val authRepository: AuthRepository,
) {
    val affirmations = mutableStateListOf<Affirmation>()

    /** Provider-neutral sign-in state; see `auth/AuthState.kt`. Settings-only, never gates a screen. */
    var authState = mutableStateOf<AuthState>(AuthState.SignedOut)
        private set

    /** Set on a recoverable sign-in failure; `null` on cancellation (not an error) or success. */
    var authError = mutableStateOf<AuthError?>(null)
        private set

    /** Set when [addAffirmationWithImage] fails to download; cleared on the next add attempt. */
    var addImageError = mutableStateOf<String?>(null)
        private set

    /** Set by [importAffirmationsFromJson]; cleared on the next import attempt. */
    var importAffirmationsError = mutableStateOf<String?>(null)
        private set

    var affirmationsStreak = mutableStateOf(WeeklyStreak(completedDays = List(7) { false }, streakDays = 0))
        private set

    var meditationStreak = mutableStateOf(WeeklyStreak(completedDays = List(7) { false }, streakDays = 0))
        private set

    /** Null until DataStore finishes its first read; the screen falls back to its own default. */
    var meditationDurationSeconds = mutableStateOf<Int?>(null)
        private set

    var reminderSettings = mutableStateOf(ChannelSettings(enabled = false, startMinute = 540, endMinute = 1260))
        private set

    var reflectionSettings = mutableStateOf(ChannelSettings(enabled = false, startMinute = 540, endMinute = 1260))
        private set

    var notificationDebugEntries = mutableStateListOf<NotificationLogEntry>()
        private set

    private var affirmationsViewedToday = DailyViewCount(epochDay = -1L, count = 0)

    init {
        scope.launch {
            affirmationDao.observeAll().collect { entities ->
                affirmations.clear()
                affirmations.addAll(entities.map { it.toAffirmation() })
            }
        }
        scope.launch {
            val today = DayClock.epochDay()
            val weekStart = DayClock.weekStartEpochDay()
            dailyCompletionDao.observeRange(weekStart - STREAK_LOOKBACK_DAYS, weekStart + 6).collect { rows ->
                affirmationsStreak.value = DailyCompletionStats.toWeeklyStreak(
                    rows = rows,
                    weekStartEpochDay = weekStart,
                    todayEpochDay = today,
                    isDone = { it.affirmationDone },
                )
                meditationStreak.value = DailyCompletionStats.toWeeklyStreak(
                    rows = rows,
                    weekStartEpochDay = weekStart,
                    todayEpochDay = today,
                    isDone = { it.meditationDone },
                )
            }
        }
        scope.launch {
            trackerPreferences.observeAffirmationsViewedToday().collect { viewed ->
                affirmationsViewedToday = viewed
            }
        }
        scope.launch {
            trackerPreferences.observeMeditationDurationSeconds().collect { seconds ->
                meditationDurationSeconds.value = seconds
            }
        }
        scope.launch {
            notificationPreferences.observe(NotificationChannelSpec.REMINDER).collect {
                reminderSettings.value = it
            }
        }
        scope.launch {
            notificationPreferences.observe(NotificationChannelSpec.REFLECTION).collect {
                reflectionSettings.value = it
            }
        }
        scope.launch {
            notificationScheduler.ensureScheduled(NotificationChannelSpec.REMINDER)
            notificationScheduler.ensureScheduled(NotificationChannelSpec.REFLECTION)
        }
        scope.launch {
            notificationDebugLog.entries.collect { entries ->
                notificationDebugEntries.clear()
                notificationDebugEntries.addAll(entries)
            }
        }
        scope.launch {
            authRepository.authState.collect { authState.value = it }
        }
    }

    /** Starts Google sign-in from the Settings account section. Never crashes: recoverable
     * failures land in [authError]; cancellation clears it and leaves the user signed out. */
    fun signIn(activityContext: Context) {
        authError.value = null
        scope.launch {
            authRepository.signIn(AuthProviderId.GOOGLE, activityContext)
                .onFailure { throwable ->
                    authError.value = when (throwable) {
                        is SignInCancelledException -> null
                        is AuthException -> throwable.error
                        else -> AuthError.Unknown(throwable.message)
                    }
                }
        }
    }

    fun signOut() {
        scope.launch { authRepository.signOut() }
    }

    fun clearNotificationDebugLog() {
        scope.launch { notificationDebugLog.clear() }
    }

    /** Posts a real notification right now, bypassing the scheduler/worker chain entirely — lets
     * the user confirm what a delivered notification looks like without waiting for a scheduled slot. */
    fun sendTestNotification() {
        scope.launch {
            notifier.notify(
                channel = NotificationChannelSpec.REMINDER,
                title = "Notificación de prueba",
                body = "Si ves esto, el sistema de notificaciones funciona en este teléfono.",
            )
        }
    }

    fun setChannelEnabled(channel: NotificationChannelSpec, enabled: Boolean) {
        scope.launch {
            notificationPreferences.setEnabled(channel, enabled)
            if (enabled) {
                notificationScheduler.scheduleNext(channel)
            } else {
                notificationScheduler.cancel(channel)
            }
        }
    }

    fun setChannelWindow(channel: NotificationChannelSpec, startMinute: Int, endMinute: Int) {
        scope.launch {
            notificationPreferences.setWindow(channel, startMinute, endMinute)
            val enabled = when (channel) {
                NotificationChannelSpec.REMINDER -> reminderSettings.value.enabled
                NotificationChannelSpec.REFLECTION -> reflectionSettings.value.enabled
            }
            if (enabled) {
                notificationScheduler.scheduleNext(channel)
            }
        }
    }

    fun addAffirmationWithColor(title: String, subtitle: String, colorHex: String) {
        addImageError.value = null
        scope.launch {
            affirmationDao.insert(
                Affirmation(
                    title = title,
                    subtitle = subtitle,
                    background = AffirmationBackground.Color(colorHex),
                ).toEntity()
            )
        }
    }

    fun addAffirmationWithImage(title: String, subtitle: String, imageUrl: String) {
        addImageError.value = null
        scope.launch {
            val localPath = runCatching { imageStore.download(imageUrl) }
                .onFailure { addImageError.value = "No se pudo descargar la imagen: ${it.message}" }
                .getOrNull() ?: return@launch
            insertImageAffirmation(title, subtitle, localPath)
        }
    }

    fun addAffirmationWithGalleryImage(title: String, subtitle: String, imageUri: Uri) {
        addImageError.value = null
        scope.launch {
            val localPath = runCatching { imageStore.importFromGallery(imageUri) }
                .onFailure { addImageError.value = "No se pudo importar la imagen: ${it.message}" }
                .getOrNull() ?: return@launch
            insertImageAffirmation(title, subtitle, localPath)
        }
    }

    private suspend fun insertImageAffirmation(title: String, subtitle: String, localPath: String) {
        affirmationDao.insert(
            Affirmation(
                title = title,
                subtitle = subtitle,
                background = AffirmationBackground.Image(localPath),
            ).toEntity()
        )
    }

    fun importAffirmationsFromJson(json: String, replaceExisting: Boolean) {
        importAffirmationsError.value = null
        val parsed = try {
            parseAffirmationsJson(json)
        } catch (e: IllegalArgumentException) {
            importAffirmationsError.value = e.message
            return
        }

        scope.launch {
            if (replaceExisting) {
                affirmationDao.deleteAll()
            }

            var failedCount = 0
            parsed.forEach { item ->
                val background = if (item.backgroundType == "image") {
                    val localPath = runCatching { imageStore.download(item.backgroundValue) }
                        .onFailure { failedCount++ }
                        .getOrNull() ?: return@forEach
                    AffirmationBackground.Image(localPath)
                } else {
                    AffirmationBackground.Color(item.backgroundValue)
                }

                affirmationDao.insert(
                    Affirmation(
                        title = item.title,
                        subtitle = item.subtitle,
                        background = background,
                    ).toEntity()
                )
            }

            if (failedCount > 0) {
                importAffirmationsError.value =
                    "$failedCount afirmación(es) no se pudieron importar: falló la descarga de la imagen."
            }
        }
    }

    fun removeAffirmation(id: String) {
        scope.launch { affirmationDao.deleteById(id) }
    }

    /** Call once per affirmation the user settles on while swiping the feed. */
    fun recordAffirmationViewed() {
        scope.launch {
            val today = DayClock.epochDay()
            val viewed = affirmationsViewedToday
            val updated = if (viewed.epochDay == today) {
                viewed.copy(count = viewed.count + 1)
            } else {
                DailyViewCount(epochDay = today, count = 1)
            }
            affirmationsViewedToday = updated
            trackerPreferences.saveAffirmationsViewedToday(updated)
            if (updated.count >= AFFIRMATIONS_GOAL_PER_DAY) {
                dailyCompletionDao.markAffirmation(today)
                widgetUpdater.refresh()
            }
        }
    }

    /** Call when a meditation session finishes its full countdown. */
    fun recordMeditationCompleted() {
        scope.launch {
            dailyCompletionDao.markMeditation(DayClock.epochDay())
            widgetUpdater.refresh()
        }
    }

    /** Call whenever the user settles on a new duration (slider release, preset tap). */
    fun recordMeditationDurationSelected(seconds: Int) {
        meditationDurationSeconds.value = seconds
        scope.launch { trackerPreferences.saveMeditationDurationSeconds(seconds) }
    }

    private companion object {
        const val AFFIRMATIONS_GOAL_PER_DAY = 2

        /** How far back to look when deriving the running streak from `daily_completion`. */
        const val STREAK_LOOKBACK_DAYS = 370L
    }
}

@Composable
fun rememberAffirmityAppState(): AffirmityAppState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        val database = AffirmityDatabase.getInstance(context)
        val notificationPreferences = NotificationPreferences(context)
        val notificationDebugLog = NotificationDebugLog(context.applicationContext)
        val googleIdAuthProvider = GoogleIdAuthProvider(CredentialManager.create(context.applicationContext))
        AffirmityAppState(
            scope = scope,
            affirmationDao = database.affirmationDao(),
            dailyCompletionDao = database.dailyCompletionDao(),
            trackerPreferences = TrackerPreferences(context),
            imageStore = AffirmationImageStore(context.applicationContext),
            notificationPreferences = notificationPreferences,
            notificationScheduler = NotificationScheduler(
                context.applicationContext,
                notificationPreferences,
                notificationDebugLog,
            ),
            notificationDebugLog = notificationDebugLog,
            notifier = Notifier(context.applicationContext, notificationDebugLog),
            widgetUpdater = widgetUpdater(context.applicationContext),
            authRepository = FirebaseAuthRepository(
                auth = FirebaseAuth.getInstance(),
                providers = mapOf(AuthProviderId.GOOGLE to googleIdAuthProvider),
            ),
        )
    }
}

/** Pushes a Glance `updateAll` for [WeeklyTrackerWidget] (D9). */
private fun widgetUpdater(context: android.content.Context): WidgetUpdater = WidgetUpdater {
    WeeklyTrackerWidget().updateAll(context)
}
