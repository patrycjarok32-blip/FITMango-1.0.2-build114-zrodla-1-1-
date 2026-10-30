package com.example.viewmodel

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.data.*

class UsabilityPreferences(context: Context) {
    companion object {
        fun validate(raw: String) {
            if (raw.isBlank()) return
            val json = org.json.JSONObject(raw)
            if (json.has(NutritionRepairSources.KEY)) NutritionRepairSources.validate(json.getString(NutritionRepairSources.KEY))
            if (json.has(NutritionAttentionSupport.KEY)) NutritionAttentionSupport.decode(json.getString(NutritionAttentionSupport.KEY))
            if (json.has(MeasurementRemovalSupport.KEY)) {
                val a = json.getJSONArray(MeasurementRemovalSupport.KEY)
                MeasurementRemovalSupport.validate((0 until a.length()).map { a.getString(it) }.toSet())
            }
            if (json.has(SportsSupport.KEY)) SportsSupport.decode(json.getString(SportsSupport.KEY))
            if (json.has(EffectsSupport.KEY)) EffectsSupport.decode(json.getString(EffectsSupport.KEY))
            if (json.has(ScaleMeasurementsSupport.KEY)) ScaleMeasurementsSupport.decode(json.getString(ScaleMeasurementsSupport.KEY))
            if (json.has(EnergyCalculatorSupport.KEY)) EnergyCalculatorSupport.decode(json.getString(EnergyCalculatorSupport.KEY))
        }
        fun export(context: Context): String {
            val json = org.json.JSONObject()
            context.getSharedPreferences("FITMangoUsability", Context.MODE_PRIVATE).all.forEach { (key, value) ->
                json.put(key, if (value is Set<*>) org.json.JSONArray(value.toList()) else value)
            }
            return json.toString()
        }
        fun restore(context: Context, raw: String) {
            if (raw.isBlank()) return
            validate(raw)
            val json = org.json.JSONObject(raw)
            val editor = context.getSharedPreferences("FITMangoUsability", Context.MODE_PRIVATE).edit().clear()
            json.keys().forEach { key ->
                when (val value = json.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Number -> editor.putInt(key, value.toInt())
                    is String -> editor.putString(key, value)
                    is org.json.JSONArray -> editor.putStringSet(key, (0 until value.length()).map { value.getString(it) }.toSet())
                }
            }
            editor.apply()
            NutritionAttentionSupport.load(context)
        }
    }
    private val contextForNutrition = context.applicationContext
    private val prefs = context.getSharedPreferences("FITMangoUsability", Context.MODE_PRIVATE)
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        .adapter<List<SubstituteCandidate>>(Types.newParameterizedType(List::class.java, SubstituteCandidate::class.java))
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    fun changed() { NutritionAttentionSupport.load(contextForNutrition); _revision.value++ }
    private fun rawProducts(key: String) = runCatching { adapter.fromJson(prefs.getString(key, "[]")!!) }.getOrNull().orEmpty()
    private fun products(key: String) = ProductHistorySupport.unique(rawProducts(key))
    fun recent() = products("recent")
    fun favorites() = products("favorites")
    private fun key(p: SubstituteCandidate) = ProductHistorySupport.key(p)
    private fun legacyKey(p: SubstituteCandidate) = p.barcode.ifBlank { p.name.trim().lowercase(java.util.Locale.ROOT) + "|" + p.brand.lowercase(java.util.Locale.ROOT) }
    fun isFavorite(p: SubstituteCandidate) = favorites().any { ProductHistorySupport.same(it, p) }
    fun lastPortion(p: SubstituteCandidate): Pair<Float, String>? = runCatching {
        val portions = org.json.JSONObject(prefs.getString("last_portions", "{}").orEmpty())
        val aliases = listOf(p) + (rawProducts("recent") + rawProducts("favorites")).filter { ProductHistorySupport.same(it, p) }
        aliases.flatMap { listOf(key(it), legacyKey(it)) }.distinct().firstNotNullOfOrNull { alias ->
            val row = portions.optJSONObject(alias) ?: return@firstNotNullOfOrNull null
            val amount = row.optDouble("quantity", Double.NaN).toFloat(); val unit = row.optString("unit")
            if (amount.isFinite() && amount > 0 && amount <= 100000 && unit in listOf("g", "ml")) amount to unit else null
        }
    }.getOrNull()
    init { NutritionAttentionSupport.load(context); repairProductHistory() }
    fun repairProductHistory() {
        val recent = recent(); val favorites = favorites()
        val portions = runCatching { org.json.JSONObject(prefs.getString("last_portions", "{}").orEmpty()) }.getOrDefault(org.json.JSONObject())
        for (p in recent + favorites) lastPortion(p)?.let { (q, u) ->
            portions.put(key(p), org.json.JSONObject().put("quantity", q).put("unit", u))
        }
        prefs.edit().putString("recent", adapter.toJson(recent)).putString("favorites", adapter.toJson(favorites))
            .putString("last_portions", portions.toString()).apply()
    }
    fun rememberProduct(p: SubstituteCandidate, quantity: Float? = null, unit: String? = null) {
        val oldRecent = recent(); val oldFavorites = favorites()
        val merged = ProductHistorySupport.unique(listOf(p) + oldRecent).take(30)
        val canonical = merged.first()
        val portions = runCatching { org.json.JSONObject(prefs.getString("last_portions", "{}").orEmpty()) }.getOrDefault(org.json.JSONObject())
        val oldPortion = lastPortion(p)
        val saved = if (quantity != null && quantity.isFinite() && quantity > 0 && quantity <= 100000 && unit in listOf("g", "ml")) quantity to unit!! else oldPortion
        saved?.let { (q, u) -> portions.put(key(canonical), org.json.JSONObject().put("quantity", q).put("unit", u)) }
        val favs = ProductHistorySupport.unique(oldFavorites.map { if (ProductHistorySupport.same(it, canonical)) canonical else it })
        prefs.edit().putString("recent", adapter.toJson(merged)).putString("favorites", adapter.toJson(favs))
            .putString("last_portions", portions.toString()).apply()
        changed()
    }
    fun removeRecent(p: SubstituteCandidate) {
        prefs.edit().putString("recent", adapter.toJson(recent().filterNot { ProductHistorySupport.same(it, p) })).apply(); changed()
    }
    fun toggleFavorite(p: SubstituteCandidate) {
        val list = favorites()
        prefs.edit().putString("favorites", adapter.toJson(if (isFavorite(p)) list.filterNot { ProductHistorySupport.same(it, p) } else ProductHistorySupport.unique(list + p))).apply(); changed()
    }
    val cards = linkedMapOf("calories" to "Kalorie i makro", "checkin" to "Jak się dziś czujesz", "produce" to "Warzywa i owoce", "health" to "Aktywność i zdrowie", "water" to "Napoje i kreatyna", "suggestions" to "Co mogę jeszcze zjeść", "badges" to "Wyzwania i odznaki", "history" to "Historia dni", "week" to "Podsumowanie tygodnia", "ai" to "AI Dietetyk", "tools" to "Szybkie narzędzia", "sport" to "Aktywność i regeneracja", "effects" to "Efekty i analiza")
    fun order(): List<String> {
        val stored = prefs.getString("order", "")!!.split(',').filter { it in cards }
        val list = (stored + cards.keys).distinct().toMutableList()
        if ("checkin" !in stored) { list.remove("checkin"); list.add((list.indexOf("calories") + 1).coerceIn(0, list.size), "checkin") }
        return list
    }
    fun hidden(id: String) = prefs.getStringSet("hidden", emptySet())!!.contains(id)
    fun show(id: String, show: Boolean) { val hidden = prefs.getStringSet("hidden", emptySet())!!.toMutableSet(); if (show) hidden.remove(id) else hidden.add(id); prefs.edit().putStringSet("hidden", hidden).apply(); changed() }
    fun move(id: String, delta: Int) { val list = order().toMutableList(); val i = list.indexOf(id); val j = (i + delta).coerceIn(0, list.lastIndex); if (i >= 0) { list.removeAt(i); list.add(j, id); prefs.edit().putString("order", list.joinToString(",")).apply(); changed() } }
    fun minutes(recipe: RecipeEntity): Int? = prefs.getInt("minutes_${recipe.id}", -1).takeIf { it in 0..1440 }
        ?: RecipeFilterSupport.providedMinutes(recipe)
    fun tag(id: Long, tag: String) = prefs.getBoolean("tag_${id}_$tag", false)
    fun setRecipe(id: Long, minutes: Int?, noCook: Boolean, work: Boolean) { prefs.edit().putInt("minutes_$id", minutes ?: -1).putBoolean("tag_${id}_nocook", noCook).putBoolean("tag_${id}_work", work).apply(); changed() }
}

