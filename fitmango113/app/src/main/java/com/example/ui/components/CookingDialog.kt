package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.RecipeEntity
import com.example.viewmodel.*
import com.example.ui.theme.*

@Composable
@OptIn(ExperimentalAnimationApi::class)
fun CookingDialog(recipe: RecipeEntity,
    ingredients: List<CookingIngredient> = CookingIngredientsSupport.fromRecipe(recipe),
    sessionKey: String = "recipe_${recipe.id}", portionLabel: String = "Ilości dla całego przepisu", initialServings: Float = 1f, onDismiss: () -> Unit) {
    var servingsInput by rememberSaveable(sessionKey) { mutableStateOf(MealPlannerSupport.formatQuantityNumber(initialServings)) }
    val servings = servingsInput.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it in 0.1f..100f }
    val cookingItems = CookingIngredientsSupport.scaled(ingredients, (servings ?: initialServings) / MealPlannerSupport.safeServings(initialServings))
    val steps = remember(recipe.instructions) { CookingSupport.steps(recipe.instructions) }
    var finished by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var step by rememberSaveable(sessionKey) { mutableIntStateOf(0) }
    var checkedIds by rememberSaveable(sessionKey, ingredients) { mutableStateOf("") }
    var showAll by rememberSaveable(sessionKey) { mutableStateOf(false) }
    val checked = checkedIds.split('|').filter { it.isNotEmpty() }.toSet()
    val view = LocalView.current
    DisposableEffect(view) { val old = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = old } }
    LaunchedEffect(servings) { checkedIds = "" }
    LaunchedEffect(recipe.instructions) { step = step.coerceIn(0, (steps.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(step) { showAll = false }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = DarkBackground) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tryb gotowania", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Zamknij") }
                }
                Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                if (finished) {
                    Spacer(Modifier.weight(1f))
                    MangoGuide("Gotowe. Smacznego!", event = 1, size = 72.dp)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("cooking_finished_close")) { Text("Zamknij gotowanie") }
                    Spacer(Modifier.weight(1f))
                } else {
                OutlinedTextField(servingsInput, { servingsInput = it.take(8) }, label = { Text("Gotuję porcji") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    isError = servings == null, modifier = Modifier.fillMaxWidth().testTag("cooking_servings"))
                Text("Ilości zmieniają się tylko w trybie gotowania.", style = MaterialTheme.typography.bodySmall)
                if (steps.isEmpty()) Text("Ten przepis nie ma zapisanych kroków. Uzupełnij instrukcję w szczegółach przepisu.")
                else {
                    Text("Krok ${step + 1} z ${steps.size}", color = MangoYellow)
                    MangoProgressBar((step + 1f) / steps.size, color = ActivityAccent, modifier = Modifier.testTag("cooking_step_progress"))
                    val duration = if (LocalReducedMangoMotion.current) 0 else 200
                    AnimatedContent(targetState = step, modifier = Modifier.weight(1f), label = "cooking_step_transition",
                        transitionSpec = {
                            val direction = if (targetState >= initialState) 1 else -1
                            (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { direction * it / 12 }) togetherWith
                                (fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) { -direction * it / 12 })
                        }) { displayedStep ->
                    val stepScroll = rememberScrollState()
                    val instruction = steps[displayedStep.coerceIn(0, steps.lastIndex)]
                    val matched = CookingIngredientsSupport.forStep(instruction, cookingItems)
                    Column(Modifier.fillMaxSize().verticalScroll(stepScroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(instruction, fontSize = 24.sp, lineHeight = 34.sp, modifier = Modifier.testTag("cooking_step"))
                        HorizontalDivider()
                        Text("Składniki do tego kroku", style = MaterialTheme.typography.titleMedium)
                        Text("${portionLabel} • przygotowujesz: ${servingsInput} porcji", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                        if (ingredients.isEmpty()) Text("Brak zapisanej listy składników w przepisie.")
                        else if (matched.isEmpty() && !showAll) Text("Krok nie wymienia składników po nazwie. Otwórz pełną listę poniżej.")
                        (if (showAll) cookingItems else matched).forEach { ingredient ->
                            val key = "$displayedStep:${ingredient.id}"; val done = key in checked
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(done, { value -> checkedIds = (if (value) checked + key else checked - key).joinToString("|") }, enabled = displayedStep == step, modifier = Modifier.testTag("cooking_check_${ingredient.id}"))
                                Text(CookingIngredientsSupport.displayForStep(instruction, ingredient), Modifier.weight(1f),
                                    color = if (done) GoldAccent else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None)
                            }
                        }
                        if (matched.isNotEmpty()) {
                            val keys = matched.map { "$displayedStep:${it.id}" }.toSet(); val allDone = checked.containsAll(keys)
                            OutlinedButton(enabled = displayedStep == step, onClick = { checkedIds = (if (allDone) checked - keys else checked + keys).joinToString("|") }, modifier = Modifier.testTag("cooking_complete_step")) {
                                Text(if (allDone) "Cofnij wykonanie kroku" else "Krok wykonany ✓")
                            }
                        }
                        if (ingredients.isNotEmpty()) TextButton(enabled = displayedStep == step, onClick = { showAll = !showAll }, modifier = Modifier.testTag("cooking_all_ingredients")) { Text(if (showAll) "Tylko składniki tego kroku" else "Pokaż wszystkie składniki") }
                    }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = step > 0, onClick = { step-- }, modifier = Modifier.weight(1f).pressScaleEffect().testTag("cooking_previous")) { Text("Wstecz") }
                        Button(enabled = servings != null, onClick = { if (step < steps.lastIndex) step++ else finished = true }, modifier = Modifier.weight(1f).pressScaleEffect().testTag("cooking_next")) { Text(if (step < steps.lastIndex) "Następny" else "Gotowe") }
                    }
                }
                }
            }
        }
    }
}
