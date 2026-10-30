package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.callGemini
import com.example.data.AppDatabase
import com.example.data.DietaRepository
import com.example.data.DocumentEntity
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.data.RecipeIngredientEntity
import com.example.data.ShoppingItemEntity
import com.example.data.UserProfileEntity
import com.example.data.WeightLogEntity
import com.example.data.BodyMeasurementEntity
import com.example.data.BodyPhotoEntity
import com.example.api.OpenFoodFactsClient
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

sealed class PdfAnalysisState {
    object Idle : PdfAnalysisState()
    object Extracting : PdfAnalysisState()
    data class Analyzing(val current: Int, val total: Int, val foundCount: Int = 0) : PdfAnalysisState()
    data class RateLimited(val current: Int, val total: Int, val foundCount: Int = 0) : PdfAnalysisState()
    data class Completed(val recipeCount: Int) : PdfAnalysisState()
    data class Failed(val error: String, val foundCount: Int = 0) : PdfAnalysisState()
}

data class ChatMessage(val role: String, val content: String)
data class DashboardNutritionSnapshot(val date: String, val nutrition: ScaledMealNutrition)

data class ParsedRecipe(
    val title: String,
    val servings: String,
    val ingredients: String,
    val instructions: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val source: String,
    val sourcePageOrChunk: String = ""
)

class DietaViewModel(application: Application) : AndroidViewModel(application) {
    val usability = UsabilityPreferences(application)
    private val shoppingMutex = Mutex()
    private val repository: DietaRepository

    fun toggleRecipeFavorite(recipeId: Long) {
        viewModelScope.launch { repository.toggleRecipeFavorite(recipeId) }
    }

    init {
        val dao = AppDatabase.getDatabase(application).dietaDao()
        repository = DietaRepository(dao)
        viewModelScope.launch {
            repository.repairKnownPlanDates()
            repository.seedInitialData()
            repository.allRecipes.collect { items ->
                RecipePhotoQueue.enqueue(application, items)
            }
        }
    }

    val recipes: StateFlow<List<RecipeEntity>> = repository.allRecipes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recipeIngredients: StateFlow<List<RecipeIngredientEntity>> = repository.allRecipeIngredients
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val mealPlans: StateFlow<List<MealPlanEntity>> = repository.allMealPlans
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shoppingItems: StateFlow<List<ShoppingItemEntity>> = repository.allShoppingItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val weightLogs: StateFlow<List<WeightLogEntity>> = repository.allWeightLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bodyMeasurements: StateFlow<List<BodyMeasurementEntity>> = repository.allBodyMeasurements
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bodyPhotos: StateFlow<List<BodyPhotoEntity>> = repository.allBodyPhotos
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val healthConnect: HealthConnectCoordinator by lazy {
        HealthConnectCoordinator(repository, application, viewModelScope, userProfile)
    }

    val backup by lazy {
        com.example.backup.BackupCoordinator(getApplication<Application>(), viewModelScope, usability) {
            healthConnect.setAutomaticSync(false)
            refreshSavedMealSets()
            usability.changed()
            viewModelScope.launch { repository.repairKnownPlanDates() }
            clearMealUndo()
        }
    }

