package com.pirxhio.affirmity.ui.meditation

import android.content.Context
import android.media.MediaPlayer
import com.pirxhio.affirmity.meditation.AudioChannel
import com.pirxhio.affirmity.meditation.DEFAULT_DUCK_FADE_MILLIS
import com.pirxhio.affirmity.meditation.EndSession
import com.pirxhio.affirmity.meditation.Fade
import com.pirxhio.affirmity.meditation.FadeTarget
import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MonotonicTimeSource
import com.pirxhio.affirmity.meditation.PauseTimer
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ResumeTimer
import com.pirxhio.affirmity.meditation.SessionEndReason
import com.pirxhio.affirmity.meditation.StartAmbient
import com.pirxhio.affirmity.meditation.StopAmbient
import com.pirxhio.affirmity.meditation.StopAudio
import com.pirxhio.affirmity.meditation.audio.TerminalAudioAction
import com.pirxhio.affirmity.meditation.audio.TerminalAudioPolicy
import com.pirxhio.affirmity.meditation.audio.VolumeRamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays [PlayAudio]/[PlayVoice]/[StartAmbient]/[StopAudio]/[StopAmbient]/[Fade] commands via one
 * [MediaPlayer] per distinct audio id (created lazily, reused across plays — same approach as the
 * original [PlayAudio]-only executor). An audio id with no entry in [resourcesByAudioId] is
 * silently ignored (or, for [PlayVoice], immediately reported complete — see [playVoice]) rather
 * than crashing: the domain can declare sounds this build doesn't ship yet.
 *
 * Owns the whole playback state machine in one place (design §3.1): channel assignment, nominal
 * vs. live volume, in-flight ramps, ducking bookkeeping, and the pause/resume + terminal-cleanup
 * policies. Ducking is intrinsically cross-channel (a [PlayVoice] ramps the *ambient* player), and
 * [StopAudio] `(null)` must reach every channel including the bed — both rule out a sibling
 * executor sharing this state through a third object.
 */
