package com.pirxhio.affirmity.compass

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.pirxhio.affirmity.notifications.NotificationCanceller
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Calls the `answerCompassQuestion` Cloud Function (Notifications V2 scope-expansion decision,
 * made mid-Phase-5 apply) with the caller's Firebase ID token and the answered question id, then
 * cancels any active Compass/Reflection notification on success -- task 4.7's fifth and final
 * completion site, closed out here (see [NotificationCanceller]'s kdoc).
 *
 * Mirrors [com.pirxhio.affirmity.billing.BillingService]'s private `syncEntitlement()` method:
 * same raw [HttpURLConnection] + `FirebaseAuth.getIdToken()` calling pattern, same
 * best-effort-on-IO-failure posture (a failed answer submission is logged, not surfaced as a hard
 * error -- the user's typed note isn't lost, only the server-side "answered" record didn't land,
 * so the notification may re-appear, which is the safe failure direction). Like that method, the
 * network call itself is intentionally NOT unit-tested here (no androidTest harness in this repo
 * mocks HttpURLConnection) -- only the two pure seams it wraps
 * ([buildAnswerCompassRequestBody], [handleAnswerCompassResult]) are, in
 * `CompassAnswerRepositoryTest`.
 */
class CompassAnswerRepository(
    private val context: Context,
    private val answerCompassQuestionUrl: String,
    private val notificationCanceller: NotificationCanceller,
    private val scope: CoroutineScope,
) {

    /** Fire-and-forget submit: posts [questionId] as answered and cancels the active Compass
     * notification once the server confirms the write (see [handleAnswerCompassResult]).
     * [onCompleted] (Notifications V2 Phase 6, design §9) fires alongside the cancellation --
     * `notification_completed`'s one completion site outside [com.pirxhio.affirmity.data.AffirmityAppState]
     * -- so the caller can attribute the completed answer back to the notification that opened this
     * screen, if any. Not unit-tested beyond the existing [handleAnswerCompassResult] coverage: the
     * composition here is a one-line addition to that pure seam, and the surrounding network call
     * remains this class's established not-unit-tested boundary (see class kdoc). */
    fun submitAnswer(questionId: String, onCompleted: () -> Unit = {}) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val idToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                        ?: return@runCatching
                    val connection = (URL(answerCompassQuestionUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $idToken")
                        setRequestProperty("Content-Type", "application/json")
                    }
                    connection.outputStream.use { stream ->
                        OutputStreamWriter(stream).use { writer ->
                            writer.write(buildAnswerCompassRequestBody(questionId))
                        }
                    }
                    val responseCode = connection.responseCode
                    connection.disconnect()
                    handleAnswerCompassResult(responseCode) {
                        notificationCanceller.cancelFamily(NotificationChannelSpec.REFLECTION)
                        onCompleted()
                    }
                }.onFailure { error -> Log.e(TAG, "answerCompassQuestion failed", error) }
            }
        }
    }

    private companion object {
        const val TAG = "CompassAnswerRepository"
    }
}

/** Pure JSON body builder -- the one part of [CompassAnswerRepository.submitAnswer] that's
 * directly unit-testable without mocking Android networking. Uses [JSONObject] rather than raw
 * string interpolation: unlike `BillingService.syncEntitlement()`'s `purchaseToken` (server-issued,
 * never attacker-controlled), [questionId] is read off [com.pirxhio.affirmity.EXTRA_NOTIFICATION_QUESTION_ID]
 * on the launching `Intent` in `MainActivity`, and `MainActivity` is `android:exported="true"` --
 * so any other app on the device can send a crafted `Intent` with an arbitrary string in that
 * extra. A `"` or `\` in an interpolated value would corrupt the JSON body of this authenticated
 * request; [JSONObject] escapes it correctly instead. */
internal fun buildAnswerCompassRequestBody(questionId: String): String =
    JSONObject().put("questionId", questionId).toString()

/** Pure success/failure decision: only a 2xx response means the answer was durably recorded
 * server-side, so only then is it safe to cancel the notification nudging the user to answer it --
 * a client-side-only cancel on a failed write would silently drop the nudge with nothing recorded
 * to suppress the next send. Extracted so this decision is directly unit-testable in isolation
 * from the surrounding raw [HttpURLConnection] call. */
internal fun handleAnswerCompassResult(responseCode: Int, cancelReflectionNotification: () -> Unit) {
    if (responseCode in 200..299) cancelReflectionNotification()
}
