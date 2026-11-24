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
        val facts = JSONObject().put("tdee_wstepne_14_dni_kcal", numeric(report.calibratedTdee))
            .put("tdee_dni_precyzyjne", report.calibrationDays)
            .put("tdee_uwaga", "Wstępny szacunek z ostatnich 14 zakończonych dni, niezależny od zakresu raportu. Woda i cykl ograniczają dokładność. Nie dodawaj kalorii treningowych ani nie zmieniaj automatycznie celu.")
            .put("zatwierdzone_dni", approved.size).put("dni_okresu", length)
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
        // Goal barriers are evidence-backed hypotheses, never medical diagnoses or proof of causality.
        val daysWithGoal = approved.filter { it.targetCalories > 0 }
        val aboveGoal = daysWithGoal.count { it.calories > it.targetCalories }
        val averageGoalDifference = daysWithGoal.takeIf { it.isNotEmpty() }
            ?.map { (it.calories - it.targetCalories).toDouble() }?.average()
        val goalBarriers = mutableListOf<String>()
        if (profile?.goal == "Redukcja" && change != null && change < 0) goalBarriers += "Średnia masy spada: nie klasyfikuj powolnej redukcji jako potwierdzonego zastoju."
        goalBarriers += "Cykl menstruacyjny (jeśli dotyczy), regeneracja i woda mogą wpływać na wagę; bez zapisów nie przypisuj fazy ani przyczyny."
        if (approved.size < length)
            goalBarriers += "Niepełny zakres obserwacji: ${approved.size}/$length dni ma zatwierdzony dziennik. Pozostałych dni nie traktuj jako dni bez jedzenia."
        if (aboveGoal > 0)
            goalBarriers += "W $aboveGoal z ${daysWithGoal.size} dni z zapisanym celem spożycie przekroczyło cel kalorii. To możliwy czynnik, nie dowód przyczyny zmiany masy."
        if (estimated > 0)
            goalBarriers += "W $estimated zatwierdzonych dniach wartości odżywcze są szacunkowe, co ogranicza dokładność porównania."
        if (!comparable)
            goalBarriers += "Brak wystarczająco porównywalnych pomiarów masy na początku i końcu okresu; nie można potwierdzić zastoju."
        if (goalBarriers.isEmpty())
            goalBarriers += "W dostępnych danych nie wykryto jednoznacznego wyjaśnienia. Sam zapis kalorii nie dowodzi przyczyny wyniku."
        val nextChecks = mutableListOf<String>()
        if (approved.size < length) nextChecks += "Przez kolejne 7 dni potwierdzaj pełny dziennik każdego zakończonego dnia."
        if (aboveGoal > 0) nextChecks += "Przez 7 dni sprawdzaj odchylenia od celu zapisanego dla każdego dnia, bez automatycznej zmiany kaloryczności."
        if (!comparable) nextChecks += "Zapisuj wagę w porównywalnych warunkach i porównuj średnie, nie pojedyncze odczyty."
        if (nextChecks.isEmpty()) nextChecks += "Kontynuuj obserwację przez 7–14 dni; porównaj zatwierdzone zapisy i średnie pomiary."
        facts.put("dlaczego_cel_nieosiagniety", JSONObject()
            .put("charakter", "Hipotezy na podstawie zapisów, nie potwierdzone przyczyny")
            .put("zatwierdzone_dni", approved.size).put("dni_okresu", length)
            .put("dni_z_celem_kcal", daysWithGoal.size)
            .put("dni_ponad_zapisanym_celem_kcal", aboveGoal)
            .put("srednie_odchylenie_od_zapisanego_celu_kcal", numeric(averageGoalDifference))
            .put("porownywalny_trend_wagi", comparable)
            .put("mozliwe_przeszkody", JSONArray(goalBarriers))
            .put("co_sprawdzic_w_7_14_dni", JSONArray(nextChecks.take(3)))
            .put("zastrzezenie", "Brak zapisu nie oznacza zera. Nie stawiaj diagnozy, nie przypisuj związku przyczynowego i nie zmieniaj automatycznie celu kalorii."))
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
        // Primary evaluation uses the latest two consecutive weigh-in dates.
        // The wider report remains secondary context, not a substitute for this interval.
        val weighIns = report.points.filter { point ->
            runCatching { LocalDate.parse(point.date) <= cutoff }.getOrDefault(false) &&
                point.kg.isFinite() && point.kg > 0
        }.distinctBy { it.date }.sortedBy { it.date }
        val previousWeighIn = weighIns.getOrNull(weighIns.size - 2)
        val currentWeighIn = weighIns.lastOrNull()
        val interval = if (previousWeighIn != null && currentWeighIn != null &&
            previousWeighIn.date < currentWeighIn.date) {
            val start = LocalDate.parse(previousWeighIn.date)
            val end = LocalDate.parse(currentWeighIn.date)
            val count = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()
            // Meals from the day AFTER the first weigh-in through the second weigh-in.
            val intervalApproved = days.filter { d ->
                runCatching { LocalDate.parse(d.date) > start && LocalDate.parse(d.date) <= end }
                    .getOrDefault(false) &&
                    WeeklyEnergySupport.isFoodConfirmed(d, confirmations.find { it.date == d.date })
            }.distinctBy { it.date }
            JSONObject().put("pomiar_od", previousWeighIn.date).put("pomiar_do", currentWeighIn.date)
                .put("liczba_dni", count).put("waga_od_kg", previousWeighIn.kg)
                .put("waga_do_kg", currentWeighIn.kg)
                .put("zmiana_kg_nie_zmiana_tluszczu", currentWeighIn.kg - previousWeighIn.kg)
                .put("dni_jedzenia_zatwierdzone", intervalApproved.size)
                .put("pokrycie_dni_procent", if (count > 0) 100.0 * intervalApproved.size / count else JSONObject.NULL)
                .put("dni_bez_zatwierdzonego_zapisu", (count - intervalApproved.size).coerceAtLeast(0))
                .put("srednia_kcal_dotyczy_wylacznie_dni_zatwierdzonych", true)
                .put("wiarygodnosc_porownania", when {
                    intervalApproved.isEmpty() -> "Brak zatwierdzonych dni jedzenia w okresie: nie oceniaj spożycia."
                    intervalApproved.size * 2 < count -> "Niskie pokrycie dziennika: średnie mogą nie reprezentować całego okresu."
                    intervalApproved.size < count -> "Niepełne pokrycie dziennika: ostrożnie interpretuj średnie."
                    else -> "Wszystkie dni okresu mają zatwierdzony zapis; wartości mogą być szacunkowe."
                })
                .put("srednie_kcal", numeric(intervalApproved.takeIf { it.isNotEmpty() }?.map { it.calories.toDouble() }?.average()))
                .put("srednie_bialko_g", numeric(intervalApproved.takeIf { it.isNotEmpty() }?.map { it.protein.toDouble() }?.average()))
                .put("uwaga", "Najważniejszy okres to dni pomiędzy kolejnymi pomiarami. Brakujące dni nie są zerami; zmiana masy nie dowodzi zmiany tkanki tłuszczowej.")
        } else JSONObject().put("uwaga", "Do analizy od pomiaru do pomiaru potrzeba dwóch dat ważenia.")
        facts.put("analiza_pomiedzy_kolejnymi_pomiarami_PRIORYTET", interval)
        if (previousWeighIn != null && currentWeighIn != null && previousWeighIn.date < currentWeighIn.date) {
            val start = LocalDate.parse(previousWeighIn.date)
            val end = LocalDate.parse(currentWeighIn.date)
            val periodDays = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()
            val confirmed = days.filter { d ->
                runCatching { LocalDate.parse(d.date) > start && LocalDate.parse(d.date) <= end }.getOrDefault(false) &&
                    WeeklyEnergySupport.isFoodConfirmed(d, confirmations.find { it.date == d.date })
            }.distinctBy { it.date }
            val previousStart = start.minusDays(periodDays.toLong())
            val previous = days.filter { d ->
                runCatching { LocalDate.parse(d.date) > previousStart && LocalDate.parse(d.date) <= start }.getOrDefault(false) &&
                    WeeklyEnergySupport.isFoodConfirmed(d, confirmations.find { it.date == d.date })
            }.distinctBy { it.date }
            val currentKcal = confirmed.takeIf { it.isNotEmpty() }?.map { it.calories.toDouble() }?.average()
            val previousKcal = previous.takeIf { it.isNotEmpty() }?.map { it.calories.toDouble() }?.average()
            val currentProtein = confirmed.takeIf { it.isNotEmpty() }?.map { it.protein.toDouble() }?.average()
            val previousProtein = previous.takeIf { it.isNotEmpty() }?.map { it.protein.toDouble() }?.average()
            val withCalorieGoal = confirmed.filter { it.targetCalories > 0 }
            interval.put("porownanie_poprzedniego_rownej_dlugosci_okresu", JSONObject()
                .put("od_wylacznie", previousStart.toString()).put("do_wlacznie", start.toString())
                .put("dni_okresu", periodDays).put("zatwierdzone_dni", previous.size)
                .put("srednie_kcal", numeric(previousKcal))
                .put("srednie_bialko_g", numeric(previousProtein))
                .put("zmiana_srednich_kcal", numeric(if (currentKcal != null && previousKcal != null) currentKcal - previousKcal else null))
                .put("zmiana_srednich_bialka_g", numeric(if (currentProtein != null && previousProtein != null) currentProtein - previousProtein else null))
                .put("uwaga", "Oba okresy mają tę samą długość; średnie pochodzą wyłącznie z zatwierdzonych dni. Różna liczba zatwierdzonych dni ogranicza porównywalność."))
            interval.put("realizacja_historycznego_celu_kcal", JSONObject()
                .put("dni_z_zapisanym_celem", withCalorieGoal.size)
                .put("srednia_roznica_od_celu_kcal", numeric(withCalorieGoal.takeIf { it.isNotEmpty() }?.map { it.calories.toDouble() - it.targetCalories }?.average()))
                .put("uwaga", "Cel zapisany w dniu, nie szacowane zapotrzebowanie. Nie jest podstawą do automatycznej zmiany kalorii."))
        }

        val summary = "Dane raportu: ${approved.size}/$length zatwierdzonych dni jedzenia; pomiary wagi z ${weights.size} dat. " +
            (kcal?.let { "Średnio ${it.roundToInt()} kcal i ${protein?.roundToInt()} g białka z tych ${approved.size} dni. " } ?: "Brak danych do pełnej średniej jedzenia. ") +
            (if (estimated > 0) "Wartości odżywcze są przybliżone dla $estimated zatwierdzonych dni. " else "") + "Bilans z Health Connect: ${pairs.size} wspólnych dni. $observation"
        val dietarySummary = buildString {
            append("\n\nAnaliza między kolejnymi pomiarami — priorytet\n")
            if (previousWeighIn != null && currentWeighIn != null && previousWeighIn.date < currentWeighIn.date) {
                val start = LocalDate.parse(previousWeighIn.date)
                val end = LocalDate.parse(currentWeighIn.date)
                val lengthDays = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()
                val confirmed = days.filter { d ->
                    runCatching { LocalDate.parse(d.date) > start && LocalDate.parse(d.date) <= end }.getOrDefault(false) &&
                        WeeklyEnergySupport.isFoodConfirmed(d, confirmations.find { it.date == d.date })
                }.distinctBy { it.date }
                append("${previousWeighIn.date} – ${currentWeighIn.date}: ${lengthDays} dni, zatwierdzone zapisy ${confirmed.size}/${lengthDays}.\n")
                append("Masa: ${"%.2f".format(java.util.Locale.ROOT, previousWeighIn.kg)} → ${"%.2f".format(java.util.Locale.ROOT, currentWeighIn.kg)} kg (różnica ${"%.2f".format(java.util.Locale.ROOT, currentWeighIn.kg - previousWeighIn.kg)} kg; nie oznacza automatycznie zmiany tkanki tłuszczowej).\n")
                if (confirmed.isNotEmpty()) append("Średnie z potwierdzonych dni: ${confirmed.map { it.calories.toDouble() }.average().roundToInt()} kcal i ${confirmed.map { it.protein.toDouble() }.average().roundToInt()} g białka.\n")
                else append("Brak zatwierdzonych dni jedzenia do obliczenia średnich.\n")
                if (confirmed.size < lengthDays) append("Uwaga: brak zatwierdzenia w części dni ogranicza reprezentatywność średnich; nie traktuj tych dni jako zero.\n")
            } else append("Potrzeba co najmniej dwóch pomiarów masy w różnych dniach.\n")
            append("\n\nDlaczego nie osiągam celu? — możliwe wyjaśnienia\n")
            goalBarriers.forEach { append("• $it\n") }
            append("Co sprawdzić w kolejnych 7–14 dniach:\n")
            nextChecks.take(3).forEach { append("• $it\n") }
            append("To obserwacje, nie pewna przyczyna braku postępów.\n")
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
