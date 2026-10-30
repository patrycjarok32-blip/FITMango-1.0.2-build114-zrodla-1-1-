package com.example.ui.components

import androidx.compose.runtime.*
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.SubstituteCandidate

/** Keep the picker open until the database confirms the save. */
@Composable
fun MealProductAdditionDialog(
    viewModel: DietaViewModel,
    mealTitle: String,
    onDismiss: () -> Unit,
    onSave: (SubstituteCandidate, Float, String, (String?) -> Unit) -> Unit
) {
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    IngredientReplacementDialog(
        originalIngredientLine = "",
        recipeTitle = mealTitle,
        isForPlannedMeal = true,
        viewModel = viewModel,
        isAddition = true,
        additionActionLabel = "Dodaj do posiłku",
        saving = saving,
        saveError = error,
        onDismiss = { if (!saving) onDismiss() },
        onConfirmReplacement = { candidate, quantity, unit, _ ->
            if (!saving) {
                saving = true
                error = null
                onSave(candidate, quantity, unit) { result ->
                    saving = false
                    error = result
                    if (result == null) onDismiss()
                }
            }
        }
    )
}
