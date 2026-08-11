package com.pirxhio.affirmity.meditation

import kotlin.reflect.KClass

/**
 * Static, declarative description of a meditation. Composed from a small set of primitives
 * ([Phase], [MeditationSequence], [Repeat]) — a new meditation is a new tree built from these,
 * never a new engine. See `breathing/BreathingMeditationDefinition.kt` for a worked example.
 */
sealed interface MeditationNode {
    /** Unique within the definition — used for [MeditationRuntimeState.activePath] and
     * [MeditationRuntimeState.iterationCounts] keys. */
    val id: String
}

/** A leaf: the only node type that actually represents "being somewhere" in the session — the
 * only one with side effects and a duration. [MeditationSequence]/[Repeat] are pure structure. */
data class Phase(
    override val id: String,
    val duration: PhaseDuration = PhaseDuration.Manual,
    val onEnter: List<MeditationCommand> = emptyList(),
    val onExit: List<MeditationCommand> = emptyList(),
    /** Event types that end this phase (in addition to [PhaseDuration.Fixed] completing on its
     * own). Matched by type only for now — matching by payload (e.g. a specific [MeditationEvent.
     * UserAction.actionId]) is a deliberately unbuilt extension point, see design notes. */
    val exitEvents: Set<KClass<out MeditationEvent>> = emptySet(),
) : MeditationNode

/** Runs [children] in order. Advancing off the last child bubbles the advance up to this node's
 * parent — there is no separate "transition target" primitive; order in the tree *is* the flow. */
data class MeditationSequence(
    override val id: String,
    val children: List<MeditationNode>,
) : MeditationNode {
    init {
        require(children.isNotEmpty()) { "MeditationSequence '$id' must have at least one child" }
    }
}

/** Runs [child] repeatedly. [strategy] decides whether another iteration starts — the *only*
 * place "how many times" logic lives, so a future repetition rule (until a condition, until a
 * duration) is a new [RepetitionStrategy], never a new node type or engine change. */
data class Repeat(
    override val id: String,
    val child: MeditationNode,
    val strategy: RepetitionStrategy,
) : MeditationNode

sealed interface PhaseDuration {
    data class Fixed(val millis: Long) : PhaseDuration

    /** No fixed duration — the phase only ends via an event in [Phase.exitEvents] (e.g. [Meditation
     * Event.Next] or a domain-specific action). Elapsed time is still tracked for display. */
    data object Manual : PhaseDuration
}

/**
 * The whole meditation: a stable [id] plus the root of its node tree. [variables] are the
 * meditation's configurable inputs (e.g. rounds, breaths per round) — carried into
 * [MeditationRuntimeState.variables] for strategies/future dynamic behavior to read, even though
 * the current [FixedCountRepetition] captures its count directly rather than reading it back out.
 */
data class MeditationDefinition(
    val id: String,
    val variables: Map<String, Any?> = emptyMap(),
    val root: MeditationNode,
)
