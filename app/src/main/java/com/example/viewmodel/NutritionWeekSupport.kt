package com.example.viewmodel

import com.example.data.UserProfileEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Approval establishes a complete diary; nutrient precision is a separate property. */
data class NutritionWeekReview(
    val approved: List<HabitDay>, val previous: List<HabitDay>, val periodDays: Int,
    val estimatedDays: Int, val meanCalories: Double?, val meanProtein: Double?,
    val calorieDifference: Double?, val proteinTarget: Int?, val proteinDaysAtTarget: Int?,
    val produceMean: Double?, val produceMeasuredDays: Int, val produceDaysAtGoal: Int,
    val drinksMean: Double?, val drinksDaysAtGoal: Int, val previousMeanCalories: Double?,
    val previousMeanProtein: Double?, val strengths: List<String>, val observations: List<String>,
    val actions: List<String>
)

object NutritionWeekSupport {
    fun measuredProduceGrams(day: HabitDay): Int? {
        val entries = day.produce.filter { it.category in listOf("vegetable", "fruit") }
        return day.produceGrams.takeIf { entries.isNotEmpty() && entries.all { entry ->
            entry.grams?.let { it.isFinite() && it > 0f } == true
        } }
    }

    fun review(profile: UserProfileEntity?, days: List<HabitDay>, confirmations: List<EnergyConfirmation>,
        from: LocalDate, until: LocalDate, today: LocalDate): NutritionWeekReview {
        val end = minOf(until, today.minusDays(1))
        val length = (ChronoUnit.DAYS.between(from, end).toInt() + 1).coerceAtLeast(0)
        fun accepted(start: LocalDate, finish: LocalDate) = days.distinctBy { it.date }.filter { d ->
            runCatching { LocalDate.parse(d.date) in start..finish }.getOrDefault(false) &&
                WeeklyEnergySupport.isFoodConfirmed(d, confirmations.find { it.date == d.date })
        }.sortedBy { it.date }
        val approved = if (length == 0) emptyList() else accepted(from, end)
        val previous = if (length == 0) emptyList() else accepted(from.minusDays(length.toLong()), from.minusDays(1))
        fun mean(rows: List<HabitDay>, selector: (HabitDay) -> Double) = rows.takeIf { it.isNotEmpty() }?.map(selector)?.average()
        val kcal = mean(approved) { it.calories.toDouble() }
        val protein = mean(approved) { it.protein.toDouble() }
        val target = profile?.targetProtein?.takeIf { it > 0 }
        val proteinDays = target?.let { goal -> approved.count { it.protein >= goal } }
        val calorieDays = approved.filter { it.targetCalories > 0 }
        val difference = mean(calorieDays) { it.calories.toDouble() - it.targetCalories }
        // An absent produce log is not evidence of no vegetables. Unknown grams stay unknown.
        val measuredProduce = approved.filter { measuredProduceGrams(it) != null }
        val produceMean = mean(measuredProduce) { it.produceGrams.toDouble() }
        val produceAtGoal = measuredProduce.count { it.produceGoal > 0 && it.produceGrams >= it.produceGoal }
        val drinks = mean(approved) { it.drinksMl.toDouble() }
        val drinksAtGoal = approved.count { it.waterGoal > 0 && it.drinksMl >= it.waterGoal }
        val estimated = approved.count { !it.nutritionComplete }
        val strengths = mutableListOf<String>()
        val observations = mutableListOf<String>()
        val actions = mutableListOf<String>()
        if (approved.isNotEmpty()) strengths += "${approved.size}/$length dni: pełny zapis potwierdzony przez Ciebie."
        if (target != null && proteinDays != null && proteinDays > 0)
            strengths += "Cel białka z aktualnego profilu osiągnięty w $proteinDays/${approved.size} zatwierdzonych dni."
        if (produceAtGoal > 0) strengths += "Zapisany cel warzyw i owoców osiągnięty w $produceAtGoal/${measuredProduce.size} dni z gramaturą."
        if (difference != null) observations += "Średnia różnica względem celów kalorii zapisanych dla dni: ${difference.roundToInt()} kcal/dzień (${calorieDays.size} dni). To nie bilans względem zapotrzebowania."
        if (estimated > 0) observations += "Zapis dnia jest pełny i potwierdzony. W $estimated dniach wartości odżywcze są przybliżone."
        if (approved.isEmpty()) actions += "Zatwierdź zakończone dni, które mają pełny zapis jedzenia."
        if (target != null && protein != null && protein < target)
            actions += "Przy planowaniu posiłków porównaj białko z aktualnym celem $target g; średnia wyniosła ${protein.roundToInt()} g."
        if (measuredProduce.isNotEmpty() && measuredProduce.any { it.produceGoal > 0 && it.produceGrams < it.produceGoal })
            actions += "Zaplanuj warzywa lub owoce do posiłków, porównując porcje z własnym zapisanym celem."
        if (approved.any { it.waterGoal > 0 && it.drinksMl < it.waterGoal })
            observations += "Zapis napojów osiąga własny cel w $drinksAtGoal/${approved.size} dni. To nie pomiar całkowitej wody z diety ani rozpoznanie odwodnienia."
        if (measuredProduce.size < approved.size) observations += "Ilość warzyw i owoców można policzyć dla ${measuredProduce.size}/${approved.size} dni. Pozostałe zapisy nie dowodzą braku warzyw; nie podważają zatwierdzenia dnia."
        if (previous.isNotEmpty() && approved.isNotEmpty() && previous.size != approved.size)
            observations += "Porównanie okresów obejmuje różną liczbę zatwierdzonych dni: ${previous.size} i ${approved.size}; nie jest porównaniem pełnych tygodni."
        if (actions.isEmpty()) actions += "Utrzymaj sprawdzone elementy planu i porównaj wyniki po kolejnym tygodniu."
        return NutritionWeekReview(approved, previous, length, estimated, kcal, protein, difference, target, proteinDays,
            produceMean, measuredProduce.size, produceAtGoal, drinks, drinksAtGoal,
            mean(previous) { it.calories.toDouble() }, mean(previous) { it.protein.toDouble() }, strengths, observations, actions.take(3))
    }
}
