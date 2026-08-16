package com.pirxhio.affirmity.meditation

/**
 * Marker for a side effect a phase declares (on entry/exit) or the engine issues as a structural
 * consequence of a transition (e.g. [StartTimer]). The engine only ever holds and forwards these
 * opaquely to a [MeditationCommandExecutor] — it never interprets them, so adding a new command
 * type (haptics, analytics, ...) never requires touching the engine, only registering a new
 * executor.
 */
interface MeditationCommand

data class PlayAudio(val audioId: String) : MeditationCommand
data class StopAudio(val audioId: String? = null) : MeditationCommand
data class ShowText(val textId: String) : MeditationCommand

/** [durationMillis] null means "no fixed duration" — the timer still runs (so elapsed time is
 * observable) but never emits [MeditationEvent.TimerCompleted]. [generation] lets the executor
 * tag the events it feeds back to the engine — see [MeditationEvent.TimerTick]. */
data class StartTimer(val durationMillis: Long?, val generation: Int) : MeditationCommand
data class PauseTimer(val generation: Int) : MeditationCommand
data class ResumeTimer(val generation: Int) : MeditationCommand

data class StartLap(val lapId: String) : MeditationCommand
data class EndLap(val lapId: String) : MeditationCommand

// --- Audio channels & fade targets -------------------------------------------------------------

/**
 * Which mixing role a piece of audio plays. Not a routing/output concept — the executor keeps one
 * [android.media.MediaPlayer] per audio id regardless; the channel is what makes "duck the bed" and
 * "stop the bed" expressible without the author re-naming the id they started it with.
 *
 * [AudioChannel.AMBIENT] is a *singleton slot*: at most one looping bed is live at a time (see
 * design §3.3). [AudioChannel.VOICE] and [AudioChannel.SOUND] may each hold several concurrent ids.
 */
enum class AudioChannel { VOICE, SOUND, AMBIENT }

/** What a [Fade] applies to. [Channel] resolves at execution time to whatever ids are currently
 * assigned to that channel — which is why an author never has to remember the bed's audio id. */
sealed interface FadeTarget {
    data class Audio(val audioId: String) : FadeTarget
    data class Channel(val channel: AudioChannel) : FadeTarget
    data object All : FadeTarget
}

/**
 * Maps linear ramp progress (0f..1f) to an eased 0f..1f multiplier. Same extension-point shape as
 * [RepetitionStrategy]: [Linear] is the only implementation this codebase needs today (accepted
 * proposal assumption (d)); a perceptual/logarithmic curve plugs in here without touching [Fade],
 * the executor, or the engine.
 *
 * NOTE (equality): like [FixedCountRepetition], implementations have identity equality, so two
 * [Fade] commands compare equal only when they share the *same* curve instance. The default
 * [Linear] is a singleton, so default-curve commands compare structurally — which is what tests
 * and definition equality rely on.
 */
fun interface FadeCurve {
    fun ease(progress: Float): Float

    companion object {
        val Linear: FadeCurve = FadeCurve { it }
    }
}

// --- Voice -------------------------------------------------------------------------------------

/**
 * Narrated voice-over. Deliberately distinct from [PlayAudio] (accepted proposal assumption (a)):
 * only a voice clip ducks the ambient bed under it, and only a voice clip is the natural exit
 * trigger for a [PhaseDuration.Manual] phase via [MeditationEvent.AudioCompleted].
 *
 * [duckAmbientTo] null disables ducking. When non-null, the ambient bed ramps to that volume for
 * the duration of this clip and ramps back to its nominal volume when the clip ends *for any
 * reason* (natural completion, [StopAudio], or session end).
 *
 * Carries no subtitle/text id on purpose: [ShowText] is already the one way the domain says
 * "display this", and duplicating it here would force [TextDisplayCommandExecutor] to know about
 * voice. Authors pair the two in the same `onEnter` list.
 */
data class PlayVoice(
    val audioId: String,
    val duckAmbientTo: Float? = DEFAULT_AMBIENT_DUCK_VOLUME,
    val duckFadeMillis: Long = DEFAULT_DUCK_FADE_MILLIS,
) : MeditationCommand

const val DEFAULT_AMBIENT_DUCK_VOLUME = 0.25f
const val DEFAULT_DUCK_FADE_MILLIS = 600L

// --- Ambient bed ---------------------------------------------------------------------------------

/**
 * Starts (or replaces) the single looping ambient bed. Unlike [PlayAudio], the bed is *not*
 * phase-scoped: it survives phase transitions and is only ever ended by [StopAmbient],
 * `StopAudio(null)`, or session end.
 *
 * [volume] is the bed's *nominal* level — ducking ramps away from it and back to it, so a duck can
 * never accidentally promote a quiet bed to full volume.
 */
data class StartAmbient(
    val audioId: String,
    val volume: Float = 1f,
    val fadeInMillis: Long = 0L,
    val loop: Boolean = true,
) : MeditationCommand

/** Authoring sugar, exactly equivalent to `Fade(FadeTarget.Channel(AMBIENT), 0f, fadeOutMillis)`
 * — see design §3.3; the executor routes both through one code path. `fadeOutMillis = 0` is an
 * immediate stop. */
data class StopAmbient(val fadeOutMillis: Long = 0L) : MeditationCommand

// --- Volume ramp -----------------------------------------------------------------------------

/**
 * Ramps [target]'s volume to [toVolume] over [durationMillis]. Covers fade-in, fade-out and
 * ducking with one command. Reaching exactly 0f pauses the affected player(s) — executor policy,
 * not an author-facing flag, so a bed can never be left looping silently at zero forever.
 *
 * A new ramp for a target cancels the ramp already in flight for it (last write wins), so a
 * duck-restore can never fight a fade-out.
 */
data class Fade(
    val target: FadeTarget,
    val toVolume: Float,
    val durationMillis: Long,
    val curve: FadeCurve = FadeCurve.Linear,
) : MeditationCommand {
    init {
        require(toVolume in 0f..1f) { "toVolume must be in 0f..1f, got $toVolume" }
        require(durationMillis >= 0L) { "durationMillis must be >= 0, got $durationMillis" }
    }
}

// --- Session lifecycle -----------------------------------------------------------------------

enum class SessionEndReason {
    /** The definition tree ran to its natural end. */
    Completed,

    /** [MeditationEvent.Cancel] — the user abandoned the session. */
    Cancelled,
}

/**
 * Engine-issued terminal broadcast: the session is over, clean up. Structurally the same category
 * as [StartTimer] — issued by the engine as a consequence of a transition, never authored in a
 * [Phase]'s onEnter/onExit.
 *
 * This is the *only* cleanup hook that fires on both paths: [MeditationEvent.Cancel] deliberately
 * does not run the active phase's `onExit` (design §2.3), so anything a phase started and did not
 * yet stop must be releasable from here alone.
 *
 * Executors must treat [SessionEndReason] as meaningful, not decorative: `Completed` is graceful
 * (an authored fade already in flight is allowed to finish), `Cancelled` is immediate. See §2.2.
 */
data class EndSession(val reason: SessionEndReason) : MeditationCommand
