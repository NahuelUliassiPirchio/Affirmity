package com.pirxhio.affirmity.data

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R

/**
 * A code-defined, read-only curated group (DG-1). Content lives in code so membership can change
 * between releases without a migration; only [DefaultGroupState] is persisted. [nameRes] is resolved
 * at the Compose boundary so a locale switch renames the group (DG-6).
 */
data class DefaultGroup(
    val id: String,
    @param:StringRes val nameRes: Int,
    val highlightId: String,
    val affirmationIds: List<String>,
)

/** Persisted per-default-group state. A missing row means disabled, never used (DG-3). */
data class DefaultGroupState(
    val enabled: Boolean,
    val lastUsedAtMillis: Long,
)

object DefaultGroups {
    const val EVERYDAY_ESSENTIALS_ID = "default.everyday_essentials"
    const val MORNING_START_ID = "default.morning_start"

    private val everydayEssentials = DefaultGroup(
        id = EVERYDAY_ESSENTIALS_ID,
        nameRes = R.string.default_group_everyday_essentials_name,
        highlightId = "violet",
        affirmationIds = listOf(
            "cat_self_worth.feeling_enough.intrinsic_worth.v4.extra.01",
            "cat_mind_anxiety.negative_thoughts.thought_not_fact.v4.01",
            "cat_calm_peace.find_calm.return_to_myself.v4.03",
            "cat_calm_peace.find_calm.return_to_myself.v4.05",
            "cat_self_worth.feeling_enough.enough_without_perfection.v4.03",
            "cat_self_worth.inner_dialogue.on_my_own_side.v4.03",
            "cat_presence_acceptance_control.personal_agency.choose_my_response.v4.01",
            "cat_presence_acceptance_control.release_uncontrollable.stop_controlling_outcomes.v4.02",
            "cat_calm_peace.find_calm.inner_safety.v4.01",
            "cat_gratitude_hope_possibility.gratitude.notice_what_is_here.v4.03",
            "cat_body_energy_wellbeing.body_care.listen_to_what_i_need.v4.04",
            "cat_gratitude_hope_possibility.hope.remember_change.v4.01",
        ),
    )

    private val morningStart = DefaultGroup(
        id = MORNING_START_ID,
        nameRes = R.string.default_group_morning_start_name,
        highlightId = "amber",
        affirmationIds = listOf(
            "cat_confidence_courage.move_with_fear.next_step.v4.01",
            "cat_motivation_discipline_responsibility.discipline_consistency.show_up_today.v4.02",
            "cat_motivation_discipline_responsibility.discipline_consistency.show_up_today.v4.07",
            "cat_motivation_discipline_responsibility.procrastination.just_start.v4.08",
            "cat_calm_peace.find_calm.calm_during_busy_days.v4.01",
            "cat_body_energy_wellbeing.energy.protect_my_energy.v4.01",
            "cat_calm_peace.overwhelmed.one_thing_at_a_time.v4.01",
            "cat_motivation_discipline_responsibility.act_without_motivation.values_over_mood.v4.05",
            "cat_presence_acceptance_control.personal_agency.next_action.v4.01",
            "cat_motivation_discipline_responsibility.discipline_consistency.build_sustainable_habits.v4.05",
            "cat_confidence_courage.decisions.choose_with_what_i_know.v4.01",
            "cat_confidence_courage.believe_in_my_abilities.see_myself_as_capable.v4.03",
        ),
    )

    /** Fixed presentation order: Everyday Essentials, then Morning Start (DG-1, DG-4). */
    val all: List<DefaultGroup> = listOf(everydayEssentials, morningStart)

    private val ids: Set<String> = all.mapTo(HashSet()) { it.id }

    fun isDefaultGroupId(id: String): Boolean = id in ids
}
