package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class Sport7IntegrationTest {
    @Test fun cyclingMatchesOnlyOneCompatiblePlan() {
        val day = LocalDate.parse("2026-11-10")
        val start = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        val record = SportSession("bike", "Cycling", start.toString(), start.plusSeconds(1800).toString(), "watch")
        val plan = SportPlan(day.toString(), "Rower", false, category = "Cardio", sessionId = "p")
        val data = SportsData(plans = listOf(plan), history = listOf(record))
        assertEquals("bike", PlannedActivityMatching.match(plan, data)?.id)
        assertNull(PlannedActivityMatching.match(plan, data.copy(plans = listOf(plan, plan.copy(sessionId = "p2")))))
        assertNull(PlannedActivityMatching.match(plan.copy(title = "Spacer"), data))
    }
}
