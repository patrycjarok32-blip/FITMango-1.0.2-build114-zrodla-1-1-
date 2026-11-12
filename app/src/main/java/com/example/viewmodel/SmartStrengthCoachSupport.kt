package com.example.viewmodel

/** Conservative strength progression hints; never automatically changes a workout. */
data class SmartStrengthSet(
    val exerciseId: String,
    val weightKg: Double,
    val repetitions: Int,
    val targetRepetitions: Int,
    val perceivedEffort: Int? = null
)
data class SmartStrengthHint(val exerciseId: String, val message: String, val suggestedWeightKg: Double?)

object SmartStrengthCoachSupport {
    fun suggest(sets: List<SmartStrengthSet>, incrementKg: Double = 2.5): List<SmartStrengthHint> {
        if (!incrementKg.isFinite() || incrementKg <= 0) return emptyList()
        return sets.filter {
            it.exerciseId.isNotBlank() && it.weightKg.isFinite() && it.weightKg >= 0 &&
                it.targetRepetitions > 0 && it.repetitions >= 0 &&
                (it.perceivedEffort == null || it.perceivedEffort in 1..10)
        }.groupBy { it.exerciseId }.map { (id, entries) ->
            val complete = entries.all { it.repetitions >= it.targetRepetitions }
            val effort = entries.mapNotNull { it.perceivedEffort }.maxOrNull()
            val weight = entries.map { it.weightKg }.distinct().singleOrNull()
            when {
                !complete -> SmartStrengthHint(id, "Najpierw osiągnij zaplanowane powtórzenia przy obecnym obciążeniu.", null)
                effort == null -> SmartStrengthHint(id, "Cel powtórzeń osiągnięty. Dodaj ocenę wysiłku przed sugestią zwiększenia ciężaru.", null)
                effort >= 9 -> SmartStrengthHint(id, "Wysoki wysiłek: rozważ utrzymanie obciążenia i regenerację.", null)
                weight == null -> SmartStrengthHint(id, "Serie mają różne ciężary. Sprawdź historię przed progresją.", null)
                effort <= 7 -> SmartStrengthHint(id, "Możesz rozważyć niewielkie zwiększenie ciężaru po ocenie techniki i regeneracji.", weight + incrementKg)
                else -> SmartStrengthHint(id, "Utrzymaj ciężar i obserwuj jakość powtórzeń.", null)
            }
        }
    }
}
