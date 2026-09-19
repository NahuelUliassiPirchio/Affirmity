package com.pirxhio.affirmity.personalization.goals

/** A stable, human-language personalization goal, deliberately separate from catalog taxonomy. */
data class UserGoal(
    val id: String,
    val label: String,
) {
    companion object {
        val all: List<UserGoal> = listOf(
            UserGoal(id = "calm", label = "Encontrar calma"),
            UserGoal(id = "confidence", label = "Fortalecer mi confianza"),
            UserGoal(id = "self_love", label = "Tratarme con más amor"),
            UserGoal(id = "motivation", label = "Recuperar motivación"),
            UserGoal(id = "connection", label = "Sentirme más conectado/a"),
            UserGoal(id = "change", label = "Atravesar cambios"),
            UserGoal(id = "direction", label = "Encontrar dirección"),
        )
    }
}
