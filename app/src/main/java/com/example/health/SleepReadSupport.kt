package com.example.health

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class SleepInterval(val start: Instant, val end: Instant)
data class SleepWindow(val start: Instant, val end: Instant, val source: String,
    val asleep: List<SleepInterval> = emptyList(), val awake: List<SleepInterval> = emptyList())
data class SleepSummary(val minutes: Long, val start: Instant, val end: Instant, val estimated: Boolean, val source: String = "")

object SleepReadSupport {
    private fun merged(intervals: List<SleepInterval>): List<SleepInterval> {
        val result = mutableListOf<SleepInterval>()
        intervals.filter { it.end > it.start }.sortedBy { it.start }.forEach { interval ->
            val last = result.lastOrNull()
            if (last != null && interval.start <= last.end) result[result.lastIndex] = SleepInterval(last.start, maxOf(last.end, interval.end))
            else result.add(interval)
        }
        return result
    }
    private fun seconds(intervals: List<SleepInterval>) = merged(intervals).sumOf { Duration.between(it.start, it.end).seconds }
    private fun sleepIntervals(window: SleepWindow): List<SleepInterval> {
        fun clipped(list: List<SleepInterval>) = list.map { SleepInterval(maxOf(it.start, window.start), minOf(it.end, window.end)) }.filter { it.end > it.start }
        if (window.asleep.isNotEmpty()) return clipped(window.asleep)
        val result = mutableListOf<SleepInterval>()
        var cursor = window.start
        merged(clipped(window.awake)).forEach { awake ->
            if (awake.start > cursor) result.add(SleepInterval(cursor, awake.start))
            cursor = maxOf(cursor, awake.end)
        }
        if (cursor < window.end) result.add(SleepInterval(cursor, window.end))
        return result
    }
    /** Join fragmented sessions; choose the longest sleep on the most recent wake date.
     * Sources are considered separately so two apps cannot double-count the same sleep. */
    fun summarize(windows: List<SleepWindow>, now: Instant, zone: ZoneId): SleepSummary? {
        val valid = windows.filter { it.end > it.start && it.end <= now && it.end > now.minus(Duration.ofDays(7)) }
        val clusters = valid.groupBy { it.source }.values.flatMap { source ->
            val groups = mutableListOf<MutableList<SleepWindow>>()
            source.sortedBy { it.start }.forEach { window ->
                val group = groups.lastOrNull()
                if (group != null && window.start <= group.maxOf { it.end }.plus(Duration.ofMinutes(90))) group.add(window)
                else groups.add(mutableListOf(window))
            }
            groups.map { group -> SleepSummary(seconds(group.flatMap { sleepIntervals(it) }) / 60,
                group.minOf { it.start }, group.maxOf { it.end }, group.any { it.asleep.isEmpty() }, group.first().source) }
        }.filter { it.minutes > 0 }
        val date = clusters.maxOfOrNull { it.end.atZone(zone).toLocalDate() } ?: return null
        return clusters.filter { it.end.atZone(zone).toLocalDate() == date }.maxWithOrNull(compareBy<SleepSummary> { it.minutes }.thenBy { it.end })
    }

    suspend fun <T> readPages(fetch: suspend (String?) -> Pair<List<T>, String?>): List<T> {
        val records = mutableListOf<T>()
        val seen = mutableSetOf<String>()
        var token: String? = null
        var pages = 0
        do {
            check(++pages <= 25) { "Odczyt snu przekroczył limit stron. Ponów synchronizację." }
            val (page, next) = fetch(token)
            records.addAll(page)
            token = next?.takeIf { it.isNotBlank() }
            if (token != null) check(seen.add(token!!)) { "Health Connect powtarza stronę odczytu snu." }
        } while (token != null)
        return records
    }
}
