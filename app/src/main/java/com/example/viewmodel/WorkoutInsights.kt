package com.example.viewmodel

import java.time.LocalDate
import java.time.ZoneId

/** Pure calculations: never write Health Connect data or estimate calories from a video title. */
object WorkoutInsights {
    data class Summary(val sessions: Int, val minutes: Long, val sets: Int,
        val volumeKg: Double, val videoMinutes: Int, val bestKg: Map<String, Double>,
        val badges: List<String>)
    fun summary(sports: SportsData, sessions: List<WorkoutSession>,
        challenges: List<VideoChallenge>): Summary {
        val finished = sessions.filter { it.ended > 0 }
        val entries = finished.flatMap { it.entries.filter { entry -> entry.done } }
        val videos = challenges.flatMap { it.workouts }.filter { it.completedAt > 0 }
        val allSets = sports.sets
        val best = allSets.groupBy { it.exercise }.mapValues { (_, sets) -> sets.maxOf { it.kg } }
        val count = finished.size + videos.size
        val badges = buildList {
            if (count >= 1) add("🥉 Pierwszy trening")
            if (count >= 5) add("🔥 5 treningów")
            if (count >= 10) add("🏅 10 treningów")
            if (count >= 30) add("🏆 30 treningów")
            if (finished.any { it.entries.count { e -> e.done } >= 10 }) add("💪 10 serii w sesji")
            if (challenges.any { it.workouts.isNotEmpty() && it.workouts.all { w -> w.completedAt > 0 } })
                add("🌟 Ukończone wyzwanie")
        }
        return Summary(count, finished.sumOf { ((it.ended - it.started) / 60000).coerceAtLeast(0) },
            entries.size, entries.sumOf { it.kg * it.reps }, videos.sumOf { it.minutes }, best, badges)
    }
    fun previousKg(sets: List<SportSet>, exercise: String): Double? =
        sets.filter { it.exercise.equals(exercise, true) && it.kg > 0 }
            .maxByOrNull { it.time }?.kg
    fun suggestKg(sets: List<SportSet>, exercise: String, targetReps: Int): Double? {
        val previous = sets.filter { it.exercise.equals(exercise, true) }.sortedByDescending { it.time }.take(3)
        if (previous.size < 2 || previous.any { it.reps < targetReps } || previous.any { it.kg <= 0 }) return null
        val last = previous.first().kg
        return if (last >= 20) last + 2.5 else last + 1.0
    }
    data class CoachAdvice(val recommendation: String, val weightKg: Double?,
        val reasons: List<String>, val dataAvailable: Boolean)
    fun coachAdvice(sports: SportsData, exercise: String, targetReps: Int,
        today: LocalDate = LocalDate.now()): CoachAdvice {
        val reasons = mutableListOf<String>()
        val check = sports.recovery.filter { it.date <= today.toString() }
            .maxByOrNull { it.date }
            ?.takeIf { runCatching { LocalDate.parse(it.date) }.getOrNull()
                ?.let { day -> !day.isBefore(today.minusDays(2)) } == true }
        val sleep = sports.daily.filter { it.date <= today.toString() }
            .maxByOrNull { it.date }
            ?.takeIf { runCatching { LocalDate.parse(it.date) }.getOrNull()
                ?.let { day -> !day.isBefore(today.minusDays(1)) } == true }
            ?.sleepMinutes
        if (check != null) reasons.add("Energia ${check.energy}/5, zmęczenie ${check.fatigue}/5")
        if (sleep != null) reasons.add("Sen: ${sleep / 60} h ${sleep % 60} min")
        val last = sports.sets.filter { it.exercise.equals(exercise, true) }
            .maxByOrNull { it.time }
        val previous = last?.kg?.takeIf { it > 0 }
        val recentTraining = sports.history.count { history ->
            runCatching { java.time.Instant.parse(history.start).atZone(ZoneId.systemDefault())
                .toLocalDate() }.getOrNull()?.let { it in today.minusDays(2)..today } == true
        }
        if (recentTraining >= 2) reasons.add("Kilka sesji aktywności w ostatnich 3 dniach")
        val lowRecovery = (check != null && (check.energy <= 2 || check.fatigue >= 4)) ||
            (sleep != null && sleep < 360)
        if (lowRecovery) return CoachAdvice("Regeneracja: rozważ mniejszy ciężar lub lżejszą sesję.",
            previous?.let { kotlin.math.round(it * 0.9 * 2) / 2 }, reasons, true)
        if (recentTraining >= 2) return CoachAdvice("Duża częstotliwość aktywności: zachowaj ciężar i oceń zmęczenie.",
            previous, reasons, true)
        val suggested = suggestKg(sports.sets, exercise, targetReps)
        if (suggested != null) return CoachAdvice(
            "Możliwa progresja przy prawidłowej technice i zapasie powtórzeń.",
            suggested, reasons + "Ostatnie serie osiągały cel powtórzeń", true)
        return CoachAdvice("Utrzymaj dotychczasowy ciężar; zwiększaj dopiero po stabilnych seriach.",
            previous, reasons, check != null || sleep != null || previous != null)
    }
    data class CalendarDay(val date: LocalDate, val strengthPlanned: Int, val strengthDone: Int,
        val videosDone: Int, val videosPlanned: Int, val recovery: Boolean)
    fun calendar(start: LocalDate, sports: SportsData, sessions: List<WorkoutSession>,
        challenges: List<VideoChallenge>): List<CalendarDay> = (0..6).map { offset ->
        val date = start.plusDays(offset.toLong())
        val dateString = date.toString()
        val video = challenges.flatMap { it.workouts }.filter { w ->
            w.completedAt > 0 && java.time.Instant.ofEpochMilli(w.completedAt)
                .atZone(ZoneId.systemDefault()).toLocalDate() == date
        }
        val plannedVideos = challenges.sumOf { challenge ->
            val startDay = runCatching { LocalDate.parse(challenge.startedOn) }.getOrNull()
            if (startDay == null) 0 else challenge.workouts.count { workout ->
                startDay.plusDays((workout.day - 1).toLong()) == date
            }
        }
        CalendarDay(date, sports.plans.count { it.date == dateString && !it.rest },
            sessions.count { it.date == dateString && it.ended > 0 },
            video.size, plannedVideos, sports.plans.any { it.date == dateString && it.rest })
    }
}
