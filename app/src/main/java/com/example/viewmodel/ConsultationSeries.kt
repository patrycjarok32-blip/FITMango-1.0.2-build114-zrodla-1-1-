package com.example.viewmodel
import java.time.LocalDate
data class CycleChartPoint(val day: Int, val current: Double?, val previous: Double?)
object ConsultationSeries {
    fun cycle(starts: List<String>, weights: List<WeightPoint>, today: LocalDate): List<CycleChartPoint> {
        val dates = starts.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.filter { it <= today }.distinct().sorted()
        if (dates.size < 2) return emptyList()
        val current = dates.last()
        val previous = dates[dates.lastIndex-1]
        val length = current.toEpochDay() - previous.toEpochDay()
        if (length !in 15L..60L) return emptyList()
        val count = minOf(length, today.toEpochDay()-current.toEpochDay()+1).toInt()
        val map = weights.filter { it.kg.isFinite() && it.kg in 30.0..400.0 }.associate { it.date to it.kg }
        return (1..count).mapNotNull { day ->
            val a = map[current.plusDays(day-1L).toString()]
            val b = map[previous.plusDays(day-1L).toString()]
            if (a == null && b == null) null else CycleChartPoint(day,a,b)
        }
    }
}
