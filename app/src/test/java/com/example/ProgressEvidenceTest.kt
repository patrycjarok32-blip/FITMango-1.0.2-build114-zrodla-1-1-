package com.example

import com.example.data.UserProfileEntity
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProgressEvidenceTest {
    private val today = LocalDate.parse("2026-10-17")
    private val profile = UserProfileEntity(goal = "Redukcja", targetCalories = 1800)
    private fun day(offset: Long, kcal: Int, complete: Boolean = true) = HabitDay(today.minusDays(offset).toString(), kcal, 100, 200, 60, 2000, 1800, 400, 2500, listOf("Posiłki"), emptyList(), complete)
    private fun evidence(days: List<HabitDay>, energy: List<DailyEnergy> = emptyList(), points: List<WeightPoint> = emptyList(), scale: List<ScaleMeasurement> = emptyList()): ProgressEvidenceResult {
        val approvals = days.map { EnergyConfirmation(it.date, WeeklyEnergySupport.fingerprint(it), 2000.0, acceptsPartial = true) }
        val report = EffectsSupport.analyze(emptyList(), emptyList(), days, approvals, profile, SportsData(), EffectsSettings(), today, 7).copy(points = points)
        return ProgressEvidence.build(profile, days, approvals, energy, report, scale, today.minusDays(7), today.minusDays(1), today)
    }
    @Test fun approvedEstimatedDaysCountInAveragesAndMatchedBalanceWhileTodayIsExcluded() {
        val food = listOf(day(1, 1600), day(2, 1800), day(3, 5000, false), day(0, 9000))
        val energy = listOf(DailyEnergy(food[0].date, 2000.0, true, 1), DailyEnergy(food[1].date, null, true, 1), DailyEnergy(food[2].date, 9000.0, true, 1))
        val f = evidence(food, energy).facts
        assertEquals(3, f.getInt("zatwierdzone_dni"))
        assertEquals(2800.0, f.getDouble("srednie_kcal"), .001)
        assertEquals(2, f.getJSONArray("daty_bilansu_health_connect").length())
        assertEquals(-4400.0, f.getDouble("zjedzone_minus_spalone_odczytane_kcal_lacznie"), .001)
        assertEquals(1, f.getInt("zatwierdzone_dni_z_wartosciami_szacunkowymi"))
        assertEquals(4, f.getInt("dni_bez_jedzenia"))
    }
    @Test fun missingDataStayUnknownRatherThanZeroOrAConfidentVerdict() {
        val f = evidence(emptyList()).facts
        assertTrue(f.isNull("srednie_kcal"))
        assertTrue(f.isNull("zjedzone_minus_spalone_odczytane_kcal_lacznie"))
        assertTrue(f.isNull("zmiana_miedzy_srednimi_kg_nie_tluszcz"))
        assertTrue(f.getString("obserwacja").contains("Za mało"))
        assertEquals(7, f.getInt("dni_bez_jedzenia"))
    }
    @Test fun weeklyWeightObservationUsesSeparateEndWindowsAndIsNotAFatForecast() {
        val points = listOf(WeightPoint("2026-10-10", 70.2), WeightPoint("2026-10-11", 70.0),
            WeightPoint("2026-10-15", 69.9), WeightPoint("2026-10-16", 69.9))
        val f = evidence(emptyList(), points = points).facts
        assertEquals(-.2, f.getDouble("zmiana_miedzy_srednimi_kg_nie_tluszcz"), .001)
        assertTrue(f.getString("obserwacja").contains("nie dowód utraty tłuszczu"))
        val sparse = evidence(emptyList(), points = points.take(2)).facts
        assertTrue(sparse.isNull("zmiana_miedzy_srednimi_kg_nie_tluszcz"))
    }
    @Test fun bodyPercentDifferencesArePercentagePointsAndSourcesCannotBeMixed() {
        fun row(id: String, date: String, source: String, fat: Double) = ScaleMeasurement(id, date, 1, source, mapOf("fat" to fat))
        val rows = listOf(row("1", "2026-10-10", "Renpho", 30.0), row("2", "2026-10-16", "Renpho", 29.5), row("3", "2026-10-16", "Other", 10.0))
        val a = evidence(emptyList(), scale = rows).facts.getJSONArray("porownania_wskaznikow_wagi_tylko_to_samo_zrodlo")
        assertEquals(1, a.length())
        assertEquals("Renpho", a.getJSONObject(0).getString("zrodlo"))
        assertEquals(-.5, a.getJSONObject(0).getDouble("roznica"), .001)
        assertEquals("punkty procentowe", a.getJSONObject(0).getString("jednostka_roznicy"))
    }
}
