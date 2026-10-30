package com.example.viewmodel

import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock

/** Up to ten guarded reversible actions from the current session. */
class MealUndoStore(private val database: AppDatabase) {
    private val dao = database.dietaDao()
    private val mutex = PersonalDataGate.mutex
    private data class State(val meal: MealPlanEntity?, val ingredients: List<RecipeIngredientEntity>, val recipe: RecipeEntity?, val baseIngredients: List<RecipeIngredientEntity>)
    private data class Action(val id: Long, val recipeId: Long, val label: String, val before: State, val after: State)
    data class Entry(val id: String, val label: String, val time: Long)
    private val actions = linkedMapOf<String, Action>()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries = _entries.asStateFlow()
    private val _label = MutableStateFlow<String?>(null)
    val label = _label.asStateFlow()

    fun clear() { actions.clear(); _entries.value = emptyList(); _label.value = null }
    fun dismissLabel() { _label.value = null }
    private suspend fun state(id: Long, recipeId: Long) = State(dao.mealPlanById(id),
        dao.ingredientsForRecipeDirect(-id).sortedBy { it.id }, dao.recipeById(recipeId), dao.ingredientsForRecipeDirect(recipeId).sortedBy { it.id })

    suspend fun edit(id: Long, label: String, mutation: suspend () -> Unit) = mutex.withLock {
        val change = database.withTransaction {
            val current = dao.mealPlanById(id) ?: error("Posiłek już nie istnieje.")
            val before = state(id, current.recipeId)
            mutation()
            val after = state(id, current.recipeId)
            if (before == after) null else Action(id, current.recipeId, label, before, after)
        }
        if (change != null) {
            val entry = Entry(java.util.UUID.randomUUID().toString(), change.label, System.currentTimeMillis())
            actions[entry.id] = change; _entries.value = (listOf(entry) + _entries.value).take(10)
            actions.keys.retainAll(_entries.value.map { it.id }.toSet()); _label.value = change.label
        }
    }

    suspend fun undo(entryId: String? = null): Boolean = mutex.withLock {
        val id = entryId ?: _entries.value.firstOrNull()?.id ?: return@withLock false
        val saved = actions[id] ?: return@withLock false
        database.withTransaction {
            val current = state(saved.id, saved.recipeId)
            // Keep a later edit from another screen/background writer intact.
            require(current.meal == saved.after.meal && current.ingredients == saved.after.ingredients && current.baseIngredients == saved.after.baseIngredients &&
                comparable(current.recipe) == comparable(saved.after.recipe)) {
                "Posiłek lub jego przepis zmienił się ponownie. Nie można bezpiecznie cofnąć tej zmiany."
            }
            if (saved.before.recipe != saved.after.recipe) {
                val recipe = saved.before.recipe ?: error("Brak przepisu.")
                require(current.recipe?.description == saved.after.recipe?.description && current.recipe?.imageUrl == saved.after.recipe?.imageUrl) {
                    "Przepis zmienił się ponownie. Nie można bezpiecznie cofnąć tej zmiany."
                }
                dao.updateRecipe(recipe.copy(isFavorite = current.recipe?.isFavorite ?: recipe.isFavorite))
            }
            if (saved.before.baseIngredients != saved.after.baseIngredients) {
                dao.deleteIngredientsForRecipe(saved.recipeId)
                saved.before.baseIngredients.forEach { dao.insertIngredient(it) }
            }
            saved.before.meal?.let { dao.insertMealPlan(it) }
            dao.deleteIngredientsForRecipe(-saved.id)
            saved.before.ingredients.forEach { dao.insertIngredient(it) }
        }
        actions.remove(id); _entries.value = _entries.value.filterNot { it.id == id }
        _label.value = _entries.value.firstOrNull()?.label; true
    }
    private fun comparable(recipe: RecipeEntity?) = recipe?.copy(imageUrl = "", description = "", isFavorite = false)
}
