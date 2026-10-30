package com.example.viewmodel

import android.content.Context
import com.example.data.UserProfileEntity
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class EnergyProfileSettings(val detailed: Boolean = true, val work: String = "desk", val averageSteps: Int? = null,
    val trainingType: String = "strength", val sessions: Int = 0, val minutes: Int = 45,
    val intensity: String = "moderate", val trainingStepsIncluded: Boolean = true,
    val deficitPercent: Int = 15, val surplusPercent: Int = 5, val proteinPerKg: Double = 1.6,
    val individualPlan: Boolean = false)
data class EnergyBase(val basal: Double, val pal: Double, val maintenance: Double, val explanation: String)
data class EnergyPlan(val base: EnergyBase, val calories: Int, val adjustment: Int, val protein: Int, val carbs: Int, val fat: Int)

/** Initial estimate, not a metabolic measurement. Detailed PAL selection is an explicit app heuristic.
 * Work/steps describe overlapping daily movement (max, not sum). Step-based training is not counted twice.
 * No watch calories, sleep score, or food target is added to maintenance.
 * Background: https://www.fao.org/4/y5686e/y5686e07.htm ; dynamic review uses EffectsSupport, not a fixed kg forecast. */
object EnergyCalculatorSupport {
    const val KEY = "energy_profile_v1"
    val works = linkedMapOf("desk" to "Głównie siedzę", "mixed" to "Siedzenie i chodzenie", "standing" to "Dużo stoję / chodzę", "physical" to "Praca fizyczna")
    val types = linkedMapOf("strength" to "Siłowy", "walkrun" to "Marsz / bieg", "cycle" to "Rower / pływanie", "mixed" to "Mieszany")
    val intensities = linkedMapOf("light" to "Lekka", "moderate" to "Umiarkowana", "vigorous" to "Wysoka")
    fun validate(s: EnergyProfileSettings) {
        require(s.work in works && s.trainingType in types && s.intensity in intensities)
        require(s.averageSteps == null || s.averageSteps in 0..60000)
        require(s.sessions in 0..14 && s.minutes in 1..240)
        require(s.deficitPercent in 5..20 && s.surplusPercent in 2..10)
        require(s.proteinPerKg.isFinite() && s.proteinPerKg in 1.2..2.0)
    }
    fun encode(s: EnergyProfileSettings): String { validate(s); return JSONObject()
        .put("detailed", s.detailed).put("work", s.work).put("steps", s.averageSteps ?: JSONObject.NULL)
        .put("type", s.trainingType).put("sessions", s.sessions).put("minutes", s.minutes).put("intensity", s.intensity)
        .put("stepsIncluded", s.trainingStepsIncluded).put("deficit", s.deficitPercent).put("surplus", s.surplusPercent)
        .put("protein", s.proteinPerKg).put("individual", s.individualPlan).toString() }
    fun decode(raw: String): EnergyProfileSettings {
        val j = JSONObject(raw)
        return EnergyProfileSettings(j.getBoolean("detailed"), j.getString("work"), if (j.isNull("steps")) null else j.getInt("steps"),
            j.getString("type"), j.getInt("sessions"), j.getInt("minutes"), j.getString("intensity"), j.getBoolean("stepsIncluded"),
            j.getInt("deficit"), j.getInt("surplus"), j.getDouble("protein"), j.getBoolean("individual")).also(::validate)
    }
    fun read(context: Context): EnergyProfileSettings? = context.getSharedPreferences("FITMangoUsability", 0).getString(KEY, null)?.let(::decode)
    fun base(profile: UserProfileEntity?, settings: EnergyProfileSettings? = null): EnergyBase? {
        val p = profile ?: return null
        settings?.let(::validate)
        if (settings?.individualPlan == true || p.age !in 18..120 || !p.currentWeightKg.isFinite() || p.currentWeightKg !in 30f..400f ||
            !p.heightCm.isFinite() || p.heightCm !in 100f..250f) return null
        val sex = when (p.gender) { "Mężczyzna" -> 5; "Kobieta" -> -161; else -> return null }
        val basal = 10 * p.currentWeightKg + 6.25 * p.heightCm - 5 * p.age + sex
        val s = settings
        val pal: Double
        val explanation: String
        if (s?.detailed == true) {
            val workBand = when (s.work) { "mixed" -> 1; "standing" -> 2; "physical" -> 4; else -> 0 }
            val stepBand = when { s.averageSteps == null -> 0; s.averageSteps < 4000 -> 0; s.averageSteps < 7000 -> 1; s.averageSteps < 10000 -> 2; s.averageSteps < 14000 -> 3; else -> 4 }
            val weekly = s.sessions * s.minutes * when (s.intensity) { "light" -> .75; "vigorous" -> 1.25; else -> 1.0 }
            // Mixed training is treated conservatively as overlapping when the user includes it in steps.
            val overlaps = s.averageSteps != null && s.trainingStepsIncluded && s.trainingType in listOf("walkrun", "mixed")
            val trainingBand = if (overlaps) 0.0 else when { weekly == 0.0 -> 0.0; weekly < 90 -> .5; weekly < 180 -> 1.0; weekly < 300 -> 1.5; else -> 2.0 }
            pal = (1.2 + max(workBand, stepBand) * .1 + trainingBand * .1).coerceAtMost(1.9)
            explanation = "Mifflin–St Jeor × PAL ${String.format(java.util.Locale.getDefault(), "%.2f", pal)}. " +
                "PAL dobrany orientacyjnie z pracy, kroków i treningów; praca i kroki nie są sumowane. " +
                if (overlaps && s.sessions > 0) "Trening krokowy jest już w krokach — bez dodatkowego zwiększenia PAL." else "Trening uwzględniony w PAL — kalorii zegarka nie dodajemy."
        } else {
            pal = when (p.activityLevel) { "Niska aktywność" -> 1.2; "Umiarkowana aktywność" -> 1.375; "Wysoka aktywność" -> 1.725; "Bardzo wysoka aktywność" -> 1.9; else -> return null }
            explanation = "Mifflin–St Jeor × dotychczasowa aktywność (${p.activityLevel}). Kalorii zegarka nie dodajemy drugi raz."
        }
        return EnergyBase(basal, pal, basal * pal, explanation)
    }
    fun plan(profile: UserProfileEntity, settings: EnergyProfileSettings): EnergyPlan {
        validate(settings)
        val b = requireNotNull(base(profile, settings)) { "Sprawdź wiek, wzrost, wagę i płeć. Indywidualny plan wymaga ręcznych celów." }
        require(profile.goal in listOf("Redukcja", "Utrzymanie", "Budowanie masy"))
        val bmi = profile.currentWeightKg / (profile.heightCm / 100.0).let { it * it }
        require(profile.goal != "Redukcja" || bmi >= 18.5) { "Automatyczna redukcja jest niedostępna dla tych danych. Ustal cel indywidualnie." }
        val rate = when (profile.goal) { "Redukcja" -> -settings.deficitPercent; "Budowanie masy" -> settings.surplusPercent; else -> 0 }
        val target = (b.maintenance * (1 + rate / 100.0)).roundToInt()
        require(target in 1500..15000) { "Wynik jest poza zakresem automatycznego kalkulatora (1500–15000 kcal). To ograniczenie aplikacji; użyj indywidualnie ustalonych celów." }
        val protein = min(profile.currentWeightKg * settings.proteinPerKg, target * .30 / 4).roundToInt()
        val fat = (target * .25 / 9).roundToInt()
        val carbs = (target - protein * 4 - fat * 9) / 4
        require(carbs >= 0)
        return EnergyPlan(b, target, target - b.maintenance.roundToInt(), protein, carbs, fat)
    }
}
