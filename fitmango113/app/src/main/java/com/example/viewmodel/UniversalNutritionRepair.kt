package com.example.viewmodel

import com.example.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

/** Scope is all active ingredients, not a fixed vocabulary or only planned recipes. */
data class NutritionRepairTarget(val name: String, val barcode: String = "", val basis: String = "g",
    val occurrences: Int = 1, val quantityMissing: Boolean = false) {
    val key get() = UniversalNutritionRepair.identity(name) + "|" + barcode.trim() + "|" + basis
}
data class NutritionRepairIssue(val target: NutritionRepairTarget, val reason: String, val candidates: List<SubstituteCandidate> = emptyList())
data class NutritionRepairProgress(val running: Boolean = false, val processed: Int = 0, val total: Int = 0,
    val saved: Int = 0, val current: String = "", val issues: List<NutritionRepairIssue> = emptyList(), val cancelled: Boolean = false, val message: String = "")
data class NutritionRepairSelection(val candidate: SubstituteCandidate? = null, val reason: String = "", val alternatives: List<SubstituteCandidate> = emptyList())

object UniversalNutritionRepair {
    fun basis(unit: String): String = when (unit.trim().lowercase(Locale.ROOT)) {
        "ml", "l", "litr", "litry", "litrow", "litrów" -> "ml"
        else -> "g" // Count measures still require an explicit or separately labelled estimated mass.
    }
    fun known(value: Float?) = value != null && value.isFinite() && value >= 0f && value <= 10000f
    fun missing(candidate: SubstituteCandidate) = values(candidate).any { !known(it) }
    fun values(candidate: SubstituteCandidate) = listOf(candidate.caloriesPer100g, candidate.proteinPer100g, candidate.carbsPer100g, candidate.fatPer100g)
    fun usable(candidate: SubstituteCandidate): Boolean {
        if (candidate.name.isBlank() || candidate.defaultUnit !in listOf("g", "ml")) return false
        if (candidate.caloriesPer100g?.let { it.isFinite() && it in 0f..1000f } != true) return false
        return listOf(candidate.proteinPer100g, candidate.carbsPer100g, candidate.fatPer100g).all { it == null || it.isFinite() && it in 0f..200f }
    }
    fun identity(name: String): String = ProduceSupport.normalize(name).replace(Regex("[^a-z0-9]+"), " ").trim()
    fun sameName(a: String, b: String) = identity(a).isNotBlank() && identity(a) == identity(b)
    fun matches(target: NutritionRepairTarget, candidate: SubstituteCandidate): Boolean =
        candidate.defaultUnit == target.basis && (if (target.barcode.isNotBlank()) candidate.barcode.trim() == target.barcode.trim()
        else sameName(target.name, candidate.name) || (candidate.brand.isNotBlank() &&
            (sameName(target.name, "${candidate.brand} ${candidate.name}") || sameName(target.name, "${candidate.name} ${candidate.brand}"))))
    fun collect(recipes: List<RecipeEntity>, meals: List<MealPlanEntity>, rows: List<RecipeIngredientEntity>): List<NutritionRepairTarget> {
        val targets = mutableListOf<NutritionRepairTarget>()
        for (recipe in recipes) {
            val structured = rows.filter { it.recipeId == recipe.id }
            MealPlannerSupport.parseRecipeIngredients(recipe.ingredients, structured).forEach { item ->
                if (IngredientSubstitutionSupport.isIgnoredSeasoning(item.name)) return@forEach
                val exactRows = structured.filter { sameName(it.foodName, item.name) || ImportedIngredientReferences.sameFood(it.foodName, item.name) }
                val p = IngredientSubstitutionSupport.lookupKnownIngredientNutrition(item.name, exactRows)
                if (p != null && listOf(p.caloriesPer100g, p.proteinPer100g, p.carbsPer100g, p.fatPer100g).all { known(it) }) return@forEach
                val amount = item.baseQuantity?.let { IngredientSubstitutionSupport.quantityInGramsOrMl(item.name, it, item.unit, structured) }
                targets += NutritionRepairTarget(item.name, basis = basis(item.unit), quantityMissing = amount == null || !amount.isFinite() || amount <= 0f)
            }
        }
        for (meal in meals) {
            val recipe = recipes.find { it.id == meal.recipeId }
            IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal, rows).forEach { item ->
                val sub = item.substitution ?: return@forEach
                if (IngredientSubstitutionSupport.isIgnoredSeasoning(item.ingredientName)) return@forEach
                if (listOf(sub.caloriesPer100g, sub.proteinPer100g, sub.carbsPer100g, sub.fatPer100g).all { known(it) }) return@forEach
                targets += NutritionRepairTarget(item.ingredientName, sub.barcode, basis(sub.unit), quantityMissing = item.quantityForMeal == null || item.quantityForMeal <= 0f)
            }
        }
        return targets.groupBy { it.key }.values.map { group -> group.first().copy(occurrences = group.size, quantityMissing = group.any { it.quantityMissing }) }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    /** Never replace a valid value, including a real zero, with an estimate. */
    fun merge(existing: SubstituteCandidate, incoming: SubstituteCandidate): SubstituteCandidate = existing.copy(
        caloriesPer100g = existing.caloriesPer100g.takeIf { known(it) } ?: incoming.caloriesPer100g,
        proteinPer100g = existing.proteinPer100g.takeIf { known(it) } ?: incoming.proteinPer100g,
        carbsPer100g = existing.carbsPer100g.takeIf { known(it) } ?: incoming.carbsPer100g,
        fatPer100g = existing.fatPer100g.takeIf { known(it) } ?: incoming.fatPer100g)
    fun select(target: NutritionRepairTarget, candidates: List<SubstituteCandidate>): NutritionRepairSelection {
        val exact = candidates.filter { usable(it) && matches(target, it) }.distinctBy { listOf(it.barcode, it.defaultUnit, values(it)) }
        if (exact.isEmpty()) return NutritionRepairSelection(reason = "Brak pewnego dopasowania z kaloriami na 100 ${target.basis}.")
        // Different brands or records with conflicting nutrients are not interchangeable.
        val conflict = (0..3).any { index -> exact.mapNotNull { values(it)[index] }.distinct().size > 1 }
        if (conflict) return NutritionRepairSelection(reason = "Kilka dopasowań ma różne wartości. Wybierz właściwy produkt.", alternatives = exact)
        return NutritionRepairSelection(candidate = exact.maxBy { values(it).count { value -> known(value) } })
    }
    fun localCandidates(rows: List<RecipeIngredientEntity>): List<SubstituteCandidate> = rows.filter { it.recipeId >= 0L }.map { row ->
        SubstituteCandidate(row.foodName, brand = "Zapisane dane produktu", caloriesPer100g = row.caloriesPer100g.takeIf { known(it) },
            proteinPer100g = row.proteinPer100g.takeIf { known(it) }, carbsPer100g = row.carbsPer100g.takeIf { known(it) },
            fatPer100g = row.fatPer100g.takeIf { known(it) }, defaultUnit = basis(row.unit), source = SubstituteOptionMode.LOCAL_DATABASE)
    }
}

