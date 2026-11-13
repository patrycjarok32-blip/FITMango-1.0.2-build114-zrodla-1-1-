package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MangoWorkoutWeeklyReportTest {
    @Test fun comparesTwoWeeksWithoutCountingUnfinishedSets() {
        fun session(date: String, kg: Double, done: Boolean = true) =
            WorkoutSession(date, date, "PUSH", 1000L,
                listOf(WorkoutEntry("Bench press", 1, 10, kg, done)), 2000L, 6)
        val report = MangoWorkoutWeeklyReport.compare(listOf(
            session("2026-11-04", 10.0),
            session("2026-11-11", 12.0),
            session("2026-11-12", 20.0, false)
        ), LocalDate.parse("2026-11-13"))
        assertEquals(2, report.current.sessions)
        assertEquals(1, report.current.sets)
        assertEquals(120.0, report.current.volumeKg, 0.001)
        assertEquals(100.0, report.previous.volumeKg, 0.001)
        assertEquals(1, report.current.records)
    }
    @Test fun emptyWeeksReturnZeroAndNoEffort() {
        val report = MangoWorkoutWeeklyReport.compare(emptyList(), LocalDate.parse("2026-11-13"))
        assertEquals(0, report.current.sessions)
        assertNull(report.current.averageEffort)
    }
}
