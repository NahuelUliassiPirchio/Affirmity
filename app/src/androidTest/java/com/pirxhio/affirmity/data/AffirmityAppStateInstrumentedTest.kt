package com.pirxhio.affirmity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.auth.AuthProviderId
import com.pirxhio.affirmity.auth.AuthRepository
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.data.local.AffirmationImageStore
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationPreferences
import com.pirxhio.affirmity.data.local.OnboardingPreferences
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.data.remote.DocWrite
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.data.remote.FirestoreMigrationSource
import com.pirxhio.affirmity.data.remote.FirestoreMigrator
import com.pirxhio.affirmity.data.remote.FirestoreOnboardingRepository
import com.pirxhio.affirmity.data.repository.DataSession
import com.pirxhio.affirmity.data.repository.RoomAffirmationRepository
import com.pirxhio.affirmity.data.repository.RoomDailyCompletionRepository
import com.pirxhio.affirmity.data.repository.RoomMeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.RoomNotificationSettingsRepository
import com.pirxhio.affirmity.notifications.Notifier
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Always [AuthState.SignedOut] — this suite exercises the Room-only path, never the swap. */
private class FakeSignedOutAuthRepository : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(provider: AuthProviderId, activityContext: Context): Result<Unit> =
        Result.success(Unit)
    override suspend fun signOut() = Unit
}

/** Never invoked while [FakeSignedOutAuthRepository] stays [AuthState.SignedOut]. */
private class UnreachableFirestoreMigrationSource : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = error("unreachable: session never leaves Local")
    override suspend fun commitChunk(writes: List<DocWrite>) = error("unreachable: session never leaves Local")
}

/**
 * Approval test for task 2.4 / spec scenario "Broken streak still shows earlier completions":
 * completing the window's first day, missing the second, and completing the third again must
 * leave the first and third days both marked completed in the derived [WeeklyStreak] — the bug
 * this change fixes.
 */
@RunWith(AndroidJUnit4::class)
class AffirmityAppStateInstrumentedTest {

    private lateinit var db: AffirmityDatabase
    private lateinit var state: AffirmityAppState

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val notificationPreferences = NotificationPreferences(context)
        val notificationDebugLog = NotificationDebugLog(context.applicationContext)
        val trackerPreferences = TrackerPreferences(context)
        val local = DataSession.Local(
            affirmations = RoomAffirmationRepository(db.affirmationDao()),
            completions = RoomDailyCompletionRepository(db.dailyCompletionDao()),
            meditation = RoomMeditationPreferencesRepository(trackerPreferences),
            notifications = RoomNotificationSettingsRepository(notificationPreferences),
        )
        state = AffirmityAppState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            local = local,
            remoteSessionFactory = { error("unreachable: session never leaves Local") },
            migrator = FirestoreMigrator(UnreachableFirestoreMigrationSource()),
            trackerPreferences = trackerPreferences,
            onboardingPreferences = OnboardingPreferences(context.applicationContext),
            imageStore = AffirmationImageStore(context.applicationContext),
            notificationDebugLog = notificationDebugLog,
            notifier = Notifier(context.applicationContext, notificationDebugLog),
            widgetUpdater = WidgetUpdater { },
            authRepository = FakeSignedOutAuthRepository(),
            fcmTokenRepository = FcmTokenRepository(FirebaseFirestore.getInstance()),
            onboardingRepository = FirestoreOnboardingRepository(FirebaseFirestore.getInstance()),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun brokenMeditationStreak_stillShowsFirstAndThirdDayCompleted() = runBlocking {
        val windowStart = DayClock.rollingWindowStartEpochDay()
        val thirdDay = windowStart + 2

        db.dailyCompletionDao().markMeditation(windowStart)
        // Second day of the window intentionally left unmarked.
        db.dailyCompletionDao().markMeditation(thirdDay)

        // Let the state's observeRange collector process the DAO writes.
        delay(200)

        assertEquals(true, state.meditationStreak.value.completedDays[0]) // first day of window
        assertEquals(false, state.meditationStreak.value.completedDays[1]) // second day
        assertEquals(true, state.meditationStreak.value.completedDays[2]) // third day
    }
}
