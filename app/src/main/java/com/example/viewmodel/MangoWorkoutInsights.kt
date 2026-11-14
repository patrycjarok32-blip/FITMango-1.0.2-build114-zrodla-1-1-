package com.example.viewmodel

import java.time.LocalDate

/** Read-only insights derived from the existing workout history. No schema changes. */
data class MangoWorkoutSummary(
    val activeMinutes: Long, val completedSets: Int, val totalSets: Int,
    val volumeKg: Double, val personalRecords: Int, val previousVolumeKg: Double?
)
data class MangoExerciseProgress(val date: String, val maxKg: Double, val maxReps: Int, val volumeKg: Double)
object MangoWorkoutInsights {
    fun summary(session: WorkoutSession, history: List<WorkoutSession>): MangoWorkoutSummary {
        val completed = session.entries.filter { it.done }
        val previous = history.filter { it.ended > 0L && it.id != session.id &&
            it.title == session.title && it.ended < session.ended }.maxByOrNull { it.ended }
        val previousMax = history.filter { it.ended > 0L && it.ended < session.ended }
            .flatMap { it.entries.filter { entry -> entry.done } }
            .groupBy { it.exercise.trim().lowercase() }.mapValues { (_, sets) ->
                sets.maxOf { it.kg }
            }
        val records = completed.groupBy { it.exercise.trim().lowercase() }.count { (name, sets) ->
            val current = sets.maxOf { it.kg }
            current > 0 && previousMax[name]?.let { current > it } == true
        }
        return MangoWorkoutSummary(
            WorkoutSessionStore.activeDurationMillis(session) / 60000L,
            completed.size, session.entries.size,
            completed.sumOf { it.kg * it.reps }, records,
            previous?.entries?.filter { it.done }?.sumOf { it.kg * it.reps })
    }
    fun exerciseHistory(name: String, history: List<WorkoutSession>): List<MangoExerciseProgress> =
        history.filter { it.ended > 0L }.sortedBy { it.ended }.mapNotNull { session ->
            val entries = session.entries.filter { it.done && it.exercise.equals(name, true) }
            if (entries.isEmpty()) null else MangoExerciseProgress(session.date,
                entries.maxOf { it.kg }, entries.maxOf { it.reps },
                entries.sumOf { it.kg * it.reps })
        }
    /** One status projection shared by daily cards and weekly calendar. */
    fun plannedStatus(plan: SportPlan, data: SportsData, workouts: List<WorkoutSession>): String {
        if (plan.rest) return "🌿 Regeneracja"
        val strength = workouts.filter { it.date == plan.date &&
            (plan.sessionId.isNotBlank() && it.plannedSessionId == plan.sessionId ||
                plan.sessionId.isBlank() && it.title == plan.title) }
        if (strength.any { it.ended > 0L }) return "✓ Wykonany"
        if (PlannedActivityMatching.match(plan, data) != null) return "✓ Wykonany"
        if (strength.any { it.ended == 0L }) return "▶ W trakcie"
        return "◷ Zaplanowany"
    }
    fun trainingSuggestion(exercise: String, history: List<WorkoutSession>): String {
        val recent = history.filter { it.ended > 0L }.sortedByDescending { it.ended }
            .flatMap { it.entries.filter { e -> e.done && e.exercise.equals(exercise, true) } }
        if (recent.isEmpty()) return "Brak danych — zacznij od wygodnego obciążenia."
        val latest = recent.first()
        val comparable = recent.take(6)
        val repeated = comparable.count { it.kg == latest.kg && it.reps >= latest.reps }
        return if (repeated >= 3 && latest.reps >= 8)
            "Stabilne serie: możesz rozważyć niewielki wzrost obciążenia po ocenie regeneracji."
        else "Utrzymaj ${latest.kg} kg i obserwuj technikę oraz regenerację."
    }
    fun calendarStatus(day: LocalDate, today: LocalDate, plans: List<SportPlan>,
        workouts: List<WorkoutSession>): String {
        val sessions = workouts.filter { it.date == day.toString() }
        val finished = sessions.count { it.ended > 0L }
        val active = sessions.any { it.ended == 0L }
        return when {
            active && finished > 0 -> "✓ $finished ukończone • ▶ sesja w toku"
            active -> "▶ Rozpoczęty"
            finished > 0 -> "✓ Ukończony ($finished)"
            plans.any { it.rest } -> "🌿 Regeneracja"
            plans.any { it.exercises.isNotEmpty() } && day.isBefore(today) -> "○ Pominięty"
            plans.any { it.exercises.isNotEmpty() } -> "◷ Zaplanowany"
            else -> "— Bez treningu"
        }
    }
    data class WeeklyReport(
        val sessions: Int, val sets: Int, val volumeKg: Double, val minutes: Long,
        val records: Int, val averageEffort: Double?, val previousSessions: Int,
        val previousVolumeKg: Double, val previousMinutes: Long
    )
    /** Local calendar weeks, Monday through Sunday; incomplete sessions never count. */
    fun weeklyReport(weekStart: LocalDate, history: List<WorkoutSession>): WeeklyReport {
        val monday = weekStart.minusDays((weekStart.dayOfWeek.value - 1).toLong())
        fun inWeek(start: LocalDate): List<WorkoutSession> =
            history.filter { session ->
                session.ended > 0L && runCatching { LocalDate.parse(session.date) }.getOrNull()
                    ?.let { !it.isBefore(start) && it.isBefore(start.plusDays(7)) } == true
            }
        val current = inWeek(monday)
        val previous = inWeek(monday.minusDays(7))
        fun volume(items: List<WorkoutSession>) = items.sumOf { session ->
            session.entries.filter { it.done }.sumOf { it.kg * it.reps }
        }
        fun minutes(items: List<WorkoutSession>) =
            items.sumOf { WorkoutSessionStore.activeDurationMillis(it) / 60000L }
        val records = current.sumOf { session -> summary(session, history).personalRecords }
        return WeeklyReport(current.size, current.sumOf { it.entries.count { e -> e.done } },
            volume(current), minutes(current), records,
            current.map { it.effort }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average(),
            previous.size, volume(previous), minutes(previous))
    }
    fun quickPlan(plan: SportPlan, minutes: Int): SportPlan {
        require(minutes in listOf(15, 20, 30))
        var budget = minutes * 60
        val counts = IntArray(plan.exercises.size)
        // Round-robin preserves exercise variety rather than spending the entire
        // time budget on the first exercise.
        var progressed: Boolean
        do {
            progressed = false
            plan.exercises.forEachIndexed { index, exercise ->
                val seconds = 45 + exercise.restSeconds.coerceIn(0, 180)
                if (counts[index] < exercise.sets && seconds <= budget) {
                    counts[index]++
                    budget -= seconds
                    progressed = true
                }
            }
        } while (progressed)
        val chosen = plan.exercises.mapIndexedNotNull { index, exercise ->
            counts[index].takeIf { it > 0 }?.let { exercise.copy(sets = it) }
        }
        return plan.copy(title = plan.title + " • ekspres $minutes min", exercises = chosen)
    }
}
