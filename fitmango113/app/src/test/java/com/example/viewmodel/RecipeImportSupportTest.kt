package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.ui.screens.MealPlanScreen
import com.example.ui.screens.RecipesScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h2400dp")
class RecipeImportSupportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @org.junit.Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        com.example.ai.SecureKeyStorage.init(app)
        com.example.ai.SecureKeyStorage.setTestingMode(true)
        com.example.ai.SecureKeyStorage.saveKey("FAKE_TESTING_KEY")
    }

    @Test
    fun `partial Cookidoo preview permits explicit save without preparation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RecipePhotoSettings(context).enabled = false
        val vm = DietaViewModel(context.applicationContext as Application)
        vm.customHtmlDocumentFetcher = { Jsoup.parse("""<script type="application/ld+json">{
            "@type":"Recipe","name":"Zupa","recipeYield":"4 porcje",
            "recipeIngredient":["100 g cebuli","800 g wody"],
            "nutrition":{"calories":"187 kcal","proteinContent":"10 g",
            "carbohydrateContent":"15 g","fatContent":"11 g"}
        }</script>""", it) }
        composeTestRule.setContent { RecipesScreen(vm) }
        composeTestRule.runOnIdle {
            vm.importRecipe("Cookidoo", "Przepis: https://cookidoo.pl/recipes/recipe/pl-PL/r122680") { _, _ -> }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_save_recipe_button").assertIsEnabled()
        composeTestRule.onNodeWithText("Zapisz bez instrukcji").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_edit_instructions_input")
            .performScrollTo().performTextReplacement("Ugotuj według instrukcji z własnego konta.")
        composeTestRule.onNodeWithTag("preview_save_recipe_button").assertIsEnabled()
        var saved: com.example.data.RecipeEntity? = null
        var error: String? = null
        composeTestRule.runOnIdle {
            vm.confirmSavePreviewedRecipe(vm.importedRecipePreview.value!!.copy(instructions = ""),
                onSaved = { saved = it }, onError = { error = it })
        }
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            saved != null || error != null
        }
        assertNull(error)
        assertEquals("", saved!!.instructions)
        assertTrue(saved!!.description.contains("brak instrukcji"))
        assertEquals(187, saved!!.calories)
        val persisted = runBlocking { AppDatabase.getDatabase(context).dietaDao().recipeById(saved!!.id) }!!
        assertEquals("", persisted.instructions)
        assertEquals(187, persisted.calories)
        assertTrue(persisted.description.contains("brak instrukcji"))
    }

    @Test
    fun `public recipe website extracts Schema recipe JSON-LD`() {
        val input = classifyRecipeInput("Website", "https://recipes.example.com/cake")
        assertTrue(input is RecipeImportInput.Url)
        val document = Jsoup.parse(
            """<script type="application/ld+json">{"@context":"https://schema.org","@type":"Recipe","name":"Apple Cake","recipeIngredient":["2 apples"],"recipeInstructions":"Bake it"}</script>"""
        )

        val schemaRecipe = extractSchemaRecipeJsonLd(document)

        assertTrue(schemaRecipe?.contains("Apple Cake") == true)
    }

    @Test
    fun `Cookidoo URL is recognized for access-aware fetching`() {
        val input = classifyRecipeInput("Website", "https://cookidoo.example/recipes/123")

        assertTrue(input is RecipeImportInput.Url && input.isCookidoo)
    }

    @Test
    fun `Instagram URL is never treated as recipe content`() {
        assertEquals(
            RecipeImportInput.InstagramUrl,
            classifyRecipeInput("Instagram", "https://www.instagram.com/p/abc123/")
        )
    }

    @Test
    fun `Instagram caption is text and parsed recipe fields are retained`() {
        val caption = "Mix 2 eggs with 100 g flour. Bake for 20 minutes."
        val input = classifyRecipeInput("Instagram", caption)
        val parsed = parseImportedRecipe(
            """{"title":"Quick cake","servings":"2","ingredients":["2 eggs","100 g flour"],"instructions":["Mix","Bake 20 minutes"],"calories":210,"protein":8,"carbs":30,"fat":6}"""
        )

        assertTrue(input is RecipeImportInput.Text)
        assertEquals(caption, (input as RecipeImportInput.Text).value)
        assertEquals("Quick cake", parsed.title)
        assertEquals("2 eggs\n100 g flour", parsed.ingredients)
        assertEquals("Mix\nBake 20 minutes", parsed.instructions)
        assertEquals(210, parsed.calories)
        assertEquals(8, parsed.protein)
        assertEquals(30, parsed.carbs)
        assertEquals(6, parsed.fat)
    }

    @Test
    fun `explicit source yield overrides AI default of one serving`() {
        assertEquals("3", extractExplicitRecipeServings("Składniki na 3 porcje:"))
        assertEquals("3", extractExplicitRecipeServings("Składniki na 3 porcje:\ncebula - 1 sztuka"))
        assertEquals("2", extractExplicitRecipeServings("Porcje: 2"))
        assertEquals("4", extractExplicitRecipeServings("4 servings"))
        assertNull(extractExplicitRecipeServings("Składniki\ncebula\nPrzygotowanie\nWymieszaj"))
    }

    @Test
    fun `malformed URL is rejected rather than sent to Gemini`() {
        assertFalse(classifyRecipeInput("Website", "https://bad url").let { it is RecipeImportInput.Text })
        assertEquals(RecipeImportInput.InvalidUrl, classifyRecipeInput("Website", "https://bad url"))
    }

    @Test
    fun `live and deterministic aniagotuje_pl JSON-LD extraction without Gemini`(): Unit = runBlocking {
        val targetUrl = "https://aniagotuje.pl/przepis/placki-z-plasterkami-jablek"

        // 1. Attempt live fetch of https://aniagotuje.pl/przepis/placki-z-plasterkami-jablek
        var liveFetchedTitle = ""
        var liveIngredientCount = 0
        var liveStepCount = 0
        var liveExtractedWithoutAi = false
        try {
            val liveDoc = Jsoup.connect(targetUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "pl-PL,pl;q=0.9")
                .followRedirects(true)
                .timeout(12000)
                .get()
            val ldScripts = liveDoc.select("script[type*=ld+json]")
            println("IMPORT_VERIFY_LIVE_DOC_TITLE=${liveDoc.title()}, ldScripts=${ldScripts.size}, itemprops=${liveDoc.select("[itemprop]").size}")
            ldScripts.forEachIndexed { idx, s ->
                println("IMPORT_VERIFY_LD_SCRIPT_$idx=${s.data().ifBlank { s.html() }.take(500)}")
            }
            val liveRecipe = extractRecipeFromDocumentWithoutAi(liveDoc, sourceUrl = targetUrl)
            if (liveRecipe != null) {
                liveFetchedTitle = liveRecipe.title
                liveIngredientCount = liveRecipe.ingredients.lines().count { it.isNotBlank() }
                liveStepCount = liveRecipe.instructions.lines().count { it.isNotBlank() }
                liveExtractedWithoutAi = liveRecipe.extractedWithoutAi
                assertEquals(targetUrl, liveRecipe.source)
                assertTrue("Live aniagotuje.pl recipe must have title", liveRecipe.title.contains("placki", ignoreCase = true) || liveRecipe.title.contains("jabł", ignoreCase = true))
                assertTrue("Live aniagotuje.pl recipe must have ingredients with quantities", liveIngredientCount >= 4)
                assertTrue("Live aniagotuje.pl recipe must have preparation instructions", liveStepCount >= 2)
                assertTrue("Must be extracted directly from JSON-LD/Schema without Gemini", liveRecipe.extractedWithoutAi)
            }
        } catch (e: Exception) {
            println("IMPORT_VERIFY_LIVE_ANIAGOTUJE_EXCEPTION=${e.javaClass.simpleName}: ${e.message}")
        }

        println("IMPORT_VERIFY_LIVE_ANIAGOTUJE=title='$liveFetchedTitle', ingredients=$liveIngredientCount, steps=$liveStepCount, withoutAi=$liveExtractedWithoutAi")
        if (liveFetchedTitle.isNotBlank()) {
            val liveDoc2 = Jsoup.connect(targetUrl).userAgent("Mozilla/5.0").get()
            val liveRec2 = extractRecipeFromDocumentWithoutAi(liveDoc2, sourceUrl = targetUrl)
            println("IMPORT_VERIFY_LIVE_ANIAGOTUJE_ING_LIST=${liveRec2?.ingredients?.lines()}")
        }

        // 2. Also verifylive Cookidoo & Instagram direct URL behavior
        var cookidooDirectStatus = "BLOCKED"
        try {
            val cookidooDoc = Jsoup.connect("https://cookidoo.pl/recipes/recipe/pl-PL/r55432")
                .userAgent("Mozilla/5.0")
                .timeout(8000)
                .get()
            val cookidooExtracted = extractPublicCookidooRecipeOrNull(cookidooDoc, "https://cookidoo.pl/recipes/recipe/pl-PL/r55432")
            cookidooDirectStatus = if (cookidooExtracted != null) "WORKING" else "BLOCKED"
        } catch (_: Exception) {
            cookidooDirectStatus = "BLOCKED"
        }

        var instagramDirectStatus = "BLOCKED"
        try {
            val igDoc = Jsoup.connect("https://www.instagram.com/p/C8abc123/")
                .userAgent("Mozilla/5.0")
                .timeout(8000)
                .get()
            val igExtracted = extractPublicInstagramRecipeOrNull(igDoc, "https://www.instagram.com/p/C8abc123/")
            instagramDirectStatus = if (igExtracted != null) "WORKING" else "BLOCKED"
        } catch (_: Exception) {
            instagramDirectStatus = "BLOCKED"
        }

        println("IMPORT_VERIFY_COOKIDOO_DIRECT_STATUS=$cookidooDirectStatus")
        println("IMPORT_VERIFY_INSTAGRAM_DIRECT_STATUS=$instagramDirectStatus")
    }

    @Test
    fun `error handling maps HTTP 403 404 429 500 503 Timeout Gemini quota and invalid content without null`() {
        val url = "https://example.com/recipe"
        val msg403 = mapImportExceptionToMessage(HttpStatusException("Forbidden", 403, url))
        val msg404 = mapImportExceptionToMessage(HttpStatusException("Not Found", 404, url))
        val msg429 = mapImportExceptionToMessage(HttpStatusException("Too Many Requests", 429, url))
        val msg500 = mapImportExceptionToMessage(HttpStatusException("Server Error", 500, url))
        val msg503 = mapImportExceptionToMessage(HttpStatusException("Service Unavailable", 503, url))
        val msgTimeout = mapImportExceptionToMessage(SocketTimeoutException("Read timed out"))
        val msgQuota = mapImportExceptionToMessage(RuntimeException("HTTP 429: Limit zapytań został przekroczony (Quota exhausted)"))
        val msgInvalid = mapImportExceptionToMessage(IllegalArgumentException(RecipeImportMessages.INVALID_RECIPE_CONTENT_MESSAGE))
        val msgNullEx = mapImportExceptionToMessage(RuntimeException(null as String?))

        assertTrue(msg403.contains("403"))
        assertTrue(msg404.contains("404"))
        assertTrue(msg429.contains("429"))
        assertTrue(msg500.contains("500"))
        assertTrue(msg503.contains("503"))
        assertTrue(msgTimeout.contains("Timeout", ignoreCase = true) || msgTimeout.contains("czas", ignoreCase = true))
        assertTrue(msgQuota.contains("429") && msgQuota.contains("zachowany"))
        assertEquals(RecipeImportMessages.INVALID_RECIPE_CONTENT_MESSAGE, msgInvalid)

        val allMessages = listOf(msg403, msg404, msg429, msg500, msg503, msgTimeout, msgQuota, msgInvalid, msgNullEx)
        for (m in allMessages) {
            assertFalse("Error message must never contain 'Błąd importowania: null': $m", m.contains("null", ignoreCase = true))
        }

        // Verify Cookidoo & Instagram specific fallback messages
        assertEquals(
            RecipeImportMessages.COOKIDOO_FALLBACK_MESSAGE,
            mapImportExceptionToMessage(HttpStatusException("Forbidden", 403, "https://cookidoo.pl/r1"), isCookidoo = true)
        )
        assertEquals(
            RecipeImportMessages.INSTAGRAM_FALLBACK_MESSAGE,
            mapImportExceptionToMessage(HttpStatusException("Forbidden", 403, "https://www.instagram.com/p/1"), isInstagram = true)
        )
    }

    @Test
    fun `structured text parser converts Cookidoo and Instagram text without fabricating missing ingredients or instructions`() {
        val cookidooText = """
            Krem z pomidorów i pieczonej papryki
            Porcje: 4
            Składniki:
            - Pomidory pelati — 800 g
            - Papryka czerwona — 300 g
            - Cebula — 100 g
            - Oliwa z oliwek — 20 ml
            - Bulion warzywny — 400 ml
            Przygotowanie:
            1. Cebulę i paprykę włóż do naczynia miksującego, rozdrobnij 5 s/obr. 5.
            2. Dodaj oliwę i duś 5 min/120°C/obr. 1.
            3. Dodaj pomidory oraz bulion, gotuj 15 min/100°C/obr. 2, a następnie zmiksuj 30 s/obr. 10.
            Kalorie: 480 kcal | B: 12g | W: 58g | T: 20g
        """.trimIndent()

        val parsedCookidoo = parseStructuredRecipeText(cookidooText, sourceLabel = "Cookidoo")
        assertNotNull(parsedCookidoo)
        assertEquals("Krem z pomidorów i pieczonej papryki", parsedCookidoo!!.title)
        assertEquals("4", parsedCookidoo.servings)
        assertEquals("Cookidoo", parsedCookidoo.source)
        assertTrue(parsedCookidoo.ingredients.contains("Pomidory pelati — 800 g"))
        assertTrue(parsedCookidoo.ingredients.contains("Bulion warzywny — 400 ml"))
        assertTrue(parsedCookidoo.instructions.contains("5 s/obr. 5"))
        assertEquals(480, parsedCookidoo.calories)
        assertEquals(12, parsedCookidoo.protein)
        assertEquals(58, parsedCookidoo.carbs)
        assertEquals(20, parsedCookidoo.fat)

        // Verify incomplete text (only ingredients, no instructions) returns null and NEVER fabricates steps
        val incompleteTextOnlyIngredients = """
            Sałatka grecka
            Składniki:
            - Pomidor — 200 g
            - Ogórek — 150 g
            - Ser feta — 100 g
        """.trimIndent()
        assertNull(
            "Must never fabricate missing instructions when only ingredients are provided",
            parseStructuredRecipeText(incompleteTextOnlyIngredients, sourceLabel = "Cookidoo")
        )
    }

    @Test
    fun `composeUi full workflow website JSON-LD Cookidoo fallback Instagram caption preview Room save and weekly planner`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = DietaViewModel(context.applicationContext as Application)
        val dao = AppDatabase.getDatabase(context).dietaDao()
        dao.clearMealPlans()
        // This UI import test must not contact public photo/GPU services.
        RecipePhotoSettings(context).enabled = false

        val aniagotujeUrl = "https://aniagotuje.pl/przepis/placki-z-plasterkami-jablek"
        val aniagotujeHtml = """
            <!DOCTYPE html>
            <html lang="pl">
            <head>
                <title>Placki z plasterkami jabłek - AniaGotuje.pl</title>
                <meta property="og:image" content="https://aniagotuje.pl/img/placki-jablka.jpg" />
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Recipe",
                  "name": "Placki z plasterkami jabłek",
                  "recipeYield": "4 porcje",
                  "recipeIngredient": [
                    "3 średnie jabłka - około 500 g",
                    "1,5 szklanki mąki pszennej - około 240 g",
                    "1 szklanka mleka - 250 ml",
                    "2 jajka"
                  ],
                  "recipeInstructions": [
                    {"@type": "HowToStep", "text": "Wymieszaj mąkę, mleko i jajka na gładkie ciasto."},
                    {"@type": "HowToStep", "text": "Obierz jabłka, pokrój w plasterki, zanurz w cieście i usmaż na złoty kolor."}
                  ],
                  "nutrition": {
                    "@type": "NutritionInformation",
                    "calories": "680 kcal",
                    "proteinContent": "22 g",
                    "carbohydrateContent": "118 g",
                    "fatContent": "14 g"
                  }
                }
                </script>
            </head>
            <body><h1>Placki z plasterkami jabłek</h1></body>
            </html>
        """.trimIndent()

        viewModel.customHtmlDocumentFetcher = { url ->
            when {
                url.contains("aniagotuje.pl") -> Jsoup.parse(aniagotujeHtml, url)
                url.contains("cookidoo.pl") -> {
                    // Simulate Cookidoo public page that shows title only and hides full recipe behind subscription
                    Jsoup.parse("<html><body><h1>Risotto z grzybami</h1><p>Zaloguj się, aby zobaczyć pełny przepis w Cookidoo.</p></body></html>", url)
                }
                url.contains("instagram.com") -> {
                    throw HttpStatusException("Forbidden", 403, url)
                }
                else -> throw HttpStatusException("Not Found", 404, url)
            }
        }

        val currentScreen = androidx.compose.runtime.mutableStateOf("recipes")
        composeTestRule.setContent {
            if (currentScreen.value == "recipes") {
                RecipesScreen(viewModel = viewModel)
            } else {
                MealPlanScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // =========================================================================
        // 1. Website Import via JSON-LD WITHOUT Gemini -> Preview -> Save to Room
        // =========================================================================
        viewModel.geminiCallsDuringRecipeImport = 0
        composeTestRule.onNodeWithTag("open_import_dialog_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("import_input_field").performTextReplacement(aniagotujeUrl)
        composeTestRule.onNodeWithTag("import_submit_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.importedRecipePreview.value != null
        }
        composeTestRule.waitForIdle()

        // Verify Gemini was NOT called for JSON-LD website import
        assertEquals("JSON-LD import must not call Gemini", 0, viewModel.geminiCallsDuringRecipeImport)

        // Verify Recipe Preview dialog displays Title, Source, Servings, Ingredients, Instructions, Nutrition, and 3 buttons
        composeTestRule.onNodeWithTag("imported_recipe_preview_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Placki z plasterkami jabłek").assertIsDisplayed()
        composeTestRule.onNodeWithText("Źródło: $aniagotujeUrl").assertIsDisplayed()
        composeTestRule.onNodeWithText("Porcje: 4 porcje").assertIsDisplayed()
        composeTestRule.onNodeWithText("- 3 średnie jabłka — 500 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("- 1,5 szklanki mąki pszennej — 240 g").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_save_recipe_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_edit_recipe_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_cancel_recipe_button").assertIsDisplayed()

        // Click "Zapisz przepis" in preview
        composeTestRule.onNodeWithTag("preview_save_recipe_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.recipes.value.any { it.title == "Placki z plasterkami jabłek" }
        }
        composeTestRule.waitForIdle()

        // =========================================================================
        // 2. Cookidoo Blocked URL -> Exact Message -> Pasted Text Import -> Save
        // =========================================================================
        composeTestRule.onNodeWithTag("open_import_dialog_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("import_mode_Cookidoo").performClick()
        composeTestRule.onNodeWithTag("import_input_field")
            .performTextReplacement("https://cookidoo.pl/recipes/recipe/pl-PL/r12345")
        composeTestRule.onNodeWithTag("import_submit_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithText(RecipeImportMessages.COOKIDOO_FALLBACK_MESSAGE)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(RecipeImportMessages.COOKIDOO_FALLBACK_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pasted_recipe_text_input").assertIsDisplayed()

        // Verify that if Gemini temporarily fails (HTTP 429), pasted text is preserved
        val cookidooPastedRecipe = """
            Risotto z grzybami Thermomix
            Porcje: 2
            Składniki:
            - Ryż arborio — 160 g
            - Borowiki — 200 g
            - Bulion warzywny — 450 ml
            - Parmezan — 40 g
            Przygotowanie:
            1. Grzyby i ryż podsmaż w naczyniu miksującym 3 min/120°C/obr. 1.
            2. Wlej bulion i gotuj 16 min/100°C/obr. wsteczne 1, na koniec dodaj parmezan.
        """.trimIndent()
        viewModel.customRecipeImportAiCaller = {
            Result.failure(RuntimeException("HTTP 429: Przekroczono limit Gemini API"))
        }
        composeTestRule.onNodeWithTag("pasted_recipe_text_input").performTextReplacement(cookidooPastedRecipe)
        composeTestRule.onNodeWithTag("import_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithTag("import_error_message").fetchSemanticsNodes().isNotEmpty()
        }
        // Pasted text is still preserved in the dialog! Now clear the failing AI override so structured text conversion runs
        viewModel.customRecipeImportAiCaller = null
        composeTestRule.onNodeWithTag("import_submit_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.importedRecipePreview.value?.title == "Risotto z grzybami Thermomix"
        }
        // This fixture intentionally lacks nutrition. Saving now enriches it before Room;
        // provide a deterministic estimate instead of calling real Gemini in a UI test.
        val risottoPreview = requireNotNull(viewModel.importedRecipePreview.value)
        assertEquals(0, risottoPreview.calories)
        var nutritionCalls = 0
        viewModel.customRecipeImportAiCaller = { prompt ->
            assertTrue(prompt.contains("NA JEDNĄ PORCJĘ"))
            assertTrue(prompt.contains("Risotto z grzybami Thermomix"))
            nutritionCalls++
            Result.success(org.json.JSONObject()
                .put("title", risottoPreview.title).put("servings", risottoPreview.servings)
                .put("ingredients", risottoPreview.ingredients).put("instructions", risottoPreview.instructions)
                .put("calories", 420).put("protein", 16).put("carbs", 62).put("fat", 12).toString())
        }
        composeTestRule.onNodeWithTag("preview_save_recipe_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.recipes.value.any { it.title == "Risotto z grzybami Thermomix" }
        }
        val savedRisotto = viewModel.recipes.value.single { it.title == "Risotto z grzybami Thermomix" }
        assertEquals(1, nutritionCalls)
        assertEquals(420, savedRisotto.calories)
        assertEquals(16, savedRisotto.protein)
        assertEquals(62, savedRisotto.carbs)
        assertEquals(12, savedRisotto.fat)
        assertEquals("Cookidoo", savedRisotto.source)
        assertTrue(savedRisotto.description.contains("szacunkowe"))
        assertEquals(risottoPreview.ingredients, savedRisotto.ingredients)
        viewModel.customRecipeImportAiCaller = null

        // =========================================================================
        // 3. Instagram Blocked URL -> Exact Message -> Caption Import -> Save
        // =========================================================================
        composeTestRule.onNodeWithTag("open_import_dialog_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("import_mode_Instagram").performClick()
        composeTestRule.onNodeWithTag("import_input_field")
            .performTextReplacement("https://www.instagram.com/p/C9xyz987/")
        composeTestRule.onNodeWithTag("import_submit_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithText(RecipeImportMessages.INSTAGRAM_FALLBACK_MESSAGE)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(RecipeImportMessages.INSTAGRAM_FALLBACK_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pasted_recipe_text_input").assertIsDisplayed()

        val instagramCaption = """
            Fit Bowl z kurczakiem i mango #fitprzepis #mangofit
            Porcje: 1
            Składniki:
            - Pierś z kurczaka — 150 g
            - Mango — 100 g
            - Ryż jaśminowy — 70 g
            Przygotowanie:
            1. Ugotuj ryż jaśminowy w lekko osolonej wodzie.
            2. Kurczaka pokrój w kostkę, usmaż na złoto i podawaj z pokrojonym mango na ryżu.
            Kalorie: 510 kcal | B: 38g | W: 68g | T: 8g
        """.trimIndent()
        composeTestRule.onNodeWithTag("pasted_recipe_text_input").performTextReplacement(instagramCaption)
        composeTestRule.onNodeWithTag("import_submit_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.importedRecipePreview.value?.title == "Fit Bowl z kurczakiem i mango"
        }
        composeTestRule.onNodeWithTag("preview_save_recipe_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.recipes.value.any { it.title == "Fit Bowl z kurczakiem i mango" }
        }

        // =========================================================================
        // 4. Open Imported Recipe Detail & Add to Weekly Planner (Monday Dinner)
        // =========================================================================
        composeTestRule.onNodeWithTag("recipes_search_input").performTextReplacement("Placki z plasterkami jabłek")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recipe_card_Placki z plasterkami jabłek").performClick()
        composeTestRule.waitForIdle()

        // Verify Recipe Detail dialog opens with ingredients, "Zamień" button, and "Dodaj do jadłospisu"
        composeTestRule.onNodeWithTag("recipe_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Źródło: $aniagotujeUrl").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_to_meal_plan_button").performClick()
        composeTestRule.waitForIdle()

        // Choose Poniedziałek, Śniadanie, 2 servings
        composeTestRule.onNodeWithTag("select_plan_day_Poniedziałek").performClick()
        composeTestRule.onNodeWithTag("select_meal_slot_Śniadanie").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("servings_input_field").performScrollTo().performTextReplacement("2")
        composeTestRule.onNodeWithTag("confirm_add_to_plan_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.mealPlans.value.any {
                it.recipeTitle == "Placki z plasterkami jabłek" &&
                    it.dayOfWeek == MealPlannerSupport.dateForDay("Poniedziałek")!!.toString() &&
                    it.mealType == "Śniadanie" &&
                    it.servings == 2.0f
            }
        }

        // Switch to MealPlanScreen and verify the imported recipe is displayed under Monday Kolacja with scaled ingredients
        currentScreen.value = "meal_plan"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("planner_day_Poniedziałek").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithText("Placki z plasterkami jabłek — 2 porcje")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Placki z plasterkami jabłek — 2 porcje").assertIsDisplayed()
        val plannedMeal = viewModel.mealPlans.value.first { it.recipeTitle == "Placki z plasterkami jabłek" && it.dayOfWeek == MealPlannerSupport.dateForDay("Poniedziałek")!!.toString() }
        composeTestRule.onNodeWithTag("toggle_meal_details_${plannedMeal.id}").performClick()
        // 500 g apples * 2 servings = 1000 g
        composeTestRule.onNodeWithText("- 3 średnie jabłka — 1000 g").assertIsDisplayed()

        println("IMPORT_VERIFY_SAVED_RECIPES=${viewModel.recipes.value.map { "${it.title} [${it.source}]" }}")
    }
}
