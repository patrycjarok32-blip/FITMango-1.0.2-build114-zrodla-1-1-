package com.example.viewmodel

import com.example.data.UserProfileEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

data class ProgressEvidenceResult(val facts: JSONObject, val summary: String)

/** Exact arithmetic stays local; the model explains these facts, not invented measurements. */
object ProgressEvidence {
    fun build(profile: UserProfileEntity?, days: List<HabitDay>, confirmations: List<EnergyConfirmation>,
        energy: List<DailyEnergy>, report: EffectsReport, scale: List<ScaleMeasurement>, from: LocalDate, until: LocalDate, today: LocalDate = LocalDate.now()): ProgressEvidenceResult {
        val cutoff = minOf(until, today.minusDays(1))
        fun numeric(v: Double?): Any = v?.takeIf { it.isFinite() } ?: JSONObject.NULL
        fun inside(d: String) = runCatching { LocalDate.parse(d) in from..cutoff }.getOrDefault(false)
        val length = java.time.temporal.ChronoUnit.DAYS.between(from, cutoff).toInt() + 1
        val food = days.filter { inside(it.date) && it.meals.isNotEmpty() }.distinctBy { it.date }
        val approved = food.filter { WeeklyEnergySupport.isFoodConfirmed(it, confirmations.find { c -> c.date == it.date }) }
        val estimated = approved.count { !it.nutritionComplete }
        val unapproved = food.count { !WeeklyEnergySupport.isFoodConfirmed(it, confirmations.find { c -> c.date == it.date }) }
        fun mean(selector: (HabitDay) -> Double) = approved.takeIf { it.isNotEmpty() }?.map(selector)?.average()
        val kcal = mean { it.calories.toDouble() }
        val protein = mean { it.protein.toDouble() }
        val sources = energy.filter { inside(it.date) && it.fullDayWindow && it.totalKcal?.let { v -> v.isFinite() && v > 0 } == true }.associateBy { it.date }
        val pairs = approved.filter { it.date in sources }
        val recordedBalance = pairs.takeIf { it.isNotEmpty() }?.sumOf { it.calories - sources.getValue(it.date).totalKcal!! }
        // Within-week averages are observations, not a kg/week extrapolation or fat/muscle change.
        val weights = report.points.filter { inside(it.date) }.distinctBy { it.date }.sortedBy { it.date }
        val first = weights.filter { it.date <= from.plusDays(2).toString() }
        val last = weights.filter { it.date >= cutoff.minusDays(2).toString() }
        val comparable = length >= 7 && first.size >= 2 && last.size >= 2 && first.last().date < last.first().date
        val startMean = first.takeIf { comparable }?.map { it.kg }?.average()
        val endMean = last.takeIf { comparable }?.map { it.kg }?.average()
        val change = if (startMean != null && endMean != null) endMean - startMean else null
        val observation = when {
            change == null -> "Za mało pomiarów z początku i końca okresu do porównania średnich."
            profile?.goal == "Redukcja" && change < 0 -> "Średnia masy z końca jest niższa niż z początku — kierunek zgodny z redukcją; to nie dowód utraty tłuszczu."
            profile?.goal == "Budowanie masy" && change > 0 -> "Średnia masy z końca jest wyższa niż z początku — kierunek zgodny z masą; to nie dowód przyrostu mięśni."
            else -> "Porównaj średnie masy i cel; jeden krótki okres wymaga dalszej obserwacji."
        }
        val reasons = mutableListOf<String>()
        if (approved.size < length) reasons += "Zatwierdzone zapisy jedzenia obejmują ${approved.size}/$length dni. Nie uzupełniaj pozostałych dni zerami."
        if (estimated > 0) reasons += "Wartości odżywcze z $estimated zatwierdzonych dni mają charakter szacunkowy. Uwzględniono je zgodnie z zatwierdzeniem użytkownika."
        if (unapproved > 0) reasons += "$unapproved zapisanych dni nie ma aktualnego zatwierdzenia."
        if (!comparable) reasons += "Porównanie średnich wymaga co najmniej dwóch dat pomiaru w pierwszych i ostatnich trzech dniach okresu."
        if (pairs.size < approved.size) reasons += "Bilans odczytany z Health Connect obejmuje tylko ${pairs.size} wspólnych dni jedzenia i spalania."
        reasons += "Krótki okres, woda, warunki ważenia i błędy zapisu ograniczają ocenę; nie podawaj procentowej pewności ani dokładnej utraty tłuszczu."
        val facts = JSONObject().put("zatwierdzone_dni", approved.size).put("dni_okresu", length)
            .put("dni_bez_jedzenia", length - food.size).put("zatwierdzone_dni_z_wartosciami_szacunkowymi", estimated).put("dni_niezatwierdzone", unapproved)
            .put("srednie_kcal", numeric(kcal)).put("srednie_bialko_g", numeric(protein))
            .put("srednie_weglowodany_g", numeric(mean { it.carbs.toDouble() })).put("srednie_tluszcze_g", numeric(mean { it.fat.toDouble() }))
            .put("roznica_kcal_wzgledem_celow_zapisanych_dla_dni", numeric(approved.filter { it.targetCalories > 0 }.takeIf { it.isNotEmpty() }?.map { it.calories.toDouble() - it.targetCalories }?.average()))
            .put("aktualny_cel_bialka_g_nie_historyczny", profile?.targetProtein ?: JSONObject.NULL)
            .put("daty_bilansu_health_connect", JSONArray(pairs.map { it.date }.sorted()))
            .put("zjedzone_minus_spalone_odczytane_kcal_lacznie", numeric(recordedBalance))
            .put("bilans_uwaga", "Tylko wspólne zatwierdzone dni; wartości szacunkowe pozostają przybliżeniem. Pełne okno odczytu nie dowodzi kompletnego pomiaru spalania. Nie zamieniaj bilansu na kilogramy.")
            .put("srednia_wagi_pierwsze_3_dni", numeric(startMean)).put("srednia_wagi_ostatnie_3_dni", numeric(endMean))
            .put("daty_wagi_poczatek", JSONArray(first.map { it.date })).put("daty_wagi_koniec", JSONArray(last.map { it.date }))
            .put("zmiana_miedzy_srednimi_kg_nie_tluszcz", numeric(change)).put("obserwacja", observation)
            .put("ograniczenia", JSONArray(reasons))
        val nutrition = NutritionWeekSupport.review(profile, days, confirmations, from, cutoff, today)
        facts.put("kompletnosc_zapisu", JSONObject()
            .put("pelne_potwierdzone_dni", approved.size)
            .put("zasada", "Zatwierdzenie użytkownika oznacza pełny zapis jedzenia. Szacunki wartości składników nie cofają kompletności dnia."))
        facts.put("ocena_jadlospisu", JSONObject()
            .put("bialko_cel_aktualny_g", nutrition.proteinTarget ?: JSONObject.NULL)
            .put("dni_z_osiagnietym_aktualnym_celem_bialka", nutrition.proteinDaysAtTarget ?: JSONObject.NULL)
            .put("bialko_uwaga", "Cel z aktualnego profilu, nie historyczny. Suma dobowa nie dowodzi rozkładu białka między posiłkami.")
            .put("srednia_roznica_od_dziennego_celu_kcal", numeric(nutrition.calorieDifference))
            .put("dni_z_celem_kalorii", nutrition.approved.count { it.targetCalories > 0 })
            .put("srednie_warzywa_owoce_zapisane_g", numeric(nutrition.produceMean))
            .put("dni_z_gramatura_warzyw_owocow", nutrition.produceMeasuredDays)
            .put("dni_z_osiagnietym_celem_warzyw_owocow", nutrition.produceDaysAtGoal)
            .put("srednie_napoje_zapisane_ml", numeric(nutrition.drinksMean))
            .put("dni_z_osiagnietym_celem_napojow", nutrition.drinksDaysAtGoal)
            .put("mocne_strony", JSONArray(nutrition.strengths))
            .put("obserwacje", JSONArray(nutrition.observations))
            .put("propozycje_na_tydzien", JSONArray(nutrition.actions))
            .put("nieoceniane_bez_pomiaru", JSONArray(listOf("błonnik", "sól", "cukry dodane", "tłuszcze nasycone", "witaminy i minerały", "pory posiłków", "rozkład białka między posiłkami")))
            .put("uwaga", "Gramatura warzyw i owoców dotyczy rozpoznanych wpisów; napoje nie obejmują wody w jedzeniu. Nazwy dań nie wystarczają do oceny składu ani różnorodności składników."))
        facts.put("porownanie_poprzedniego_okresu_jedzenia", JSONObject()
            .put("od", from.minusDays(nutrition.periodDays.toLong()).toString()).put("do", from.minusDays(1).toString())
            .put("zatwierdzone_dni", nutrition.previous.size).put("biezace_zatwierdzone_dni", approved.size)
            .put("srednie_kcal", numeric(nutrition.previousMeanCalories)).put("srednie_bialko_g", numeric(nutrition.previousMeanProtein))
            .put("roznica_srednich_kcal", numeric(if (nutrition.meanCalories != null && nutrition.previousMeanCalories != null) nutrition.meanCalories - nutrition.previousMeanCalories else null))
            .put("roznica_srednich_bialka_g", numeric(if (nutrition.meanProtein != null && nutrition.previousMeanProtein != null) nutrition.meanProtein - nutrition.previousMeanProtein else null))
            .put("uwaga", "To wyłącznie kontekst porównawczy dwóch okresów. Podaj liczbę dni każdego okresu. Inny cel lub skład dni ogranicza porównywalność; nie wnioskuj o poprawie tylko z niższych kalorii."))
        val comparisons = JSONArray()
        scale.filter { inside(it.date) }.flatMap { row -> row.values.map { (key, v) -> Triple(row, key, v) } }
            .groupBy { it.second to it.first.source }.forEach { (group, values) ->
                val daily = values.groupBy { it.first.date }.values.map { it.maxBy { r -> r.first.time } }.sortedBy { it.first.date }
                if (daily.size >= 2) {
                    val a = daily.first(); val b = daily.last(); val m = ScaleMeasurementsSupport.metrics.getValue(group.first)
                    comparisons.put(JSONObject().put("wskaznik", m.label).put("zrodlo", group.second).put("data_od", a.first.date).put("data_do", b.first.date)
                        .put("pierwszy", a.third).put("ostatni", b.third).put("roznica", b.third - a.third)
                        .put("jednostka_roznicy", if (m.unit == "%") "punkty procentowe" else m.unit)
                        .put("uwaga", "Wskaźnik BIA/szacunek wagi; pojedyncza różnica nie określa zmiany tłuszczu ani mięśni."))
                }
            }
        facts.put("porownania_wskaznikow_wagi_tylko_to_samo_zrodlo", comparisons)
        val summary = "Dane raportu: ${approved.size}/$length zatwierdzonych dni jedzenia; pomiary wagi z ${weights.size} dat. " +
            (kcal?.let { "Średnio ${it.roundToInt()} kcal i ${protein?.roundToInt()} g białka z tych ${approved.size} dni. " } ?: "Brak danych do pełnej średniej jedzenia. ") +
            (if (estimated > 0) "Wartości odżywcze są przybliżone dla $estimated zatwierdzonych dni. " else "") + "Bilans z Health Connect: ${pairs.size} wspólnych dni. $observation"
        val dietarySummary = buildString {
            append("\n\nOcena jadłospisu — obliczenia lokalne\n")
            append("Pełny zapis potwierdzony przez Ciebie: ${approved.size}/$length dni.\n")
            nutrition.strengths.forEach { append("• $it\n") }
            nutrition.observations.forEach { append("• $it\n") }
            if (nutrition.previousMeanCalories != null && nutrition.meanCalories != null) {
                append("Porównanie kalorii: poprzednio ${nutrition.previousMeanCalories.roundToInt()} kcal/dzień (${nutrition.previous.size} dni), teraz ${nutrition.meanCalories.roundToInt()} kcal/dzień (${approved.size} dni). Inny cel lub zestaw dni ogranicza porównywalność.\n")
            }
            append("Na kolejny tydzień:\n")
            nutrition.actions.forEachIndexed { index, action -> append("${index + 1}. $action\n") }
            append("Bez pomiarów nie oceniamy błonnika, soli, cukrów dodanych, tłuszczów nasyconych, witamin ani pór jedzenia.")
        }
        return ProgressEvidenceResult(facts, summary + dietarySummary)
    }
}
