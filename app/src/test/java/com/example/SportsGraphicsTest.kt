package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SportsGraphicsTest {
    private val today = LocalDate.parse("2026-10-21")
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val zone = ZoneId.of("UTC")
    private fun food(date: LocalDate, kcal: Int, complete: Boolean = true) = HabitDay(date.toString(), kcal, 100, 180, 60,
        2000, 1800, 400, 2000, listOf("Zapisany posiłek"), emptyList(), complete)
    private fun session(id: String, start: String = "2026-10-20T10:00:00Z", end: String = "2026-10-20T11:00:00Z") =
        SportSession(id, "Spacer", start, end, "zepp")

    @Test fun averagesUseApprovedPastDaysIncludingAcceptedEstimatesWithoutFillingMissingDates() {
        val one = food(today.minusDays(1), 1600)
        val two = food(today.minusDays(2), 1800, false)
        val rejected = food(today.minusDays(3), 900)
        val days = listOf(one, two, rejected, food(today, 9999), food(today.minusDays(8), 9999))
        val confirmations = listOf(EnergyConfirmation(one.date, WeeklyEnergySupport.fingerprint(one), 0.0),
            EnergyConfirmation(two.date, WeeklyEnergySupport.fingerprint(two), 0.0, acceptsPartial = true),
            EnergyConfirmation(rejected.date, "stale", 0.0))
        val facts = SportsGraphicsSupport.report(SportsData(), days, confirmations, today, now, zone)
        assertEquals(2, facts.approved)
        assertEquals(1700.0, facts.meanCalories!!, 0.0)
        assertTrue(facts.estimatedFood)
        assertEquals(today.minusDays(7), facts.from)
        assertEquals(today.minusDays(1), facts.until)
        assertEquals(7, facts.days.size)
        val empty = SportsGraphicsSupport.report(SportsData(), emptyList(), emptyList(), today, now, zone)
        assertNull(empty.meanCalories); assertNull(empty.meanProtein)
    }
    @Test fun dayBarsUseUnionOfIntervalsAndKeepNoSessionDistinctFromARecordedSubMinuteSession() {
        val data = SportsData(history = listOf(session("a"), session("copy"), session("b", "2026-10-20T10:30:00Z", "2026-10-20T11:30:00Z"),
            session("tiny", "2026-10-19T10:00:00Z", "2026-10-19T10:00:20Z")))
        val rows = SportsGraphicsSupport.days(data, today.minusDays(2), now, zone)
        assertEquals(0L, rows[0].minutes!!)
        assertEquals(90L, rows[1].minutes!!)
        assertEquals(2, rows[1].sessions.size)
        assertNull(rows[2].minutes)
        assertTrue(rows[3].future)
        assertNull(rows[3].minutes)
    }
    @Test fun missingSleepAndRatingsStayUnknownAndDatesAreWakeDates() {
        val data = SportsData(daily = listOf(ActivityDay(today.minusDays(1).toString(), sleepMinutes = 420)),
            recovery = listOf(RecoveryCheck(today.toString(), 2, 4)))
        val rows = SportsGraphicsSupport.days(data, today.minusDays(1), now, zone)
        assertEquals(420L, rows[0].sleepMinutes!!)
        assertNull(rows[1].sleepMinutes)
        assertNull(rows[0].energy)
        assertEquals(2, rows[1].energy!!)
        assertEquals("—", SportsGraphicsSupport.sleepLabel(null))
        assertEquals("7 h 0 min", SportsGraphicsSupport.sleepLabel(420))
    }
    @Test fun aiHeadingsAndInlineAnswersBecomeCardsWithAtMostThreeActions() {
        val text = """
            ## Kierunek względem celu: wymaga obserwacji
            Sprawdź trend pomiarów.
            **Posiłki i białko**
            Średnia pochodzi z Twoich zapisów.
            ### Aktywność i regeneracja
            Uwzględniono sen i spacery.
            ## Co zrobić dalej
            1. Pierwsza wskazówka.
            2. Druga wskazówka.
            3. Trzecia wskazówka.
            4. Dodatkowy szczegół.
            ## Pewność oceny i braki danych
            Brakuje kilku odczytów.
        """.trimIndent()
        val cards = SportsGraphicsSupport.aiCards(text)
        assertTrue(cards.direction.contains("wymaga obserwacji"))
        assertTrue(cards.food.contains("Twoich zapisów"))
        assertTrue(cards.recovery.contains("sen i spacery"))
        assertEquals(listOf("Pierwsza wskazówka.", "Druga wskazówka.", "Trzecia wskazówka."), cards.actions)
        assertTrue(cards.confidence.contains("odczytów"))
        assertFalse(cards.actions.any { it.contains("odczytów") })
    }
    @Test fun unstructuredAiIsNotInventedIntoAdviceAndDifferentReportPeriodsNeverMatch() {
        assertEquals(AiReportCards(), SportsGraphicsSupport.aiCards("Dowolny akapit bez nagłówków."))
        val report = WeeklyAiExplanation("2026-10-14", "2026-10-20", "original", "full", 123, true)
        assertEquals(report, SportsGraphicsSupport.matching(report, today.minusDays(7), today.minusDays(1)))
        assertNull(SportsGraphicsSupport.matching(report, today.minusDays(8), today.minusDays(1)))
        assertNull(SportsGraphicsSupport.matching(report, today.minusDays(7), today))
    }
}
