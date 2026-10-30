package com.example.viewmodel

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale
import kotlin.math.roundToInt

internal sealed interface RecipeImportInput {
    data class Url(val uri: URI, val isCookidoo: Boolean) : RecipeImportInput
    data object InstagramUrl : RecipeImportInput
    data object InvalidUrl : RecipeImportInput
    data class Text(val value: String) : RecipeImportInput
}

data class ImportedRecipe(
    val title: String,
    val servings: String?,
    val ingredients: String,
    val instructions: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val source: String = "",
    val imageUrl: String = "",
    val nutritionAvailable: Boolean = (calories > 0 || protein > 0 || carbs > 0 || fat > 0),
    val nutritionIsEstimated: Boolean = false,
    val extractedWithoutAi: Boolean = false,
    val requiresInstructionsCompletion: Boolean = false,
    val totalPreparationMinutes: Int? = null,
    val providedCategory: String? = null
)

object RecipeImportMessages {
    const val COOKIDOO_FALLBACK_MESSAGE =
        "Cookidoo nie udostępnia pełnego przepisu automatycznie. Skopiuj składniki i instrukcje z Cookidoo i wklej je poniżej."

    const val INSTAGRAM_FALLBACK_MESSAGE =
        "Nie udało się odczytać pełnego przepisu z Instagrama. Wklej opis zawierający składniki i przygotowanie; sam film nie jest odczytywany z linku."

    const val INVALID_RECIPE_CONTENT_MESSAGE =
        "Nieprawidłowa treść przepisu: nie znaleziono pełnej listy składników oraz instrukcji przygotowania."
}

/** Extract one URL from a short share caption; keep actual pasted recipe text intact. */
internal fun extractSharedRecipeUrlOrNull(input: String): String? {
    val text = input.trim()
    if (text.length > 500 || Regex("(?im)^\\s*(składniki|skladniki|ingredients|przygotowanie|instructions|sposób przygotowania)\\s*[:\\-–—]?").containsMatchIn(text)) return null
    val matches = Regex("https?://[^\\s<>\"]+|www\\.[^\\s<>\"]+", RegexOption.IGNORE_CASE).findAll(text).toList()
    if (matches.size != 1) return null
    val match = matches.single()
    val prefix = text.substring(0, match.range.first).trim()
    val suffix = text.substring(match.range.last + 1).trim()
    // Do not turn a malformed URL or a caption containing recipe content into a link.
    if (prefix.isEmpty() && suffix.isNotEmpty()) return null
    if (suffix.length > 2) return null
    return match.value.trimEnd('.', ',', ';', ')', ']')
}

