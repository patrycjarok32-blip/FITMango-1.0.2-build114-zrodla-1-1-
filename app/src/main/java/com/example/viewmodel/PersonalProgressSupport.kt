package com.example.viewmodel

import com.example.data.WeightLogEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.Duration

data class PersonalRecord(val title: String, val value: Double, val unit: String, val date: String, val source: String)
data class ComparedWeek(val from: LocalDate, val until: LocalDate, val activity: ActivitySummary, val approvedDays: Int,
    val calories: Double?, val protein: Double?, val sleepMinutes: Double?, val sleepDays: Int, val weight: Double?, val weightDays: Int)
data class WeekComparison(val older: ComparedWeek, val newer: ComparedWeek)

object PersonalProgressSupport {
    fun records(data: SportsData, now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<PersonalRecord> {
        val sessions = ActivityRecoverySupport.unique(data.history).filter { Instant.parse(it.end) <= now && Instant.parse(it.end) > Instant.parse(it.start) }
        fun isKind(s: SportSession, type: Int, names: Set<String>) = s.activityType == type ||
            (s.activityType == null && ProduceSupport.normalize(s.title.trim()) in names)
        val walk = sessions.filter { isKind(it, androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING, setOf("spacer", "walking", "walk")) }
            .maxByOrNull { Duration.between(Instant.parse(it.start), Instant.parse(it.end)).seconds }
        val cycle = sessions.filter { isKind(it, androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING, setOf("rower", "cycling", "biking", "bike")) && it.distanceKm?.let { v -> v.isFinite() && v > 0 } == true }
            .maxByOrNull { it.distanceKm!! }
        val weeks = sessions.flatMap { listOf(SportsSupport.week(Instant.parse(it.start).atZone(zone).toLocalDate()),
            SportsSupport.week(Instant.parse(it.end).minusNanos(1).atZone(zone).toLocalDate())) }.distinct()
        val bestWeek = weeks.map { it to ActivityRecoverySupport.summary(data, it, it.plusDays(6), now, zone).activeDays }
            .maxWithOrNull(compareBy<Pair<LocalDate, Int>> { it.second }.thenBy { it.first })
        return listOfNotNull(walk?.let { PersonalRecord("Najdłuższy spacer", Duration.between(Instant.parse(it.start), Instant.parse(it.end)).seconds / 60.0,
                "min", Instant.parse(it.start).atZone(zone).toLocalDate().toString(), it.source) },
            cycle?.let { PersonalRecord("Najdłuższa trasa rowerowa", it.distanceKm!!, "km", Instant.parse(it.start).atZone(zone).toLocalDate().toString(), it.source) },
            bestWeek?.let { PersonalRecord("Najwięcej aktywnych dni", it.second.toDouble(), "dni", "${it.first} – ${it.first.plusDays(6)}", "Zapisane sesje • tydzień pn.–nd.") })
    }
    fun normalizedDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()
        ?: runCatching { LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)) }.getOrNull()
    fun compare(data: SportsData, food: List<HabitDay>, confirmations: List<EnergyConfirmation>, weights: List<WeightLogEntity>,
        today: LocalDate, now: Instant, zone: ZoneId = ZoneId.systemDefault()): WeekComparison {
        val monday = SportsSupport.week(today)
        fun week(start: LocalDate): ComparedWeek {
            val until = start.plusDays(6)
            val summary = SportsGraphicsSupport.report(data, food, confirmations, until.plusDays(1), now, zone)
            val sleep = data.daily.filter { normalizedDate(it.date)?.let { d -> d in start..until } == true }.mapNotNull { it.sleepMinutes }
            val weight = weights.mapNotNull { row -> normalizedDate(row.date)?.takeIf { it in start..until && row.weightKg.isFinite() && row.weightKg in 30f..400f }?.let { it to row } }
                .groupBy { it.first }.values.map { entries -> entries.maxBy { it.second.id }.second.weightKg.toDouble() }
            return ComparedWeek(start, until, summary.activity, summary.approved, summary.meanCalories, summary.meanProtein,
                sleep.takeIf { it.isNotEmpty() }?.average(), sleep.size, weight.takeIf { it.isNotEmpty() }?.average(), weight.size)
        }
        return WeekComparison(week(monday.minusWeeks(2)), week(monday.minusWeeks(1)))
    }
    fun checkin(sports: SportsData, effects: EffectsSettings, date: LocalDate, energy: Int, fatigue: Int, hunger: Int): Pair<SportsData, EffectsSettings> {
        require(energy in 1..5 && fatigue in 1..5 && hunger in 1..5)
        val key = date.toString(); val old = effects.wellbeing.find { it.date == key }
        return sports.copy(recovery = (sports.recovery.filterNot { it.date == key } + RecoveryCheck(key, energy, fatigue)).sortedByDescending { it.date }.take(730)) to
            effects.copy(wellbeing = (effects.wellbeing.filterNot { it.date == key } + WellbeingDay(key, hunger, energy, old?.sleepHours)).sortedByDescending { it.date }.take(730))
    }
}
