package com.example.viewmodel

import java.time.LocalDate

/** Read-only comparison of the current seven-day period and the previous seven days. */
data class MangoWeekStats(val sessions: Int, val sets: Int, val volumeKg: Double,
    val records: Int, val averageEffort: Double?)
data class MangoWeeklyComparison(val current: MangoWeekStats, val previous: MangoWeekStats)

object MangoWorkoutWeeklyReport {
    fun compare(sessions: List<WorkoutSession>, today: LocalDate): MangoWeeklyComparison {
        val start = today.minusDays(6)
        fun stats(from: LocalDate, to: LocalDate): MangoWeekStats {
            val finished = sessions.filter { session ->
                session.ended > 0L &&
                    runCatching { LocalDate.parse(session.date) }.getOrNull()
                        ?.let { it >= from && it <= to } == true
            }
            val entries = finished.flatMap { it.entries.filter { entry -> entry.done } }
            val previousBest = mutableMapOf<String, Double>()
            sessions.filter { it.ended > 0L && it.date < from.toString() }
                .flatMap { it.entries.filter { entry -> entry.done } }
                .forEach { entry ->
                    previousBest[entry.exercise] = maxOf(previousBest[entry.exercise] ?: 0.0, entry.kg)
                }
            var records = 0
            finished.sortedBy { it.ended }.forEach { session ->
                session.entries.filter { it.done }.forEach { entry ->
                    if (entry.kg > 0 && entry.kg > (previousBest[entry.exercise] ?: 0.0)) {
                        records++
                        previousBest[entry.exercise] = entry.kg
                    }
                }
            }
            return MangoWeekStats(finished.size, entries.size,
                entries.sumOf { it.kg * it.reps }, records,
                finished.takeIf { it.isNotEmpty() }?.map { it.effort.toDouble() }?.average())
        }
        return MangoWeeklyComparison(stats(start, today),
            stats(start.minusDays(7), start.minusDays(1)))
    }
}