object MealChangePreview {
    fun calculate(meal: MealPlanEntity, recipe: RecipeEntity?, rows: List<RecipeIngredientEntity>, target: String, candidate: SubstituteCandidate, amount: Float, unit: String): ScaledMealNutrition {
        val parsed = MealPlannerSupport.parseSingleIngredientLine(target)
        val name = parsed.name.ifBlank { target }
        val existing = IngredientSubstitutionSupport.getSubstitutionsForMeal(meal.id, rows)
            .find { IngredientSubstitutionSupport.normalizeIngredientKey(it.originalIngredientName) == IngredientSubstitutionSupport.normalizeIngredientKey(name) }
        val base = MealPlannerSupport.parseRecipeIngredients(recipe?.ingredients.orEmpty(), rows.filter { it.recipeId == recipe?.id })
            .find { IngredientSubstitutionSupport.normalizeIngredientKey(it.name) == IngredientSubstitutionSupport.normalizeIngredientKey(name) }
        val encoded = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(existingId = existing?.entityId ?: 0L,
            mealPlanId = meal.id, originalIngredientName = existing?.originalIngredientName ?: name,
            originalBaseQuantity = existing?.originalBaseQuantity ?: base?.baseQuantity ?: (parsed.baseQuantity ?: amount) / MealPlannerSupport.safeServings(meal.servings),
            originalUnit = existing?.originalUnit ?: base?.unit ?: unit, candidate = candidate,
            baseQuantityPerServing = amount / MealPlannerSupport.safeServings(meal.servings), unit = unit)
        return IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, rows.filterNot { existing != null && it.id == existing.entityId } + encoded)
    }
    fun line(before: ScaledMealNutrition, after: ScaledMealNutrition): String {
        fun n(value: Int?) = value?.toString() ?: "?"
        return "Posiłek: ${n(before.calories)} → ${n(after.calories)} kcal\nB: ${n(before.protein)} → ${n(after.protein)} g • W: ${n(before.carbs)} → ${n(after.carbs)} g • T: ${n(before.fat)} → ${n(after.fat)} g" + if (after.isPartial) "\nSzacunek — część składników ma niepełne dane." else ""
    }
}
