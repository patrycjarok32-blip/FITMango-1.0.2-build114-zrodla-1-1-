package com.example.viewmodel

import java.text.Normalizer

/** Only unambiguous single-column rows are prefilled. Unknown values stay empty. */
object NutritionLabelSupport {
    data class Values(val calories: Float?, val protein: Float?, val carbs: Float?, val fat: Float?)
    fun parse(text: String): Values {
        val lines = Normalizer.normalize(text.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lines()
        fun row(label: String, unit: String): Float? {
            val found = lines.filter { Regex(label).containsMatchIn(it) }.flatMap { line ->
                Regex("(?<![\\d.,])([0-9]+(?:[.,][0-9]+)?)\\s*$unit\\b").findAll(line)
                    .mapNotNull { it.groupValues[1].replace(',', '.').toFloatOrNull() }.toList()
            }
            return found.singleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        }
        return Values(row("energia|energetycz|energy", "kcal"), row("bialko|protein", "g"),
            row("weglowodan|carbohydrate", "g"), row("^\\s*(?:tluszcz|fat|total fat)\\b", "g"))
    }
    fun product(name: String, calories: String, protein: String, carbs: String, fat: String,
        basis: String, unit: String): SubstituteCandidate? {
        fun number(value: String) = value.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 }
        val base = number(basis)?.takeIf { it > 0 && it <= 10000 } ?: return null
        val c = number(calories) ?: return null
        val p = number(protein) ?: return null
        val w = number(carbs) ?: return null
        val f = number(fat) ?: return null
        val ratio = 100f / base
        if (name.isBlank() || unit !in listOf("g", "ml") || c * ratio > 1000 || listOf(p, w, f).any { it * ratio > 100 }) return null
        return SubstituteCandidate(name = name.trim().take(120), caloriesPer100g = c * ratio,
            proteinPer100g = p * ratio, carbsPer100g = w * ratio, fatPer100g = f * ratio,
            defaultUnit = unit, source = SubstituteOptionMode.LOCAL_DATABASE)
    }
}