    private val mealUndoStore by lazy { MealUndoStore(AppDatabase.getDatabase(getApplication<Application>())) }
    val mealUndoLabel get() = mealUndoStore.label
    val mealChangeHistory get() = mealUndoStore.entries
    private val _mealStatusBusy = MutableStateFlow<Set<Long>>(emptySet())
    val mealStatusBusy = _mealStatusBusy.asStateFlow()
    data class MealMotionEvent(val mealId: Long, val date: String, val completed: Boolean?, val undo: Boolean = false)
    private val _mealMotionEvents = kotlinx.coroutines.flow.MutableSharedFlow<MealMotionEvent>(extraBufferCapacity = 8)
    val mealMotionEvents = _mealMotionEvents.asSharedFlow()
    private val _mealUndoMessage = MutableStateFlow<String?>(null)
    val mealUndoMessage = _mealUndoMessage.asStateFlow()
    private fun clearMealUndo() { mealUndoStore.clear(); MeasurementChangeHistory.clear(); _mealUndoMessage.value = null }
    fun dismissMealUndo() { mealUndoStore.dismissLabel(); _mealUndoMessage.value = null }
    fun undoLastMealChange(entryId: String? = null) = viewModelScope.launch {
        try {
            if (mealUndoStore.undo(entryId)) {
                _mealMotionEvents.emit(MealMotionEvent(0L, "", null, undo = true))
                rebuildShoppingListInternal(); _mealUndoMessage.value = "Zmiana została cofnięta."
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { _mealUndoMessage.value = e.message ?: "Nie udało się cofnąć zmiany." }
    }
    private suspend fun undoableMealEdit(id: Long, label: String, onCommitted: (() -> Unit)? = null, edit: suspend () -> Unit): Boolean {
        return try {
            mealUndoStore.edit(id, label, edit)
            onCommitted?.invoke()
            _mealUndoMessage.value = null
            try { rebuildShoppingListInternal() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _mealUndoMessage.value = "Posiłek zapisano. Nie udało się odświeżyć listy zakupów." }
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { _mealUndoMessage.value = e.message ?: "Nie udało się zapisać zmiany."; false }
    }

    // Chat AI State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage("assistant", "Cześć! Jestem Twoim osobistym asystentem i dietetykiem MangoFit. W czym mogę Ci dzisiaj pomóc?"))
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isLoadingAi = MutableStateFlow(false)
    val isLoadingAi: StateFlow<Boolean> = _isLoadingAi.asStateFlow()

    private val _mealPlanError = MutableStateFlow<String?>(null)
    val mealPlanError: StateFlow<String?> = _mealPlanError.asStateFlow()

    private val _pdfNextChunkIndex = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val pdfNextChunkIndex: StateFlow<Map<Long, Int>> = _pdfNextChunkIndex.asStateFlow()

    private val _pdfFoundRecipes = MutableStateFlow<Map<Long, List<ParsedRecipe>>>(emptyMap())
    val pdfFoundRecipes: StateFlow<Map<Long, List<ParsedRecipe>>> = _pdfFoundRecipes.asStateFlow()

    private val _pdfRawFoundRecipes = MutableStateFlow<Map<Long, List<ParsedRecipe>>>(emptyMap())
    private val _pdfChunksCache = MutableStateFlow<Map<Long, List<PdfTextChunk>>>(emptyMap())
    private val _pdfChunkRecipeCounts = MutableStateFlow<Map<Long, Map<Int, Int>>>(emptyMap())
    val pdfChunkRecipeCounts: StateFlow<Map<Long, Map<Int, Int>>> = _pdfChunkRecipeCounts.asStateFlow()

    private val _pdfAnalysisStates = MutableStateFlow<Map<Long, PdfAnalysisState>>(emptyMap())
    val pdfAnalysisStates: StateFlow<Map<Long, PdfAnalysisState>> = _pdfAnalysisStates.asStateFlow()

    fun sendChatMessage(prompt: String) {
        if (prompt.isBlank() || _isLoadingAi.value) return
        val currentList = _chatMessages.value.toMutableList()
        currentList.add(ChatMessage("user", prompt))
        _chatMessages.value = currentList
        _isLoadingAi.value = true

        viewModelScope.launch {
            try {
                // Enrich prompt if barcode scanned
                val enrichedPrompt = if (prompt.startsWith("Zeskanowano produkt o kodzie kreskowym: ")) {
                    val barcode = prompt.substringAfter("Zeskanowano produkt o kodzie kreskowym: ").substringBefore(".")
                    val offProductResult = try {
                        OpenFoodFactsClient.service.getProductByBarcode(barcode)
                    } catch (e: Exception) {
                        null
                    }
                    if (offProductResult?.status == 1 && offProductResult.product != null) {
                        val p = offProductResult.product
                        """
                        Użytkownik zeskanował produkt o kodzie $barcode za pomocą skanera.
                        Dane produktu pobrane z Open Food Facts:
                        Nazwa: ${p.productName ?: "Nieznana"}
                        Marka: ${p.brands ?: "Nieznana"}
                        Wartości odżywcze na 100g:
                        Kalorie: ${p.nutriments?.energyKcal100g ?: "Brak danych"} kcal
                        Białko: ${p.nutriments?.proteins100g ?: "Brak danych"} g
                        Węglowodany: ${p.nutriments?.carbohydrates100g ?: "Brak danych"} g
                        Tłuszcz: ${p.nutriments?.fat100g ?: "Brak danych"} g
                        
                        Przeanalizuj ten produkt jako dietetyk AI. Skomentuj jego skład, profil makroskładników, czy jest odpowiedni w diecie użytkownika oraz zaproponuj jego zdrowe i mniej przetworzone alternatywy/zamienniki (jeśli to możliwe). Odpowiedz w języku polskim.
                        """.trimIndent()
                    } else {
                        prompt
                    }
                } else {
                    prompt
                }

                val result = callGemini(enrichedPrompt, systemPrompt = "Odpowiadaj po polsku. Jeżeli podajesz przepis, podaj jeden kompletny przepis z nazwą, liczbą porcji, nagłówkiem Składniki, ilościami składników, nagłówkiem Przygotowanie i krokami. Wartości kcal/B/W/T na jedną porcję oznacz jako szacunek, jeżeli są dostępne; nie wymyślaj brakujących danych. W zwykłej rozmowie nie stosuj szablonu przepisu.")
                val response = result.fold(
                    onSuccess = { it },
                    onFailure = { err -> "Błąd: ${err.message ?: "Nie udało się uzyskać odpowiedzi od AI."}" }
                )
                currentList.add(ChatMessage("assistant", response))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                currentList.add(ChatMessage("assistant", "Błąd: ${e.message ?: "Wystąpił nieoczekiwany wyjątek."}"))
            } finally {
                _chatMessages.value = currentList
                _isLoadingAi.value = false
            }
        }
    }

    // AI Meal Plan Generator — wybiera zestaw najbliższy dziennemu celowi kalorii.
    fun generateAiMealPlan(goal: String) {
        _isLoadingAi.value = true
        _mealPlanError.value = null
        viewModelScope.launch {
            try {
                val currentGoal = userProfile.value?.goal ?: "Redukcja"
                val targetCalories = (userProfile.value?.targetCalories ?: 2000).coerceAtLeast(1)
                val prompt = """
                Ułóż jadłospis na JEDEN dzień dla użytkownika, którego cel to "$currentGoal".
                Dzienny cel energetyczny wynosi DOKŁADNIE $targetCalories kcal. To główne kryterium doboru posiłków.
                Przygotuj 5 RÓŻNYCH kandydackich zestawów całego dnia. Każdy zestaw ma zawierać dokładnie 3 posiłki:
                Śniadanie, Obiad i Kolacja. Dobieraj potrawy i ich kaloryczność tak, aby suma kcal w każdym zestawie
                była możliwie bliska $targetCalories kcal (najlepiej różnica nie większa niż 2%). Rozłóż energię
                rozsądnie między posiłki: około 25-30% na śniadanie, 35-40% na obiad i resztę na kolację.
                Kalorie muszą być wiarygodne dla podanych porcji, a nie losowe. Nie dodawaj przekąsek.

                Zwróć WYŁĄCZNIE poprawny JSON: tablicę 5 obiektów, każdy z polem "meals" zawierającym tablicę
                dokładnie 3 obiektów z polami: "mealType" (dokładnie "Śniadanie", "Obiad" lub "Kolacja"),
                "recipeTitle" (nazwa potrawy po polsku), "calories" (całkowite kcal całego posiłku).
                Bez markdown i bez dodatkowego tekstu. Wszystkie 5 zestawów muszą mieć różne potrawy.
                """.trimIndent()

                val aiResult = callGemini(prompt)
                aiResult.fold(
                    onSuccess = { responseText ->
                        val cleanJson = responseText.trim()
                            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                            .removeSuffix("```").trim()
                        try {
                            val array = JSONArray(cleanJson)
                            require(array.length() > 0) { "Brak zestawów" }
                            val candidates = (0 until array.length()).mapNotNull { candidateIndex ->
                                val candidate = array.getJSONObject(candidateIndex)
                                // Obsługa także starszego formatu: bezpośrednia tablica 3 posiłków.
                                val mealArray = candidate.optJSONArray("meals")
                                    ?: if (candidate.has("mealType")) array else null
                                if (mealArray == null || mealArray.length() != 3) return@mapNotNull null
                                try {
                                    val parsed = (0 until mealArray.length()).map { i ->
                                        val obj = mealArray.getJSONObject(i)
                                        val mealType = obj.getString("mealType")
                                        val title = obj.getString("recipeTitle").trim()
                                        val kcal = obj.getInt("calories")
                                        require(mealType in listOf("Śniadanie", "Obiad", "Kolacja") && title.isNotBlank() && kcal > 0)
                                        Triple(mealType, title, kcal)
                                    }
                                    require(parsed.map { it.first }.toSet() == setOf("Śniadanie", "Obiad", "Kolacja"))
                                    parsed
                                } catch (_: Exception) { null }
                            }
                            require(candidates.isNotEmpty()) { "Brak poprawnych zestawów" }
                            // Minimalizujemy różnicę od celu; przy remisie preferujemy zestaw nieprzekraczający celu.
                            val chosen = candidates.minWithOrNull(
                                compareBy<List<Triple<String, String, Int>>> { meals ->
                                    kotlin.math.abs(meals.sumOf { it.third } - targetCalories)
                                }.thenBy { meals -> if (meals.sumOf { it.third } <= targetCalories) 0 else 1 }
                            )!!
                            val meals = chosen.map { (mealType, title, kcal) ->
                                MealPlanEntity(dayOfWeek = goal, mealType = mealType, recipeTitle = title,
                                    calories = kcal, servings = 1f)
                            }
                            repository.insertMealPlans(meals)
                        } catch (e: Exception) {
                            _mealPlanError.value = "AI zwróciło nieprawidłowy jadłospis. Spróbuj ponownie."
                        }
                    },
                    onFailure = { err -> _mealPlanError.value = "Błąd AI: ${err.message}" }
                )
            } catch (e: Exception) {
                _mealPlanError.value = "Błąd: ${e.localizedMessage}"
            } finally {
                _isLoadingAi.value = false
            }
        }
    }

    // --- Website, Cookidoo & Instagram Recipe Import & Preview State ---
    var customHtmlDocumentFetcher: (suspend (String) -> org.jsoup.nodes.Document)? = null
    var customRecipeImportAiCaller: (suspend (String) -> Result<String>)? = null
    var geminiCallsDuringRecipeImport: Int = 0

    private val _importedRecipePreview = MutableStateFlow<ImportedRecipe?>(null)
    val importedRecipePreview: StateFlow<ImportedRecipe?> = _importedRecipePreview.asStateFlow()

    private val _importFallbackPromptMode = MutableStateFlow<String?>(null)
    val importFallbackPromptMode: StateFlow<String?> = _importFallbackPromptMode.asStateFlow()

    private val _pendingSharedRecipeText = MutableStateFlow<String?>(null)
    val pendingSharedRecipeText: StateFlow<String?> = _pendingSharedRecipeText.asStateFlow()

    fun handleIncomingSharedRecipeText(sharedText: String?) {
        val cleaned = sharedText?.trim()?.takeIf { it.isNotBlank() } ?: return
        _pendingSharedRecipeText.value = cleaned
    }

    fun consumePendingSharedRecipeText() {
        _pendingSharedRecipeText.value = null
    }

    fun clearImportFallbackMode() {
        _importFallbackPromptMode.value = null
    }

    fun cancelRecipePreview() {
        _importedRecipePreview.value = null
    }

    fun updateRecipePreview(updated: ImportedRecipe) {
        _importedRecipePreview.value = updated
    }

    private val _recipeSaveInProgress = MutableStateFlow(false)
    val recipeSaveInProgress: StateFlow<Boolean> = _recipeSaveInProgress.asStateFlow()

    fun confirmSavePreviewedRecipe(
        editedRecipe: ImportedRecipe? = null,
        onSaved: ((RecipeEntity) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (_recipeSaveInProgress.value) return
        val input = editedRecipe ?: _importedRecipePreview.value ?: return
        _recipeSaveInProgress.value = true
        viewModelScope.launch {
            try {
            val preview = enrichImportedNutrition(input)
            val cleanTitle = preview.title.trim().ifBlank { "Zaimportowany przepis" }
            val cleanServings = preview.servings?.trim()?.takeIf { it.isNotBlank() } ?: "1"
            val cleanSource = preview.source.trim().ifBlank { "Import" }
            val cleanIngredients = preview.ingredients.lines()
                .map { normalizeExtractedIngredientLine(it) }
                .filter { it.isNotBlank() }
                .joinToString("\n")
            val cleanInstructions = preview.instructions.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")

            val entity = RecipeEntity(
                title = cleanTitle,
                description = "Porcje: $cleanServings\n" +
                    (preview.totalPreparationMinutes?.takeIf { it in 0..1440 }?.let { "Czas całkowity: $it min\n" } ?: "") +
                    (if (preview.requiresInstructionsCompletion && cleanInstructions.isBlank())
                        "Przygotowanie: brak instrukcji w publicznym źródle; uzupełnij z własnego konta Cookidoo.\n" else "") + if (preview.nutritionIsEstimated) {
                    "Wartości odżywcze: uzupełnione automatycznie (tabele / AI), szacunkowe. Sprawdź ilości i etykiety produktów."
                } else if (preview.extractedWithoutAi) {
                    "Wartości odżywcze podane w źródle (niezweryfikowane niezależnie)."
                } else {
                    "Wartości odżywcze: szacunek AI / import niezweryfikowany."
                },
                ingredients = cleanIngredients,
                instructions = cleanInstructions,
                calories = preview.calories.coerceAtLeast(0),
                protein = preview.protein.coerceAtLeast(0),
                carbs = preview.carbs.coerceAtLeast(0),
                fat = preview.fat.coerceAtLeast(0),
                category = preview.providedCategory?.takeIf { RecipeCategories.canonical(it) != "Inne" }
                    ?: inferCategoryFromTitle(cleanTitle),
                source = cleanSource,
                imageUrl = preview.imageUrl
            )
            val insertedId = repository.saveImportedRecipe(entity, importedIngredients(cleanIngredients))
            _importedRecipePreview.value = null
            _importFallbackPromptMode.value = null
            onSaved?.invoke(entity.copy(id = insertedId))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ImportedNutritionException) {
                onError?.invoke(e.message ?: "Uzupełnij wartości odżywcze.")
            } catch (_: Exception) {
                onError?.invoke("Nie udało się zapisać przepisu. Sprawdź pełną nazwę, składniki i instrukcje; spróbuj ponownie.")
            } finally { _recipeSaveInProgress.value = false }
        }
    }

    private fun importedIngredients(text: String): List<RecipeIngredientEntity> =
        MealPlannerSupport.parseRecipeIngredients(text).filter { it.name.isNotBlank() }.map {
            RecipeIngredientEntity(
                recipeId = 0, foodName = it.name, quantity = it.baseQuantity ?: 0f,
                unit = it.unit, grams = if (it.unit == "g") it.baseQuantity ?: 0f else 0f,
                caloriesPer100g = -1f, proteinPer100g = -1f, carbsPer100g = -1f, fatPer100g = -1f
            )
        }

    private suspend fun fetchHtmlDocument(url: String): org.jsoup.nodes.Document {
        val customFetcher = customHtmlDocumentFetcher
        if (customFetcher != null) {
            return customFetcher(url)
        }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .followRedirects(true)
                .timeout(12000)
                .get()
        }
    }

    // Recipe Import from URL / Cookidoo / Instagram / Text via Jsoup, Schema.org JSON-LD & AI fallback
    fun importRecipeImage(context: Context, uri: Uri, onResult: (Boolean, String?) -> Unit) =
        importRecipeImages(context, listOf(uri), onResult)

    fun importRecipeImages(context: Context, uris: List<Uri>, onResult: (Boolean, String?) -> Unit) {
        if (_isLoadingAi.value || uris.isEmpty()) return
        _isLoadingAi.value = true
        viewModelScope.launch {
            var recognizedText: String? = null
            try {
                val text = RecipeImageExtractor.extractTexts(uris) { RecipeImageExtractor.extractText(context, it) }
                if (text.isBlank()) {
                    onResult(false, "Nie rozpoznano tekstu. Wybierz wyraźne zdjęcie składników i instrukcji lub wklej tekst ręcznie.")
                    return@launch
                }
                handleIncomingSharedRecipeText(text)
                // Preserve OCR text for manual correction even if extraction or Gemini fails.
                recognizedText = text
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: RecipeImageImportException) {
                onResult(false, e.message)
            } catch (_: Exception) {
                onResult(false, "Nie udało się odczytać zdjęć. Wybierz inne pliki lub wklej tekst ręcznie.")
            } finally {
                _isLoadingAi.value = false
            }
            recognizedText?.let { importRecipe("Zdjęcie OCR", it, onResult) }
        }
    }

    fun importRecipe(sourceName: String, inputData: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedInput = inputData.trim()
        if (trimmedInput.isBlank()) {
            onResult(false, "Wprowadź adres URL przepisu lub wklej jego treść.")
            return
        }

        _isLoadingAi.value = true
        viewModelScope.launch {
            try {
                val inputType = classifyRecipeInput(sourceName, trimmedInput)
                when (inputType) {
                    is RecipeImportInput.InvalidUrl -> {
                        onResult(false, "Nieprawidłowy adres URL przepisu. Sprawdź poprawność linku.")
                        _isLoadingAi.value = false
                    }

                    is RecipeImportInput.InstagramUrl -> {
                        val igUri = instagramPostUriOrNull(trimmedInput)
                        if (igUri != null) {
                            val publicRecipe = try {
                                val doc = fetchHtmlDocument(igUri.toString())
                                extractPublicInstagramRecipeOrNull(doc, igUri.toString())
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) {
                                null
                            }
                            if (publicRecipe != null) {
                                _importedRecipePreview.value = publicRecipe.copy(source = igUri.toString())
                                _importFallbackPromptMode.value = null
                                onResult(true, null)
                                _isLoadingAi.value = false
                                return@launch
                            }
                        }
                        _importFallbackPromptMode.value = "Instagram"
                        onResult(false, RecipeImportMessages.INSTAGRAM_FALLBACK_MESSAGE)
                        _isLoadingAi.value = false
                    }

                    is RecipeImportInput.Url -> {
                        val urlString = inputType.uri.toString()
                        val isCookidoo = inputType.isCookidoo || sourceName.equals("Cookidoo", ignoreCase = true)

                        val doc = try {
                            fetchHtmlDocument(urlString)
                        } catch (e: Exception) {
                            if (isCookidoo) {
                                _importFallbackPromptMode.value = "Cookidoo"
                                onResult(false, RecipeImportMessages.COOKIDOO_FALLBACK_MESSAGE)
                            } else {
                                val errMsg = mapImportExceptionToMessage(e, isCookidoo = false, isInstagram = false)
                                onResult(false, errMsg)
                            }
                            _isLoadingAi.value = false
                            return@launch
                        }

                        if (isCookidoo) {
                            val cookidooRecipe = extractCookidooPreviewOrNull(doc, urlString)
                            if (cookidooRecipe != null) {
                                _importedRecipePreview.value = cookidooRecipe.copy(source = urlString)
                                _importFallbackPromptMode.value = null
                                onResult(true, null)
                            } else {
                                _importFallbackPromptMode.value = "Cookidoo"
                                onResult(false, RecipeImportMessages.COOKIDOO_FALLBACK_MESSAGE)
                            }
                            _isLoadingAi.value = false
                            return@launch
                        }

                        // 1. Public cooking website: Use Schema.org Recipe JSON-LD / Microdata FIRST without Gemini!
                        val directRecipe = extractRecipeFromDocumentWithoutAi(doc, sourceUrl = urlString)
                        if (directRecipe != null &&
                            directRecipe.title.isNotBlank() &&
                            directRecipe.ingredients.isNotBlank() &&
                            directRecipe.instructions.isNotBlank()
                        ) {
                            _importedRecipePreview.value = directRecipe.copy(source = urlString)
                            _importFallbackPromptMode.value = null
                            onResult(true, null)
                            _isLoadingAi.value = false
                            return@launch
                        }

                        // 2. Fallback to structured page text or Gemini ONLY when JSON-LD is missing/incomplete
                        val pageText = extractReadablePageText(doc).take(6000)
                        if (pageText.length < 30) {
                            onResult(false, RecipeImportMessages.INVALID_RECIPE_CONTENT_MESSAGE)
                            _isLoadingAi.value = false
                            return@launch
                        }

                        if (customRecipeImportAiCaller == null) {
                            val structuredPageRecipe = parseStructuredRecipeText(pageText, sourceLabel = urlString)
                            if (structuredPageRecipe != null) {
                                _importedRecipePreview.value = structuredPageRecipe.copy(source = urlString)
                                _importFallbackPromptMode.value = null
                                onResult(true, null)
                                _isLoadingAi.value = false
                                return@launch
                            }
                        }

                        val schemaJsonLd = extractSchemaRecipeJsonLd(doc)
                        val prompt = buildWebsiteGeminiExtractPrompt(schemaJsonLd, pageText)
                        callGeminiForRecipePreview(prompt, sourceLabel = urlString, sourceText = pageText, onResult = onResult)
                    }

                    is RecipeImportInput.Text -> {
                        val textValue = inputType.value.trim()
                        val resolvedSourceLabel = when {
                            _importFallbackPromptMode.value == "Cookidoo" || sourceName.equals("Cookidoo", ignoreCase = true) -> "Cookidoo"
                            _importFallbackPromptMode.value == "Instagram" || sourceName.equals("Instagram", ignoreCase = true) -> "Instagram"
                            sourceName.equals("Website", ignoreCase = true) -> "Wklejony przepis"
                            else -> sourceName.ifBlank { "Wklejony przepis" }
                        }

                        // If a custom AI caller is injected (e.g., to test Gemini quota exhaustion on pasted text), invoke it first
                        if (customRecipeImportAiCaller != null) {
                            val prompt = buildPastedTextGeminiPrompt(textValue)
                            callGeminiForRecipePreview(prompt, sourceLabel = resolvedSourceLabel, sourceText = textValue, onResult = onResult)
                            return@launch
                        }

                        // Otherwise, try deterministic structured recipe parsing first (never fabricates missing fields)
                        val structuredFromText = parseStructuredRecipeText(textValue, sourceLabel = resolvedSourceLabel)
                        if (structuredFromText != null) {
                            _importedRecipePreview.value = structuredFromText
                            _importFallbackPromptMode.value = null
                            onResult(true, null)
                            _isLoadingAi.value = false
                            return@launch
                        }

                        val longInstagramCaption = resolvedSourceLabel == "Instagram" && textValue.length >= 100
                        if (textValue.length < 25 || (textValue.lines().count { it.isNotBlank() } < 2 && !longInstagramCaption)) {
                            onResult(false, RecipeImportMessages.INVALID_RECIPE_CONTENT_MESSAGE)
                            _isLoadingAi.value = false
                            return@launch
                        }

                        val prompt = buildPastedTextGeminiPrompt(textValue)
                        callGeminiForRecipePreview(prompt, sourceLabel = resolvedSourceLabel, sourceText = textValue, onResult = onResult)
                    }
                }
            } catch (e: Exception) {
                val errMsg = mapImportExceptionToMessage(e)
                onResult(false, errMsg)
                _isLoadingAi.value = false
            }
        }
    }

    private fun buildWebsiteGeminiExtractPrompt(schemaJsonLd: String?, pageText: String): String {
        val sourceContent = schemaJsonLd ?: pageText
        return """
        Wyodrębnij przepis kulinarny z poniższej treści strony i zwróć WYŁĄCZNIE poprawny obiekt JSON (bez znaczników markdown).
        WAŻNE: Nie wymyślaj brakujących składników ani instrukcji przygotowania. Jeśli w tekście nie ma pełnego przepisu, zwróć pusty obiekt {}.
        Wymagane pola JSON:
        - "title": nazwa przepisu (string)
        - "servings": liczba porcji (string, np. "2")
        - "ingredients": tablica składników z ilościami i jednostkami (array of strings)
        - "instructions": tablica kroków przygotowania (array of strings)
        - "calories": kalorie NA JEDNĄ PORCJĘ jeśli podano, w przeciwnym razie 0 (int)
        - "protein": białko w gramach jeśli podano, w przeciwnym razie 0 (int)
        - "carbs": węglowodany w gramach jeśli podano, w przeciwnym razie 0 (int)
        - "fat": tłuszcz w gramach jeśli podano, w przeciwnym razie 0 (int)

        Treść źródłowa:
        $sourceContent
        """.trimIndent()
    }

    private fun buildPastedTextGeminiPrompt(textValue: String): String {
        return """
        Przekształć poniższy wklejony tekst przepisu (np. z Cookidoo lub opisu z Instagrama) w ustrukturyzowany obiekt JSON w języku polskim.
        Zwróć WYŁĄCZNIE poprawny obiekt JSON bez znaczników markdown.
        ZASADA KRYTYCZNA: NIGDY nie wymyślaj brakujących składników, gramatur ani kroków przygotowania. Wykorzystaj wyłącznie informacje obecne w tekście. Jeśli tekst nie zawiera składników lub instrukcji, zwróć {}.
        Wymagane pola JSON:
        - "title": nazwa przepisu (string)
        - "servings": liczba porcji całego przepisu (string). Odczytaj ją dokładnie ze źródła; np. "Składniki na 3 porcje" oznacza servings="3". Nie ustawiaj 1, jeśli źródło podaje inną liczbę.
        - "ingredients": tablica składników z ilościami i jednostkami (array of strings)
        - "instructions": tablica kroków przygotowania (array of strings)
        - "calories": kalorie NA JEDNĄ PORCJĘ jeśli podano w tekście, w przeciwnym razie 0 (int)
        - "protein": białko w gramach jeśli podano w tekście, w przeciwnym razie 0 (int)
        - "carbs": węglowodany w gramach jeśli podano w tekście, w przeciwnym razie 0 (int)
        - "fat": tłuszcz w gramach jeśli podano w tekście, w przeciwnym razie 0 (int)

        Koniecznie odczytaj oznaczenia B/białko/protein, W/węglowodany/carbs, T/tłuszcz/fat i kcal, również z końca opisu, hashtagów lub tekstu po OCR.
        Wartości makro również dotyczą jednej porcji. Jeżeli źródło podaje wartości całego przepisu i liczbę porcji, podziel wartości przez liczbę porcji.
        Nie szacuj brakujących wartości: wpisz 0 i pozwól użytkownikowi uzupełnić dane w podglądzie.

        Wklejony tekst:
        $textValue
        """.trimIndent()
    }

    private suspend fun enrichImportedNutrition(preview: ImportedRecipe): ImportedRecipe {
        if (!ImportedNutritionSupport.needsEnrichment(preview)) return preview
        val local = ImportedNutritionSupport.fromLocalIngredients(preview)
        if (local != null) return ImportedNutritionSupport.merge(preview, local)
        val prompt = """
            Uzupełnij brakujące kalorie i makro B/W/T NA JEDNĄ PORCJĘ. Są to szacunki, nie pomiary.
            Składniki poniżej dotyczą całego przepisu; podziel obliczone wartości przez liczbę porcji.
            Nie zmieniaj przepisu. Nie zgaduj brakujących ilości ani liczby porcji. Jeżeli nie możesz
            obliczyć wartości z podanych ilości, zwróć calories: 0. Zero dla makro oznacza rzeczywiste zero.
            Zwróć tylko JSON: title, servings, ingredients, instructions, calories, protein, carbs, fat.
            Nazwa: ${preview.title}
            Porcje: ${preview.servings ?: "1"}
            Składniki: ${preview.ingredients}
            Przygotowanie: ${preview.instructions}
        """.trimIndent()
        val response = customRecipeImportAiCaller?.invoke(prompt)
            ?: com.example.ai.GeminiRequestManager.callGeminiCached(prompt, repository)
        val estimated = response.getOrNull()?.let {
            try { parseImportedRecipe(it, preview.source) } catch (_: Exception) { null }
        }
        if (estimated != null && ImportedNutritionSupport.hasUsableNutrition(estimated)) {
            return ImportedNutritionSupport.merge(preview, estimated)
        }
        // Preserve valid source data for naturally zero nutrients when AI is unavailable.
        if (ImportedNutritionSupport.hasUsableNutrition(preview)) return preview
        throw ImportedNutritionException("Nie zapisano „${preview.title}”: nie udało się uzupełnić kalorii i makro. Sprawdź gramaturę i liczbę porcji oraz klucz Gemini w Profilu, albo wpisz wartości w edycji podglądu. " +
            (response.exceptionOrNull()?.let { mapImportExceptionToMessage(it) } ?: ""))
    }

    fun estimateImportedNutrition(preview: ImportedRecipe, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                _importedRecipePreview.value = enrichImportedNutrition(preview)
                onResult(true, null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(false, e.message ?: "Nie udało się uzupełnić wartości odżywczych.") }
        }
    }

    private suspend fun callGeminiForRecipePreview(
        prompt: String,
        sourceLabel: String,
        sourceText: String? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        geminiCallsDuringRecipeImport++
        val customCaller = customRecipeImportAiCaller
        val aiResult = if (customCaller != null) {
            customCaller(prompt)
        } else {
            com.example.ai.GeminiRequestManager.callGeminiCached(prompt, repository)
        }
        aiResult.fold(
            onSuccess = { responseText ->
                try {
                    val imported = parseImportedRecipe(responseText, sourceUrlOrLabel = sourceLabel)
                    // Source text is authoritative for an explicitly stated yield (e.g. "Składniki na 3 porcje").
                    // Gemini sometimes defaults servings to 1 even when the caption clearly states otherwise.
                    val sourceServings = sourceText?.let(::extractExplicitRecipeServings)
                    val corrected = if (sourceServings != null) imported.copy(servings = sourceServings) else imported
                    _importedRecipePreview.value = corrected
                    _importFallbackPromptMode.value = null
                    onResult(true, null)
                } catch (e: Exception) {
                    val errMsg = mapImportExceptionToMessage(e)
                    onResult(false, errMsg)
                }
            },
            onFailure = { err ->
                val errMsg = mapImportExceptionToMessage(err)
                onResult(false, errMsg)
            }
        )
        _isLoadingAi.value = false
    }

    // Real PDF Import & Extraction using pdfbox-android with OCR check
    fun importPdf(context: Context, uri: Uri, fileName: String, onResult: (Boolean, String?) -> Unit) {
        _isLoadingAi.value = true
        viewModelScope.launch {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Ignored
                }

                PDFBoxResourceLoader.init(context)
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    onResult(false, "Nie udało się odczytać pliku PDF.")
                    _isLoadingAi.value = false
                    return@launch
                }

                val extractedText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    inputStream.use { stream -> PdfRecipeExtractor.extractFullTextFromPdf(stream) }
                }

                if (!PdfRecipeExtractor.hasExtractablePdfText(extractedText)) {
                    onResult(false, "Nie udało się odczytać tekstu z tego PDF. Dokument może być skanem i wymaga OCR.")
                    _isLoadingAi.value = false
                    return@launch
                }

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                repository.insertDocument(DocumentEntity(
                    title = fileName,
                    uriString = uri.toString(),
                    extractedText = extractedText,
                    dateAdded = dateStr,
                    aiAnalysisResult = ""
                ))
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, "Błąd odczytu PDF: ${e.localizedMessage}")
            } finally {
                _isLoadingAi.value = false
            }
        }
    }

    // Parse recipes from PDF text with boundary-aware chunking, sequential processing, structured JSON, repair, & smart deduplication
    fun parsePdfRecipes(document: DocumentEntity, resume: Boolean = false, onResult: (List<ParsedRecipe>, String?) -> Unit) {
        val currentState = _pdfAnalysisStates.value[document.id]
        if (currentState is PdfAnalysisState.Analyzing || currentState is PdfAnalysisState.Extracting) {
            // Guard duplicate analysis coroutines
            return
        }

        _isLoadingAi.value = true
        _pdfAnalysisStates.value = _pdfAnalysisStates.value + (document.id to PdfAnalysisState.Extracting)

        viewModelScope.launch {
            try {
                val text = document.extractedText
                if (!PdfRecipeExtractor.hasExtractablePdfText(text)) {
                    _isLoadingAi.value = false
                    _pdfAnalysisStates.value = _pdfAnalysisStates.value + (document.id to PdfAnalysisState.Failed("Pusty lub zbyt krótki tekst."))
                    onResult(emptyList(), "Nie udało się odczytać tekstu z tego PDF. Dokument może być skanem i wymaga OCR.")
                    return@launch
                }

                val cachedChunks = if (resume) _pdfChunksCache.value[document.id] else null
                val chunks = if (!cachedChunks.isNullOrEmpty()) {
                    cachedChunks
                } else {
                    PdfRecipeExtractor.createPdfChunks(
                        fullText = text,
                        config = PdfChunkConfig.forModelContext("gemini-3.5-flash")
                    ).also { generated ->
                        _pdfChunksCache.value = _pdfChunksCache.value + (document.id to generated)
                    }
                }

                val startChunk = if (resume) (_pdfNextChunkIndex.value[document.id] ?: 0) else 0
                val initialRaw = if (resume) (_pdfRawFoundRecipes.value[document.id] ?: emptyList()) else emptyList()
                val initialCounts = if (resume) (_pdfChunkRecipeCounts.value[document.id] ?: emptyMap()) else emptyMap()

                if (!resume) {
                    _pdfNextChunkIndex.value = _pdfNextChunkIndex.value + (document.id to 0)
                    _pdfFoundRecipes.value = _pdfFoundRecipes.value + (document.id to emptyList())
                    _pdfRawFoundRecipes.value = _pdfRawFoundRecipes.value + (document.id to emptyList())
                    _pdfChunkRecipeCounts.value = _pdfChunkRecipeCounts.value + (document.id to emptyMap())
                }

                val outcome = PdfRecipeExtractor.executePdfRecipeExtraction(
                    documentTitle = document.title,
                    chunks = chunks,
                    startChunkIndex = startChunk,
                    initialRawRecipes = initialRaw,
                    initialPerChunkCounts = initialCounts,
                    chunkCall = { prompt ->
                        com.example.ai.GeminiRequestManager.callGeminiPdfChunkCached(
                            prompt = prompt,
                            repository = repository,
                            isValidResponse = { resp ->
                                PdfRecipeExtractor.tryParseStructuredRecipesJson(resp) != null
                            }
                        )
                    },
                    repairCall = { repairPrompt ->
                        com.example.ai.GeminiRequestManager.callGeminiPdfChunkCached(
                            prompt = repairPrompt,
                            repository = repository,
                            isValidResponse = { resp ->
                                PdfRecipeExtractor.tryParseStructuredRecipesJson(resp) != null
                            }
                        )
                    },
                    onProgress = { currentOneBased, total, foundCount, partialDeduplicated, partialRaw, nextChunkIdx, perChunkCounts ->
                        _pdfNextChunkIndex.value = _pdfNextChunkIndex.value + (document.id to nextChunkIdx)
                        _pdfFoundRecipes.value = _pdfFoundRecipes.value + (document.id to partialDeduplicated)
                        _pdfRawFoundRecipes.value = _pdfRawFoundRecipes.value + (document.id to partialRaw)
                        _pdfChunkRecipeCounts.value = _pdfChunkRecipeCounts.value + (document.id to perChunkCounts)
                        _pdfAnalysisStates.value = _pdfAnalysisStates.value + (
                            document.id to PdfAnalysisState.Analyzing(
                                current = currentOneBased,
                                total = total,
                                foundCount = foundCount
                            )
                        )
                    }
                )

                val distinctRecipes = outcome.deduplicatedRecipes
                _pdfFoundRecipes.value = _pdfFoundRecipes.value + (document.id to distinctRecipes)
                _pdfRawFoundRecipes.value = _pdfRawFoundRecipes.value + (document.id to outcome.recipesBeforeDeduplication)
                _pdfChunkRecipeCounts.value = _pdfChunkRecipeCounts.value + (document.id to outcome.perChunkCounts)
                _isLoadingAi.value = false

                if (!outcome.completedAllChunks) {
                    _pdfNextChunkIndex.value = _pdfNextChunkIndex.value + (document.id to outcome.nextChunkIndex)
                    val stoppedAtOneBased = (outcome.nextChunkIndex + 1).coerceAtMost(outcome.totalChunks)
                    if (outcome.rateLimitedOrPaused) {
                        _pdfAnalysisStates.value = _pdfAnalysisStates.value + (
                            document.id to PdfAnalysisState.RateLimited(
                                current = stoppedAtOneBased,
                                total = outcome.totalChunks,
                                foundCount = distinctRecipes.size
                            )
                        )
                        val summary = "Wstrzymano analizę (część $stoppedAtOneBased/${outcome.totalChunks}). Znaleziono dotąd: ${distinctRecipes.size} przepisów."
                        repository.updateDocument(document.copy(aiAnalysisResult = summary))
                        onResult(
                            distinctRecipes,
                            "Limit AI został osiągnięty (część $stoppedAtOneBased/${outcome.totalChunks}). Znaleziono dotąd: ${distinctRecipes.size} przepisów. Możesz kliknąć „Kontynuuj analizę”, aby dokończyć."
                        )
                    } else {
                        val errMsg = outcome.errorMessage ?: "Błąd analizy AI."
                        _pdfAnalysisStates.value = _pdfAnalysisStates.value + (
                            document.id to PdfAnalysisState.Failed(
                                error = errMsg,
                                foundCount = distinctRecipes.size
                            )
                        )
                        onResult(
                            distinctRecipes,
                            "Błąd AI: $errMsg. ${if (distinctRecipes.isNotEmpty()) "Zachowano znalezione dotąd przepisy (${distinctRecipes.size})." else ""}"
                        )
                    }
                } else {
                    _pdfNextChunkIndex.value = _pdfNextChunkIndex.value + (document.id to 0)
                    _pdfAnalysisStates.value = _pdfAnalysisStates.value + (
                        document.id to PdfAnalysisState.Completed(distinctRecipes.size)
                    )
                    val summary = "Znaleziono ${distinctRecipes.size} przepisów (przeanalizowano ${outcome.totalChunks}/${outcome.totalChunks} części)."
                    repository.updateDocument(document.copy(aiAnalysisResult = summary))
                    if (distinctRecipes.isEmpty()) {
                        onResult(emptyList(), "Nie znaleziono przepisów w tym dokumencie.")
                    } else {
                        onResult(distinctRecipes, null)
                    }
                }
            } catch (e: Exception) {
                _isLoadingAi.value = false
                val savedSoFar = _pdfFoundRecipes.value[document.id].orEmpty()
                _pdfAnalysisStates.value = _pdfAnalysisStates.value + (
                    document.id to PdfAnalysisState.Failed(
                        error = e.localizedMessage ?: "Wystąpił nieoczekiwany błąd.",
                        foundCount = savedSoFar.size
                    )
                )
                onResult(savedSoFar, "Wystąpił błąd podczas analizy: ${e.localizedMessage}")
            }
        }
    }

    fun saveParsedRecipesToRoom(recipesToSave: List<ParsedRecipe>, onResult: ((String?) -> Unit)? = null) {
        viewModelScope.launch {
            try {
            for (input in recipesToSave) {
                val nutrition = enrichImportedNutrition(ImportedRecipe(input.title, input.servings, input.ingredients,
                    input.instructions, input.calories, input.protein, input.carbs, input.fat, input.source))
                val r = input.copy(calories = nutrition.calories, protein = nutrition.protein, carbs = nutrition.carbs, fat = nutrition.fat)
                val resolvedSource = r.source.trim().ifBlank { "PDF" }
                repository.saveImportedRecipe(RecipeEntity(
                    title = r.title.trim(),
                    description = "Porcje: ${r.servings.ifBlank { "1" }} (Źródło: $resolvedSource)\nWartości odżywcze: import / szacunek AI niezweryfikowany.",
                    ingredients = r.ingredients,
                    instructions = r.instructions,
                    calories = r.calories,
                    protein = r.protein,
                    carbs = r.carbs,
                    fat = r.fat,
                    category = inferCategoryFromTitle(r.title),
                    source = resolvedSource
                ), importedIngredients(r.ingredients))
            }
            onResult?.invoke(null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ImportedNutritionException) {
                onResult?.invoke((e.message ?: "Brak wartości odżywczych.") + " Wcześniejsze przepisy z tej partii zostały zachowane; ponowny zapis nie zdubluje ich.")
            } catch (_: Exception) {
                onResult?.invoke("Nie udało się zapisać wszystkich przepisów. Ponów zapis; zapisane pozycje nie zostaną zdublowane.")
            }
        }
    }

    private fun inferCategoryFromTitle(title: String): String {
        val lower = title.lowercase(Locale.forLanguageTag("pl-PL"))
        return when {
            lower.contains("owsiank") || lower.contains("omlet") || lower.contains("jajecznic") ||
                lower.contains("śniadan") || lower.contains("kanapk") || lower.contains("placusz") ||
                lower.contains("naleśnik") || lower.contains("szakszuk") || lower.contains("tost") -> "Śniadanie"
            lower.contains("koktajl") || lower.contains("smoothie") || lower.contains("deser") ||
                lower.contains("baton") || lower.contains("przekąsk") || lower.contains("budyń") -> "Przekąska"
            lower.contains("sałatk") || lower.contains("kolacj") || lower.contains("twaroż") ||
                lower.contains("zupa krem") -> "Kolacja"
            else -> "Obiad"
        }
    }

    // Analyze PDF with Gemini AI (fallback analysis or chat-like answer)
    fun analyzePdfWithAi(document: DocumentEntity, onComplete: (String) -> Unit) {
        _isLoadingAi.value = true
        viewModelScope.launch {
            val prompt = "Answer questions or summarize recommendations about this PDF document text:\n\n${document.extractedText.take(4000)}"
            val result = callGemini(prompt)
            result.fold(
                onSuccess = { analysis ->
                    repository.updateDocument(document.copy(aiAnalysisResult = analysis))
                    _isLoadingAi.value = false
                    onComplete(analysis)
                },
                onFailure = { err ->
                    _isLoadingAi.value = false
                    onComplete(err.localizedMessage ?: "Błąd AI")
                }
            )
        }
    }

    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }

    fun assignLegacyPlanWeek(ids: List<Long>, week: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                recipeEditMutex.withLock { repository.assignLegacyPlanWeek(ids, week) }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się przypisać tygodnia.") }
        }
    }

    fun addMealPlan(dayOfWeek: String, mealType: String, recipe: RecipeEntity, servings: Float, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
            require(servings.isFinite() && servings > 0 && servings <= 1000)
            val safeServings = servings.coerceAtLeast(0.25f)
            val normalizedDay = MealPlannerSupport.normalizeDayOfWeek(dayOfWeek)
            val normalizedMealType = MealPlannerSupport.normalizeMealType(mealType)
            val scaledCalories = if (recipe.calories > 0) (recipe.calories * safeServings).roundToInt() else 0
            val insertedId = repository.insertMealPlan(MealPlanEntity(
                dayOfWeek = normalizedDay,
                mealType = normalizedMealType,
                recipeId = recipe.id,
                recipeTitle = recipe.title,
                servings = safeServings,
                calories = scaledCalories
            ))
            if (insertedId > 0L) {
                repository.deleteIngredientsForRecipe(-insertedId)
            }
            rebuildShoppingListInternal()
            onResult(insertedId > 0L, if (insertedId > 0L) null else "Nie udało się dodać posiłku.")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult(false, "Nie udało się dodać posiłku.") }
        }
    }

    fun addSuggestedMeal(recipeId: Long, servings: Float, type: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                recipeEditMutex.withLock {
                    require(servings.isFinite() && servings > 0 && servings <= 2)
                    require(MealPlannerSupport.MEAL_CATEGORIES.any { it.canonicalName == type })
                    val recipe = repository.allRecipes.first().find { it.id == recipeId } ?: error("Przepis został usunięty.")
                    val today = java.time.LocalDate.now()
                    val day = MealPlannerSupport.WEEK_DAYS[today.dayOfWeek.value - 1].canonicalName
                    repository.insertMealPlan(MealPlanEntity(dayOfWeek = day, mealType = type, recipeId = recipe.id,
                        recipeTitle = recipe.title, servings = servings, calories = (recipe.calories * servings).roundToInt()))
                    rebuildShoppingListInternal()
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się dodać posiłku.") }
        }
    }

    fun copyMeals(ids: List<Long>, day: String, type: String? = null, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                require(MealPlannerSupport.isPlanDay(day))
                require(type == null || MealPlannerSupport.MEAL_CATEGORIES.any { it.canonicalName == type })
                recipeEditMutex.withLock { repository.copyMeals(ids, day, type); rebuildShoppingListInternal() }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się skopiować posiłku.") }
        }
    }

    private val _savedMealSets = MutableStateFlow(MealConvenienceSupport.read(getApplication()))
    val savedMealSets: StateFlow<List<SavedMealSet>> = _savedMealSets.asStateFlow()

    fun refreshSavedMealSets() { _savedMealSets.value = MealConvenienceSupport.read(getApplication()) }
    fun saveMealSet(name: String, ids: List<Long>, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                recipeEditMutex.withLock {
                    val meals = repository.allMealPlans.first()
                    val selected = ids.distinct().map { id -> meals.find { it.id == id } ?: error("Posiłek został usunięty.") }
                    val set = MealConvenienceSupport.snapshot(name, selected, repository.allRecipeIngredients.first())
                    MealConvenienceSupport.write(getApplication(), _savedMealSets.value + set)
                    refreshSavedMealSets()
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się zapisać zestawu.") }
        }
    }
    fun deleteMealSet(id: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                recipeEditMutex.withLock {
                    MealConvenienceSupport.write(getApplication(), _savedMealSets.value.filter { it.id != id }); refreshSavedMealSets()
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się usunąć zestawu.") }
        }
    }
    fun applyMealSet(id: String, day: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                recipeEditMutex.withLock {
                    require(MealPlannerSupport.isPlanDay(day))
                    val set = _savedMealSets.value.find { it.id == id } ?: error("Zestaw został usunięty.")
                    val recipes = repository.allRecipes.first()
                    require(set.entries.all { entry -> recipes.any { it.id == entry.meal.recipeId } }) { "Przepis z zestawu został usunięty. Zapisz zestaw ponownie." }
                    val allIngredients = repository.allRecipeIngredients.first()
                    val currentEntries = set.entries.map { entry ->
                        val recipe = recipes.first { it.id == entry.meal.recipeId }
                        val temporary = entry.meal.copy(id = Long.MAX_VALUE)
                        val current = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(temporary, recipe,
                            allIngredients.filter { it.recipeId >= 0 } + entry.overrides.map { it.copy(recipeId = -Long.MAX_VALUE) })
                        entry.copy(meal = entry.meal.copy(calories = current.calories ?: 0))
                    }
                    repository.insertSavedMeals(currentEntries, day); rebuildShoppingListInternal()
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się dodać zestawu.") }
        }
    }
    fun planPortionBatch(mealId: Long, prepared: Float, assignments: List<PortionMealAssignment>, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                MealQueueSupport.validatePortions(prepared, assignments)
                recipeEditMutex.withLock {
                    val meal = repository.allMealPlans.first().find { it.id == mealId } ?: error("Posiłek został usunięty.")
                    val recipe = repository.allRecipes.first().find { it.id == meal.recipeId } ?: error("Przepis został usunięty.")
                    val ingredients = repository.allRecipeIngredients.first()
                    val overrides = ingredients.filter { it.recipeId == -meal.id && it.foodName != MealConvenienceSupport.BATCH_WEIGHT }
                    val entries = assignments.map { row ->
                        val servings = MealPlannerSupport.safeServings(meal.servings) * row.portions
                        require(servings.isFinite() && servings <= 1000) { "Zbyt duża liczba porcji." }
                        val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal.copy(servings = servings), recipe, ingredients)
                        SavedMealEntry(QuickUseSupport.freshCopy(meal, row.date, meal.mealType).copy(servings = servings, calories = nutrition.calories ?: 0), overrides)
                    }
                    repository.insertSavedMeals(entries)
                    rebuildShoppingListInternal()
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się zaplanować porcji.") }
        }
    }

    fun planCookedBatch(mealId: Long, total: Float, portions: Float, assignments: List<BatchMealPortion>, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val servings = MealConvenienceSupport.batchServings(total, portions, assignments)
                recipeEditMutex.withLock {
                    val meal = repository.allMealPlans.first().find { it.id == mealId } ?: error("Posiłek został usunięty.")
                    val recipe = repository.allRecipes.first().find { it.id == meal.recipeId } ?: error("Przepis został usunięty.")
                    val ingredients = repository.allRecipeIngredients.first()
                    val entries = assignments.mapIndexed { index, row ->
                        val copy = QuickUseSupport.freshCopy(meal, row.day, meal.mealType).copy(servings = servings[index],
                            recipeTitle = recipe.title)
                        val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal.copy(servings = servings[index]), recipe, ingredients)
                        SavedMealEntry(copy.copy(calories = nutrition.calories ?: 0),
                            ingredients.filter { it.recipeId == -meal.id && it.foodName != MealConvenienceSupport.BATCH_WEIGHT } +
                                RecipeIngredientEntity(recipeId = -meal.id, foodName = MealConvenienceSupport.BATCH_WEIGHT,
                                    quantity = total / portions, grams = total / portions, caloriesPer100g = -1f,
                                    proteinPer100g = -1f, carbsPer100g = -1f, fatPer100g = -1f))
                    }
                    repository.insertSavedMeals(entries); rebuildShoppingListInternal()
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się podzielić dania.") }
        }
    }

    fun saveOwnProduct(product: SubstituteCandidate, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val recipe = QuickUseSupport.productRecipe(product)
                recipeEditMutex.withLock {
                    repository.saveImportedRecipe(recipe, listOf(ScannedProductMealSupport.ingredient(product, 100f, product.defaultUnit)))
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się zapisać produktu.") }
        }
    }

    fun saveCookedWeight(recipeId: Long, total: Float, portions: Float, mealId: Long? = null, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                recipeEditMutex.withLock {
                    val recipe = repository.allRecipes.first().find { it.id == recipeId } ?: error("Przepis został usunięty.")
                    val portionWeight = QuickUseSupport.portionWeight(total, portions)
                    val batch = mealId?.let { id -> repository.allRecipeIngredients.first().firstOrNull { it.recipeId == -id && it.foodName == MealConvenienceSupport.BATCH_WEIGHT } }
                    if (batch != null) repository.updateIngredient(batch.copy(grams = portionWeight, quantity = portionWeight))
                    else repository.updateRecipe(QuickUseSupport.withCookedWeight(recipe, total, portions))
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się zapisać wagi.") }
        }
    }

    fun recordMealGrams(meal: MealPlanEntity, eatenGrams: Float, portionGrams: Float,
        onResult: (Boolean, String?) -> Unit) {
        val servings = try { MealPlannerSupport.servingsFromGrams(eatenGrams, portionGrams) }
            catch (e: IllegalArgumentException) { onResult(false, e.message); return }
        viewModelScope.launch {
            val saved = undoableMealEdit(meal.id, "Zapisano zjedzoną ilość w gramach") {
                val latest = repository.allMealPlans.first().firstOrNull { it.id == meal.id }
                    ?: error("Posiłek został usunięty.")
                val recipe = repository.allRecipes.first().firstOrNull { it.id == latest.recipeId }
                val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
                    latest.copy(servings = servings), recipe, repository.allRecipeIngredients.first())
                val calories = nutrition.calories ?: (latest.calories / MealPlannerSupport.safeServings(latest.servings) * servings).roundToInt()
                val date = MealPlannerSupport.dateForDay(latest.dayOfWeek)?.toString()
                    ?: error("Nieprawidłowy dzień posiłku.")
                repository.updateMealPlan(latest.copy(servings = servings, calories = calories,
                    isCompleted = true, completedDate = date))
            }
            onResult(saved, if (saved) null else _mealUndoMessage.value ?: "Nie udało się zapisać ilości.")
        }
    }

    fun updateMealPlanServings(meal: MealPlanEntity, newServings: Float) {
        viewModelScope.launch {
            undoableMealEdit(meal.id, "Zmieniono liczbę porcji") {
                val latestMeal = repository.allMealPlans.first().find { it.id == meal.id } ?: return@undoableMealEdit
                val safeServings = MealPlannerSupport.safeServings(newServings)
                val allRecipesList = repository.allRecipes.first()
                val allIngredientsList = repository.allRecipeIngredients.first()
                val recipe = allRecipesList.find { it.id == latestMeal.recipeId }
                    ?: allRecipesList.find { it.title.equals(latestMeal.recipeTitle, ignoreCase = true) }
                val tempMeal = latestMeal.copy(servings = safeServings)
                val recalculated = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
                    meal = tempMeal,
                    recipe = recipe,
                    allIngredientEntities = allIngredientsList
                )
                val updatedCalories = recalculated.calories ?: when {
                    recipe != null && recipe.calories > 0 -> (recipe.calories * safeServings).roundToInt()
                    latestMeal.calories > 0 && latestMeal.servings > 0f -> ((latestMeal.calories / latestMeal.servings) * safeServings).roundToInt()
                    else -> 0
                }
                repository.updateMealPlan(
                    latestMeal.copy(
                        servings = safeServings,
                        calories = updatedCalories
                    )
                )
            }
        }
    }

    fun changeMealPlanRecipe(meal: MealPlanEntity, newRecipe: RecipeEntity) {
        viewModelScope.launch {
            undoableMealEdit(meal.id, "Zmieniono przepis posiłku") {
                val latestMeal = repository.allMealPlans.first().find { it.id == meal.id } ?: return@undoableMealEdit
                val safeServings = MealPlannerSupport.safeServings(latestMeal.servings)
                val updatedCalories = if (newRecipe.calories > 0) (newRecipe.calories * safeServings).roundToInt() else 0
                if (latestMeal.id > 0L) {
                    repository.deleteIngredientsForRecipe(-latestMeal.id)
                }
                repository.updateMealPlan(
                    latestMeal.copy(
                        recipeId = newRecipe.id,
                        recipeTitle = newRecipe.title,
                        calories = updatedCalories
                    )
                )
            }
        }
    }

    fun moveMealPlan(meal: MealPlanEntity, targetDayOfWeek: String, targetMealType: String = meal.mealType, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val saved = undoableMealEdit(meal.id, "Przeniesiono posiłek") {
                val latestMeal = repository.allMealPlans.first().find { it.id == meal.id } ?: error("Posiłek został usunięty")
                val normalizedDay = MealPlannerSupport.dateForDay(targetDayOfWeek)?.toString() ?: error("Nieprawidłowa data posiłku")
                val normalizedMealType = MealPlannerSupport.normalizeMealType(targetMealType)
                repository.updateMealPlan(
                    latestMeal.copy(
                        dayOfWeek = normalizedDay,
                        mealType = normalizedMealType
                    )
                )
            }
            onResult(saved, if (saved) null else "Nie udało się przenieść posiłku.")
        }
    }

    fun deleteMealPlan(meal: MealPlanEntity) {
        viewModelScope.launch {
            undoableMealEdit(meal.id, "Usunięto posiłek") {
                val latestMeal = repository.allMealPlans.first().find { it.id == meal.id } ?: return@undoableMealEdit
                if (latestMeal.id > 0L) {
                    repository.deleteIngredientsForRecipe(-latestMeal.id)
                }
                repository.deleteMealPlan(latestMeal)
            }
        }
    }

    // --- Ingredient Substitution State & Operations ---
    var customAiSubstituteCaller: (suspend (String, Boolean) -> Result<String>)? = null

    private val _offSearchResults = MutableStateFlow<List<SubstituteCandidate>>(emptyList())
    val offSearchResults: StateFlow<List<SubstituteCandidate>> = _offSearchResults.asStateFlow()

    private val _isSearchingOff = MutableStateFlow(false)
    val isSearchingOff: StateFlow<Boolean> = _isSearchingOff.asStateFlow()

    private val _offSearchError = MutableStateFlow<String?>(null)
    val offSearchError: StateFlow<String?> = _offSearchError.asStateFlow()

    private val _scannedProductResult = MutableStateFlow<SubstituteCandidate?>(null)
    val scannedProductResult: StateFlow<SubstituteCandidate?> = _scannedProductResult.asStateFlow()

    private val _isScanningBarcodeOff = MutableStateFlow(false)
    val isScanningBarcodeOff: StateFlow<Boolean> = _isScanningBarcodeOff.asStateFlow()

    private val _barcodeLookupError = MutableStateFlow<String?>(null)
    val barcodeLookupError: StateFlow<String?> = _barcodeLookupError.asStateFlow()

    private val _aiSubstituteSuggestions = MutableStateFlow<List<SubstituteCandidate>>(emptyList())
    val aiSubstituteSuggestions: StateFlow<List<SubstituteCandidate>> = _aiSubstituteSuggestions.asStateFlow()

    private val _isLoadingAiSubstitutes = MutableStateFlow(false)
    val isLoadingAiSubstitutes: StateFlow<Boolean> = _isLoadingAiSubstitutes.asStateFlow()

    private val _aiSubstitutesError = MutableStateFlow<String?>(null)
    val aiSubstitutesError: StateFlow<String?> = _aiSubstitutesError.asStateFlow()

    fun resetSubstitutionTransientState() {
        _offSearchResults.value = emptyList()
        _offSearchError.value = null
        _isSearchingOff.value = false
        _scannedProductResult.value = null
        _barcodeLookupError.value = null
        _isScanningBarcodeOff.value = false
        _aiSubstituteSuggestions.value = emptyList()
        _aiSubstitutesError.value = null
        _isLoadingAiSubstitutes.value = false
    }

    fun searchOpenFoodFacts(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            _offSearchResults.value = emptyList()
            _offSearchError.value = "Wpisz nazwę produktu do wyszukania w Open Food Facts."
            return
        }
        _isSearchingOff.value = true
        _offSearchError.value = null
        viewModelScope.launch {
            try {
                val response = OpenFoodFactsClient.searchProductsWithFallback(cleanQuery)
                val mapped = response.products.orEmpty()
                    .mapNotNull { IngredientSubstitutionSupport.fromOpenFoodFactsProduct(it, SubstituteOptionMode.OPEN_FOOD_FACTS) }
                _offSearchResults.value = mapped
                if (mapped.isEmpty()) {
                    _offSearchError.value = "Brak produktów w bazie Open Food Facts dla zapytania: \"$cleanQuery\"."
                }
            } catch (e: Exception) {
                _offSearchResults.value = emptyList()
                _offSearchError.value = "Błąd połączenia z Open Food Facts: ${e.localizedMessage ?: "Spróbuj ponownie."}"
            } finally {
                _isSearchingOff.value = false
            }
        }
    }

    fun addScannedProductToMeal(product: SubstituteCandidate, quantity: Float, unit: String,
        day: String, mealType: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                require(MealPlannerSupport.isPlanDay(day))
                require(MealPlannerSupport.MEAL_CATEGORIES.any { it.canonicalName == mealType })
                val recipe = ScannedProductMealSupport.recipe(product, quantity, unit)
                recipeEditMutex.withLock {
                    if (listOf(product.caloriesPer100g, product.proteinPer100g, product.carbsPer100g, product.fatPer100g).all { it != null && it.isFinite() && it >= 0 }) {
                        repository.saveImportedRecipe(QuickUseSupport.productRecipe(product),
                            listOf(ScannedProductMealSupport.ingredient(product, 100f, product.defaultUnit)))
                    }
                    repository.addScannedProductMeal(recipe, ScannedProductMealSupport.ingredient(product, quantity, unit), day, mealType)
                    usability.rememberProduct(product, quantity, unit)
                    rebuildShoppingListInternal()
                }
                onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się dodać produktu do posiłku.") }
        }
    }

    fun lookupProductByBarcode(
        rawBarcode: String,
        onFound: ((SubstituteCandidate) -> Unit)? = null
    ) {
        val cleanCode = rawBarcode.trim()
        if (!IngredientSubstitutionSupport.isValidSupportedBarcode(cleanCode)) {
            _scannedProductResult.value = null
            _barcodeLookupError.value = "Nieprawidłowy kod kreskowy \"$cleanCode\". Obsługiwane formaty: EAN-13, EAN-8, UPC."
            return
        }
        _isScanningBarcodeOff.value = true
        _barcodeLookupError.value = null
        _scannedProductResult.value = null
        viewModelScope.launch {
            try {
                val response = OpenFoodFactsClient.service.getProductByBarcode(cleanCode)
                if (response.status == 1 && response.product != null) {
                    val candidate = IngredientSubstitutionSupport.fromOpenFoodFactsProduct(
                        response.product,
                        SubstituteOptionMode.BARCODE_SCAN
                    )?.copy(
                        barcode = response.product.code?.takeIf { it.isNotBlank() } ?: cleanCode
                    )
                    if (candidate != null) {
                        _scannedProductResult.value = candidate
                        onFound?.invoke(candidate)
                    } else {
                        _barcodeLookupError.value = "Produkt o kodzie $cleanCode w Open Food Facts nie posiada pełnych danych nazwy."
                    }
                } else {
                    _barcodeLookupError.value = "Nie znaleziono produktu o kodzie kreskowym $cleanCode w bazie Open Food Facts."
                }
            } catch (e: Exception) {
                _barcodeLookupError.value = "Błąd pobierania produktu ($cleanCode) z Open Food Facts: ${e.localizedMessage ?: "Błąd sieci"}"
            } finally {
                _isScanningBarcodeOff.value = false
            }
        }
    }

    fun requestAiSubstitutes(ingredientRaw: String, recipeTitle: String = "") {
        val parsed = MealPlannerSupport.parseSingleIngredientLine(ingredientRaw)
        val ingredientName = parsed.name.ifBlank { ingredientRaw.trim() }
        val defaultQty = parsed.baseQuantity ?: 100f
        val defaultUnit = IngredientSubstitutionSupport.normalizeMassOrVolumeUnit(parsed.unit)

        _isLoadingAiSubstitutes.value = true
        _aiSubstitutesError.value = null
        _aiSubstituteSuggestions.value = emptyList()

        viewModelScope.launch {
            try {
                val contextInfo = if (recipeTitle.isNotBlank()) " w przepisie \"$recipeTitle\"" else ""
                val prompt = """
                Zaproponuj od 3 do 5 zdrowych zamienników dla składnika "$ingredientName" (ilość bazowa: ${MealPlannerSupport.formatQuantityNumber(defaultQty)} $defaultUnit)$contextInfo.
                Zwróć odpowiedź WYŁĄCZNIE jako poprawną tablicę JSON zawierającą od 3 do 5 obiektów:
                [
                  {
                    "name": "Nazwa zamiennika po polsku",
                    "reason": "Krótkie uzasadnienie dietetyczne i kulinarne",
                    "suggestedQuantity": ${defaultQty.toInt()},
                    "unit": "$defaultUnit",
                    "caloriesPer100g": 342,
                    "proteinPer100g": 12.3,
                    "carbsPer100g": 69.0,
                    "fatPer100g": 1.3
                  }
                ]
                """.trimIndent()

                val caller = customAiSubstituteCaller
                val result = if (caller != null) {
                    caller(prompt, true)
                } else {
                    callGemini(prompt, jsonMode = true)
                }

                result.fold(
                    onSuccess = { responseText ->
                        val parsedCandidates = IngredientSubstitutionSupport.parseAiSubstituteSuggestions(
                            rawResponse = responseText,
                            defaultQuantity = defaultQty,
                            defaultUnit = defaultUnit
                        )
                        if (parsedCandidates.isNotEmpty()) {
                            _aiSubstituteSuggestions.value = parsedCandidates
                        } else {
                            _aiSubstitutesError.value = "AI nie zwróciło poprawnych propozycji zamienników. Skorzystaj z wyszukiwarki Open Food Facts lub lokalnej bazy."
                        }
                    },
                    onFailure = { err ->
                        _aiSubstituteSuggestions.value = emptyList()
                        _aiSubstitutesError.value = err.message ?: "Brak dostępu do Gemini AI. Skorzystaj z Open Food Facts lub bazy lokalnej."
                    }
                )
            } catch (e: Exception) {
                _aiSubstituteSuggestions.value = emptyList()
                _aiSubstitutesError.value = e.localizedMessage ?: "Błąd zapytania AI."
            } finally {
                _isLoadingAiSubstitutes.value = false
            }
        }
    }


    fun addMealIngredient(meal: MealPlanEntity, candidate: SubstituteCandidate, quantity: Float, unit: String,
        onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val saved = undoableMealEdit(meal.id, "Dodano składnik") {
                    require(candidate.name.isNotBlank() && quantity.isFinite() && quantity > 0 && quantity <= 10000)
                    val current = repository.allMealPlans.first().find { it.id == meal.id } ?: error("Posiłek już nie istnieje.")
                    val known = IngredientSubstitutionSupport.lookupKnownIngredientNutrition(candidate.name)
                    val resolved = candidate.copy(caloriesPer100g = candidate.caloriesPer100g ?: known?.caloriesPer100g,
                        proteinPer100g = candidate.proteinPer100g ?: known?.proteinPer100g,
                        carbsPer100g = candidate.carbsPer100g ?: known?.carbsPer100g,
                        fatPer100g = candidate.fatPer100g ?: known?.fatPer100g)
                    require(resolved.caloriesPer100g?.let { it.isFinite() && it >= 0f } == true)
                    repository.insertIngredient(IngredientSubstitutionSupport.encodeMealSubstitutionEntity(
                        mealPlanId = current.id, originalIngredientName = IngredientSubstitutionSupport.EXTRA_PREFIX + java.util.UUID.randomUUID(),
                        originalBaseQuantity = 0f, originalUnit = unit, candidate = resolved,
                        baseQuantityPerServing = quantity / MealPlannerSupport.safeServings(current.servings), unit = unit))
                    refreshEditedMealNutrition(current)
                }
                if (saved) usability.rememberProduct(candidate, quantity, unit)
                onResult(saved, if (saved) null else _mealUndoMessage.value)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult(false, "Nie udało się dodać składnika. Spróbuj ponownie.") }
        }
    }

    fun removeMealIngredient(meal: MealPlanEntity, ingredient: ResolvedMealIngredientItem, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val saved = undoableMealEdit(meal.id, "Usunięto składnik") {
                    val current = repository.allMealPlans.first().find { it.id == meal.id } ?: error("Posiłek już nie istnieje.")
                    val rows = repository.allRecipeIngredients.first()
                    val existing = IngredientSubstitutionSupport.getSubstitutionsForMeal(current.id, rows)
                        .find { it.originalIngredientName == ingredient.originalIngredientName }
                    if (existing != null && IngredientSubstitutionSupport.isExtra(existing)) {
                        rows.find { it.id == existing.entityId }?.let { repository.deleteIngredient(it) }
                    } else {
                        // A zero-quantity override removes this ingredient only from this meal.
                        repository.insertIngredient(IngredientSubstitutionSupport.encodeMealSubstitutionEntity(
                            existingId = existing?.entityId ?: 0L, mealPlanId = current.id,
                            originalIngredientName = ingredient.originalIngredientName,
                            originalBaseQuantity = ingredient.originalBaseQuantity ?: existing?.originalBaseQuantity ?: 0f,
                            originalUnit = ingredient.originalUnit,
                            candidate = SubstituteCandidate(name = ingredient.ingredientName, caloriesPer100g = 0f,
                                proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 0f),
                            baseQuantityPerServing = 0f, unit = "g"))
                    }
                    refreshEditedMealNutrition(current)
                }
                onResult(saved, if (saved) null else _mealUndoMessage.value)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult(false, "Nie udało się usunąć składnika. Spróbuj ponownie.") }
        }
    }

    private suspend fun refreshEditedMealNutrition(meal: MealPlanEntity) {
        val recipes = repository.allRecipes.first()
        val recipe = recipes.find { it.id == meal.recipeId } ?: recipes.find { it.title == meal.recipeTitle }
        val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, repository.allRecipeIngredients.first())
        // Read again to preserve an eaten status changed while editing ingredients.
        val latest = repository.allMealPlans.first().find { it.id == meal.id } ?: return
        nutrition.calories?.let { repository.updateMealPlan(latest.copy(calories = it)) }
    }

    fun editMealIngredientQuantity(meal: MealPlanEntity, ingredient: ResolvedMealIngredientItem, quantity: Float) {
        if (!quantity.isFinite() || quantity <= 0 || quantity > 100000) return
        val profile = IngredientSubstitutionSupport.lookupKnownIngredientNutrition(ingredient.ingredientName, recipeIngredients.value.filter { it.recipeId == meal.recipeId })
        val sub = ingredient.substitution
        val candidate = SubstituteCandidate(name = ingredient.ingredientName,
            barcode = sub?.barcode.orEmpty(), brand = sub?.brand.orEmpty(), imageUrl = sub?.imageUrl.orEmpty(),
            caloriesPer100g = sub?.caloriesPer100g ?: profile?.caloriesPer100g,
            proteinPer100g = sub?.proteinPer100g ?: profile?.proteinPer100g,
            carbsPer100g = sub?.carbsPer100g ?: profile?.carbsPer100g,
            fatPer100g = sub?.fatPer100g ?: profile?.fatPer100g)
        replaceIngredientInMealPlan(meal, "${ingredient.originalIngredientName} — ${ingredient.originalBaseQuantity ?: 0f} ${ingredient.originalUnit}", candidate, quantity, if (ingredient.unit in listOf("ml", "l")) "ml" else "g")
    }

    fun replaceIngredientInMealPlan(
        meal: MealPlanEntity,
        originalIngredientRaw: String,
        candidate: SubstituteCandidate,
        enteredQuantity: Float,
        enteredUnit: String,
        alsoSaveToOriginalRecipe: Boolean = false,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val saved = undoableMealEdit(meal.id, "Zmieniono składnik") {
                require(enteredQuantity.isFinite() && enteredQuantity > 0 && enteredQuantity <= 100000) { "Podaj poprawną ilość składnika." }
                val latestMeal = repository.allMealPlans.first().find { it.id == meal.id } ?: error("Posiłek już nie istnieje.")
                val allRecipesList = repository.allRecipes.first()
                val allIngredientsList = repository.allRecipeIngredients.first()
                val recipe = allRecipesList.find { it.id == latestMeal.recipeId }
                    ?: allRecipesList.find { it.title.equals(latestMeal.recipeTitle, ignoreCase = true) }

                val servings = MealPlannerSupport.safeServings(latestMeal.servings)
                val safeEnteredQty = enteredQuantity.coerceAtLeast(0.01f)
                val normalizedUnit = IngredientSubstitutionSupport.normalizeMassOrVolumeUnit(enteredUnit)

                val parsedTarget = MealPlannerSupport.parseSingleIngredientLine(originalIngredientRaw)
                val targetName = parsedTarget.name.ifBlank { originalIngredientRaw.trim() }

                val existingSubs = IngredientSubstitutionSupport.getSubstitutionsForMeal(latestMeal.id, allIngredientsList)
                val matchedExistingSub = existingSubs.find { sub ->
                    IngredientSubstitutionSupport.normalizeIngredientKey(sub.originalIngredientName) == IngredientSubstitutionSupport.normalizeIngredientKey(targetName)
                } ?: existingSubs.find { sub -> !IngredientSubstitutionSupport.isExtra(sub) && IngredientSubstitutionSupport.ingredientNamesMatch(sub.originalIngredientName, targetName) }

                val recipeStructured = if (recipe != null) {
                    allIngredientsList.filter { it.recipeId == recipe.id }
                } else {
                    emptyList()
                }
                val baseRecipeIngredients = MealPlannerSupport.parseRecipeIngredients(
                    ingredientsText = recipe?.ingredients.orEmpty(),
                    fallbackEntities = recipeStructured
                )
                val originalKey = IngredientSubstitutionSupport.normalizeIngredientKey(matchedExistingSub?.originalIngredientName ?: targetName)
                val matchedBaseIng = baseRecipeIngredients.find {
                    IngredientSubstitutionSupport.normalizeIngredientKey(it.name) == originalKey
                } ?: baseRecipeIngredients.find {
                    IngredientSubstitutionSupport.ingredientNamesMatch(it.name, matchedExistingSub?.originalIngredientName ?: targetName)
                }

                val trueOrigName = matchedExistingSub?.originalIngredientName
                    ?: matchedBaseIng?.name
                    ?: targetName
                val trueOrigBaseQty = matchedExistingSub?.originalBaseQuantity
                    ?: matchedBaseIng?.baseQuantity
                    ?: ((parsedTarget.baseQuantity ?: safeEnteredQty) / servings)
                val trueOrigUnit = matchedExistingSub?.originalUnit
                    ?: matchedBaseIng?.unit?.ifBlank { normalizedUnit }
                    ?: normalizedUnit

                val newBaseQuantityPerServing = safeEnteredQty / servings

                val subEntity = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(
                    existingId = matchedExistingSub?.entityId ?: 0L,
                    mealPlanId = latestMeal.id,
                    originalIngredientName = trueOrigName,
                    originalBaseQuantity = trueOrigBaseQty,
                    originalUnit = trueOrigUnit,
                    candidate = candidate,
                    baseQuantityPerServing = newBaseQuantityPerServing,
                    unit = normalizedUnit
                )
                repository.insertIngredient(subEntity)

                if (alsoSaveToOriginalRecipe && recipe != null) {
                    val updatedRecipe = IngredientSubstitutionSupport.replaceIngredientInRecipeEntity(
                        recipe = recipe,
                        originalIngredientRaw = "$trueOrigName — ${MealPlannerSupport.formatQuantityNumber(trueOrigBaseQty)} $trueOrigUnit",
                        candidate = candidate,
                        enteredQuantity = newBaseQuantityPerServing,
                        enteredUnit = normalizedUnit,
                        recipeStructuredIngredients = recipeStructured
                    )
                    repository.updateRecipe(updatedRecipe)
                    val oldRows = recipeStructured.filter { IngredientSubstitutionSupport.normalizeIngredientKey(it.foodName) in
                        listOf(IngredientSubstitutionSupport.normalizeIngredientKey(trueOrigName), IngredientSubstitutionSupport.normalizeIngredientKey(candidate.name)) }
                    oldRows.forEach { repository.deleteIngredient(it) }
                    repository.insertIngredient(RecipeIngredientEntity(recipeId = recipe.id, foodName = candidate.name.trim(),
                        quantity = newBaseQuantityPerServing, unit = normalizedUnit, grams = newBaseQuantityPerServing,
                        caloriesPer100g = candidate.caloriesPer100g ?: -1f, proteinPer100g = candidate.proteinPer100g ?: -1f,
                        carbsPer100g = candidate.carbsPer100g ?: -1f, fatPer100g = candidate.fatPer100g ?: -1f))
                    // The recipe now contains the replacement name. Keep its meal override attached to that name.
                    val inserted = repository.allRecipeIngredients.first().find { it.recipeId == -latestMeal.id && it.foodName == subEntity.foodName }
                        ?: error("Nie udało się odczytać zapisanego składnika.")
                    repository.insertIngredient(IngredientSubstitutionSupport.encodeMealSubstitutionEntity(existingId = inserted.id,
                        mealPlanId = latestMeal.id, originalIngredientName = candidate.name.trim(), originalBaseQuantity = newBaseQuantityPerServing,
                        originalUnit = normalizedUnit, candidate = candidate, baseQuantityPerServing = newBaseQuantityPerServing, unit = normalizedUnit))
                }

                val refreshedIngredients = repository.allRecipeIngredients.first()
                val refreshedRecipes = repository.allRecipes.first()
                val refreshedRecipe = refreshedRecipes.find { it.id == latestMeal.recipeId }
                    ?: refreshedRecipes.find { it.title.equals(latestMeal.recipeTitle, ignoreCase = true) }

                val updatedNutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
                    meal = latestMeal,
                    recipe = refreshedRecipe,
                    allIngredientEntities = refreshedIngredients
                )
                if (updatedNutrition.calories != null) {
                    repository.updateMealPlan(latestMeal.copy(calories = updatedNutrition.calories))
                }
            }
            if (saved) usability.rememberProduct(candidate, enteredQuantity, enteredUnit)
            onResult(saved, if (saved) null else _mealUndoMessage.value)
        }
    }

    fun updateMealSubstitutionQuantity(
        meal: MealPlanEntity,
        substitution: MealIngredientSubstitution,
        newQuantityForMeal: Float,
        newUnit: String = substitution.unit
    ) {
        val candidate = SubstituteCandidate(
            name = substitution.replacementName,
            brand = substitution.brand,
            barcode = substitution.barcode,
            imageUrl = substitution.imageUrl,
            caloriesPer100g = substitution.caloriesPer100g,
            proteinPer100g = substitution.proteinPer100g,
            carbsPer100g = substitution.carbsPer100g,
            fatPer100g = substitution.fatPer100g,
            defaultQuantity = newQuantityForMeal,
            defaultUnit = newUnit
        )
        replaceIngredientInMealPlan(
            meal = meal,
            originalIngredientRaw = substitution.originalIngredientName,
            candidate = candidate,
            enteredQuantity = newQuantityForMeal,
            enteredUnit = newUnit,
            alsoSaveToOriginalRecipe = false
        )
    }

    fun replaceIngredientInRecipe(
        recipe: RecipeEntity,
        originalIngredientRaw: String,
        candidate: SubstituteCandidate,
        enteredQuantity: Float,
        enteredUnit: String
    ) {
        viewModelScope.launch {
            val allIngredientsList = repository.allRecipeIngredients.first()
            val recipeStructured = allIngredientsList.filter { it.recipeId == recipe.id }
            val normalizedUnit = IngredientSubstitutionSupport.normalizeMassOrVolumeUnit(enteredUnit)
            val safeQty = enteredQuantity.coerceAtLeast(1f)

            val updatedRecipe = IngredientSubstitutionSupport.replaceIngredientInRecipeEntity(
                recipe = recipe,
                originalIngredientRaw = originalIngredientRaw,
                candidate = candidate,
                enteredQuantity = safeQty,
                enteredUnit = normalizedUnit,
                recipeStructuredIngredients = recipeStructured
            )
            repository.updateRecipe(updatedRecipe)

            val parsedOrig = MealPlannerSupport.parseSingleIngredientLine(originalIngredientRaw)
            val origName = parsedOrig.name.ifBlank { originalIngredientRaw.trim() }
            val existingEntity = recipeStructured.find {
                IngredientSubstitutionSupport.ingredientNamesMatch(it.foodName, origName)
            }
            if (existingEntity != null) {
                repository.updateIngredient(
                    existingEntity.copy(
                        foodName = candidate.name.trim(),
                        quantity = safeQty,
                        unit = normalizedUnit,
                        grams = safeQty,
                        caloriesPer100g = candidate.caloriesPer100g ?: -1f,
                        proteinPer100g = candidate.proteinPer100g ?: -1f,
                        carbsPer100g = candidate.carbsPer100g ?: -1f,
                        fatPer100g = candidate.fatPer100g ?: -1f
                    )
                )
            }

            // Update meal plans using this recipe that haven't overridden this ingredient
            val allMeals = repository.allMealPlans.first()
            val refreshedIngredients = repository.allRecipeIngredients.first()
            for (meal in allMeals.filter { it.recipeId == recipe.id }) {
                val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
                    meal = meal,
                    recipe = updatedRecipe,
                    allIngredientEntities = refreshedIngredients
                )
                if (nutrition.calories != null) {
                    repository.updateMealPlan(meal.copy(calories = nutrition.calories))
                }
            }

            rebuildShoppingListInternal()
        }
    }

    fun toggleMealPlanCompleted(meal: MealPlanEntity) {
        if (meal.id in _mealStatusBusy.value) return
        _mealStatusBusy.value = _mealStatusBusy.value + meal.id
        viewModelScope.launch {
            try {
            var event: MealMotionEvent? = null
            undoableMealEdit(meal.id, "Zmieniono status posiłku", onCommitted = {
                event?.let { _mealMotionEvents.tryEmit(it) }
            }) {
                val current = repository.allMealPlans.first().find { it.id == meal.id } ?: return@undoableMealEdit
                val date = MealPlannerSupport.dateForDay(current.dayOfWeek)?.toString() ?: return@undoableMealEdit
                val completed = !(current.isCompleted && current.completedDate == date)
                val recipe = repository.allRecipes.first().firstOrNull { it.id == current.recipeId }
                val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(current, recipe, repository.allRecipeIngredients.first())
                event = MealMotionEvent(current.id, date, completed)
                repository.updateMealPlan(current.copy(calories = nutrition.calories ?: current.calories,
                    isCompleted = completed, completedDate = if (completed) date else null))
            }
            } finally { _mealStatusBusy.value = _mealStatusBusy.value - meal.id }
        }
    }

    private val diaryDate = MutableStateFlow(java.time.LocalDate.now().toString())
    val currentDate: StateFlow<String> = diaryDate.asStateFlow()
    val dashboardNutrition: StateFlow<DashboardNutritionSnapshot?> = kotlinx.coroutines.flow.combine(
        repository.allMealPlans, repository.allRecipes, repository.allRecipeIngredients, diaryDate
    ) { meals, recipes, ingredients, date ->
        val day = java.time.LocalDate.parse(date)
        DashboardNutritionSnapshot(date, IngredientSubstitutionSupport.totalMealNutrition(meals.filter {
            it.isCompleted && it.completedDate == date && MealPlannerSupport.dateForDay(it.dayOfWeek, day) == day
        }, recipes, ingredients))
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val habits = HabitsDiary(application, repository, currentDate, viewModelScope)
    fun refreshCurrentDate() { diaryDate.value = java.time.LocalDate.now().toString() }
    val beverageLogs = kotlinx.coroutines.flow.combine(repository.beverageLogs, diaryDate) { logs, date ->
        logs.filter { it.date == date }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val waterDrankMl = beverageLogs.map { logs -> logs.sumOf { it.amountMl } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val creatineLogs = repository.creatineLogs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val creatineTakenToday = kotlinx.coroutines.flow.combine(creatineLogs, diaryDate) { logs, date ->
        logs.any { it.date == date }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            while (true) {
                diaryDate.value = java.time.LocalDate.now().toString()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    fun addBeverage(amount: Int) = addSpecificBeverage("Woda", amount)

    fun addSpecificBeverage(type: String, amount: Int, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                diaryDate.value = java.time.LocalDate.now().toString()
                repository.insertBeverageLog(com.example.data.BeverageLogEntity(
                    date = diaryDate.value, type = type.trim(), amountMl = amount))
            } catch (_: Exception) { onError("Nie udało się zapisać napoju. Podaj 1–10000 ml i nazwę do 40 znaków.") }
        }
    }

    fun deleteBeverageLog(id: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try { repository.deleteBeverageLog(id) }
            catch (_: Exception) { onError("Nie udało się usunąć napoju.") }
        }
    }

    fun markCreatineTaken(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                diaryDate.value = java.time.LocalDate.now().toString()
                repository.markCreatineTaken(diaryDate.value)
            } catch (_: Exception) { onError("Nie udało się zapisać kreatyny.") }
        }
    }

    fun deleteCreatineLog(date: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try { repository.deleteCreatineLog(date) }
            catch (_: Exception) { onError("Nie udało się usunąć wpisu kreatyny.") }
        }
    }

    val photoFillProgress = MutableStateFlow<String?>(null)
    val photoFillResult = MutableStateFlow<String?>(null)
    fun fillMissingRecipePhotos(context: Context, automatic: Boolean = false, improveExisting: Boolean = false) {
        viewModelScope.launch {
            RecipePhotoSettings(context).enabled = true
            RecipePhotoQueue.enqueue(context, repository.allRecipes.first(), retry = !automatic, improveExisting = improveExisting)
            photoFillResult.value = "Kolejka zdjęć działa w tle: Pixabay, następnie Wikimedia bez klucza. Gdy brak pewnego dopasowania, próbujemy ilustracji AI w dostępnym limicie. Własne zdjęcia z telefonu pozostają bez zmian."
        }
    }

    fun cancelRecipePhotoFill() {
        val context = getApplication<Application>()
        RecipePhotoSettings(context).enabled = false
        androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag(RecipePhotoQueue.TAG)
        photoFillProgress.value = null
        photoFillResult.value = "Automatyczne zdjęcia wstrzymane."
    }

    fun setRecipePhoto(context: Context, recipe: RecipeEntity, photo: RecipePhotoCandidate,
        onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val localPath = RecipePhotoSearch.savePhoto(context, photo)
                val current = repository.allRecipes.first().find { it.id == recipe.id } ?: error("Przepis został usunięty.")
                require(current.imageUrl == recipe.imageUrl) { "Zdjęcie zmieniło się w trakcie pobierania." }
                val description = current.description.substringBefore("\n\nZdjęcie: ") +
                    "\n\nZdjęcie: ${photo.author}; ${photo.license}; ${photo.pageUrl}" +
                    (if (photo.licenseUrl.isNotBlank()) "; ${photo.licenseUrl}" else "")
                val saved = AppDatabase.getDatabase(context).dietaDao().saveAutomaticPhoto(current.id, current.imageUrl,
                    current.title, current.ingredients, current.description, localPath, description)
                check(saved == 1) { "Przepis zmienił się w trakcie zapisywania zdjęcia." }
                onResult(true, null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult(false, "Nie udało się pobrać lub zapisać zdjęcia. Sprawdź internet i spróbuj ponownie.") }
        }
    }

    fun refreshRecipePhotoFromSource(recipe: RecipeEntity, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val source = parseValidUriOrNull(recipe.source)
                require(source != null)
                val doc = fetchHtmlDocument(source.toString())
                val imported = extractRecipeFromDocumentWithoutAi(doc, source.toString())
                val image = imported?.imageUrl.orEmpty()
                require(image.isNotBlank())
                val imageUrl = java.net.URI(source.toString()).resolve(image).toString()
                require(java.net.URI(imageUrl).scheme == "https")
                val localPath = RecipePhotoSearch.saveSourcePhoto(getApplication<Application>(), imageUrl)
                val current = repository.allRecipes.first().find { it.id == recipe.id } ?: error("Przepis został usunięty.")
                require(current.imageUrl == recipe.imageUrl) { "Zdjęcie zmieniło się w trakcie pobierania." }
                val saved = AppDatabase.getDatabase(getApplication<Application>()).dietaDao().saveAutomaticPhoto(current.id, current.imageUrl,
                    current.title, current.ingredients, current.description, localPath,
                    current.description.substringBefore("\n\nZdjęcie: ") + "\n\nZdjęcie: ze strony przepisu; ${recipe.source}")
                check(saved == 1) { "Przepis zmienił się w trakcie zapisywania zdjęcia." }
                onResult(true, null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult(false, "Strona przepisu nie udostępniła zdjęcia. Wyszukaj i wybierz fotografię potrawy.") }
        }
    }

    private val recipeEditMutex = kotlinx.coroutines.sync.Mutex()

    fun addRecipeIngredient(recipe: RecipeEntity, candidate: SubstituteCandidate, quantity: Float, unit: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                require(candidate.name.isNotBlank() && quantity.isFinite() && quantity > 0 && quantity <= 10000 && unit in listOf("g", "ml"))
                recipeEditMutex.withLock {
                    val known = IngredientSubstitutionSupport.lookupKnownIngredientNutrition(candidate.name)
                    val resolved = candidate.copy(caloriesPer100g = candidate.caloriesPer100g ?: known?.caloriesPer100g,
                        proteinPer100g = candidate.proteinPer100g ?: known?.proteinPer100g,
                        carbsPer100g = candidate.carbsPer100g ?: known?.carbsPer100g,
                        fatPer100g = candidate.fatPer100g ?: known?.fatPer100g)
                    require(resolved.caloriesPer100g?.let { it.isFinite() && it >= 0f } == true)
                    val current = repository.allRecipes.first().first { it.id == recipe.id }
                    val structured = repository.allRecipeIngredients.first().filter { it.recipeId == recipe.id }
                    val line = "${candidate.name.trim()} — ${MealPlannerSupport.formatQuantityNumber(quantity)} $unit"
                    val updated = IngredientSubstitutionSupport.addIngredientNutrition(current, resolved, quantity).copy(
                        ingredients = listOf(current.ingredients.trim(), line).filter { it.isNotBlank() }.joinToString("\n"),
                        description = current.description + if (current.calories <= 0 || listOf(resolved.caloriesPer100g, resolved.proteinPer100g, resolved.carbsPer100g, resolved.fatPer100g).any { it == null }) "\nNiepełne wartości: dodano składnik bez danych odżywczych." else "\nDodany składnik: wartości podane przez użytkownika z etykiety.")
                    val added = RecipeIngredientEntity(recipeId = recipe.id, foodName = candidate.name.trim(), quantity = quantity, grams = quantity, unit = unit,
                        caloriesPer100g = resolved.caloriesPer100g ?: -1f, proteinPer100g = resolved.proteinPer100g ?: -1f,
                        carbsPer100g = resolved.carbsPer100g ?: -1f, fatPer100g = resolved.fatPer100g ?: -1f)
                    repository.updateRecipeWithIngredients(updated, structured + added)
                    refreshPlannedRecipeNutrition(updated)
                    rebuildShoppingListInternal()
                }
                onResult(true, null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult(false, "Nie udało się dodać składnika. Sprawdź nazwę i ilość.") }
        }
    }

    private suspend fun refreshPlannedRecipeNutrition(recipe: RecipeEntity) {
        val ingredients = repository.allRecipeIngredients.first()
        for (meal in repository.allMealPlans.first().filter { it.recipeId == recipe.id }) {
            val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, ingredients)
            repository.updateMealPlan(meal.copy(recipeTitle = recipe.title, calories = nutrition.calories ?: meal.calories))
        }
    }

    fun renameRecipe(recipeId: Long, title: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            recipeEditMutex.withLock {
                try {
                    require(title.isNotBlank())
                    val current = repository.allRecipes.first().find { it.id == recipeId }
                        ?: error("Przepis został usunięty.")
                    val updated = current.copy(title = title.trim())
                    repository.updateRecipe(updated)
                    refreshPlannedRecipeNutrition(updated)
                    onResult(null)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { onResult("Nie udało się zmienić nazwy przepisu.") }
            }
        }
    }

    fun deleteRecipe(recipe: RecipeEntity) {
        viewModelScope.launch { recipeEditMutex.withLock { repository.deleteRecipe(recipe); rebuildShoppingListInternal() } }
    }

    fun removeExactDuplicateRecipes(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            recipeEditMutex.withLock {
                var removed = 0
                val groups = repository.allRecipes.first().groupBy {
                    listOf(it.title.trim().lowercase(Locale.ROOT), it.ingredients.trim(), it.instructions.trim(), it.calories.toString(), it.protein.toString(), it.carbs.toString(), it.fat.toString(), it.description.lineSequence().firstOrNull { line -> line.startsWith("Porcje:") }.orEmpty())
                }
                for (group in groups.values.filter { it.size > 1 }) {
                    val keeper = group.minBy { it.id }
                    for (duplicate in group.filter { it.id != keeper.id }) {
                        repository.mergeDuplicateRecipe(duplicate, keeper); removed++
                    }
                }
                rebuildShoppingListInternal(); onResult(removed)
            }
        }
    }

    fun editRecipeIngredient(recipe: RecipeEntity, originalLine: String, quantity: Float?) {
        val parsed = MealPlannerSupport.parseRecipeIngredients(recipe.ingredients)
        var matched = false
        val updated = parsed.mapNotNull {
            if (matched || it.formatForServings(1f) != originalLine) it
            else { matched = true; quantity?.let { qty -> it.copy(baseQuantity = qty) } }
        }
        if (updated.isEmpty()) return
        updateRecipeDetails(recipe, recipe.title, recipe.description, updated.joinToString("\n") { it.formatForServings(1f) },
            recipe.instructions, recipe.calories, recipe.protein, recipe.carbs, recipe.fat)
    }

    fun setRecipePhotoFromPhone(context: Context, recipe: RecipeEntity, uri: android.net.Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val path = RecipePhotoSearch.savePhonePhoto(context, uri)
                val current = repository.allRecipes.first().find { it.id == recipe.id } ?: error("Przepis został usunięty.")
                require(current.imageUrl == recipe.imageUrl) { "Zdjęcie zmieniło się w trakcie pobierania." }
                val saved = AppDatabase.getDatabase(context).dietaDao().saveAutomaticPhoto(current.id, current.imageUrl,
                    current.title, current.ingredients, current.description, path,
                    current.description.substringBefore("\n\nZdjęcie: ") + "\n\nZdjęcie: dodane z telefonu.")
                if (saved == 0) java.io.File(path).delete()
                check(saved == 1) { "Przepis zmienił się w trakcie zapisywania zdjęcia." }
                onResult(true, null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(false, e.message ?: "Nie udało się zapisać zdjęcia.") }
        }
    }

    fun updateRecipeDetails(recipe: RecipeEntity, newTitle: String, newDescription: String, newIngredients: String, newInstructions: String, newCalories: Int, newProtein: Int, newCarbs: Int, newFat: Int, onError: ((String) -> Unit)? = null, newCategory: String = recipe.category) {
        viewModelScope.launch {
            recipeEditMutex.withLock {
            try {
            val structured = repository.allRecipeIngredients.first().filter { it.recipeId == recipe.id }
            val calculated = IngredientSubstitutionSupport.recalculateEditedIngredients(recipe, newIngredients, structured)
            val updated = recipe.copy(
                title = newTitle.trim(),
                category = RecipeCategories.canonical(newCategory),
                description = if (newIngredients != recipe.ingredients) newDescription.substringBefore("\nZmiany składników:") + "\nZmiany składników: przeliczono dostępne dane produktów. Wartości tabelaryczne są szacunkowe; dla brakujących danych zachowano wcześniejsze wartości." else newDescription,
                ingredients = newIngredients,
                instructions = newInstructions,
                calories = if (newCalories != recipe.calories) newCalories.coerceAtLeast(0) else calculated.calories,
                protein = if (newProtein != recipe.protein) newProtein.coerceAtLeast(0) else calculated.protein,
                carbs = if (newCarbs != recipe.carbs) newCarbs.coerceAtLeast(0) else calculated.carbs,
                fat = if (newFat != recipe.fat) newFat.coerceAtLeast(0) else calculated.fat
             )
            val preserved = importedIngredients(newIngredients).map { fresh ->
                structured.find { IngredientSubstitutionSupport.normalizeIngredientKey(it.foodName) == IngredientSubstitutionSupport.normalizeIngredientKey(fresh.foodName) }
                    ?.let { old -> old.copy(quantity = fresh.quantity, grams = if (fresh.grams > 0) fresh.grams else if (old.unit == fresh.unit && old.quantity > 0) old.grams * fresh.quantity / old.quantity else 0f, unit = fresh.unit) } ?: fresh
            }
            repository.updateRecipeWithIngredients(updated, preserved)
            refreshPlannedRecipeNutrition(updated)
            rebuildShoppingListInternal()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                onError?.invoke("Nie udało się zapisać zmian. Sprawdź nazwę, składniki i instrukcje.")
            }
            }
        }
    }

    fun rememberMissingNutrition(name: String, candidate: SubstituteCandidate, barcode: String = "", done: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                com.example.data.PersonalDataGate.mutex.withLock {
                    val context = getApplication<Application>()
                    val db = AppDatabase.getDatabase(context)
                    // Update only missing profiles; preserve each meal's own quantity and substitutions.
                    db.withTransaction {
                        val meals = repository.allMealPlans.first()
                        val rows = repository.allRecipeIngredients.first()
                        require(listOf(candidate.caloriesPer100g, candidate.proteinPer100g, candidate.carbsPer100g, candidate.fatPer100g).all { it != null && it.isFinite() && it >= 0 && it <= 10000 })
                        if (barcode.isBlank()) for (recipe in repository.allRecipes.first()) {
                            val structured = rows.filter { it.recipeId == recipe.id }
                            for (item in MealPlannerSupport.parseRecipeIngredients(recipe.ingredients, structured)) {
                                if (IngredientSubstitutionSupport.normalizeIngredientKey(item.name) != IngredientSubstitutionSupport.normalizeIngredientKey(name)) continue
                                val old = structured.firstOrNull { IngredientSubstitutionSupport.normalizeIngredientKey(it.foodName) == IngredientSubstitutionSupport.normalizeIngredientKey(name) }
                                if (old != null && listOf(old.caloriesPer100g, old.proteinPer100g, old.carbsPer100g, old.fatPer100g).all { it >= 0 }) continue
                                repository.insertIngredient(RecipeIngredientEntity(id = old?.id ?: 0L, recipeId = recipe.id, foodName = item.name,
                                    quantity = item.baseQuantity ?: 0f, unit = item.unit,
                                    grams = item.baseQuantity?.let { IngredientSubstitutionSupport.quantityInGramsOrMl(item.name, it, item.unit, structured) } ?: 0f,
                                    caloriesPer100g = candidate.caloriesPer100g!!, proteinPer100g = candidate.proteinPer100g!!,
                                    carbsPer100g = candidate.carbsPer100g!!, fatPer100g = candidate.fatPer100g!!))
                            }
                        }
                        for (meal in meals) for (sub in IngredientSubstitutionSupport.getSubstitutionsForMeal(meal.id, rows)) {
                            if (IngredientSubstitutionSupport.normalizeIngredientKey(sub.replacementName) != IngredientSubstitutionSupport.normalizeIngredientKey(name) || sub.barcode.trim() != barcode) continue
                            if (listOf(sub.caloriesPer100g, sub.proteinPer100g, sub.carbsPer100g, sub.fatPer100g).all { it != null && it >= 0 }) continue
                            require(listOf(candidate.caloriesPer100g, candidate.proteinPer100g, candidate.carbsPer100g, candidate.fatPer100g).all { it != null && it.isFinite() && it >= 0 })
                            repository.insertIngredient(IngredientSubstitutionSupport.encodeMealSubstitutionEntity(
                                existingId = sub.entityId, mealPlanId = meal.id, originalIngredientName = sub.originalIngredientName,
                                originalBaseQuantity = sub.originalBaseQuantity, originalUnit = sub.originalUnit,
                                candidate = candidate.copy(name = sub.replacementName, barcode = sub.barcode, brand = sub.brand),
                                baseQuantityPerServing = sub.baseQuantityPerServing, unit = sub.unit))
                        }
                    }
                    if (barcode.isBlank()) NutritionAttentionSupport.save(context, name, candidate)
                    db.withTransaction {
                        val allRecipes = repository.allRecipes.first(); val allRows = repository.allRecipeIngredients.first()
                        for (meal in repository.allMealPlans.first()) {
                            val calories = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, allRecipes.find { it.id == meal.recipeId }, allRows).calories
                            if (calories != null && calories != meal.calories) repository.updateMealPlan(meal.copy(calories = calories))
                        }
                    }
                }
                usability.changed(); done(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { done(e.message ?: "Nie udało się zapisać wartości produktu.") }
        }
    }

    private val _nutritionRepair = MutableStateFlow(NutritionRepairProgress())
    val nutritionRepair = _nutritionRepair.asStateFlow()
    private var nutritionRepairJob: kotlinx.coroutines.Job? = null
    private val nutritionRepairNetwork = Mutex()
    private var nutritionRepairLastRequest = 0L
    private suspend fun fetchNutritionRepair(target: NutritionRepairTarget): List<SubstituteCandidate> = nutritionRepairNetwork.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        if (nutritionRepairLastRequest > 0L) kotlinx.coroutines.delay((nutritionRepairLastRequest + 6500L - now).coerceAtLeast(0L))
        nutritionRepairLastRequest = android.os.SystemClock.elapsedRealtime()
        if (target.barcode.isNotBlank()) {
            val response = OpenFoodFactsClient.service.getProductByBarcode(target.barcode)
            if (response.status != 1) emptyList() else listOfNotNull(response.product?.let {
                IngredientSubstitutionSupport.fromOpenFoodFactsProduct(it, SubstituteOptionMode.OPEN_FOOD_FACTS)
            })
        } else OpenFoodFactsClient.searchProductsForNutritionRepair(target.name).products.orEmpty().mapNotNull {
            IngredientSubstitutionSupport.fromOpenFoodFactsProduct(it, SubstituteOptionMode.OPEN_FOOD_FACTS)
        }
    }
    fun repairAllMissingNutrition(online: Boolean) {
        if (nutritionRepairJob?.isActive == true) return
        _nutritionRepair.value = NutritionRepairProgress(running = true, message = "Sprawdzam wszystkie przepisy i jadłospis…")
        nutritionRepairJob = viewModelScope.launch {
            try {
                val snapshot = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    AppDatabase.getDatabase(getApplication<Application>()).withTransaction {
                        Triple(repository.allRecipes.first(), repository.allMealPlans.first(), repository.allRecipeIngredients.first())
                    }
                }
                val allRecipes = snapshot.first
                val allMeals = snapshot.second
                val allRows = snapshot.third
                val store = NutritionRepairStore(getApplication<Application>(), repository)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val targets = UniversalNutritionRepair.collect(allRecipes, allMeals, allRows)
                    val candidates = UniversalNutritionRepair.localCandidates(allRows)
                    UniversalNutritionRepairRunner().run(targets, candidates, online,
                        fetch = { fetchNutritionRepair(it) }, save = { target, candidate -> store.save(target, candidate) },
                        onProgress = { _nutritionRepair.value = it })
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _nutritionRepair.value = _nutritionRepair.value.copy(cancelled = true, message = "Przerwano. Zapisane wyniki pozostają dostępne; możesz sprawdzić pozostałe braki ponownie.")
                throw e
            } catch (_: Exception) {
                _nutritionRepair.value = _nutritionRepair.value.copy(message = "Nie udało się zakończyć operacji. Zapisane wyniki pozostają dostępne; spróbuj ponownie.")
            } finally {
                _nutritionRepair.value = _nutritionRepair.value.copy(running = false, current = "")
                usability.changed()
            }
        }
    }
    fun cancelNutritionRepair() { nutritionRepairJob?.cancel() }
    fun dismissNutritionRepairReport() { if (!_nutritionRepair.value.running) _nutritionRepair.value = NutritionRepairProgress() }
    fun applyNutritionRepairChoice(target: NutritionRepairTarget, candidate: SubstituteCandidate, manuallyConfirmed: Boolean = false, done: (String?) -> Unit) {
        if (_nutritionRepair.value.running) { done("Poczekaj na zakończenie lub przerwij uzupełnianie."); return }
        viewModelScope.launch {
            try {
                // Manual label entry is explicitly confirmed for this target; automatic results must match identity.
                if (target.barcode.isNotBlank() && candidate.barcode.isNotBlank()) require(target.barcode.trim() == candidate.barcode.trim()) {
                    "Wybrany produkt ma inny kod kreskowy. Wybierz właściwy produkt lub dane z jego etykiety."
                }
                val selected = if (manuallyConfirmed) candidate.copy(name = target.name, barcode = target.barcode, source = SubstituteOptionMode.LOCAL_DATABASE) else candidate
                require(selected.defaultUnit == target.basis) { "Wartości muszą dotyczyć 100 ${target.basis}. Nie zamieniaj gramów na mililitry bez danych produktu." }
                val result = NutritionRepairStore(getApplication<Application>(), repository).save(target, selected)
                require(result.changed) { "Produkt nie wymaga już tych wartości albo zapis zmienił się w trakcie wyszukiwania." }
                val state = _nutritionRepair.value
                val remaining = if (!result.complete) "Kalorie zapisane, nadal brakuje części makro."
                    else if (target.quantityMissing) "Profil zapisany. Ilość lub masę sprawdź w szczegółach posiłku."
                    else ""
                _nutritionRepair.value = state.copy(message = "Zapisano dane produktu: ${target.name}.",
                    issues = state.issues.filterNot { it.target.key == target.key } +
                        (if (remaining.isBlank()) emptyList() else listOf(NutritionRepairIssue(target, remaining))))
                usability.changed(); done(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { done(e.message ?: "Nie udało się zapisać produktu.") }
        }
    }

    // Shopping list generator
    fun generateShoppingList(week: String? = null) {
        viewModelScope.launch {
            rebuildShoppingListInternal(week)
        }
    }

    private suspend fun rebuildShoppingListInternal(requestedWeek: String? = null) = shoppingMutex.withLock {
        val plans = repository.allMealPlans.first()
        val keys = if (requestedWeek != null) listOf(ShoppingWeekSupport.requireListKey(requestedWeek))
            else (listOf(ShoppingWeekSupport.start(java.time.LocalDate.now()).toString()) +
                repository.allShoppingItems.first().map { it.weekStart }.filter { key ->
                    key.isNotBlank() && runCatching { ShoppingWeekSupport.requireListKey(key) }.isSuccess
                }).distinct()
        for (key in keys) rebuildShoppingWeek(key, plans)
    }

    private suspend fun rebuildShoppingWeek(week: String, plans: List<MealPlanEntity>) {
        val oldItems = repository.allShoppingItems.first().filter { it.weekStart == week }
        val generatedItems = mutableListOf<ShoppingItemEntity>()

        val meals = plans.filter { ShoppingWeekSupport.includes(week, it) }

        val allRecipesList = repository.allRecipes.first()
        val allIngredientsList = repository.allRecipeIngredients.first()
        val recipeMap = allRecipesList.associateBy { it.id }
        val aggregatedIngredients = mutableMapOf<String, Pair<Float, String>>()

        for (meal in meals) {
            val recipe = recipeMap[meal.recipeId]
                ?: allRecipesList.find { it.title.equals(meal.recipeTitle, ignoreCase = true) }
            val scale = MealPlannerSupport.safeServings(meal.servings)

            val resolvedItems = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(
                recipe = recipe,
                meal = meal,
                allIngredientEntities = allIngredientsList
            )

            if (resolvedItems.isNotEmpty()) {
                for (item in resolvedItems) {
                    val name = item.ingredientName.trim()
                    if (name.isBlank()) continue
                    val scaledQty = item.quantityForMeal ?: (1.0f * scale)
                    val unit = item.unit.trim().lowercase(Locale.forLanguageTag("pl-PL")).ifBlank {
                        if (item.quantityForMeal != null) "" else "szt."
                    }
                    val key = name.lowercase(Locale.forLanguageTag("pl-PL")) + "\u0000" + unit
                    val existing = aggregatedIngredients[key]
                    if (existing != null) {
                        val resolvedUnit = existing.second.ifBlank { unit }
                        aggregatedIngredients[key] = Pair(existing.first + scaledQty, resolvedUnit)
                    } else {
                        aggregatedIngredients[key] = Pair(scaledQty, unit)
                    }
                }
            } else {
                if (recipe != null && MealPlannerSupport.parseRecipeIngredients(recipe.ingredients).isNotEmpty()) continue
                val key = meal.recipeTitle.lowercase(Locale.forLanguageTag("pl-PL")) + "\u0000szt."
                val existing = aggregatedIngredients[key]
                if (existing != null) {
                    aggregatedIngredients[key] = Pair(existing.first + scale, existing.second)
                } else {
                    aggregatedIngredients[key] = Pair(scale, "szt.")
                }
            }
        }

        for ((key, pair) in aggregatedIngredients) {
            val name = key.substringBefore("\u0000").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val qty = pair.first
            val unit = pair.second

            val category = ShoppingCategories.infer(name)

            val amountStr = if (unit.isBlank()) {
                "${if (qty % 1.0f == 0f) qty.toInt().toString() else String.format(Locale.US, "%.1f", qty)}"
            } else {
                "${if (qty % 1.0f == 0f) qty.toInt().toString() else String.format(Locale.US, "%.1f", qty)} $unit"
            }

            val previous = oldItems.find { !it.isManual && it.itemName.equals(name, ignoreCase = true) &&
                it.amount.substringAfter(' ', "") == amountStr.substringAfter(' ', "") }
            generatedItems.add(ShoppingItemEntity(
                id = previous?.id ?: 0L,
                itemName = name,
                category = category,
                amount = amountStr,
                weekStart = week,
                isChecked = previous?.isChecked ?: false
            ))
        }
        repository.replaceShoppingWeek(week, generatedItems)
    }

    fun repairShoppingCategories() {
        viewModelScope.launch { shoppingMutex.withLock {
            for (item in repository.allShoppingItems.first()) {
                val category = ShoppingCategories.infer(item.itemName)
                if (item.category == "Warzywa i owoce" && category != item.category) repository.updateShoppingItem(item.copy(category = category))
            }
        } }
    }

    fun toggleShoppingItem(item: ShoppingItemEntity) {
        viewModelScope.launch { shoppingMutex.withLock {
            val current = repository.allShoppingItems.first().find { it.id == item.id } ?: return@withLock
            repository.updateShoppingItem(current.copy(isChecked = !current.isChecked))
        } }
    }

    fun addShoppingItem(name: String, category: String, week: String = ShoppingWeekSupport.start(java.time.LocalDate.now()).toString()) {
        viewModelScope.launch { shoppingMutex.withLock {
            repository.insertShoppingItem(ShoppingItemEntity(itemName = name.trim(), category = category,
                weekStart = ShoppingWeekSupport.requireListKey(week), isManual = true))
        } }
    }

    fun addWeightLog(weight: Float) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            repository.insertWeightLog(WeightLogEntity(date = dateStr, weightKg = weight))
        }
    }

    fun updateProfile(name: String, cal: Int, p: Int, c: Int, f: Int, goal: String) {
        if (cal !in 1..15000 || p !in 0..1000 || c !in 0..2000 || f !in 0..1000) return
        viewModelScope.launch {
            val current = repository.userProfile.first() ?: UserProfileEntity()
            stampEffectsProfileChange(goal, cal)
            repository.saveUserProfile(current.copy(name = name, targetCalories = cal,
                targetProtein = p, targetCarbs = c, targetFat = f, goal = goal))
        }
    }

    private suspend fun stampEffectsProfileChange(goal: String, calories: Int) {
        val old = repository.userProfile.first() ?: return
        if (old.goal == goal && old.targetCalories == calories) return
        com.example.data.PersonalDataGate.mutex.withLock {
            val settings = EffectsSupport.read(getApplication<Application>())
            val date = java.time.LocalDate.now().toString()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                EffectsSupport.save(getApplication<Application>(), settings.copy(
                    lastAdjustment = if (old.targetCalories != calories) date else settings.lastAdjustment,
                    goalChanged = if (old.goal != goal) date else settings.goalChanged))
            }
        }
        usability.changed()
    }

    private val _weeklyAiAnalysis = MutableStateFlow<String>("Uruchom ocenę ostatnich 7 zakończonych dni: zapisane posiłki, aktywność i pomiary względem Twojego celu.")
    val weeklyAiAnalysis: StateFlow<String> = _weeklyAiAnalysis.asStateFlow()

    private val _effectsAiBusy = MutableStateFlow(false)
    val effectsAiBusy = _effectsAiBusy.asStateFlow()
    private val _effectsAiResult = MutableStateFlow("")
    val effectsAiResult = _effectsAiResult.asStateFlow()
    private val _sportsWeeklyAi = MutableStateFlow<WeeklyAiExplanation?>(null)
    val sportsWeeklyAi = _sportsWeeklyAi.asStateFlow()
    fun saveQuickWellbeing(energy: Int, fatigue: Int, hunger: Int, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.data.PersonalDataGate.mutex.withLock {
                    val context = getApplication<Application>()
                    val updated = PersonalProgressSupport.checkin(SportsSupport.read(context), EffectsSupport.read(context), java.time.LocalDate.now(), energy, fatigue, hunger)
                    val sport = SportsSupport.encode(updated.first); SportsSupport.decode(sport)
                    val effects = EffectsSupport.encode(updated.second); EffectsSupport.decode(effects)
                    check(context.getSharedPreferences("FITMangoUsability", 0).edit().putString(SportsSupport.KEY, sport)
                        .putString(EffectsSupport.KEY, effects).commit()) { "Nie udało się zapisać samopoczucia." }
                } }
                usability.changed(); onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się zapisać samopoczucia.") }
        }
    }
    fun runWeeklyAiAnalysis() { runEffectsAiAnalysis(7) }
    fun runEffectsAiAnalysis(range: Int) {
        if (_effectsAiBusy.value) return
        _effectsAiBusy.value = true
        viewModelScope.launch {
            try {
                // One immutable snapshot for the numerical report and AI context, so screens cannot mix revisions.
                val snapshotProfile = repository.userProfile.first()
                val snapshotWeights = repository.allWeightLogs.first()
                val snapshotMeasurements = repository.allBodyMeasurements.first()
                val snapshotDays = habits.days.value.toList()
                val snapshotConfirmations = healthConnect.weeklyEnergy.confirmations.value.toList()
                val snapshotEnergy = healthConnect.weeklyEnergy.energy.value.toList()
                val snapshotHealth = healthConnect.healthConnectSync.value
                val today = java.time.LocalDate.now()
                val prompt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val sports = SportsSupport.read(getApplication<Application>())
                    val scale = ScaleMeasurementsSupport.read(getApplication<Application>())
                    val report = EffectsSupport.analyze(ScaleMeasurementsSupport.withScaleWeights(snapshotWeights, scale), snapshotMeasurements, snapshotDays,
                        snapshotConfirmations, snapshotProfile, sports, EffectsSupport.read(getApplication<Application>()),
                        today, range, EnergyCalculatorSupport.read(getApplication<Application>()))
                    val from = today.minusDays(if (range == 7) 7L else range - 1L)
                    val until = today.minusDays(1)
                    ProgressAiContext.build(snapshotProfile, snapshotDays, snapshotConfirmations, sports, snapshotEnergy,
                        snapshotHealth, report, today, range, scale) to
                        ProgressEvidence.build(snapshotProfile, snapshotDays, snapshotConfirmations, snapshotEnergy, report, scale, from, until, today).summary
                }
                val frozenEvidence = if (range == 7) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { AiEvidenceSupport.describe(prompt.first) }.getOrDefault("Nie udało się odtworzyć szczegółów danych tego raportu. Pełne uzasadnienie AI pozostaje dostępne.")
                } else ""
                val result = callGemini(prompt.first, systemPrompt = ProgressAiContext.SYSTEM)
                val answer = result.getOrElse { "Nie udało się uzyskać wyjaśnienia AI. Raport lokalny nadal działa. ${it.message.orEmpty()}" }
                val generated = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                val heading = if (range == 7) "Raport tygodniowy: ${today.minusDays(7)} – ${today.minusDays(1)}" else "Analiza okresu $range dni"
                val text = "$heading\nWygenerowano: $generated\n\n${prompt.second}\n\n$answer"
                _effectsAiResult.value = text; _weeklyAiAnalysis.value = text
                if (range == 7) _sportsWeeklyAi.value = WeeklyAiExplanation(today.minusDays(7).toString(), today.minusDays(1).toString(),
                    answer, text, System.currentTimeMillis(), result.isSuccess, frozenEvidence)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                val error = "Nie udało się przygotować wyjaśnienia. Raport lokalny nadal działa."
                _effectsAiResult.value = error; _weeklyAiAnalysis.value = error
                if (range == 7) {
                    val date = java.time.LocalDate.now()
                    _sportsWeeklyAi.value = WeeklyAiExplanation(date.minusDays(7).toString(), date.minusDays(1).toString(), error, error, System.currentTimeMillis(), false)
                }
            }
            finally { _effectsAiBusy.value = false }
        }
    }
    fun saveEffectsSettings(settings: EffectsSettings, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.PersonalDataGate.mutex.withLock {
                    val latest = EffectsSupport.read(getApplication<Application>())
                    EffectsSupport.save(getApplication<Application>(), settings.copy(lastAdjustment = latest.lastAdjustment, goalChanged = latest.goalChanged))
                }
            }; usability.changed(); onResult(null) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult("Nie udało się zapisać ustawień analizy.") }
        }
    }
    fun setEffectsGoal(goal: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try { require(goal in listOf("Redukcja", "Utrzymanie", "Budowanie masy"))
                com.example.data.PersonalDataGate.mutex.withLock {
                    val current = repository.userProfile.first() ?: error("Uzupełnij Profil.")
                    if (current.goal != goal) {
                        val settings = EffectsSupport.read(getApplication<Application>())
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            EffectsSupport.save(getApplication<Application>(), settings.copy(goalChanged = java.time.LocalDate.now().toString()))
                        }
                        try { repository.saveUserProfile(current.copy(goal = goal)) }
                        catch (e: Exception) { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) { EffectsSupport.save(getApplication<Application>(), settings) }; throw e }
                    }
                }; usability.changed(); onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult("Nie udało się zapisać celu.") }
        }
    }
    fun applyEffectsAdjustment(expectedCalories: Int, delta: Int, range: Int, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try { com.example.data.PersonalDataGate.mutex.withLock {
                val current = repository.userProfile.first() ?: error("Uzupełnij Profil.")
                require(current.targetCalories == expectedCalories) { "Cel zmienił się. Odśwież analizę." }
                val settings = EffectsSupport.read(getApplication<Application>())
                val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    EffectsSupport.analyze(ScaleMeasurementsSupport.withScaleWeights(weightLogs.value, ScaleMeasurementsSupport.read(getApplication<Application>())), bodyMeasurements.value, habits.days.value,
                        healthConnect.weeklyEnergy.confirmations.value, current, SportsSupport.read(getApplication<Application>()), settings, java.time.LocalDate.now(), range, EnergyCalculatorSupport.read(getApplication<Application>()))
                }
                require(report.proposedDelta == delta && delta in listOf(-100, 100)) { "Dane zmieniły się. Ponownie sprawdź propozycję." }
                val target = expectedCalories + delta
                require(target in 1500..15000 && target >= current.targetProtein * 4 + current.targetFat * 9)
                val carbs = ((target - current.targetProtein * 4 - current.targetFat * 9) / 4).coerceAtLeast(0)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { EffectsSupport.save(getApplication<Application>(), settings.copy(lastAdjustment = java.time.LocalDate.now().toString())) }
                try { repository.saveUserProfile(current.copy(targetCalories = target, targetCarbs = carbs)) }
                catch (e: Exception) { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) { EffectsSupport.save(getApplication<Application>(), settings) }; throw e }
            }; usability.changed(); onResult(null) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się zapisać korekty.") }
        }
    }
    private val chatRecipeMutex = kotlinx.coroutines.sync.Mutex()
    fun saveChatRecipe(draft: ImportedRecipe, date: String?, slot: String, portions: Float, onResult: (RecipeEntity?, String?) -> Unit) {
        if (!chatRecipeMutex.tryLock()) { onResult(null, "Trwa zapis przepisu."); return }
        viewModelScope.launch {
            try {
                require(draft.title.isNotBlank() && draft.title.length <= 200 && draft.ingredients.isNotBlank() && draft.instructions.isNotBlank())
                require(draft.ingredients.length <= 30000 && draft.instructions.length <= 50000 && portions.isFinite() && portions in .25f..100f)
                val batch = draft.servings?.replace(',', '.')?.toFloatOrNull() ?: 1f
                require(batch.isFinite() && batch in .25f..1000f)
                require(draft.calories in 0..15000 && draft.protein in 0..1000 && draft.carbs in 0..2000 && draft.fat in 0..1000)
                date?.let { java.time.LocalDate.parse(it) }
                require(slot in MealPlannerSupport.MEAL_CATEGORIES.map { it.canonicalName })
                val entity = RecipeEntity(title = draft.title.trim(), description = "Porcje: $batch\nWartości odżywcze: szacunek AI, niezweryfikowany. Sprawdź gramatury i produkty; brak danych składników może dawać niepełną sumę.",
                    ingredients = draft.ingredients.trim(), instructions = draft.instructions.trim(), calories = draft.calories, protein = draft.protein, carbs = draft.carbs, fat = draft.fat,
                    category = draft.providedCategory ?: inferCategoryFromTitle(draft.title), source = "AI Dietetyk")
                val saved = com.example.data.PersonalDataGate.mutex.withLock {
                    val existing = repository.allRecipes.first().find { it.source == entity.source && it.title == entity.title && it.ingredients == entity.ingredients && it.instructions == entity.instructions &&
                        it.calories == entity.calories && it.protein == entity.protein && it.carbs == entity.carbs && it.fat == entity.fat && it.description.lineSequence().firstOrNull() == entity.description.lineSequence().firstOrNull() }
                    val id = AppDatabase.getDatabase(getApplication<Application>()).dietaDao().saveChatRecipeAndPlan(entity, importedIngredients(entity.ingredients), existing?.id, date,
                        MealPlannerSupport.normalizeMealType(slot), portions)
                    entity.copy(id = id)
                }
                try { rebuildShoppingListInternal() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* The recipe and meal are already committed; do not report an unsuccessful save. */ }
                onResult(saved, null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onResult(null, "Nie udało się zapisać przepisu. Sprawdź składniki, kroki, porcje i wartości odżywcze.") }
            finally { chatRecipeMutex.unlock() }
        }
    }

    fun saveIndividualEnergyMode(settings: EnergyProfileSettings, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                require(settings.individualPlan)
                val raw = EnergyCalculatorSupport.encode(settings)
                com.example.data.PersonalDataGate.mutex.withLock {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        check(getApplication<Application>().getSharedPreferences("FITMangoUsability", 0).edit().putString(EnergyCalculatorSupport.KEY, raw).commit())
                    }
                }
                usability.changed(); onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się zapisać trybu indywidualnego.") }
        }
    }

    private val energySaveMutex = kotlinx.coroutines.sync.Mutex()
    fun saveCalculatedEnergyProfile(draft: UserProfileEntity, settings: EnergyProfileSettings,
        expectedProfile: UserProfileEntity?, onResult: (String?) -> Unit) {
        if (!energySaveMutex.tryLock()) { onResult("Trwa zapis profilu."); return }
        viewModelScope.launch {
            try {
                val plan = EnergyCalculatorSupport.plan(draft, settings)
                val updated = draft.copy(id = 1, targetCalories = plan.calories, targetProtein = plan.protein,
                    targetCarbs = plan.carbs, targetFat = plan.fat)
                com.example.data.PersonalDataGate.mutex.withLock {
                    val current = repository.userProfile.first()
                    require(current == expectedProfile) { "Profil zmienił się podczas podglądu. Sprawdź aktualne dane i otwórz podgląd ponownie." }
                    val context = getApplication<Application>()
                    val prefs = context.getSharedPreferences("FITMangoUsability", 0)
                    val oldEnergy = prefs.getString(EnergyCalculatorSupport.KEY, null)
                    val oldEffects = prefs.getString(EffectsSupport.KEY, null)
                    val date = java.time.LocalDate.now().toString()
                    val effects = EffectsSupport.read(context)
                    val changed = current == null || current.goal != draft.goal || current.targetCalories != plan.calories ||
                        oldEnergy != EnergyCalculatorSupport.encode(settings)
                    val nextEffects = if (changed) effects.copy(goalChanged = date,
                        lastAdjustment = if (current?.targetCalories != plan.calories) date else effects.lastAdjustment) else effects
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            check(prefs.edit().putString(EnergyCalculatorSupport.KEY, EnergyCalculatorSupport.encode(settings))
                                .putString(EffectsSupport.KEY, EffectsSupport.encode(nextEffects)).commit())
                        }
                        AppDatabase.getDatabase(context).withTransaction {
                            repository.saveUserProfile(updated)
                            if (current == null || current.currentWeightKg != draft.currentWeightKg)
                                repository.insertWeightLog(WeightLogEntity(date = date, weightKg = draft.currentWeightKg))
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                            val edit = prefs.edit()
                            if (oldEnergy == null) edit.remove(EnergyCalculatorSupport.KEY) else edit.putString(EnergyCalculatorSupport.KEY, oldEnergy)
                            if (oldEffects == null) edit.remove(EffectsSupport.KEY) else edit.putString(EffectsSupport.KEY, oldEffects)
                            check(edit.commit())
                        }
                        throw e
                    }
                }
                usability.changed(); onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się zapisać profilu.") }
            finally { energySaveMutex.unlock() }
        }
    }

    // Compatibility entry point; new UI always displays and explicitly approves the proposal.
    fun updateProfileWithTdee(name: String, age: Int, heightCm: Float, weightKg: Float,
        gender: String, activityLevel: String, goal: String) {
        val current = userProfile.value
        saveCalculatedEnergyProfile((current ?: UserProfileEntity()).copy(name = name, age = age,
            heightCm = heightCm, currentWeightKg = weightKg, gender = gender, activityLevel = activityLevel, goal = goal),
            EnergyCalculatorSupport.read(getApplication<Application>()) ?: EnergyProfileSettings(detailed = false), current) { error ->
                if (error != null) _mealUndoMessage.value = error
            }
    }

    fun deleteBodyMeasurement(id: Long, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                com.example.data.PersonalDataGate.mutex.withLock {
                    val context = getApplication<Application>()
                    val db = AppDatabase.getDatabase(context)
                    val before = MeasurementChangeHistory.snapshot(context)
                    val all = repository.getAllBodyMeasurementsDirect()
                    val row = all.find { it.id == id } ?: error("Pomiar został już usunięty.")
                    val logs = MeasurementRemovalSupport.linkedLogs(row, all.filterNot { it.id == id }, repository.allWeightLogs.first())
                    val previous = MeasurementRemovalSupport.hidden(context)
                    val record = row.healthRecordId?.takeIf { row.source == "HEALTH_CONNECT" && it.isNotBlank() }
                    try {
                        if (record != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            MeasurementRemovalSupport.write(context, previous + record)
                        }
                        db.withTransaction {
                            db.dietaDao().deleteBodyMeasurement(row)
                            logs.forEach { db.dietaDao().deleteWeightLog(it) }
                        }
                        MeasurementChangeHistory.recordBody(context, "Usunięto pomiar ${row.date}", before, MeasurementChangeHistory.snapshot(context))
                    } catch (e: Exception) {
                        if (record != null) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                            MeasurementRemovalSupport.write(context, previous)
                        }
                        throw e
                    }
                }
                usability.changed(); onResult(null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onResult(e.message ?: "Nie udało się usunąć pomiaru.") }
        }
    }

    fun addBodyMeasurement(weightKg: Float, waist: Float, hips: Float, chest: Float, arms: Float, thighs: Float) {
        if (!listOf(weightKg, waist, hips, chest, arms, thighs).all { it.isFinite() && it >= 0 } || weightKg <= 0) return
        viewModelScope.launch {
            try {
                com.example.data.PersonalDataGate.mutex.withLock {
                    val context = getApplication<Application>(); val db = AppDatabase.getDatabase(context)
                    val before = MeasurementChangeHistory.snapshot(context)
                    val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                    db.withTransaction {
                        repository.insertBodyMeasurement(BodyMeasurementEntity(date = date, weightKg = weightKg, waistCm = waist, hipsCm = hips, chestCm = chest, armsCm = arms, thighsCm = thighs))
                        repository.insertWeightLog(WeightLogEntity(date = date, weightKg = weightKg))
                    }
                    MeasurementChangeHistory.recordBody(context, "Dodano pomiar $date", before, MeasurementChangeHistory.snapshot(context))
                }
                usability.changed()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _mealUndoMessage.value = e.message ?: "Nie udało się zapisać pomiaru." }
        }
    }

    fun undoMeasurementChange(id: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            try { MeasurementChangeHistory.undo(id); usability.changed(); done(null) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { done(e.message ?: "Nie udało się cofnąć zmiany.") }
        }
    }

    private fun bodyPhotoStore() = BodyPhotoStore(getApplication<Application>(), AppDatabase.getDatabase(getApplication<Application>()).dietaDao())

    fun addBodyPhoto(frontUri: String, sideUri: String, backUri: String, done: (String?) -> Unit = {}) {
        bodyPhotoOperation(done) { bodyPhotoStore().add(frontUri, sideUri, backUri) }
    }
    fun replaceBodyPhoto(id: Long, angle: BodyPhotoAngle, uri: String, done: (String?) -> Unit) {
        bodyPhotoOperation(done) { bodyPhotoStore().replace(id, angle, uri) }
    }
    fun deleteBodyPhoto(id: Long, done: (String?) -> Unit) {
        bodyPhotoOperation(done) { bodyPhotoStore().delete(id) }
    }
    fun secureLegacyBodyPhotos() {
        viewModelScope.launch {
            try { bodyPhotoStore().secureLegacyPhotos() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Each unavailable photo retains its original reference and repair action. */ }
        }
    }
    private fun bodyPhotoOperation(done: (String?) -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action(); done(null) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { done(e.message ?: "Nie udało się zapisać zdjęć. Wybierz je ponownie.") }
        }
    }
}
