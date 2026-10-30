package com.example

import com.example.health.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SleepReadSupportTest {
    private fun t(value: String) = Instant.parse(value)
    private val now = t("2026-10-12T15:00:00Z")
    private val zone = ZoneId.of("Europe/London")
    @Test fun fragmentsAndDuplicateSourcesDoNotDoubleCountOrReplaceNightWithNap() {
        val a = SleepWindow(t("2026-10-11T22:00:00Z"), t("2026-10-12T02:00:00Z"), "watch")
        val b = SleepWindow(t("2026-10-12T02:30:00Z"), t("2026-10-12T06:00:00Z"), "watch")
        val nap = SleepWindow(t("2026-10-12T13:00:00Z"), t("2026-10-12T13:30:00Z"), "watch")
        val summary = SleepReadSupport.summarize(listOf(a, b, a, b.copy(source = "other"), nap), now, zone)!!
        assertEquals(450L, summary.minutes)
        assertEquals(b.end, summary.end)
        assertTrue(summary.estimated)
    }
    @Test fun knownStagesExcludeAwakeAndOldSessionsKeepTheirRealDate() {
        val start = t("2026-10-10T22:00:00Z")
        val end = t("2026-10-11T06:00:00Z")
        val summary = SleepReadSupport.summarize(listOf(SleepWindow(start, end, "watch",
            asleep = listOf(SleepInterval(start, t("2026-10-11T02:00:00Z")),
                SleepInterval(t("2026-10-11T03:00:00Z"), end)))), now, zone)!!
        assertEquals(420L, summary.minutes)
        assertFalse(summary.estimated)
        assertEquals(end, summary.end)
        assertNull(SleepReadSupport.summarize(listOf(SleepWindow(start.minusSeconds(10 * 86400), end.minusSeconds(10 * 86400), "watch")), now, zone))
    }
    @Test fun paginationReadsLaterPagesAndStopsOnEmptyToken() = runTest {
        val tokens = mutableListOf<String?>()
        val records = SleepReadSupport.readPages<Int> { token ->
            tokens.add(token)
            if (token == null) listOf(1) to "next" else listOf(2) to ""
        }
        assertEquals(listOf(1, 2), records)
        assertEquals(listOf(null, "next"), tokens)
    }
    @Test fun repeatedPageTokenFailsInsteadOfFreezing() = runTest {
        assertTrue(runCatching { SleepReadSupport.readPages<Int> { listOf(1) to "same" } }.isFailure)
    }
}