class GuidedMeditationAudioExecutor(
    private val context: Context,
    /** Values are `@RawRes` ids — not annotated on the map itself since `@RawRes` only applies to
     * a single int/long, not a `Map` value type. */
    private val resourcesByAudioId: Map<String, Int>,
    private val scope: CoroutineScope,
    private val timeSource: MonotonicTimeSource,
    private val sendEvent: (MeditationEvent) -> Unit = {},
) : MeditationCommandExecutor {
    private val players = mutableMapOf<String, MediaPlayer>()

    /** id -> which mixing role it plays on (design §3.3). [AudioChannel.AMBIENT] is a singleton
     * slot: at most one id may be assigned to it at a time. */
    private val channelOf = mutableMapOf<String, AudioChannel>()

    /** id -> its authored/nominal level. Ducking always ramps away from and back to this, never a
     * drifted intermediate value (REQ-5.8). */
    private val nominalVolume = mutableMapOf<String, Float>()

    /** id -> its live, currently-applied level. */
    private val currentVolume = mutableMapOf<String, Float>()

    /** id -> its in-flight ramp, if any. */
    private val ramps = mutableMapOf<String, RampJob>()

    /** id -> ramp frozen mid-flight by [pauseAll], resumed from its frozen level by [resumeAll]
     * (REQ-5.10). */
    private val frozenRamps = mutableMapOf<String, VolumeRamp>()

    /** The voice id currently holding a duck on the ambient bed, if any, alongside the fade
     * duration it used — [restoreDuck] ramps back over the same duration the duck itself used. */
    private var duckHeldBy: String? = null
    private var duckFadeMillisHeld: Long = DEFAULT_DUCK_FADE_MILLIS

    /** ids paused by the last [PauseTimer], to be resumed by the next [ResumeTimer]. */
    private var pausedIds: Set<String> = emptySet()

    private data class RampJob(val job: Job, val ramp: VolumeRamp, val startedAtMillis: Long)

    override fun execute(command: MeditationCommand) {
        when (command) {
            is PlayAudio -> playSound(command.audioId)
            is PlayVoice -> playVoice(command)
            is StartAmbient -> startAmbient(command)
            is StopAmbient -> execute(Fade(FadeTarget.Channel(AudioChannel.AMBIENT), 0f, command.fadeOutMillis))
            is StopAudio -> if (command.audioId != null) hardStop(command.audioId) else hardStopAll()
            is Fade -> fade(command)
            is PauseTimer -> pauseAll()
            is ResumeTimer -> resumeAll()
            is EndSession -> endSession(command.reason)
            else -> Unit
        }
    }

    // --- One-shot sound (unchanged behaviour from the original executor, now channel-tagged) ----

    private fun playSound(audioId: String) {
        val player = resolvePlayer(audioId) ?: return
        channelOf[audioId] = AudioChannel.SOUND
        player.seekTo(0)
        player.start()
    }

    // --- Voice: ducking + the AudioCompleted producer (REQ-5.8, EC-3) --------------------------

    private fun playVoice(command: PlayVoice) {
        val audioId = command.audioId
        val player = resolvePlayer(audioId)
        if (player == null) {
            // Missing/unresolvable asset: reporting "complete" immediately instead of silently
            // doing nothing prevents a Manual/ceiling-gated phase from hanging forever (EC-3).
            sendEvent(MeditationEvent.AudioCompleted(audioId))
            return
        }
        channelOf[audioId] = AudioChannel.VOICE

        val bedId = channelOf.entries.firstOrNull { it.value == AudioChannel.AMBIENT }?.key
        if (command.duckAmbientTo != null && bedId != null) {
            duckHeldBy = audioId
            duckFadeMillisHeld = command.duckFadeMillis
            startRamp(
                bedId,
                VolumeRamp(
                    fromVolume = currentVolume[bedId] ?: nominalVolume[bedId] ?: 1f,
                    toVolume = command.duckAmbientTo,
                    durationMillis = command.duckFadeMillis,
                ),
            )
        }

        player.setOnCompletionListener {
            restoreDuck(audioId)
            sendEvent(MeditationEvent.AudioCompleted(audioId))
        }
        player.seekTo(0)
        player.start()
    }

    /** Ramps the ambient bed back to its nominal volume — runs whenever the ducking voice stops
     * for *any* reason (natural completion, [StopAudio], or [EndSession]), never restoring to
     * whatever level it happened to be at (REQ-5.8). */
    private fun restoreDuck(voiceId: String) {
        if (duckHeldBy != voiceId) return
        duckHeldBy = null
        val bedId = channelOf.entries.firstOrNull { it.value == AudioChannel.AMBIENT }?.key ?: return
        val nominal = nominalVolume[bedId] ?: return
        startRamp(
            bedId,
            VolumeRamp(
                fromVolume = currentVolume[bedId] ?: nominal,
                toVolume = nominal,
                durationMillis = duckFadeMillisHeld,
            ),
        )
    }

    // --- Ambient bed: singleton AMBIENT slot, hard-cut replacement (REQ-4.15) ------------------

    private fun startAmbient(command: StartAmbient) {
        val currentBedId = channelOf.entries.firstOrNull { it.value == AudioChannel.AMBIENT }?.key
        if (currentBedId != null && currentBedId != command.audioId) {
            hardStop(currentBedId)
        }

        val player = resolvePlayer(command.audioId) ?: return
        channelOf[command.audioId] = AudioChannel.AMBIENT
        nominalVolume[command.audioId] = command.volume
        player.isLooping = command.loop

        if (command.fadeInMillis > 0L) {
            applyVolume(player, command.audioId, 0f)
            player.start()
            startRamp(
                command.audioId,
                VolumeRamp(fromVolume = 0f, toVolume = command.volume, durationMillis = command.fadeInMillis),
            )
        } else {
            applyVolume(player, command.audioId, command.volume)
            player.start()
        }
    }

    // --- Fade: the one ramp entry point, resolves FadeTarget at execution time (REQ-4.7) -------

    private fun fade(command: Fade) {
        val targets = resolveFadeTarget(command.target)
        targets.forEach { audioId ->
            val from = currentVolume[audioId] ?: nominalVolume[audioId] ?: 1f
            startRamp(
                audioId,
                VolumeRamp(
                    fromVolume = from,
                    toVolume = command.toVolume,
                    durationMillis = command.durationMillis,
                    curve = command.curve,
                ),
            )
        }
    }

    private fun resolveFadeTarget(target: FadeTarget): Set<String> = when (target) {
        is FadeTarget.Audio -> setOfNotNull(target.audioId.takeIf { players.containsKey(it) })
        is FadeTarget.Channel -> channelOf.filterValues { it == target.channel }.keys.toSet()
        FadeTarget.All -> players.keys.toSet()
    }

    // --- Ramp driver: one coroutine per audio id, last-write-wins cancellation (REQ-5.7) --------

    private fun startRamp(audioId: String, ramp: VolumeRamp) {
        ramps.remove(audioId)?.job?.cancel()
        val player = players[audioId] ?: return
        val startedAt = timeSource.nowMillis()
        val job = scope.launch {
            while (isActive) {
                val elapsed = timeSource.nowMillis() - startedAt
                applyVolume(player, audioId, ramp.volumeAt(elapsed))
                if (ramp.isCompleteAt(elapsed)) break
                delay(VolumeRamp.TICK_MILLIS)
            }
            onRampFinished(audioId, ramp)
        }
        ramps[audioId] = RampJob(job, ramp, startedAt)
    }

    /** Reaching exactly 0f pauses the player and releases its channel assignment, including the
     * AMBIENT slot if it held it — the `MediaPlayer` instance stays in [players] for reuse
     * (REQ-5.9). A ramp toward a non-zero target (e.g. a duck) leaves the channel assignment
     * untouched. */
    private fun onRampFinished(audioId: String, ramp: VolumeRamp) {
        ramps.remove(audioId)
        if (ramp.toVolume != 0f) return
        players[audioId]?.let { player -> if (player.isPlaying) player.pause() }
        channelOf.remove(audioId)
        pausedIds = pausedIds - audioId
    }

    // --- Hard-cut stop paths (StopAudio, REQ-5.13, EC-5) ----------------------------------------

    private fun hardStop(audioId: String) {
        ramps.remove(audioId)?.job?.cancel()
        players[audioId]?.let { player -> if (player.isPlaying) player.pause() }
        channelOf.remove(audioId)
        currentVolume.remove(audioId)
        pausedIds = pausedIds - audioId
        // The ducking voice stopping for any reason restores the bed (REQ-5.8 point 4); harmless
        // if the bed is stopped in the same batch (e.g. StopAudio(null)) — its own hardStop call
        // cancels this restore ramp immediately after.
        if (duckHeldBy == audioId) restoreDuck(audioId)
    }

    private fun hardStopAll() {
        val ids = players.keys.toList()
        ids.forEach { hardStop(it) }
        duckHeldBy = null
        pausedIds = emptySet()
        frozenRamps.clear()
    }

    // --- Pause/resume-aware ramps (REQ-5.10) ----------------------------------------------------

    private fun pauseAll() {
        val frozenAt = timeSource.nowMillis()
        ramps.forEach { (audioId, rampJob) ->
            rampJob.job.cancel()
            val elapsedAtPause = frozenAt - rampJob.startedAtMillis
            val volumeAtPause = currentVolume[audioId] ?: rampJob.ramp.volumeAt(elapsedAtPause)
            frozenRamps[audioId] = rampJob.ramp.resumedFrom(volumeAtPause, elapsedAtPause)
        }
        ramps.clear()
        val playing = players.filterValues { it.isPlaying }.keys.toSet()
        playing.forEach { players[it]?.pause() }
        pausedIds = playing
    }

    private fun resumeAll() {
        pausedIds.forEach { players[it]?.start() }
        pausedIds = emptySet()
        val toResume = frozenRamps.toMap()
        frozenRamps.clear()
        toResume.forEach { (audioId, ramp) -> startRamp(audioId, ramp) }
    }

    // --- Session end (REQ-5.11 half; the other half is release() ordering below) ---------------

    private fun endSession(reason: SessionEndReason) {
        val playing = players.filterValues { it.isPlaying }.keys
        val rampingToSilence = ramps.filterValues { it.ramp.toVolume == 0f }.keys
        val actions = TerminalAudioPolicy.plan(reason, playing, rampingToSilence)
        actions.forEach { action ->
            when (action) {
                is TerminalAudioAction.FadeOut ->
                    startRamp(
                        action.audioId,
                        VolumeRamp(
                            fromVolume = currentVolume[action.audioId] ?: 1f,
                            toVolume = 0f,
                            durationMillis = action.durationMillis,
                        ),
                    )

                is TerminalAudioAction.StopNow -> hardStop(action.audioId)
            }
        }
    }

    // --- Player resolution + volume application -------------------------------------------------

    private fun resolvePlayer(audioId: String): MediaPlayer? {
        players[audioId]?.let { return it }
        val resId = resourcesByAudioId[audioId] ?: return null
        val player = MediaPlayer.create(context, resId) ?: return null
        players[audioId] = player
        return player
    }

    /** [IllegalStateException] is swallowed defensively (EC-4): a ramp tick that races [release]
     * must not crash even though [release]'s ordering already makes that window as small as
     * possible. */
    private fun applyVolume(player: MediaPlayer, audioId: String, volume: Float) {
        currentVolume[audioId] = volume
        try {
            player.setVolume(volume, volume)
        } catch (_: IllegalStateException) {
            // Player already released — see release()'s mandatory ordering below.
        }
    }

    /** Must be called when the owning screen leaves composition — mirrors the `DisposableEffect
     * { onDispose { audioExecutor.release() } }` pattern.
     *
     * Ramp jobs MUST be cancelled before any player is released (REQ-5.11, EC-4): a ramp
     * coroutine that ticks once after `MediaPlayer.release()` calls `setVolume` on an already-
     * released native player, which throws `IllegalStateException`. */
    fun release() {
        ramps.values.forEach { it.job.cancel() }
        ramps.clear()
        frozenRamps.clear()
        players.values.forEach { it.release() }
        players.clear()
        channelOf.clear()
        nominalVolume.clear()
        currentVolume.clear()
        duckHeldBy = null
        pausedIds = emptySet()
    }
}
