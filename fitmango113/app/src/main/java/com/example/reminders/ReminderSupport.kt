package com.example.reminders

import android.content.Context
import androidx.work.*
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

data class ReminderSettings(val water: Boolean = false, val waterTime: String = "09:00", val waterInterval: Int = 120,
    val creatine: Boolean = false, val creatineTime: String = "18:00", val day: Boolean = false, val dayTime: String = "21:00",
    val quietStart: String = "22:00", val quietEnd: String = "08:00",
    val weight: Boolean = false, val weightTime: String = "08:30", val weightDay: Int = 0,
    val activity: Boolean = false, val activityTime: String = "17:00", val activityDay: Int = 0,
    val weekly: Boolean = false, val weeklyTime: String = "19:00", val weeklyDay: Int = 1)
object ReminderSupport {
    const val WORK = "fitmango-habit-reminders"
    fun prefs(context: Context) = context.getSharedPreferences("FITMangoReminders", Context.MODE_PRIVATE)
    fun validate(s: ReminderSettings) {
        listOf(s.waterTime, s.creatineTime, s.dayTime, s.quietStart, s.quietEnd, s.weightTime, s.activityTime, s.weeklyTime).forEach { require(Regex("[0-2][0-9]:[0-5][0-9]").matches(it)); LocalTime.parse(it) }
        require(s.waterInterval in listOf(0, 120, 180, 240))
        require(s.weightDay in 0..7 && s.activityDay in 0..7 && s.weeklyDay in 1..7)
    }
    fun encode(s: ReminderSettings): String { validate(s); return JSONObject().put("water", s.water).put("waterTime", s.waterTime).put("waterInterval", s.waterInterval)
        .put("creatine", s.creatine).put("creatineTime", s.creatineTime).put("day", s.day).put("dayTime", s.dayTime)
        .put("quietStart", s.quietStart).put("quietEnd", s.quietEnd)
        .put("weight", s.weight).put("weightTime", s.weightTime).put("weightDay", s.weightDay)
        .put("activity", s.activity).put("activityTime", s.activityTime).put("activityDay", s.activityDay)
        .put("weekly", s.weekly).put("weeklyTime", s.weeklyTime).put("weeklyDay", s.weeklyDay).toString() }
    fun decode(json: String): ReminderSettings {
        if (json.isBlank()) return ReminderSettings()
        val j = JSONObject(json)
        return ReminderSettings(j.optBoolean("water"), j.optString("waterTime", "09:00"), j.optInt("waterInterval", 120),
            j.optBoolean("creatine"), j.optString("creatineTime", "18:00"), j.optBoolean("day"), j.optString("dayTime", "21:00"),
            j.optString("quietStart", "22:00"), j.optString("quietEnd", "08:00"),
            j.optBoolean("weight"), j.optString("weightTime", "08:30"), j.optInt("weightDay", 0),
            j.optBoolean("activity"), j.optString("activityTime", "17:00"), j.optInt("activityDay", 0),
            j.optBoolean("weekly"), j.optString("weeklyTime", "19:00"), j.optInt("weeklyDay", 1)).also(::validate)
    }
    fun export(context: Context) = prefs(context).getString("settings", "").orEmpty()
    fun read(context: Context) = decode(export(context))
    fun save(context: Context, s: ReminderSettings) {
        check(prefs(context).edit().putString("settings", encode(s)).commit())
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (!s.water) manager.cancel(4901)
        if (!s.creatine) manager.cancel(4902)
        if (!s.day) manager.cancel(4903)
        if (!s.weight) manager.cancel(4905)
        if (!s.activity) manager.cancel(4906)
        if (!s.weekly) manager.cancel(4907)
        schedule(context, cancelDisabled = true)
    }
    fun quiet(time: LocalTime, s: ReminderSettings): Boolean {
        val start = LocalTime.parse(s.quietStart); val end = LocalTime.parse(s.quietEnd)
        return if (start == end) false else if (start < end) time >= start && time < end else time >= start || time < end
    }
    /** Only the newest slot, never a queue of missed alerts after sleep or reboot. */
    fun due(s: ReminderSettings, now: LocalDateTime): Map<String, String> {
        validate(s)
        if (quiet(now.toLocalTime(), s)) return emptyMap()
        val result = mutableMapOf<String, String>()
        val minute = now.hour * 60 + now.minute
        fun slot(kind: String, enabled: Boolean, clock: String, interval: Int = 0) {
            if (!enabled) return
            val time = LocalTime.parse(clock); val start = time.hour * 60 + time.minute
            if (minute < start) return
            val index = if (interval == 0) 0 else (minute - start) / interval
            val age = minute - start - index * interval
            if (age < 60) result[kind] = "${now.toLocalDate()}:$index"
        }
        slot("water", s.water, s.waterTime, s.waterInterval)
        slot("creatine", s.creatine, s.creatineTime)
        slot("day", s.day, s.dayTime)
        if (s.weightDay == 0 || now.dayOfWeek.value == s.weightDay) slot("weight", s.weight, s.weightTime)
        if (s.activityDay == 0 || now.dayOfWeek.value == s.activityDay) slot("activity", s.activity, s.activityTime)
        if (now.dayOfWeek.value == s.weeklyDay) slot("weekly", s.weekly, s.weeklyTime)
        return result
    }
    fun schedule(context: Context, cancelDisabled: Boolean = false) {
        val s = read(context)
        if (!s.water && !s.creatine && !s.day && !s.weight && !s.activity && !s.weekly) {
            if (cancelDisabled && prefs(context).getBoolean("scheduled", false)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK)
                prefs(context).edit().putBoolean("scheduled", false).apply()
            }
            return
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HabitReminderWorker>(15, TimeUnit.MINUTES).build())
        prefs(context).edit().putBoolean("scheduled", true).apply()
    }
}
