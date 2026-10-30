package com.example.viewmodel

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Presentation values only. Unknown days stay null; no new energy calculations. */
data class SportGraphicDay(val date: LocalDate, val minutes: Long?, val sleepMinutes: Long?,
    val sessions: List<SportSession>, val energy: Int?, val fatigue: Int?, val future: Boolean = false)
data class SportsReportNumbers(val from: LocalDate, val until: LocalDate, val approved: Int,
    val meanCalories: Double?, val meanProtein: Double?, val food: List<HabitDay>,
    val activity: ActivitySummary, val days: List<SportGraphicDay>, val estimatedFood: Boolean)
data class WeeklyAiExplanation(val from: String, val until: String, val answer: String, val fullText: String,
    val generatedAt: Long, val available: Boolean, val evidence: String = "")
data class AiReportCards(val direction: String = "", val food: String = "", val recovery: String = "",
    val actions: List<String> = emptyList(), val confidence: String = "")

object SportsGraphicsSupport {
    fun days(data: SportsData, from: LocalDate, now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<SportGraphicDay> {
        val today = now.atZone(zone).toLocalDate()
        val unique = ActivityRecoverySupport.unique(data.history).filter { Instant.parse(it.end) <= now }
        val clean = data.copy(history = unique)
        return (0L..6L).map { offset ->
            val day = from.plusDays(offset)
            val lo = day.atStartOfDay(zone).toInstant(); val hi = minOf(day.plusDays(1).atStartOfDay(zone).toInstant(), now)
            val sessions = unique.filter { Instant.parse(it.start) < hi && Instant.parse(it.end) > lo && hi > lo }
            val record = data.daily.find { it.date == day.toString() }
            val note = data.recovery.find { it.date == day.toString() }
            SportGraphicDay(day, if (sessions.isEmpty()) null else ActivityRecoverySupport.summary(clean, day, day, now, zone).minutes,
                record?.sleepMinutes, sessions, note?.energy, note?.fatigue, day > today)
        }
    }
    fun report(data: SportsData, food: List<HabitDay>, confirmations: List<EnergyConfirmation>, today: LocalDate,
        now: Instant, zone: ZoneId = ZoneId.systemDefault()): SportsReportNumbers {
        val from = today.minusDays(7); val until = today.minusDays(1)
        val accepted = food.filter { it.date in from.toString()..until.toString() }
            .distinctBy { it.date }.filter { WeeklyEnergySupport.isFoodConfirmed(it, confirmations.find { c -> c.date == it.date }) }.sortedBy { it.date }
        return SportsReportNumbers(from, until, accepted.size,
            accepted.takeIf { it.isNotEmpty() }?.map { it.calories.toDouble() }?.average(),
            accepted.takeIf { it.isNotEmpty() }?.map { it.protein.toDouble() }?.average(), accepted,
            ActivityRecoverySupport.summary(data, from, until, now, zone), days(data, from, now, zone), accepted.any { !it.nutritionComplete })
    }
    fun matching(report: WeeklyAiExplanation?, from: LocalDate, until: LocalDate): WeeklyAiExplanation? =
        report?.takeIf { it.from == from.toString() && it.until == until.toString() }
    fun sleepLabel(minutes: Long?) = minutes?.let { "${it / 60} h ${it % 60} min" } ?: "—"
    fun weekday(day: LocalDate) = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")[day.dayOfWeek.value - 1]

    /** Accept common Markdown headings; unexpected prose stays accessible in the full original. */
    fun aiCards(raw: String): AiReportCards {
        val sections = mutableMapOf<String, MutableList<String>>()
        var current = ""
        val headings = listOf("Kierunek względem celu" to "direction", "Posiłki i białko" to "food",
            "Aktywność i regeneracja" to "recovery", "Co zrobić dalej" to "actions", "Na kolejny tydzień" to "actions",
            "Pewność oceny i braki danych" to "confidence")
        raw.lineSequence().forEach { line ->
            val clean = line.trim().replace(Regex("^[#>\\s]+"), "").replace("**", "").replace("__", "")
                .replace(Regex("^\\d+[.)]\\s+"), "").replace(Regex("^[-*•]\\s+"), "").trim()
            val match = headings.firstOrNull { (title, _) ->
                clean.startsWith(title, ignoreCase = true) && (clean.length == title.length || clean[title.length] in listOf(':', ' ', '—', '–', '-'))
            }
            if (match != null) {
                current = match.second
                val remainder = clean.substring(match.first.length).trim().trimStart(':', '—', '–', '-').trim()
                if (remainder.isNotBlank()) sections.getOrPut(current) { mutableListOf() }.add(remainder)
            } else if (current.isNotBlank() && clean.isNotBlank()) sections.getOrPut(current) { mutableListOf() }.add(clean)
        }
        fun text(key: String) = sections[key].orEmpty().joinToString("\n")
        val actions = sections["actions"].orEmpty().flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
            .map { it.replace(Regex("^[-*•✓\\s]+"), "").replace(Regex("^\\d+[.)]\\s+"), "").replace(Regex("^[-*•]\\s+"), "").trim() }
            .filter { it.isNotBlank() }.take(3)
        return AiReportCards(text("direction"), text("food"), text("recovery"), actions, text("confidence"))
    }
}
