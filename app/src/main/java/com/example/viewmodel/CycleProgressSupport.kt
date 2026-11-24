package com.example.viewmodel
import java.time.LocalDate
object CycleProgressSupport {
    fun compare(starts: List<String>, weights: List<WeightPoint>, today: LocalDate): String {
        val dates = starts.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.filter { it <= today }.distinct().sorted()
        if (dates.isEmpty()) return "Dodaj pierwszy dzień miesiączki."
        val current = dates.last()
        val day = today.toEpochDay() - current.toEpochDay() + 1
        val prefix = "Dzień od ostatniego początku cyklu: $day."
        if (dates.size < 2) return "$prefix Dodaj wcześniejszy cykl, aby porównać wagę."
        val previous = dates[dates.lastIndex - 1]
        val length = current.toEpochDay() - previous.toEpochDay()
        if (day > length || length !in 15L..60L) return "$prefix Brak porównywalnego okresu."
        val from = (day - 3).coerceAtLeast(1)
        fun values(start: LocalDate) = weights.filter { it.date in start.plusDays(from - 1).toString()..start.plusDays(day - 1).toString() && it.kg.isFinite() && it.kg in 30.0..400.0 }.distinctBy { it.date }.map { it.kg }
        val old = values(previous); val now = values(current)
        if (old.size < 2 || now.size < 2) return "$prefix Potrzeba co najmniej dwóch ważeń w odpowiadających dniach obu cykli."
        return prefix + " Zmiana średniej w dniach $from–$day: " + String.format(java.util.Locale.getDefault(), "%+.2f kg", now.average() - old.average())
    }
}
