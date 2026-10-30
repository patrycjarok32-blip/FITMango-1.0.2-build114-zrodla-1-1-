package com.example.viewmodel

import android.content.Context
import com.example.data.BodyMeasurementEntity
import com.example.data.WeightLogEntity
import java.time.LocalDate

object MeasurementRemovalSupport {
    const val KEY = "deleted_health_measurements_v1"
    fun hidden(c: Context): Set<String> = c.getSharedPreferences("FITMangoUsability", 0).getStringSet(KEY, emptySet()).orEmpty().toSet()
    fun validate(ids: Set<String>) { require(ids.size <= 5000 && ids.all { it.length in 1..500 }) }
    fun write(c: Context, ids: Set<String>) { validate(ids); check(c.getSharedPreferences("FITMangoUsability", 0).edit().putStringSet(KEY, ids).commit()) }
    fun date(raw: String): String = runCatching { LocalDate.parse(raw).toString() }.getOrElse {
        runCatching { LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")).toString() }.getOrDefault(raw)
    }
    fun linkedLogs(row: BodyMeasurementEntity, remaining: List<BodyMeasurementEntity>, logs: List<WeightLogEntity>): List<WeightLogEntity> {
        val day = date(row.date)
        if (remaining.any { date(it.date) == day && kotlin.math.abs(it.weightKg - row.weightKg) < .001f }) return emptyList()
        return logs.filter { date(it.date) == day && kotlin.math.abs(it.weightKg - row.weightKg) < .001f }
    }
    fun hiddenScale(row: ScaleMeasurement, ids: Set<String>) = row.origin == "health" && "weight" in row.values && ids.any { row.id.endsWith("|$it") }
}
