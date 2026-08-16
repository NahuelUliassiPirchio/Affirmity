package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ShowText

/**
 * The four editorial flavours of a REST span. An enum feeding a builder — deliberately NOT a
 * `MeditationCommand` (accepted proposal assumption (b)): the kinds differ only in the cue the
 * user hears or reads, and no executor would behave differently for [OPEN_AWARENESS] than for
 * [BREATH_AWARENESS]. Modelling them as commands would encode editorial choices in the type
 * system and force every executor to grow a branch per content decision.
 */
enum class RestKind { SILENCE, AMBIENT, BREATH_AWARENESS, OPEN_AWARENESS }

/**
 * A timed rest span. All four kinds produce the same [Phase] *shape* — a duration plus at most a
 * cue — which is precisely why they are one builder and not four commands.
 *
 * [RestKind.SILENCE] rejects a cue outright (a "silent" rest with narration is an authoring
 * mistake worth failing loudly on, in the same spirit as `MeditationSequence`'s existing
 * `require(children.isNotEmpty())` convention). [RestKind.AMBIENT] emits no audio of its own: the
 * bed is started by a preceding `StartAmbient` and simply keeps playing across this phase.
 *
 * [skippable] adds [MeditationEvent.UserAction] to the phase's exit events, matching the existing
 * retention-phase pattern.
 */
fun restPhase(
    id: String,
    kind: RestKind,
    duration: PhaseDuration,
    cueTextId: String? = null,
    cueVoiceId: String? = null,
    skippable: Boolean = false,
): Phase {
    if (kind == RestKind.SILENCE) {
        require(cueTextId == null && cueVoiceId == null) {
            "RestKind.SILENCE rest phase '$id' must not carry a cue (cueTextId=$cueTextId, cueVoiceId=$cueVoiceId)"
        }
    }

    val onEnter = when (kind) {
        RestKind.SILENCE -> emptyList()
        // No audio command of its own — a preceding StartAmbient's bed is already playing.
        RestKind.AMBIENT -> listOfNotNull(cueTextId?.let { ShowText(it) })
        RestKind.BREATH_AWARENESS, RestKind.OPEN_AWARENESS ->
            listOfNotNull(
                cueTextId?.let { ShowText(it) },
                cueVoiceId?.let { PlayVoice(it) },
            )
    }

    return Phase(
        id = id,
        duration = duration,
        onEnter = onEnter,
        exitEvents = if (skippable) setOf(MeditationEvent.UserAction::class) else emptySet(),
    )
}
