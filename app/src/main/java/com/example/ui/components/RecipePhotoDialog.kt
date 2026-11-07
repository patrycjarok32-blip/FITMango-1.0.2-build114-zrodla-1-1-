package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.data.RecipeEntity
import com.example.ui.theme.*
import com.example.viewmodel.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun RecipePhotoDialog(recipe: RecipeEntity, viewModel: DietaViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val links = LocalUriHandler.current
    var usePixabay by remember { mutableStateOf(RecipePhotoSettings(context).token("pixabay").isNotBlank()) }
    var confirmReplace by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf(RecipePhotoSearch.suggestedQuery(recipe.title)) }
    var candidates by remember { mutableStateOf(emptyList<RecipePhotoCandidate>()) }
    var selected by remember { mutableStateOf<RecipePhotoCandidate?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var visualCheck by remember(recipe.id) { mutableStateOf<RecipeVisualCheck.Result?>(null) }
    var checkingImage by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            loading = true
            viewModel.setRecipePhotoFromPhone(context, recipe, uri) { success, error ->
                loading = false
                if (success) onDismiss() else message = error
            }
        }
    }
    suspend fun searchPhotos(): List<RecipePhotoCandidate> {
        val english = RecipePhotoMatching.englishSearchQuery(query)
            ?: error("Wpisz krótką nazwę potrawy po angielsku lub wybierz zdjęcie z telefonu.")
        val found = if (usePixabay) withContext(Dispatchers.IO) {
        RecipePhotoService(cacheDirectory = File(context.cacheDir, "pixabay_search_cache"))
            .searchPixabay(english, RecipePhotoSettings(context).token("pixabay"))
        } else RecipePhotoSearch.search(english)
        return found.distinctBy { it.imageUrl }.sortedByDescending { RecipePhotoMatching.score(english, it.title, recipe.ingredients) }
    }
    LaunchedEffect(recipe.id, usePixabay) {
        candidates = emptyList(); message = null; selected = null
        loading = true
        try {
            candidates = searchPhotos()
            if (candidates.isEmpty()) message = "Nie znaleziono zdjęć. Spróbuj krótszej nazwy lub nazwy po angielsku."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message ?: "Sprawdź połączenie z internetem." }
        finally { loading = false }
    }
    if (confirmReplace) AlertDialog(onDismissRequest = { confirmReplace = false },
        title = { Text("Zastąpić zapisane zdjęcie?") },
        text = { Text("Dotychczasowe zdjęcie tego przepisu zostanie zastąpione wybranym. Inne pobrane zdjęcia pozostaną bez zmian.") },
        confirmButton = { TextButton(onClick = {
            confirmReplace = false
            selected?.let { photo ->
                loading = true
                viewModel.setRecipePhoto(context, recipe, photo) { success, error ->
                    loading = false
                    if (success) onDismiss() else message = error
                }
            }
        }) { Text("Zastąp zdjęcie") } },
        dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Anuluj") } })
    AlertDialog(onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Zdjęcie potrawy", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                selected?.let { photo ->
                    RecipePhoto(photo.imageUrl, photo.title, Modifier.fillMaxWidth().height(170.dp))
                    Text("Podgląd — sprawdź, czy potrawa i główne składniki pasują. Dopiero „Zapisz zdjęcie” zatwierdzi wybór.", color = MaterialTheme.colorScheme.primary)
                    TextButton(enabled = !loading, onClick = { selected = null }) { Text("Wybierz inne") }
                }
                val imageToCheck = selected?.imageUrl ?: recipe.imageUrl
                if (imageToCheck.isNotBlank()) {
                    OutlinedButton(enabled = !checkingImage, onClick = {
                        checkingImage = true
                        visualCheck = null
                        scope.launch {
                            try {
                                visualCheck = RecipeVisualCheck.inspect(context, recipe.title, imageToCheck)
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                visualCheck = RecipeVisualCheck.Result("Nie można sprawdzić zdjęcia: ${e.message ?: "błąd odczytu"}", emptyList(), true)
                            } finally { checkingImage = false }
                        }
                    }) { Text(if (checkingImage) "Analizowanie zdjęcia…" else "Sprawdź zgodność zdjęcia (AI na urządzeniu)") }
                    visualCheck?.let { result ->
                        Text(result.summary, color = MaterialTheme.colorScheme.onSurface)
                        if (result.labels.isNotEmpty()) Text("Rozpoznano: " + result.labels.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall)
                        Text("Wynik orientacyjny. Zdjęcie nie jest automatycznie zmieniane.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = usePixabay, enabled = !loading, onClick = { usePixabay = true }, label = { Text("Pixabay") })
                    FilterChip(selected = !usePixabay, enabled = !loading, onClick = { usePixabay = false }, label = { Text("Wikimedia") })
                }
                Text(if (usePixabay) "Zdjęcia z Pixabay. Darmowy klucz dodaj w Profilu. Wybierz zdjęcie pasujące do potrawy; autor i licencja zostaną zapisane przy przepisie." else "Bezpłatne zdjęcia z Wikimedia Commons, bez klucza. Wybierz zdjęcie pasujące do potrawy.", color = MaterialTheme.colorScheme.onSurface)
                if (usePixabay) TextButton(onClick = { links.openUri("https://pixabay.com") }) { Text("Zdjęcia: Pixabay", color = MaterialTheme.colorScheme.primary) }
                OutlinedTextField(query, { query = it.take(if (usePixabay) 100 else 120) }, label = { Text("Hasło do zdjęcia") }, modifier = Modifier.fillMaxWidth())
                Text("Szukamy po angielsku. Nazwa przepisu w aplikacji pozostaje bez zmian. Zdjęcia z bazy są poglądowe.", color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !loading, onClick = {
                        keyboard?.hide()
                        loading = true; message = null; candidates = emptyList(); selected = null
                        scope.launch {
                            try {
                                candidates = searchPhotos()
                                if (candidates.isEmpty()) message = "Nie znaleziono zdjęć. Zmień nazwę potrawy."
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { message = e.message ?: "Sprawdź połączenie z internetem." }
                            finally { loading = false }
                        }
                    }) { Text("Szukaj zdjęć", color = MaterialTheme.colorScheme.primary) }
                    TextButton(enabled = !loading, onClick = {
                        keyboard?.hide()
                        loading = true; message = null
                        viewModel.refreshRecipePhotoFromSource(recipe) { success, error ->
                            loading = false
                            if (success) onDismiss() else message = error
                        }
                    }) { Text("Ze strony przepisu", color = MaterialTheme.colorScheme.primary) }
                }
                TextButton(enabled = !loading, onClick = { gallery.launch("image/*") }) { Text("Wybierz zdjęcie z telefonu", color = MaterialTheme.colorScheme.primary) }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                LazyColumn(Modifier.heightIn(max = 270.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates, key = { it.imageUrl }) { photo ->
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).clickable(enabled = !loading) {
                            keyboard?.hide()
                            selected = photo; message = null
                        }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RecipePhoto(photo.imageUrl, photo.title, Modifier.fillMaxWidth().height(120.dp))
                            Text(photo.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                            Text("${photo.author} • ${photo.license}", color = MaterialTheme.colorScheme.primary, maxLines = 2)
                            Text(if (selected?.imageUrl == photo.imageUrl) "Wybrane — zatwierdź poniżej" else "Pokaż podgląd", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            selected?.let { photo ->
                Button(enabled = !loading, onClick = {
                    loading = true; message = null
                    if (recipe.imageUrl.isNotBlank() && !confirmReplace) {
                        loading = false; confirmReplace = true
                    } else viewModel.setRecipePhoto(context, recipe, photo) { success, error ->
                        loading = false
                        if (success) onDismiss() else message = error
                    }
                }) { Text("Zapisz zdjęcie") }
            }
        },
        dismissButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text("Zamknij", color = MaterialTheme.colorScheme.onSurface) } })
}
