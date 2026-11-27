package com.example
import com.example.viewmodel.*
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.*
class ConsultationSeriesTest {
 @Test fun cycleMatchesDaysAndPreservesMissing() {
   val rows = ConsultationSeries.cycle(listOf("2026-10-01","2026-10-29"),listOf(WeightPoint("2026-10-01",61.0),WeightPoint("2026-10-29",60.0),WeightPoint("2026-10-30",60.1)),LocalDate.parse("2026-10-30"))
   assertEquals(2,rows.size)
   assertEquals(61.0,rows[0].previous!!,0.001)
   assertNull(rows[1].previous)
 }
 @Test fun oneCycleIsInsufficient() { assertTrue(ConsultationSeries.cycle(listOf("2026-10-01"),emptyList(),LocalDate.parse("2026-10-30")).isEmpty()) }
}
