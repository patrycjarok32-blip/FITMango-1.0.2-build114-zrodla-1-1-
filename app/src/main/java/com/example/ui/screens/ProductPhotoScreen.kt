package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.api.OpenFoodFactsClient
import com.example.viewmodel.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPhotoScreen(viewModel: DietaViewModel, onBack: () -> Unit, onProduct: (SubstituteCandidate) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var readRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var revision by rememberSaveable { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var packagingQueries by remember { mutableStateOf<List<String>>(emptyList()) }
    var useAi by rememberSaveable { mutableStateOf(false) }
    val aiAllowed = !com.example.ai.SecureKeyStorage.localOnly()
    var remote by remember { mutableStateOf<List<SubstituteCandidate>>(emptyList()) }
    val recipes by viewModel.recipes.collectAsState()
    val ingredients by viewModel.recipeIngredients.collectAsState()
    val local = remember(searchQuery, recipes, ingredients) { ProductPhotoSupport.localMatches(searchQuery,
        recipes.mapNotNull { QuickUseSupport.product(it, ingredients) } + IngredientSubstitutionSupport.defaultLocalCatalog) }
    val photo = remember { File(File(context.cacheDir, "nutrition_labels").apply { mkdirs() }, "product-photo.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.nutrition_labels", photo) }
    val busy = readRequest != null || searching
    LaunchedEffect(readRequest) {
        val request = readRequest ?: return@LaunchedEffect
        var completed = false
        message = null; remote = emptyList(); searchQuery = ""; packagingQueries = emptyList(); query = ""
        try {
            if (useAi && aiAllowed) {
                query = withTimeout(90_000) { ProductPhotoSupport.recognize(context, android.net.Uri.parse(request)) }
                searchQuery = query; revision++
                message = "Sprawdź rozpoznaną nazwę i wybierz pasujący produkt."
            } else {
                packagingQueries = withTimeout(30_000) { ProductPhotoSupport.recognizePackaging(context, android.net.Uri.parse(request)) }
                query = packagingQueries.first()
                message = "Odczytano tekst na telefonie. Wybierz nazwę lub ją popraw, a następnie wyszukaj w bazie."
            }
            completed = true
        } catch (_: TimeoutCancellationException) {
            message = "Rozpoznanie trwało zbyt długo. Wpisz nazwę i wyszukaj produkt."; completed = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message ?: "Nie udało się rozpoznać produktu. Wpisz nazwę ręcznie."; completed = true }
        finally { if (completed) { readRequest = null; if (request == photoUri.toString()) photo.delete() } }
    }
    LaunchedEffect(revision) {
        if (searchQuery.isBlank()) return@LaunchedEffect
        searching = true; remote = emptyList()
        try {
            remote = withTimeout(30_000) { OpenFoodFactsClient.searchProductsWithFallback(searchQuery).products.orEmpty()
                .mapNotNull { IngredientSubstitutionSupport.fromOpenFoodFactsProduct(it) }.take(20) }
            if (remote.isEmpty()) message = "Brak wyników internetowych. Spróbuj krótszej nazwy lub wybierz produkt z lokalnej bazy."
        } catch (_: TimeoutCancellationException) { message = "Wyszukiwanie trwało zbyt długo. Ponów lub wybierz wynik lokalny." }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = "Nie udało się połączyć z bazą. Ponów lub wybierz wynik lokalny." }
        finally { searching = false }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if ((success || photo.length() > 0) && photo.length() > 0) readRequest = photoUri.toString()
        else { photo.delete(); message = "Nie zapisano zdjęcia. Wybierz zdjęcie z galerii." }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) readRequest = uri.toString() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp).testTag("product_photo_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("Wróć") }
            Text("Rozpoznaj produkt ze zdjęcia", style = MaterialTheme.typography.headlineSmall)
            Text("Sfotografuj nazwę na przodzie opakowania. Tekst odczytujemy lokalnie, bez Gemini i bez wysyłania zdjęcia. Możesz poprawić nazwę przed wyszukaniem w bazie.")
            if (aiAllowed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rozpoznawanie obrazu przez Gemini", modifier = Modifier.weight(1f))
                    Switch(checked = useAi, onCheckedChange = { useAi = it }, enabled = !busy)
                }
                if (useAi) Text("Zdjęcie zostanie wysłane do Gemini. Wymagany jest klucz w Profilu; koszt zależy od konta Google.")
            } else Text("Tryb lokalny: odczyt nazw z opakowania działa bez AI. Dla produktu bez napisów wpisz nazwę ręcznie.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { try { photo.delete(); camera.launch(photoUri) } catch (_: Exception) { message = "Nie można uruchomić aparatu. Wybierz galerię." } }) { Text("Aparat") }
                OutlinedButton(enabled = !busy, onClick = { gallery.launch("image/*") }) { Text("Galeria") }
            }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (readRequest != null) "Rozpoznawanie produktu…" else "Wyszukiwanie w bazie…") }
            if (packagingQueries.isNotEmpty()) {
                Text("Odczytane napisy — wybierz nazwę lub markę:")
                packagingQueries.forEach { name ->
                    OutlinedButton(enabled = !busy, onClick = { query = name }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                }
            }
            OutlinedTextField(query, { query = it.take(120) }, label = { Text("Nazwa lub marka produktu") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("product_photo_query"), enabled = readRequest == null)
            Button(enabled = !busy && query.isNotBlank(), onClick = { searchQuery = query.trim(); revision++; message = null }, modifier = Modifier.testTag("product_photo_search")) { Text("Wyszukaj w bazie") }
            message?.let { Text(it) }
            Text("Wybierz właściwy wariant. Kalorie i makro pochodzą z bazy, a nie ze zdjęcia. Sprawdź je z etykietą przed dodaniem.")
            @Composable fun results(title: String, items: List<SubstituteCandidate>) {
                if (items.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleMedium)
                items.forEach { item ->
                    OutlinedCard(onClick = { onProduct(item) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            if (item.brand.isNotBlank()) Text(item.brand)
                            Text("${item.caloriesPer100g?.let { "$it kcal" } ?: "Kalorie: brak danych"} / 100 ${item.defaultUnit}")
                            if (listOf(item.proteinPer100g, item.carbsPer100g, item.fatPer100g).any { it == null }) Text("Niepełne makro w bazie")
                            Text("Wybierz i sprawdź wartości →")
                        }
                    }
                }
            }
            results("Moje produkty i baza lokalna", local)
            results("Open Food Facts", remote)
        }
    }
}