/** Injected IO makes all products, partial results, cancellation and network failures testable. */
class UniversalNutritionRepairRunner {
    suspend fun run(targets: List<NutritionRepairTarget>, local: List<SubstituteCandidate>, online: Boolean,
        fetch: suspend (NutritionRepairTarget) -> List<SubstituteCandidate>,
        save: suspend (NutritionRepairTarget, SubstituteCandidate) -> NutritionRepairSaveResult,
        onProgress: (NutritionRepairProgress) -> Unit): NutritionRepairProgress {
        var state = NutritionRepairProgress(running = true, total = targets.size)
        val fetched = mutableMapOf<String, Result<List<SubstituteCandidate>>>()
        var networkFailures = 0
        onProgress(state)
        for (target in targets) {
            currentCoroutineContext().ensureActive()
            state = state.copy(current = target.name); onProgress(state)
            var selection = UniversalNutritionRepair.select(target, local)
            var reason = selection.reason
            if ((selection.candidate == null || selection.candidate?.let { UniversalNutritionRepair.missing(it) } == true) && online && networkFailures < 3) {
                if (target.name.length !in 1..180 || target.barcode.isNotBlank() && !target.barcode.matches(Regex("[0-9]{8,14}"))) {
                    reason = "Nieprawidłowa nazwa lub kod produktu. Popraw dane przed wyszukiwaniem."
                } else {
                    val key = target.barcode.ifBlank { UniversalNutritionRepair.identity(target.name) }
                    val result = fetched[key] ?: (try {
                        Result.success(fetch(target)).also { networkFailures = 0 }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        networkFailures++
                        Result.failure<List<SubstituteCandidate>>(IllegalStateException("Nie udało się połączyć z bazą. Możesz ponowić pozostałe braki."))
                    }).also { fetched[key] = it }
                    if (result.isSuccess) {
                        selection = UniversalNutritionRepair.select(target, local + result.getOrThrow())
                        reason = selection.reason
                    } else reason = result.exceptionOrNull()?.message.orEmpty()
                }
            } else if (selection.candidate == null && online && networkFailures >= 3) reason = "Po błędach połączenia nie wyszukano tego produktu online. Lokalne dane sprawdzono; ponów po odzyskaniu połączenia."
            else if (selection.candidate == null && !online && selection.alternatives.isEmpty()) reason = "Brak jednoznacznych zapisanych danych. Włącz wyszukiwanie w Open Food Facts lub uzupełnij etykietę."
            val candidate = selection.candidate
            var saved = false
            if (candidate != null) {
                try {
                    val persisted = save(target, candidate)
                    saved = persisted.changed
                    reason = if (!saved) "Dane zmieniły się albo znaleziony produkt nie uzupełnia pozostałych braków." else if (!persisted.complete) "Kalorie zapisane. Baza nie zawiera pełnego makro — pozostałych pól nie zastąpiono zerami." else ""
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { reason = "Nie udało się zapisać danych tego produktu. Pozostałe produkty są nadal przetwarzane." }
            }
            if (saved && target.quantityMissing) reason += (if (reason.isBlank()) "" else " ") + "Profil produktu zapisany. Ilość lub masę sprawdź w szczegółach posiłku; nie została zgadnięta."
            val issue = if (reason.isNotBlank()) NutritionRepairIssue(target, reason, selection.alternatives) else null
            state = state.copy(processed = state.processed + 1, saved = state.saved + if (saved) 1 else 0,
                issues = if (issue != null) state.issues + issue else state.issues)
            onProgress(state)
        }
        return state.copy(running = false, current = "").also(onProgress)
    }
}
