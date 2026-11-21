package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DayClosureReminderTest {
    private val today = LocalDate.of(2026, 11, 21)
    private fun day(date: String) = HabitDay(date, 1650, 110, 150, 55, 2000, 1700, 400, 2500, listOf("Obiad"), emptyList(), true)

    @Test fun promptsForYesterdayWithMeals() {
        val prompt = DayClosureReminder.pendingYesterday(today, listOf(day("2026-11-20")), emptyList())
        assertEquals("2026-11-20", prompt?.date)
        assertEquals(1650, prompt?.calories)
    }

    @Test fun doesNotPromptForOlderDays() {
        assertNull(DayClosureReminder.pendingYesterday(today, listOf(day("2026-11-19")), emptyList()))
    }

    @Test fun doesNotPromptForAlreadyApprovedDay() {
        val d = day("2026-11-20")
        val approved = EnergyConfirmation(d.date, WeeklyEnergySupport.fingerprint(d), 0.0, "food", false)
        assertNull(DayClosureReminder.pendingYesterday(today, listOf(d), listOf(approved)))
    }
}
