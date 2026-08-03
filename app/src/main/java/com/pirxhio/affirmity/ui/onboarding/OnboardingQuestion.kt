package com.pirxhio.affirmity.ui.onboarding

/** A single onboarding question with its selectable options (single-choice). */
data class OnboardingQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
)

/**
 * Placeholder onboarding content (D: content is not final — see conversation with product).
 * Wording and options will be revisited once the actual question set is defined; this exists to
 * exercise the onboarding flow end to end.
 */
val onboardingQuestions: List<OnboardingQuestion> = listOf(
    OnboardingQuestion(
        id = "improve_area",
        question = "¿Qué te gustaría mejorar con Affirmity?",
        options = listOf(
            "Confianza en mí mismo/a",
            "Manejo del estrés",
            "Hábitos y disciplina",
            "Gratitud",
            "Enfoque y productividad",
        ),
    ),
    OnboardingQuestion(
        id = "usage_frequency",
        question = "¿Con qué frecuencia te gustaría usar la app?",
        options = listOf(
            "Todos los días",
            "Varias veces por semana",
            "Una vez por semana",
            "Cuando lo necesite",
        ),
    ),
    OnboardingQuestion(
        id = "notification_time",
        question = "¿En qué momento del día preferís recibir tus afirmaciones?",
        options = listOf(
            "Mañana",
            "Mediodía",
            "Tarde",
            "Noche",
        ),
    ),
    OnboardingQuestion(
        id = "current_mindset",
        question = "¿Cómo te sentís habitualmente respecto a tus objetivos personales?",
        options = listOf(
            "Motivado/a pero disperso/a",
            "Constante pero sin dirección",
            "Estancado/a",
            "Recién empezando",
        ),
    ),
    OnboardingQuestion(
        id = "content_preference",
        question = "¿Qué tipo de contenido preferís?",
        options = listOf(
            "Frases breves y directas",
            "Reflexiones más largas",
            "Ejercicios guiados de meditación",
            "Un poco de todo",
        ),
    ),
)
