package com.example.viewmodel

import java.time.LocalDate
import java.time.DayOfWeek
import java.util.Locale

/** Observational deficit check. Never estimates lost fat or changes calorie targets. */
data class DeficitCheck(
    val status: String,
    val explanation: String,
    val approvedDays: Int,
    val periodDays: Int,
    val averageCalories: Double?,
    val averageGoalDifference: Double?,
    val weekendCalories: Double?,
    val weekdayCalories: Double?,
    val weightStartAverage: Double?,
    val weightEndAverage: Double?,
    val weightChange: Double?,
    val weightReadings: Int,
    val observations: List<String>,
    val actions: List<String>
)

object DeficitMonitorSupport {
    fun evaluate(days: List<HabitDay>, confirmations: List<EnergyConfirmation>,
        points: List<WeightPoint>, today: LocalDate, window: Int = 28): DeficitCheck {
        require(window in 14..90)
        val end = today.minusDays(1)
        val start = end.minusDays(window.toLong() - 1)
        val approved = days.filter { day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            date != null && date in start..end && day.meals.isNotEmpty() &&
                WeeklyEnergySupport.isFoodConfirmed(day, confirmations.find { it.date == day.date })
        }.distinctBy { it.date }
        val goals = approved.filter { it.targetCalories > 0 }
        val weights = points.filter { point ->
            val date = runCatching { LocalDate.parse(point.date) }.getOrNull()
            date != null && date in start..end && point.kg.isFinite() && point.kg > 0
        }.distinctBy { it.date }.sortedBy { it.date }
        val first = weights.filter { LocalDate.parse(it.date) in start..start.plusDays(6) }
        val last = weights.filter { LocalDate.parse(it.date) in end.minusDays(6)..end }
        val comparable = window >= 21 && first.size >= 4 && last.size >= 4 &&
            first.last().date < last.first().date
        val a = first.takeIf { comparable }?.map { it.kg }?.average()
        val b = last.takeIf { comparable }?.map { it.kg }?.average()
        val delta = if (a != null && b != null) b - a else null
        val sufficientFood = approved.size >= (window * 0.8).toInt()
        val status = when {
            !comparable || !sufficientFood -> "Za mało danych"
            delta!! < -0.2 -> "Trend zgodny z deficytem"
            delta > 0.2 -> "Trend masy rosnący"
            else -> "Trend masy stabilny"
        }
        val observations = mutableListOf<String>()
        if (!sufficientFood) observations += "Zatwierdzono ${approved.size}/$window dni; brakujących dni nie uznajemy za zero kcal."
        if (!comparable) observations += "Do porównania średnich potrzeba co najmniej 4 ważeń w pierwszym i ostatnim tygodniu 28-dniowej obserwacji."
        val above = goals.count { it.calories > it.targetCalories }
        if (above > 0) observations += "Cel kalorii przekroczono w $above z ${goals.size} zatwierdzonych dni z zapisanym celem."
        val estimated = approved.count { !it.nutritionComplete }
        if (estimated > 0) observations += "W $estimated dniach wartości odżywcze były szacunkowe."
        if (delta != null && kotlin.math.abs(delta) <= 0.2) observations += "Różnica średnich mieści się w zakresie niewielkich wahań; nie dowodzi braku deficytu."
        if (delta != null && delta < -0.2) observations += "Średnia masa ciała maleje; sam pomiar nie określa, jaka część zmiany to tkanka tłuszczowa."
        if (delta != null && delta > 0.2) observations += "Średnia masa ciała wzrosła; możliwy jest wpływ wody i innych wahań."
        val actions = mutableListOf<String>()
        if (!sufficientFood) actions += "Potwierdzaj pełne zapisy jedzenia przez kolejne 7–14 dni."
        if (!comparable) actions += "Waż się rano w podobnych warunkach i porównuj średnie z 7 dni."
        if (above > 0) actions += "Sprawdź powtarzalne przekroczenia celu i wielkości porcji, bez automatycznej zmiany kalorii."
        if (actions.isEmpty()) actions += "Obserwuj trend przez kolejne 7–14 dni; nie wyciągaj wniosków z jednego ważenia."
        fun mean(items: List<HabitDay>) = items.takeIf { it.isNotEmpty() }?.map { it.calories.toDouble() }?.average()
        val weekend = approved.filter { LocalDate.parse(it.date).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        val weekday = approved - weekend.toSet()
        return DeficitCheck(status,
            "To ocena trendu, a nie pomiar rzeczywistego wydatku energetycznego. Kalorie zegarka nie są dodawane do limitu.",
            approved.size, window, mean(approved),
            goals.takeIf { it.isNotEmpty() }?.map { (it.calories - it.targetCalories).toDouble() }?.average(),
            mean(weekend), mean(weekday), a, b, delta, weights.size, observations, actions.take(3))
    }
}