/** Android share text can contain an introductory sentence before the public post link. */
internal fun instagramPostUriOrNull(input: String): URI? {
    val uri = parseValidUriOrNull(extractSharedRecipeUrlOrNull(input) ?: input.trim()) ?: return null
    val host = uri.host?.lowercase().orEmpty()
    return uri.takeIf { host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am" }
}

internal fun usableInstagramCaption(text: String): String? = text.trim().takeIf {
    it.length in 20..60000 && classifyRecipeInput("Instagram", it) is RecipeImportInput.Text
}

internal fun isInstagramReaderUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    return uri.scheme == "https" && uri.userInfo == null &&
        (host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am")
}

internal fun isInstagramRecipePage(url: String): Boolean = isInstagramReaderUrl(url) &&
    runCatching { URI(url).path }.getOrDefault("").matches(Regex("/(?:p|reel|reels|tv)/[^/]+/?"))

internal fun classifyRecipeInput(sourceName: String, input: String): RecipeImportInput {
    val value = extractSharedRecipeUrlOrNull(input) ?: input.trim()
    if (value.isEmpty()) return RecipeImportInput.Text(value)

    // Multi-line text or text containing explicit recipe sections is always treated as pasted recipe text
    if (value.contains('\n') || value.length > 280) {
        return RecipeImportInput.Text(value)
    }

    val looksLikeUrl = value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("www.", ignoreCase = true) ||
        URL_LIKE_PATTERN.matches(value)
    if (!looksLikeUrl) return RecipeImportInput.Text(value)

    if (value.contains(' ')) return RecipeImportInput.InvalidUrl

    val normalized = if (value.startsWith("www.", ignoreCase = true) || "://" !in value) {
        "https://$value"
    } else {
        value
    }
    val uri = try {
        URI(normalized)
    } catch (_: Exception) {
        return RecipeImportInput.InvalidUrl
    }
    val host = uri.host?.lowercase(Locale.ROOT) ?: return RecipeImportInput.InvalidUrl
    if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) {
        return RecipeImportInput.InvalidUrl
    }
    if (sourceName.equals("Instagram", ignoreCase = true) &&
        (host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am")
    ) {
        return RecipeImportInput.InstagramUrl
    }
    if (host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am") {
        return RecipeImportInput.InstagramUrl
    }
    return RecipeImportInput.Url(uri, host.contains("cookidoo"))
}

internal fun parseValidUriOrNull(input: String): URI? {
    val value = extractSharedRecipeUrlOrNull(input) ?: input.trim()
    if (value.isEmpty() || value.contains(' ') || value.contains('\n')) return null
    val normalized = if (value.startsWith("www.", ignoreCase = true) || "://" !in value) {
        "https://$value"
    } else {
        value
    }
    return try {
        val uri = URI(normalized)
        if (uri.host != null && uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https")) uri else null
    } catch (_: Exception) {
        null
    }
}

internal fun extractSchemaRecipeJsonLd(document: Document): String? {
    for (script in document.select("script[type=application/ld+json]")) {
        val rawScript = script.data().ifBlank { script.html() }.trim()
        if (rawScript.isBlank()) continue
        val root = tryParseJsonLenient(rawScript) ?: continue
        val recipe = findRecipeObject(root) ?: continue
        return recipe.toString()
    }
    return null
}

private fun tryParseJsonLenient(raw: String): Any? {
    val cleaned = raw
        .removePrefix("<!--")
        .removeSuffix("-->")
        .trim()
    try {
        return JSONTokener(cleaned).nextValue()
    } catch (_: Exception) {
        // Fallback: sanitize unescaped control characters inside JSON strings
        val sanitized = cleaned.replace(Regex("""[\u0000-\u001F]"""), " ")
        return try {
            JSONTokener(sanitized).nextValue()
        } catch (_: Exception) {
            null
        }
    }
}

internal fun extractReadablePageText(document: Document): String =
    document.body()?.text()?.trim().orEmpty()

internal fun normalizeExtractedIngredientLine(raw: String): String {
    val cleanText = Jsoup.parse(raw).text()
        .replace('\u00A0', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
        .removePrefix("-")
        .removePrefix("•")
        .removePrefix("*")
        .trim()
    if (cleanText.isBlank()) return ""

    // Convert blog patterns like "3 średnie jabłka - około 500 g" or "1 szklanka mleka - ok. 250 ml"
    // into "3 średnie jabłka — 500 g" so MealPlannerSupport extracts quantity & unit accurately.
    val approxDashRegex = Regex(
        """^(.+?)\s*[-–—]\s*(?:około|ok\.?|ca\.?|mniej więcej)\s*(\d+(?:[.,]\d+)?)\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ.]+)(?:\s+.*)?$""",
        RegexOption.IGNORE_CASE
    )
    approxDashRegex.find(cleanText)?.let { match ->
        val namePart = match.groupValues[1].trim()
        val qtyPart = match.groupValues[2].trim()
        val unitPart = match.groupValues[3].trim()
        if (namePart.isNotBlank() && qtyPart.isNotBlank()) {
            return "$namePart — $qtyPart $unitPart"
        }
    }

    // Convert "Name - 150 g" to "Name — 150 g"
    val simpleDashRegex = Regex(
        """^(.+?)\s+-\s+(\d+(?:[.,]\d+)?)\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ.]+)$"""
    )
    simpleDashRegex.find(cleanText)?.let { match ->
        val namePart = match.groupValues[1].trim()
        val qtyPart = match.groupValues[2].trim()
        val unitPart = match.groupValues[3].trim()
        if (namePart.isNotBlank() && qtyPart.isNotBlank()) {
            return "$namePart — $qtyPart $unitPart"
        }
    }

    return cleanText
}

internal fun parseImportedRecipe(
    response: String,
    sourceUrlOrLabel: String = ""
): ImportedRecipe {
    val json = response.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val recipe = try {
        val firstBrace = json.indexOf('{')
        val lastBrace = json.lastIndexOf('}')
        val candidateJson = if (firstBrace >= 0 && lastBrace > firstBrace) {
            json.substring(firstBrace, lastBrace + 1)
        } else {
            json
        }
        JSONObject(candidateJson)
    } catch (_: Exception) {
        throw IllegalArgumentException(RecipeImportMessages.INVALID_RECIPE_CONTENT_MESSAGE)
    }
    val title = recipe.optString("title").ifBlank { recipe.optString("name") }.trim()
    val rawIngredients = recipeValueAsText(recipe.opt("ingredients").takeIf { it != null && it != JSONObject.NULL } ?: recipe.opt("recipeIngredient"))
    val ingredients = rawIngredients.lines()
        .map { normalizeExtractedIngredientLine(it) }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    val rawInstructions = recipeValueAsText(recipe.opt("instructions").takeIf { it != null && it != JSONObject.NULL } ?: recipe.opt("recipeInstructions"))
    val instructions = rawInstructions.lines()
        .map { Jsoup.parse(it).text().trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    if (title.isBlank() || ingredients.isBlank() || instructions.isBlank()) {
        throw IllegalArgumentException(RecipeImportMessages.INVALID_RECIPE_CONTENT_MESSAGE)
    }

    val nutrition = recipe.optJSONObject("nutrition") ?: recipe
    fun number(key: String, schemaKey: String): Int {
        val aliases = when (key) {
            "calories" -> listOf("kcal")
            "protein" -> listOf("B", "bialko", "białko")
            "carbs" -> listOf("W", "carbohydrates", "weglowodany", "węglowodany")
            else -> listOf("T", "tluszcz", "tłuszcz")
        }
        return (listOf(key, schemaKey) + aliases).asSequence().map { field ->
            recipeNumber(recipe, field).takeIf { it > 0 } ?: recipeNumber(nutrition, field)
        }.firstOrNull { it > 0 } ?: 0
    }
    val calories = number("calories", "calories")
    val protein = number("protein", "proteinContent")
    val carbs = number("carbs", "carbohydrateContent")
    val fat = number("fat", "fatContent")

    return ImportedRecipe(
        title = title,
        servings = extractServingsString(recipe.opt("servings") ?: recipe.opt("recipeYield")),
        ingredients = ingredients,
        instructions = instructions,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        source = sourceUrlOrLabel,
        imageUrl = extractImageUrlFromSchema(recipe.opt("image")).ifBlank { recipe.optString("imageUrl") }.takeIf { it.startsWith("https://") }.orEmpty(),
        nutritionAvailable = (calories > 0 || protein > 0 || carbs > 0 || fat > 0),
        extractedWithoutAi = false
    )
}

private val URL_LIKE_PATTERN = Regex(
    pattern = """^[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?:/.*)?$""",
    option = RegexOption.IGNORE_CASE
)

private fun isSchemaRecipeType(typeValue: String): Boolean {
    val clean = typeValue.trim().lowercase(Locale.ROOT)
    return clean == "recipe" || clean.endsWith("/recipe") || clean.endsWith(":recipe")
}

private fun findRecipeObject(value: Any?): JSONObject? = when (value) {
    is JSONObject -> {
        val type = value.opt("@type")
        val isRecipe = when (type) {
            is String -> isSchemaRecipeType(type)
            is JSONArray -> (0 until type.length()).any {
                isSchemaRecipeType(type.optString(it))
            }
            else -> false
        }
        if (isRecipe) value else {
            var found: JSONObject? = null
            val keys = value.keys()
            while (keys.hasNext() && found == null) {
                found = findRecipeObject(value.opt(keys.next()))
            }
            found
        }
    }
    is JSONArray -> {
        var found: JSONObject? = null
        for (index in 0 until value.length()) {
            found = findRecipeObject(value.opt(index))
            if (found != null) break
        }
        found
    }
    else -> null
}

private fun recipeValueAsText(value: Any?): String = when (value) {
    is JSONArray -> (0 until value.length())
        .mapNotNull { idx ->
            when (val elem = value.opt(idx)) {
                is JSONObject -> elem.optString("text").ifBlank { elem.optString("name") }.takeIf(String::isNotBlank)
                else -> elem?.toString()?.takeIf(String::isNotBlank)
            }
        }
        .joinToString("\n")
    is JSONObject -> value.optString("text").ifBlank { value.toString() }
    null, JSONObject.NULL -> ""
    else -> value.toString().trim()
}

private fun extractServingsString(raw: Any?): String? {
    return when (raw) {
        null, JSONObject.NULL -> null
        is Number -> raw.toString()
        is JSONArray -> {
            val items = (0 until raw.length())
                .mapNotNull { raw.optString(it).trim().takeIf { s -> s.isNotBlank() && s != "null" } }
            items.firstOrNull()
        }
        else -> raw.toString().trim().takeUnless { it.isBlank() || it == "null" }
    }
}

private fun extractImageUrlFromSchema(imageObj: Any?): String {
    return when (imageObj) {
        is String -> imageObj.trim()
        is JSONObject -> imageObj.optString("url").ifBlank { imageObj.optString("contentUrl") }.trim()
        is JSONArray -> {
            for (i in 0 until imageObj.length()) {
                val url = extractImageUrlFromSchema(imageObj.opt(i))
                if (url.isNotBlank()) return url
            }
            ""
        }
        else -> ""
    }
}

private fun recipeNumber(recipe: JSONObject, key: String): Int {
    val value = recipe.opt(key)
    if (value is JSONObject) {
        return recipeNumber(value, key)
    }
    val text = when (value) {
        is Number -> value.toString()
        is String -> value
        else -> return 0
    }
    val number = Regex("""\d+(?:[.,]\d+)?""").find(text)?.value
        ?.replace(',', '.')?.toDoubleOrNull() ?: return 0
    return number.roundToInt()
}

internal fun parseSchemaRecipeDirectly(
    jsonLd: String,
    sourceUrl: String = "",
    fallbackImageUrl: String = "",
    allowMissingInstructions: Boolean = false
): ImportedRecipe? {
    try {
        val root = tryParseJsonLenient(jsonLd) ?: return null
        val recipe = findRecipeObject(root) ?: return null

        val rawTitle = recipe.optString("name").ifBlank { recipe.optString("headline") }
        val title = Jsoup.parse(rawTitle).text().trim()
        if (title.isBlank()) return null

        val servings = extractServingsString(recipe.opt("recipeYield") ?: recipe.opt("yield"))

        // Parse ingredients
        val ingredientsList = mutableListOf<String>()
        val ingredientsNode = recipe.opt("recipeIngredient") ?: recipe.opt("ingredients")
        when (ingredientsNode) {
            is JSONArray -> {
                for (i in 0 until ingredientsNode.length()) {
                    val ing = normalizeExtractedIngredientLine(ingredientsNode.optString(i))
                    if (ing.isNotBlank()) {
                        ingredientsList.add(ing)
                    }
                }
            }
            is String -> {
                ingredientsNode.lines()
                    .map { normalizeExtractedIngredientLine(it) }
                    .filter { it.isNotBlank() }
                    .forEach { ingredientsList.add(it) }
            }
        }
        if (ingredientsList.isEmpty()) return null
        val ingredients = ingredientsList.joinToString("\n")

        // Parse instructions
        val instructionsList = mutableListOf<String>()
        collectSchemaInstructionSteps(recipe.opt("recipeInstructions"), instructionsList)
        if (instructionsList.isEmpty() && !allowMissingInstructions) return null
        val instructions = instructionsList.mapIndexed { idx, step ->
            val cleanedStep = Jsoup.parse(step).text().trim()
            if (cleanedStep.matches(Regex("""^\d+[.)]\s+.*"""))) {
                cleanedStep
            } else {
                "${idx + 1}. $cleanedStep"
            }
        }.joinToString("\n")

        // Parse nutrition if available
        var calories = 0
        var protein = 0
        var carbs = 0
        var fat = 0
        val nutrition = recipe.optJSONObject("nutrition")
        if (nutrition != null) {
            calories = recipeNumber(nutrition, "calories")
            protein = recipeNumber(nutrition, "proteinContent")
            carbs = recipeNumber(nutrition, "carbohydrateContent")
            fat = recipeNumber(nutrition, "fatContent")
        }

        val imageUrl = extractImageUrlFromSchema(recipe.opt("image")).ifBlank { fallbackImageUrl }

        val prep = RecipeFilterSupport.durationMinutes(recipe.optString("prepTime"))
        val cook = RecipeFilterSupport.durationMinutes(recipe.optString("cookTime"))
        val totalMinutes = RecipeFilterSupport.durationMinutes(recipe.optString("totalTime"))
            ?: if (prep != null && cook != null) (prep + cook).takeIf { it in 0..1440 } else null
        val categoryText = when (val categoryNode = recipe.opt("recipeCategory")) {
            is JSONArray -> (0 until categoryNode.length()).map { categoryNode.optString(it) }.joinToString(" / ")
            is String -> categoryNode
            else -> ""
        }
        val suppliedCategory = categoryText.takeIf { it.isNotBlank() && RecipeCategories.canonical(it) != "Inne" }
        return ImportedRecipe(
            title = title,
            servings = servings,
            ingredients = ingredients,
            instructions = instructions,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            source = sourceUrl,
            imageUrl = imageUrl,
            nutritionAvailable = (calories > 0 || protein > 0 || carbs > 0 || fat > 0),
            extractedWithoutAi = true,
            totalPreparationMinutes = totalMinutes,
            providedCategory = suppliedCategory
        )
    } catch (_: Exception) {
        return null
    }
}

private fun collectSchemaInstructionSteps(node: Any?, out: MutableList<String>) {
    when (node) {
        null, JSONObject.NULL -> return
        is String -> {
            val cleaned = Jsoup.parse(node).wholeText().trim()
            cleaned.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { out.add(it) }
        }
        is JSONArray -> {
            for (i in 0 until node.length()) {
                collectSchemaInstructionSteps(node.opt(i), out)
            }
        }
        is JSONObject -> {
            val itemList = node.opt("itemListElement")
            if (itemList != null && itemList != JSONObject.NULL) {
                collectSchemaInstructionSteps(itemList, out)
                return
            }
            val text = node.optString("text").ifBlank { node.optString("description") }.trim()
            if (text.isNotBlank()) {
                out.add(text)
            }
        }
    }
}

/**
 * Extracts a complete recipe from a Jsoup Document using Schema.org JSON-LD first,
 * and Schema.org HTML microdata (`itemprop="recipeIngredient"`, `itemprop="recipeInstructions"`) second.
 * Also enriches nutrition/servings/image from HTML microdata when present on pages like aniagotuje.pl.
 */
internal fun extractRecipeFromDocumentWithoutAi(
    document: Document,
    sourceUrl: String = ""
): ImportedRecipe? {
    val ogImage = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().orEmpty()
    val microdataNutrition = extractMicrodataNutrition(document)

    // 1. Try Schema.org Recipe JSON-LD first
    val schemaJsonLd = extractSchemaRecipeJsonLd(document)
    if (schemaJsonLd != null) {
        val parsed = parseSchemaRecipeDirectly(schemaJsonLd, sourceUrl = sourceUrl, fallbackImageUrl = ogImage)
        if (parsed != null && parsed.title.isNotBlank() && parsed.ingredients.isNotBlank() && parsed.instructions.isNotBlank()) {
            val finalCalories = if (parsed.calories > 0) parsed.calories else microdataNutrition.first
            val finalProtein = if (parsed.protein > 0) parsed.protein else microdataNutrition.second
            val finalCarbs = if (parsed.carbs > 0) parsed.carbs else microdataNutrition.third
            val finalFat = if (parsed.fat > 0) parsed.fat else microdataNutrition.fourth
            return parsed.copy(
                calories = finalCalories,
                protein = finalProtein,
                carbs = finalCarbs,
                fat = finalFat,
                nutritionAvailable = (finalCalories > 0 || finalProtein > 0 || finalCarbs > 0 || finalFat > 0)
            )
        }
    }

    // 2. Try Schema.org HTML Microdata (`[itemprop=recipeIngredient]`, `[itemprop=recipeInstructions]`)
    val ingredientElements = document.select("[itemprop=recipeIngredient], [itemprop=ingredients], ul.recipe-ing-list li")
    val instructionElements = document.select("[itemprop=recipeInstructions], .step-text, [itemtype*=HowToStep]")
    if (ingredientElements.isNotEmpty() && instructionElements.isNotEmpty()) {
        val title = listOf(
            "h1[itemprop=name]",
            "[itemtype*=Recipe] h1",
            "article h1",
            "h1",
            "[itemtype*=Recipe] > [itemprop=name]"
        ).firstNotNullOfOrNull { selector ->
            document.select(selector)
                .map { el -> el.text().trim().ifBlank { el.attr("content").trim() } }
                .firstOrNull { it.isNotBlank() }
        } ?: document.title().substringBefore("|").substringBefore(" - ").trim()

        val ingredients = ingredientElements
            .map { el -> normalizeExtractedIngredientLine(el.text().ifBlank { el.attr("content") }) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

        val stepSubElements = instructionElements.select("[itemprop=text], .step-text, p, li")
        val rawSteps = if (stepSubElements.isNotEmpty()) {
            stepSubElements
                .map { el -> el.text().trim().ifBlank { el.attr("content").trim() } }
                .filter { it.isNotBlank() }
                .distinct()
        } else {
            instructionElements
                .map { el -> el.text().trim().ifBlank { el.attr("content").trim() } }
                .filter { it.isNotBlank() }
                .distinct()
        }
        val instructions = rawSteps.mapIndexed { idx, s ->
            if (s.matches(Regex("""^\d+[.)]\s+.*"""))) s else "${idx + 1}. $s"
        }.joinToString("\n")

        val servings = document.select("[itemprop=recipeYield]")
            .map { el -> el.text().trim().ifBlank { el.attr("content").trim() } }
            .firstOrNull { it.isNotBlank() }

        if (title.isNotBlank() && ingredients.isNotBlank() && instructions.isNotBlank()) {
            return ImportedRecipe(
                title = title,
                servings = servings,
                ingredients = ingredients,
                instructions = instructions,
                calories = microdataNutrition.first,
                protein = microdataNutrition.second,
                carbs = microdataNutrition.third,
                fat = microdataNutrition.fourth,
                source = sourceUrl,
                imageUrl = ogImage,
                nutritionAvailable = (microdataNutrition.first > 0 || microdataNutrition.second > 0 || microdataNutrition.third > 0 || microdataNutrition.fourth > 0),
                extractedWithoutAi = true
            )
        }
    }

    return null
}

private data class NutritionQuad(val first: Int, val second: Int, val third: Int, val fourth: Int)

private fun extractMicrodataNutrition(document: Document): NutritionQuad {
    fun parseNum(selector: String): Int {
        val text = document.select(selector)
            .map { el -> el.text().trim().ifBlank { el.attr("content").trim() } }
            .firstOrNull { it.isNotBlank() }
            ?: return 0
        return Regex("""\d+(?:[.,]\d+)?""").find(text)?.value?.replace(',', '.')?.toDoubleOrNull()?.roundToInt() ?: 0
    }
    val cal = parseNum("[itemprop=calories]")
    val pro = parseNum("[itemprop=proteinContent]")
    val carb = parseNum("[itemprop=carbohydrateContent]")
    val fat = parseNum("[itemprop=fatContent]")
    return NutritionQuad(cal, pro, carb, fat)
}

/**
 * Checks whether a Cookidoo HTML document contains a genuinely accessible complete recipe
 * (not blocked by a login wall, subscription gate, or paywall snippet without steps).
 */
internal fun extractPublicCookidooRecipeOrNull(document: Document, sourceUrl: String): ImportedRecipe? {
    val direct = extractRecipeFromDocumentWithoutAi(document, sourceUrl)
    if (direct != null) {
        val ingCount = direct.ingredients.lines().count { it.isNotBlank() }
        val stepCount = direct.instructions.lines().count { it.isNotBlank() }
        if (ingCount >= 2 && stepCount >= 1 && !looksLikePaywallPlaceholder(direct.instructions)) {
            return direct
        }
    }
    return null
}

/** Public Cookidoo data may omit preparation. Return an editable preview, never invent steps. */
internal fun extractCookidooPreviewOrNull(document: Document, sourceUrl: String): ImportedRecipe? {
    extractPublicCookidooRecipeOrNull(document, sourceUrl)?.let { return it }
    val schema = extractSchemaRecipeJsonLd(document) ?: return null
    val image = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
    val partial = parseSchemaRecipeDirectly(schema, sourceUrl, image, allowMissingInstructions = true) ?: return null
    if (partial.ingredients.lines().count { it.isNotBlank() } < 2) return null
    val steps = partial.instructions.takeUnless { looksLikePaywallPlaceholder(it) }.orEmpty()
    return partial.copy(instructions = steps, requiresInstructionsCompletion = steps.isBlank())
}

private fun looksLikePaywallPlaceholder(instructions: String): Boolean {
    val lower = instructions.lowercase(Locale.forLanguageTag("pl-PL"))
    return lower.contains("zaloguj się") ||
        lower.contains("abonament") ||
        lower.contains("subskrypcj") ||
        lower.contains("log in to") ||
        lower.contains("sign in to view")
}

/**
 * Attempts to extract a recipe from a publicly accessible Instagram document if legitimate public
 * description/JSON-LD contains a complete recipe (title, ingredients, and preparation steps).
 */
internal fun extractPublicInstagramRecipeOrNull(document: Document, sourceUrl: String): ImportedRecipe? {
    val directSchema = extractRecipeFromDocumentWithoutAi(document, sourceUrl)
    if (directSchema != null) return directSchema

    val ogDescription = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim().orEmpty()
    if (ogDescription.isNotBlank()) {
        // Extract caption inside quotes if present in og:description
        val captionText = Regex("""["„”]([\s\S]+)["„”]""").find(ogDescription)?.groupValues?.get(1) ?: ogDescription
        val parsed = parseStructuredRecipeText(captionText, sourceLabel = sourceUrl.ifBlank { "Instagram" })
        if (parsed != null) return parsed
    }
    return null
}

/** Extract an explicitly stated recipe yield from source text, so AI defaults cannot override it. */
internal fun extractExplicitRecipeServings(text: String): String? {
    val normalized = text.replace('\u00a0', ' ')
    val patterns = listOf(
        // Common social caption format: "Składniki na 3 porcje:".
        Regex("""(?i)(?:składniki|skladniki)\s+na\s+(\d+(?:[.,]\d+)?)\s*(?:porcj[aei]|servings?)\b"""),
        Regex("""(?i)(?:liczba\s+porcji|porcje|porcji|servings?|yield)\s*[:\-–—]?\s*(\d+(?:[.,]\d+)?)"""),
        Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(?:porcje|porcji|porcja|servings?)\b"""),
    )
    for (pattern in patterns) {
        val value = pattern.find(normalized)?.groupValues?.get(1)?.replace(',', '.') ?: continue
        val number = value.toDoubleOrNull() ?: continue
        if (number.isFinite() && number in 0.1..1000.0) return value
    }
    return null
}

/**
 * Deterministically converts user-pasted recipe text (from Cookidoo, Instagram captions, or websites)
 * into a structured ImportedRecipe WITHOUT fabricating any missing ingredients, instructions, or macros.
 * Returns null if the pasted text does not contain both recognizable ingredients and instructions.
 */
internal fun parseStructuredRecipeText(
    rawText: String,
    sourceLabel: String = ""
): ImportedRecipe? {
    val cleanedText = rawText
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    if (cleanedText.length < 15) return null

    val rawLines = cleanedText.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
    if (rawLines.size < 3) return null

    val ingHeaderRegex = Regex(
        """^(?:składniki|skladniki|ingredients|lista składników|potrzebujesz|co potrzebujesz)\s*[:\-–—]?$""",
        RegexOption.IGNORE_CASE
    )
    val instHeaderRegex = Regex(
        """^(?:przygotowanie|sposób przygotowania|sposob przygotowania|wykonanie|instrukcja|instrukcje|kroki|przepis|instructions|preparation)\s*[:\-–—]?$""",
        RegexOption.IGNORE_CASE
    )
    val InlineIngRegex = Regex(
        """^(?:składniki|skladniki|ingredients)\s*[:\-–—]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    val InlineInstRegex = Regex(
        """^(?:przygotowanie|sposób przygotowania|wykonanie|instrukcje|instrukcja|kroki)\s*[:\-–—]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    val servingsRegex = Regex(
        """(?:porcj[ei]|liczba porcji|servings|yield)\s*[:\-–—]?\s*(\d+(?:[.,]\d+)?)|(\d+)\s*porcj[ei]""",
        RegexOption.IGNORE_CASE
    )
    val macroLineRegex = Regex(
        """^(\d+\s*kcal\b.*|(?:kalorie|wartości odżywcze|makro)\s*[:\-–—].*)$""",
        RegexOption.IGNORE_CASE
    )

    // Extract macros if explicitly present in text (never fabricate)
    val calories = Regex("""(\d+(?:[.,]\d+)?)\s*kcal|(?:kalorie|kcal)\s*[:=]\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        .find(cleanedText)?.let { m -> (m.groupValues[1].ifBlank { m.groupValues[2] }).replace(',', '.').toDoubleOrNull()?.roundToInt() } ?: 0
    val protein = Regex("""(?:białko|bialko|protein|\bB)\s*[:=]\s*(\d+(?:[.,]\d+)?)\s*g?""", RegexOption.IGNORE_CASE)
        .find(cleanedText)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.roundToInt() ?: 0
    val carbs = Regex("""(?:węglowodany|weglowodany|węgle|carbs|\bW)\s*[:=]\s*(\d+(?:[.,]\d+)?)\s*g?""", RegexOption.IGNORE_CASE)
        .find(cleanedText)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.roundToInt() ?: 0
    val fat = Regex("""(?:tłuszcze?|tluszcze?|fat|\bT)\s*[:=]\s*(\d+(?:[.,]\d+)?)\s*g?""", RegexOption.IGNORE_CASE)
        .find(cleanedText)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.roundToInt() ?: 0

    var servings: String? = null
    servingsRegex.find(cleanedText)?.let { m ->
        servings = m.groupValues[1].ifBlank { m.groupValues[2] }.takeIf { it.isNotBlank() }
    }

    var title = ""
    val ingredientLines = mutableListOf<String>()
    val instructionLines = mutableListOf<String>()

    var currentSection = "NONE" // NONE, INGREDIENTS, INSTRUCTIONS

    for (line in rawLines) {
        if (macroLineRegex.matches(line)) continue
        if (line.matches(Regex("""^(?:porcj[ei]|liczba porcji)\s*[:\-–—]?\s*\d+.*$""", RegexOption.IGNORE_CASE))) {
            continue
        }

        if (ingHeaderRegex.matches(line)) {
            currentSection = "INGREDIENTS"
            continue
        }
        if (instHeaderRegex.matches(line)) {
            currentSection = "INSTRUCTIONS"
            continue
        }
        InlineIngRegex.find(line)?.let { m ->
            currentSection = "INGREDIENTS"
            m.groupValues[1].split(Regex("[,;]"))
                .map { normalizeExtractedIngredientLine(it) }
                .filter { it.isNotBlank() }
                .forEach { ingredientLines.add(it) }
            continue
        }
        InlineInstRegex.find(line)?.let { m ->
            currentSection = "INSTRUCTIONS"
            val step = m.groupValues[1].trim()
            if (step.isNotBlank()) instructionLines.add(step)
            continue
        }

        if (title.isBlank() && currentSection == "NONE" && !looksLikeIngredientLine(line) && !looksLikeInstructionStep(line)) {
            title = cleanRecipeTitle(line)
            continue
        }

        when (currentSection) {
            "INGREDIENTS" -> {
                if (looksLikeInstructionStep(line) && ingredientLines.size >= 2 && !line.startsWith("-") && !line.startsWith("•")) {
                    currentSection = "INSTRUCTIONS"
                    instructionLines.add(line)
                } else {
                    val norm = normalizeExtractedIngredientLine(line)
                    if (norm.isNotBlank()) ingredientLines.add(norm)
                }
            }
            "INSTRUCTIONS" -> {
                instructionLines.add(line)
            }
            else -> {
                if (looksLikeIngredientLine(line)) {
                    val norm = normalizeExtractedIngredientLine(line)
                    if (norm.isNotBlank()) ingredientLines.add(norm)
                } else if (looksLikeInstructionStep(line)) {
                    instructionLines.add(line)
                } else if (ingredientLines.isNotEmpty()) {
                    instructionLines.add(line)
                }
            }
        }
    }

    if (title.isBlank()) {
        val firstCandidate = rawLines.firstOrNull { !ingHeaderRegex.matches(it) && !instHeaderRegex.matches(it) }.orEmpty()
        title = cleanRecipeTitle(firstCandidate).ifBlank { "Przepis ($sourceLabel)" }
    }

    // Strict rule: never fabricate missing ingredients or instructions
    if (ingredientLines.isEmpty() || instructionLines.isEmpty()) {
        return null
    }

    val formattedInstructions = instructionLines.mapIndexed { idx, step ->
        val cleanStep = step.trim()
        if (cleanStep.matches(Regex("""^\d+[.)]\s+.*"""))) cleanStep else "${idx + 1}. $cleanStep"
    }.joinToString("\n")

    return ImportedRecipe(
        title = title,
        servings = servings ?: "1",
        ingredients = ingredientLines.joinToString("\n"),
        instructions = formattedInstructions,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        source = sourceLabel.ifBlank { "Wklejony tekst" },
        nutritionAvailable = (calories > 0 || protein > 0 || carbs > 0 || fat > 0),
        extractedWithoutAi = true
    )
}

private fun cleanRecipeTitle(raw: String): String {
    return raw
        .replace(Regex("""#[\p{L}\p{N}_]+"""), "")
        .trim()
        .removePrefix("-")
        .removePrefix("•")
        .trim()
}

private fun looksLikeIngredientLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*")) {
        return !looksLikeCookingActionSentence(trimmed)
    }
    val hasQtyAndUnit = Regex(
        """\b\d+(?:[.,/]\d+)?\s*(?:g|dag|kg|ml|l|szt\.?|sztuk[ai]?|łyżk[ai]|łyżeczk[ai]|szklank[ai]|szczypt[ay]|ząbk[iów]|opakowani[ea]|puszk[ai]|garś[ćci])\b""",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(trimmed)
    return hasQtyAndUnit && trimmed.length <= 95 && !looksLikeCookingActionSentence(trimmed)
}

private fun looksLikeInstructionStep(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.matches(Regex("""^\d+[.)]\s+.*"""))) return true
    // Cookidoo / Thermomix step notation like "5 s/obr. 5" or "10 min/100°C/obr. 1"
    if (Regex("""\b\d+\s*(?:s|sek|min)\s*/.*obr""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) return true
    return looksLikeCookingActionSentence(trimmed)
}

private fun looksLikeCookingActionSentence(line: String): Boolean {
    return Regex(
        """\b(?:wymieszaj|zmiksuj|zblenduj|ugotuj|gotuj|piecz|upiecz|smaż|usmaż|podsmaż|dodaj|wlej|wsyp|pokrój|posiekaj|odstaw|rozgrzej|podawaj|przełóż|włóż|wyrabiaj|uformuj|dopraw|duś|obierz|zetrzyj)\b""",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(line)
}

/**
 * Maps network, HTTP, Gemini quota, and validation exceptions into clear, user-friendly Polish messages.
 * Never returns "null" or "Błąd importowania: null".
 */
internal fun mapImportExceptionToMessage(
    error: Throwable,
    isCookidoo: Boolean = false,
    isInstagram: Boolean = false
): String {
    if (error is HttpStatusException) {
        val status = error.statusCode
        if (isCookidoo && (status == 401 || status == 403 || status == 404)) {
            return RecipeImportMessages.COOKIDOO_FALLBACK_MESSAGE
        }
        if (isInstagram && (status == 401 || status == 403 || status == 404 || status == 429)) {
            return RecipeImportMessages.INSTAGRAM_FALLBACK_MESSAGE
        }
        return when {
            status == 403 -> "Błąd HTTP 403: Strona blokuje automatyczny import przepisu (dostęp zabroniony)."
            status == 404 -> "Błąd HTTP 404: Nie znaleziono strony z przepisem pod podanym adresem URL."
            status == 429 -> "Błąd HTTP 429: Serwer strony ograniczył liczbę zapytań. Spróbuj ponownie za chwilę."
            status == 500 -> "Błąd HTTP 500: Wewnętrzny błąd serwera strony z przepisem. Spróbuj ponownie później."
            status == 503 -> "Błąd HTTP 503: Serwer strony z przepisem jest chwilowo niedostępny (przeciążony)."
            status in 500..599 -> "Błąd HTTP $status: Serwer strony jest chwilowo niedostępny."
            else -> "Błąd pobierania strony (HTTP $status). Sprawdź adres URL i spróbuj ponownie."
        }
    }

    if (error is SocketTimeoutException || error is InterruptedIOException) {
        return "Przekroczono czas oczekiwania na odpowiedź serwera (Timeout). Sprawdź połączenie i spróbuj ponownie."
    }

    if (error is UnknownHostException || error is ConnectException) {
        return "Brak połączenia z internetem lub nie można połączyć się z serwerem strony."
    }

    val rawMsg = error.message?.trim().orEmpty()
    val safeMsg = if (rawMsg.isBlank() || rawMsg.equals("null", ignoreCase = true)) "" else rawMsg

    if (safeMsg.contains("429") ||
        safeMsg.contains("Limit zapytań", ignoreCase = true) ||
        safeMsg.contains("Quota", ignoreCase = true) ||
        safeMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
    ) {
        return "Wyczerpano limit zapytań Gemini AI (HTTP 429). Wklejony tekst został zachowany — spróbuj ponownie za chwilę."
    }

    if (error is IllegalArgumentException && safeMsg.isNotBlank()) {
        return safeMsg
    }

    if (safeMsg.contains("timeout", ignoreCase = true)) {
        return "Przekroczono czas oczekiwania na odpowiedź serwera (Timeout). Sprawdź połączenie i spróbuj ponownie."
    }

    return if (safeMsg.isNotBlank()) {
        "Błąd importowania: $safeMsg"
    } else {
        "Błąd importowania: nie udało się przetworzyć danych przepisu."
    }
}
