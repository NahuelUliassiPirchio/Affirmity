package com.pirxhio.affirmity.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingQuestionTest {

    @Test
    fun `survey exposes the three typed personalization questions`() {
        assertEquals(listOf("goals", "tone", "moments"), onboardingQuestions.map { it.id })
        assertEquals(
            listOf(
                "¿Qué te vendría bien ahora?",
                "¿Qué tono te gustaría encontrar?",
                "¿En qué momentos te gustaría recibir afirmaciones?",
            ),
            onboardingQuestions.map { it.question },
        )
        assertEquals(listOf(3, 1, 6), onboardingQuestions.map { it.maxSelections })

        assertEquals(
            listOf(
                OnboardingOption("calm", "Encontrar calma"),
                OnboardingOption("confidence", "Fortalecer mi confianza"),
                OnboardingOption("self_love", "Tratarme con más amor"),
                OnboardingOption("motivation", "Recuperar motivación"),
                OnboardingOption("connection", "Sentirme más conectado/a"),
                OnboardingOption("change", "Atravesar cambios"),
                OnboardingOption("direction", "Encontrar dirección"),
            ),
            onboardingQuestions[0].options,
        )
        assertEquals(
            listOf(
                OnboardingOption("soft", "Suave", "Puedo darme tiempo para encontrar mi camino."),
                OnboardingOption("direct", "Directo", "Sé lo que tengo que hacer. Empiezo."),
                OnboardingOption("powerful", "Poderoso", "Yo puedo. Avanzo aunque cueste."),
                OnboardingOption("manifestation", "Manifestación", "Me abro a nuevas oportunidades."),
            ),
            onboardingQuestions[1].options,
        )
        assertEquals(
            listOf(
                OnboardingOption("after_waking", "Al empezar el día"),
                OnboardingOption("during_work_or_study", "Durante el día"),
                OnboardingOption("before_important_event", "Antes de algo importante"),
                OnboardingOption("difficult_moment", "En momentos difíciles"),
                OnboardingOption("end_of_day", "Al terminar el día"),
                OnboardingOption("before_sleep", "Antes de dormir"),
            ),
            onboardingQuestions[2].options,
        )
    }
}
