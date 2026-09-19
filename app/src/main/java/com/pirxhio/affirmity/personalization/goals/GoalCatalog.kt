package com.pirxhio.affirmity.personalization.goals

/**
 * Hand-authored bridge from stable human goals to the generated affirmation theme taxonomy.
 * Goal ids deliberately remain independent of catalog ids so catalog generation cannot redefine
 * the vocabulary presented to the user.
 */
object GoalCatalog {
    val themeIdsByGoalId: Map<String, Set<String>> = linkedMapOf(
        "calm" to setOf(
            "calm_peace.find_calm",
            "calm_peace.release_tension",
            "mind_anxiety.overthinking",
            "presence_acceptance_control.present_moment",
        ),
        "confidence" to setOf(
            "confidence_courage.believe_in_my_abilities",
            "confidence_courage.decisions",
            "confidence_courage.take_space",
            "self_worth.recognize_my_value",
        ),
        "self_love" to setOf(
            "self_worth.feeling_enough",
            "self_worth.inner_dialogue",
            "self_worth.self_forgiveness",
            "body_energy_wellbeing.body_acceptance",
        ),
        "motivation" to setOf(
            "motivation_discipline_responsibility.act_without_motivation",
            "motivation_discipline_responsibility.discipline_consistency",
            "motivation_discipline_responsibility.finish_what_i_start",
            "motivation_discipline_responsibility.procrastination",
        ),
        "connection" to setOf(
            "connection_belonging.authentic_connection",
            "connection_belonging.belonging",
            "connection_belonging.friendships",
            "connection_belonging.loneliness",
        ),
        "change" to setOf(
            "change_loss_new_beginnings.adapt_to_change",
            "change_loss_new_beginnings.difficult_period",
            "change_loss_new_beginnings.new_beginning",
            "change_loss_new_beginnings.rebuild",
        ),
        "direction" to setOf(
            "purpose_identity_direction.direction",
            "purpose_identity_direction.purpose",
            "purpose_identity_direction.self_discovery",
            "purpose_identity_direction.values",
        ),
    )

    /** Union used by the generated-catalog guard test. */
    val allMappedThemeIds: Set<String> = themeIdsByGoalId.values.flatten().toSet()
}
