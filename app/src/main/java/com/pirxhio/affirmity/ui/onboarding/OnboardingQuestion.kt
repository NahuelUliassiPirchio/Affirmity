package com.pirxhio.affirmity.ui.onboarding

import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
import com.pirxhio.affirmity.personalization.goals.UserGoal

data class OnboardingOption(
    val id: String,
    val label: String,
    val example: String? = null,
)

/** A single onboarding question with stable option IDs and an explicit selection limit. */
data class OnboardingQuestion(
    val id: String,
    val question: String,
    val options: List<OnboardingOption>,
    val maxSelections: Int,
)

internal fun toggleSelection(current: Set<String>, optionId: String, maxSelections: Int): Set<String> =
    when {
        maxSelections == 1 -> setOf(optionId)
        optionId in current -> current - optionId
        current.size < maxSelections -> current + optionId
        else -> current
    }

internal fun Map<String, Set<String>>.toOnboardingAnswers() = OnboardingAnswers(
    goalIds = get("goals").orEmpty(),
    preferredTone = get("tone").orEmpty().singleOrNull(),
    usageMoments = get("moments")?.takeIf { it.isNotEmpty() },
)

internal suspend fun completeSurvey(
    answers: Map<String, Set<String>>,
    onSurveyCompleted: suspend (OnboardingAnswers) -> Unit,
    onFinished: () -> Unit,
) {
    onSurveyCompleted(answers.toOnboardingAnswers())
    onFinished()
}

val onboardingQuestions: List<OnboardingQuestion> = listOf(
    OnboardingQuestion(
        id = "goals",
        question = "¿Qué te vendría bien ahora?",
        options = UserGoal.all.map { OnboardingOption(id = it.id, label = it.label) },
        maxSelections = 3,
    ),
    OnboardingQuestion(
        id = "tone",
        question = "¿Qué tono te gustaría encontrar?",
        options = listOf(
            OnboardingOption("soft", "Suave", "Puedo darme tiempo para encontrar mi camino."),
            OnboardingOption("direct", "Directo", "Sé lo que tengo que hacer. Empiezo."),
            OnboardingOption("powerful", "Poderoso", "Yo puedo. Avanzo aunque cueste."),
            OnboardingOption("manifestation", "Manifestación", "Me abro a nuevas oportunidades."),
        ),
        maxSelections = 1,
    ),
    run {
        val momentOptions = listOf(
            OnboardingOption("after_waking", "Al empezar el día"),
            OnboardingOption("during_work_or_study", "Durante el día"),
            OnboardingOption("before_important_event", "Antes de algo importante"),
            OnboardingOption("difficult_moment", "En momentos difíciles"),
            OnboardingOption("end_of_day", "Al terminar el día"),
            OnboardingOption("before_sleep", "Antes de dormir"),
        )
        OnboardingQuestion(
            id = "moments",
            question = "¿En qué momentos te gustaría recibir afirmaciones?",
            options = momentOptions,
            // No product cap: every moment may be selected, derived so it can never drift from
            // the option list above if one is added or removed.
            maxSelections = momentOptions.size,
        )
    },
)
