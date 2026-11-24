package com.example
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class TdeeCalibrationTest {
    @Test fun fourteenDaysRequireReliableCoverage() {
        val today = LocalDate.of(2026, 11, 24)
        val weights = (1..14).map { WeightPoint(today.minusDays(it.toLong()).toString(), 61.0) }
        assertEquals(1700.0, EffectsSupport.calibrateTdee(weights, List(14) { 1700.0 }, today)!!, 0.01)
        assertNull(EffectsSupport.calibrateTdee(weights, List(11) { 1700.0 }, today))
        assertNull(EffectsSupport.calibrateTdee(weights.take(6), List(14) { 1700.0 }, today))
        val falling = weights.mapIndexed { i, p -> p.copy(kg = 61.0 + i * 0.03) }
        assertTrue(EffectsSupport.calibrateTdee(falling, List(14) { 1700.0 }, today)!! > 1700.0)
    }
}
