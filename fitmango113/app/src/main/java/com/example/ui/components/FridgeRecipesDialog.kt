package com.example.ui.components

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
    var matches by remember { mutableStateOf(emptyList<FridgeRecipeMatch>()) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var settledInput by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recipes, contents) {
        settledInput = null
        delay(250)
        val result = withContext(Dispatchers.Default) {
            FridgeRecipeSupport.find(recipes, contents) to FridgeRecipeSupport.suggestions(recipes, contents)
        }
        matches = result.first
        suggestions = result.second
        settledInput = contents
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().testTag("fridge_contents_input"))
                    Text("Oddziel produkty przecinkami. Wybierz podpowiedź, aby dodać dokładną nazwę składnika.",
                        style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                    if (settledInput == contents) {
                        suggestions.forEach { name ->
                            SuggestionChip(onClick = { onContentsChange(FridgeRecipeSupport.completeSuggestion(contents, name)) },
                                label = { Text(name) }, modifier = Modifier.fillMaxWidth())
                        }
                        if (contents.trim().length >= 2 && matches.isEmpty() && suggestions.isEmpty()) {
                            Text("Brak dopasowań. Spróbuj innej nazwy produktu.", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (matches.isNotEmpty()) Text("Pasujące przepisy: ${matches.size}. Sprawdź ilości składników.",
                            style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                        matches.take(20).forEach { match ->
                            OutlinedCard(onClick = { onRecipeClick(match.recipe) }, modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MutedSurface1)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(match.recipe.title, fontWeight = FontWeight.SemiBold)
                                    Text("Masz: ${match.available.joinToString(", ")}", color = LeafGreen, style = MaterialTheme.typography.bodySmall)
                                    Text(if (match.missing.isEmpty()) "Masz wszystkie składniki — sprawdź ilości." else "Brakuje: ${match.missing.joinToString(", ")}",
                                        color = if (match.missing.isEmpty()) LeafGreen else OrangeInk, style = MaterialTheme.typography.bodySmall)
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
