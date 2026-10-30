package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.UserProfileEntity
import com.example.data.WeightLogEntity
import com.example.viewmodel.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScaleMeasurementsTest {
    private val today = LocalDate.parse("2026-10-17")
    private fun manual(date: String = "2026-10-16") = ScaleMeasurementsSupport.manual(date, mapOf(
        "weight" to "61,90", "bmi" to "21.9", "fat" to "29.5", "subcutaneous" to "27.5", "visceral" to "5",
        "muscle" to "41.04", "skeletal" to "41.1", "bone" to "2.60", "lean" to "43.64", "age" to "41",
        "bmr" to "1306", "water" to "48.4", "protein" to "16.4"), today)
    @Test fun allThirteenFieldsRoundTripWithDecimalCommaAndKeepDistinctUnits() {
        val row = manual()
        assertEquals(13, row.values.size)
        assertEquals(61.9, row.values.getValue("weight"), .001)
        assertEquals(row, ScaleMeasurementsSupport.decode(ScaleMeasurementsSupport.encode(listOf(row))).single())
        assertEquals("kg", ScaleMeasurementsSupport.metrics.getValue("muscle").unit)
        assertEquals("%", ScaleMeasurementsSupport.metrics.getValue("skeletal").unit)
        assertEquals("%", ScaleMeasurementsSupport.metrics.getValue("protein").unit)
        assertEquals("", ScaleMeasurementsSupport.metrics.getValue("visceral").unit)
    }
    @Test fun unknownIsAbsentAndInvalidOrFutureInputsAreRejected() {
        val r = ScaleMeasurementsSupport.manual("2026-10-16", mapOf("fat" to "29.5", "water" to ""), today)
        assertFalse("water" in r.values)
        for (v in listOf("NaN", "Infinity", "abc", "101", "-1")) {
            assertTrue(runCatching { ScaleMeasurementsSupport.manual("2026-10-16", mapOf("fat" to v), today) }.isFailure)
        }
        assertTrue(runCatching { ScaleMeasurementsSupport.manual("2026-10-18", mapOf("weight" to "62"), today) }.isFailure)
        assertTrue(runCatching { ScaleMeasurementsSupport.manual("2026-10-16", emptyMap(), today) }.isFailure)
        assertTrue(runCatching { ScaleMeasurementsSupport.manual("2026-10-16", mapOf("weight" to "62", "lean" to "620"), today) }.isFailure)
    }
    @Test fun importIsIdempotentAndMissingTypesDoNotEraseManualMeasurements() {
        val m = manual()
        val h = ScaleMeasurement("fat|renpho|id", "2026-10-16", 1, "renpho", mapOf("fat" to 29.5), "health")
        val merged = ScaleMeasurementsSupport.merge(listOf(m), listOf(h))
        assertEquals(merged, ScaleMeasurementsSupport.merge(merged, listOf(h)))
        assertEquals(merged, ScaleMeasurementsSupport.merge(merged, emptyList()))
        assertEquals(13, merged.first { it.origin == "manual" }.values.size)
        assertEquals("renpho", merged.first { it.origin == "health" }.source)
    }
    @Test fun measurementsSurviveExistingBackupRestoreAndDoNotChangeTargets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        ScaleMeasurementsSupport.write(context, listOf(manual()))
        val backup = UsabilityPreferences.export(context)
        UsabilityPreferences.validate(backup)
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        UsabilityPreferences.restore(context, backup)
        assertEquals(13, ScaleMeasurementsSupport.read(context).single().values.size)
        val p = UserProfileEntity(targetCalories = 1800)
        val report = EffectsSupport.analyze(emptyList(), emptyList(), emptyList(), emptyList(), p, SportsData(), EffectsSettings(), today, 7)
        val text = ProgressAiContext.build(p, emptyList(), emptyList(), SportsData(), emptyList(), null, report, today, 7,
            listOf(manual("2026-10-09"), manual("2026-10-10"), manual("2026-10-16"), manual("2026-10-17")))
        val root = JSONObject(text.substringAfter("\n"))
        assertEquals(2, root.getJSONArray("sklad_ciala_z_wagi").length())
        assertEquals(1800, root.getJSONObject("profil").getInt("cel_jedzenia_kcal"))
        assertTrue(root.getString("sklad_ciala_zastrzezenia").contains("nie jest spożytym białkiem"))
    }
    @Test fun additionalScaleWeightsFillMissingDaysWithoutOverridingExistingHistory() {
        val existing = listOf(WeightLogEntity(id = 1, date = "16.10.2026", weightKg = 62f))
        val result = ScaleMeasurementsSupport.withScaleWeights(existing, listOf(manual("2026-10-16"), manual("2026-10-15")))
        assertEquals(2, result.size)
        assertEquals(62f, result.first().weightKg)
        assertEquals("2026-10-15", result.last().date)
    }
}
