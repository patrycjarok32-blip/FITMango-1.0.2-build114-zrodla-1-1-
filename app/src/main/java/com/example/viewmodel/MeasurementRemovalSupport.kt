package com.example.viewmodel

import android.content.Context
import com.example.data.BodyMeasurementEntity
import com.example.data.WeightLogEntity
import java.time.LocalDate

object MeasurementRemovalSupport {
    const val KEY = "deleted_health_measurements_v1"

    fun hidden(c: Context): Set<String> = c.getSharedPreferences("FITMangoUsability", 0)
        .getStringSet(KEY, emptySet())
        .orEmpty()
        .toSet()

    fun validate(ids: Set<String>) { require(ids.size <= 5000 && ids.all { it.length in 1..500 }) }

    fun write(c: Context, ids: Set<String>) {
        validate(ids)
        check(c.getSharedPreferences("FITMangoUsability", 0).edit().putStringSet(KEY, ids).commit())
    }

    fun date(raw: String): String = runCatching { LocalDate.parse(raw).toString() }.getOrElse {
        runCatching { LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")).toString() }.getOrDefault(raw)
    }

    fun linkedLogs(row: BodyMeasurementEntity, remaining: List<BodyMeasurementEntity>, logs: List<WeightLogEntity>): List<WeightLogEntity> {
        val day = date(row.date)
        if (remaining.any { date(it.date) == day && kotlin.math.abs(it.weightKg - row.weightKg) < .001f }) return emptyList()
        return logs.filter { date(it.date) == day && kotlin.math.abs(it.weightKg - row.weightKg) < .001f }
    }

    fun hiddenIdsForMeasurement(recordId: String?, sourcePackageName: String? = null): Set<String> {
        val raw = recordId?.trim().orEmpty()
        if (raw.isEmpty()) return emptySet()
        val plain = raw.substringAfterLast("|").ifBlank { raw }
        val ids = linkedSetOf(raw, plain)
        sourcePackageName?.trim()?.takeIf { it.isNotBlank() }?.let { pkg -> ids += "$pkg|$plain" }
        return ids.filter { it.isNotBlank() }.toSet()
    }

    fun hiddenScale(row: ScaleMeasurement, ids: Set<String>): Boolean {
        if (row.origin != "health" || "weight" !in row.values) return false
        val hiddenSet = ids.flatMap { hiddenIdsForMeasurement(it) }
        val rowKey = row.id
        val rowPlain = row.id.substringAfterLast("|")
        return rowKey in hiddenSet || rowPlain in hiddenSet || hiddenSet.any { rowKey.endsWith("|$it") || rowKey == it || rowPlain == it }
    }
}
