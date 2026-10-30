package com.example.viewmodel

import android.content.Context
import kotlin.math.max

object CookingSupport {
    fun steps(instructions: String): List<String> = instructions.trim()
        .split(Regex("(?:^|\\n)\\s*\\d+[.)]\\s+"))
        .flatMap { block -> if (block.contains('\n') && !Regex("(?m)^\\s*\\d+[.)]").containsMatchIn(instructions)) block.lines() else listOf(block) }
        .map { it.trim() }.filter { it.isNotBlank() }
}

data class CookingTimerState(val deadlineMillis: Long = 0, val pausedMillis: Long = 0) {
    fun remainingMillis(now: Long) = if (deadlineMillis > 0) max(0, deadlineMillis - now) else pausedMillis
    fun remainingSeconds(now: Long) = (remainingMillis(now) + 999) / 1000
    fun pause(now: Long) = CookingTimerState(pausedMillis = remainingMillis(now))
    fun resume(now: Long) = if (pausedMillis > 0) CookingTimerState(now + pausedMillis) else this
    companion object {
        fun start(minutes: Int, now: Long): CookingTimerState {
            require(minutes in 1..180)
            return CookingTimerState(now + minutes * 60_000L)
        }
    }
}

class CookingTimerStore(context: Context, private val recipeId: Long) {
    private val prefs = context.getSharedPreferences("FITMangoCooking", Context.MODE_PRIVATE)
    fun read() = CookingTimerState(prefs.getLong("deadline_$recipeId", 0), prefs.getLong("paused_$recipeId", 0))
    fun save(state: CookingTimerState) {
        prefs.edit().putLong("deadline_$recipeId", state.deadlineMillis).putLong("paused_$recipeId", state.pausedMillis)
            .putBoolean("notified_$recipeId", false).apply()
    }
    fun consumeFinished(now: Long): Boolean {
        val state = read()
        if (state.deadlineMillis == 0L || state.remainingMillis(now) > 0 || prefs.getBoolean("notified_$recipeId", false)) return false
        prefs.edit().putBoolean("notified_$recipeId", true).apply()
        return true
    }
}
