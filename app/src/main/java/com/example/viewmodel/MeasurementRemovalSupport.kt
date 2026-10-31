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

    /**
     * Strict identifier handling.
     *
     * Valid formats:
     * - legacy: recordId
     * - package-scoped: package|recordId
     * - metric-scoped: metric|package|recordId
     *
     * No plain alias is generated for package-scoped measurements.
     */
    fun hiddenIdsForMeasurement(recordId: String?, sourcePackageName: String? = null): Set<String> {
        val raw = recordId?.trim().orEmpty()
        if (raw.isEmpty()) return emptySet()

        val parts = raw.split("|")
        return when (parts.size) {
            1 -> {
                val legacy = raw.trim()
                if (legacy.isEmpty()) emptySet() else setOf(legacy)
            }
            2 -> {
                val pkg = parts[0].trim()
                val rec = parts[1].trim()
                if (pkg.isEmpty() || rec.isEmpty()) return emptySet()
                if (!sourcePackageName.isNullOrBlank() && sourcePackageName != pkg) return emptySet()
                setOf("$pkg|$rec")
            }
            3 -> {
                val metric = parts[0].trim()
                val pkg = parts[1].trim()
                val rec = parts[2].trim()
                if (metric.isEmpty() || pkg.isEmpty() || rec.isEmpty()) return emptySet()
                if (!sourcePackageName.isNullOrBlank() && sourcePackageName != pkg) return emptySet()
                setOf("$metric|$pkg|$rec")
            }
            else -> emptySet()
        }
    }

    fun hiddenScale(row: ScaleMeasurement, ids: Set<String>): Boolean {
        if (row.origin != "health" || "weight" !in row.values) return false

        val rowId = row.id.trim()
        if (rowId.isEmpty()) return false

        val normalizedRowId = HealthConnectSupport.normalizeMeasurementId(rowId)
        if (normalizedRowId.isEmpty()) return false

        val hiddenNormalized = ids.mapNotNull { raw ->
            val id = raw.trim()
            if (id.isEmpty()) null else HealthConnectSupport.normalizeMeasurementId(id)
        }.filter { it.isNotEmpty() }.toSet()

        return normalizedRowId in hiddenNormalized
    }
}
