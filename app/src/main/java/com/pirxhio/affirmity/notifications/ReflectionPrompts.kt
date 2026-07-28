package com.pirxhio.affirmity.notifications

import kotlin.random.Random

/**
 * Bundled, static reflection-prompt question bank — no backend (see README "Decisions"). Written
 * in English by design, independent of the app's Spanish UI copy.
 */
object ReflectionPrompts {

    val questions: List<String> = listOf(
        "What are you grateful for right now?",
        "What is one small win you had today?",
        "What emotion have you been avoiding lately?",
        "Who made you smile recently?",
        "What would make today feel complete?",
        "What is one thing you can let go of today?",
        "What does your ideal tomorrow look like?",
        "What challenge taught you the most this month?",
        "What are you proud of that no one else knows about?",
        "What habit would you like to build?",
        "What is weighing on your mind right now?",
        "What made you feel calm today?",
        "What would you tell your younger self?",
        "What is something you've been putting off?",
        "How did you take care of yourself today?",
        "What relationship deserves more of your attention?",
        "What is a fear you'd like to face?",
        "What brought you joy this week?",
        "What does success mean to you right now?",
        "What is one thing you can forgive yourself for?",
        "What are you looking forward to?",
        "What thought keeps coming back to you today?",
        "What boundary do you need to set?",
        "What is something kind you can do for yourself tomorrow?",
        "What made today different from yesterday?",
    )

    /** One question drawn at random from [questions]. */
    fun random(random: Random = Random.Default): String = questions[random.nextInt(questions.size)]
}
