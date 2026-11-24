package com.example.ui.components

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.RecipeEntity
import com.example.ui.theme.*
import com.example.viewmodel.FridgeRecipeMatch
import com.example.viewmodel.FridgeRecipeSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun FridgeRecipesDialog(recipes: List<RecipeEntity>, contents: String, onContentsChange: (String) -> Unit,
    onDismiss: () -> Unit, onRecipeClick: (RecipeEntity) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var onlyComplete by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf(emptyList<FridgeRecipeMatch>()) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var settledInput by remember { mutableStateOf<String?>(null) }
    var searchRequest by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }
    LaunchedEffect(recipes, contents, searchRequest) {
        settledInput = null
        searching = true
        searchError = false
        if (searchRequest == 0) delay(250)
        try {
        val result = withContext(Dispatchers.Default) {
            FridgeRecipeSupport.find(recipes, contents) to FridgeRecipeSupport.suggestions(recipes, contents)
        }
        matches = result.first
        suggestions = result.second
        settledInput = contents
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { matches = emptyList(); suggestions = emptyList(); searchError = true }
        finally { searching = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.95f).imePadding().heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp), color = DarkBackground) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Co masz w lodówce?", style = MaterialTheme.typography.titleLarge, color = Cream)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = contents, onValueChange = onContentsChange,
                        label = { Text("Wpisz produkty") }, placeholder = { Text("np. jajka, ryż, pomidor") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = { if (contents.isNotEmpty()) IconButton(onClick = { onContentsChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Wyczyść produkty")
                        } }, minLines = 1, maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); showResults = true; searchRequest++ }),
                        modifier = Modifier.fillMaxWidth().testTag("fridge_contents_input"))
                    Text("Oddziel produkty przecinkami. Wybierz podpowiedź, aby dodać dokładną nazwę składnika.",
                        style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                    val entered = FridgeRecipeSupport.enteredProducts(contents)
                    if (entered.isNotEmpty()) {
                        Text("Dodane produkty: ${entered.size}", style = MaterialTheme.typography.labelMedium)
                        entered.forEach { product ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(product, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { onContentsChange(FridgeRecipeSupport.removeProduct(contents, product)) }) {
                                    Text("Usuń")
                                }
                            }
                        }
                        Button(onClick = { keyboard?.hide(); showResults = true; searchRequest++ },
                            modifier = Modifier.fillMaxWidth().testTag("fridge_search_button")) {
                            Text("🔎 Znajdź przepisy")
                        }
                    }
                    if (searching && entered.isNotEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Wyszukiwanie w bazie przepisów…") }
                    if (searchError) Text("Nie udało się przeszukać bazy. Naciśnij Znajdź przepisy ponownie.", color = MaterialTheme.colorScheme.error)
                    if (recipes.isEmpty()) Text("Baza przepisów jest pusta lub jeszcze się ładuje.")
                    if (!searching && settledInput == contents && (showResults || matches.isNotEmpty() || suggestions.isNotEmpty())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = onlyComplete, onCheckedChange = { onlyComplete = it },
                                modifier = Modifier.testTag("fridge_complete_only"))
                            Text("Tylko przepisy bez brakujących składników")
                        }
                        val displayed = if (onlyComplete) matches.filter { it.missing.isEmpty() } else matches
                        Text("Znaleziono ${displayed.size} przepisów" +
                            if (onlyComplete) " z kompletem składników" else " (w tym ${matches.count { it.missing.isEmpty() }} z kompletem)",
                            style = MaterialTheme.typography.titleSmall)

                        suggestions.forEach { name ->
                            SuggestionChip(onClick = { onContentsChange(FridgeRecipeSupport.completeSuggestion(contents, name)) },
                                label = { Text(name) }, modifier = Modifier.fillMaxWidth())
                        }
                        if (contents.trim().length >= 2 && matches.isEmpty()) {
                            Text("Brak dopasowań. Spróbuj innej nazwy produktu.", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (displayed.isNotEmpty()) Text("Sprawdź ilości składników w przepisie.",
                            style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                        displayed.take(30).forEach { match ->
                            OutlinedCard(onClick = { onRecipeClick(match.recipe) }, modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MutedSurface1)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(match.recipe.title, fontWeight = FontWeight.SemiBold)
                                    Text("Masz: ${match.available.joinToString(", ")}", color = LeafGreen, style = MaterialTheme.typography.bodySmall)
                                    Text(if (match.missing.isEmpty()) "Masz wszystkie składniki — sprawdź ilości." else "Brakuje: ${match.missing.joinToString(", ")}",
                                        color = if (match.missing.isEmpty()) LeafGreen else OrangeInk, style = MaterialTheme.typography.bodySmall)
                                    if (match.missing.isNotEmpty()) {
                                        val context = androidx.compose.ui.platform.LocalContext.current
                                        TextButton(onClick = {
                                            val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                                as android.content.ClipboardManager
                                            manager.setPrimaryClip(android.content.ClipData.newPlainText(
                                                "FITMango — lista zakupów", match.missing.joinToString("\n")))
                                            android.widget.Toast.makeText(context, "Skopiowano brakujące produkty",
                                                android.widget.Toast.LENGTH_SHORT).show()
                                        }) { Text("📋 Kopiuj brakujące produkty") }
                                    }
                                    Text("${match.recipe.calories} kcal • Otwórz przepis", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Zamknij") }
            }
        }
    }
}
