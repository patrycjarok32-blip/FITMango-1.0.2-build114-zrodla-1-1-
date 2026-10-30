package com.example.viewmodel

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class StructuredPdfIngredient(
    val name: String,
    val quantity: String = "",
    val unit: String = ""
) {
    fun toFormattedLine(): String {
        val cleanName = name.trim().replace(Regex("\\s+"), " ").replace(",", " -")
        val cleanQty = quantity.trim().replace(",", ".")
        val cleanUnit = unit.trim().replace(",", "")
        if (cleanName.isBlank()) return ""

        val qtyAndUnit = when {
            cleanQty.isNotEmpty() && cleanUnit.isNotEmpty() -> {
                if (cleanQty.endsWith(cleanUnit, ignoreCase = true) ||
                    cleanName.startsWith("$cleanUnit ", ignoreCase = true)
                ) {
                    cleanQty
                } else {
                    "$cleanQty $cleanUnit"
                }
            }
            cleanQty.isNotEmpty() -> cleanQty
            cleanUnit.isNotEmpty() -> cleanUnit
            else -> ""
        }.trim()

        return when {
            qtyAndUnit.isEmpty() -> cleanName
            qtyAndUnit.first().isDigit() -> "$qtyAndUnit $cleanName"
            else -> "$cleanName ($qtyAndUnit)"
        }
    }
}

data class StructuredPdfRecipe(
    val title: String,
    val servings: String,
    val ingredients: List<StructuredPdfIngredient>,
    val instructions: List<String>,
    val calories: Int?,
    val protein: Int?,
    val carbs: Int?,
    val fat: Int?,
    val sourcePageOrChunk: String
) {
    fun toParsedRecipe(documentTitle: String): ParsedRecipe {
        val formattedIngredients = ingredients
            .map { it.toFormattedLine() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val formattedInstructions = instructions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { idx, step ->
                val cleaned = step.replace(Regex("^\\d+[.)]\\s*"), "").trim()
                "${idx + 1}. $cleaned"
            }
            .joinToString("\n")

        val resolvedCalories = calories?.takeIf { it > 0 } ?: estimateCalories(protein, carbs, fat)
        val resolvedProtein = protein?.takeIf { it >= 0 } ?: 0
        val resolvedCarbs = carbs?.takeIf { it >= 0 } ?: 0
        val resolvedFat = fat?.takeIf { it >= 0 } ?: 0

        return ParsedRecipe(
            title = title.trim(),
            servings = servings.trim().ifBlank { "1" },
            ingredients = formattedIngredients,
            instructions = formattedInstructions,
            calories = resolvedCalories,
            protein = resolvedProtein,
            carbs = resolvedCarbs,
            fat = resolvedFat,
            source = documentTitle.ifBlank { "PDF" },
            sourcePageOrChunk = sourcePageOrChunk.trim()
        )
    }

    private fun estimateCalories(p: Int?, c: Int?, f: Int?): Int {
        if (p != null && c != null && f != null && (p > 0 || c > 0 || f > 0)) {
            return (p * 4) + (c * 4) + (f * 9)
        }
        return 0
    }
}

data class PdfChunkConfig(
    val targetChunkChars: Int = 14_000,
    val maxChunkChars: Int = 18_000,
    val minChunkChars: Int = 8_500,
    val overlapChars: Int = 1_500
) {
    companion object {
        fun forModelContext(modelName: String = "gemini-3.5-flash"): PdfChunkConfig {
            return when {
                modelName.contains("pro", ignoreCase = true) -> PdfChunkConfig(
                    targetChunkChars = 16_000,
                    maxChunkChars = 20_000,
                    minChunkChars = 10_000,
                    overlapChars = 1_600
                )
                modelName.contains("lite", ignoreCase = true) -> PdfChunkConfig(
                    targetChunkChars = 12_500,
                    maxChunkChars = 16_000,
                    minChunkChars = 8_000,
                    overlapChars = 1_400
                )
                else -> PdfChunkConfig(
                    targetChunkChars = 14_000,
                    maxChunkChars = 18_000,
                    minChunkChars = 8_500,
                    overlapChars = 1_500
                )
            }
        }
    }
}

data class PdfTextChunk(
    val index: Int,
    val totalChunks: Int,
    val text: String,
    val startCharOffset: Int,
    val endCharOffset: Int
)

data class PdfChunkExtractionOutcome(
    val completedAllChunks: Boolean,
    val rateLimitedOrPaused: Boolean,
    val nextChunkIndex: Int,
    val totalChunks: Int,
    val perChunkCounts: Map<Int, Int>,
    val recipesBeforeDeduplication: List<ParsedRecipe>,
    val deduplicatedRecipes: List<ParsedRecipe>,
    val errorMessage: String? = null
)

internal object PdfRecipeExtractor {

    private val PAGE_BOUNDARY_REGEX = Regex("""\n(?==== STRONA \d+ ===|--- PAGE \d+ ---|\f|Strona \d+\b)""", RegexOption.IGNORE_CASE)

    private val RECIPE_HEADING_REGEX = Regex(
        """\n(?=(?:PRZEPIS|Przepis|RECIPE)\s*\d*[:.\s-]|[0-9]{1,2}[.)]\s+[A-ZĄĆĘŁŃÓŚŹŻ]|(?:ŚNIADANIE|Śniadanie|II ŚNIADANIE|II śniadanie|OBIAD|Obiad|KOLACJA|Kolacja|PODWIECZOREK|Podwieczorek|PRZEKĄSKA|Przekąska)\s*[:\-–]|[A-ZĄĆĘŁŃÓŚŹŻ][A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż0-9\s,()\-–]{4,70}\n\s*(?:Składniki|SKŁADNIKI|Porcje|PORCJE|Czas| Wartości))"""
    )

    private val PARAGRAPH_BOUNDARY_REGEX = Regex("""\n\s*\n""")

    private val PAGE_MARKER_CLEANUP_REGEX = Regex("""===\s*STRONA\s+\d+\s*===|---\s*PAGE\s+\d+\s*---""", RegexOption.IGNORE_CASE)

    /**
     * Extracts the complete text from all pages of the PDF without truncating pages or characters.
     */
    fun extractFullTextFromPdf(inputStream: InputStream): String {
        PDDocument.load(inputStream).use { document ->
            val totalPages = document.numberOfPages
            if (totalPages <= 0) return ""

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            val builder = StringBuilder()
            for (page in 1..totalPages) {
                stripper.startPage = page
                stripper.endPage = page
                val pageText = stripper.getText(document)
                    ?.replace("\r\n", "\n")
                    ?.replace('\r', '\n')
                    ?.trim()
                    .orEmpty()

                if (pageText.isNotEmpty()) {
                    if (builder.isNotEmpty()) {
                        builder.append("\n\n")
                    }
                    builder.append("=== STRONA ").append(page).append(" ===\n")
                    builder.append(pageText)
                }
            }
            return builder.toString().trim()
        }
    }

    /**
     * Checks whether extracted PDF text contains meaningful readable content (not a blank scan requiring OCR).
     */
    fun hasExtractablePdfText(extractedText: String): Boolean {
        val withoutMarkers = PAGE_MARKER_CLEANUP_REGEX.replace(extractedText, "").trim()
        val nonWhitespaceCount = withoutMarkers.count { !it.isWhitespace() }
        return nonWhitespaceCount >= 15
    }

    /**
     * Splits the full PDF text into safe large chunks preferring:
     * 1. Page boundaries
     * 2. Recipe heading boundaries
     * 3. Paragraph boundaries
     * 4. Line boundaries
     * with overlap so recipes spanning boundaries are never lost.
     */
    fun createPdfChunks(
        fullText: String,
        config: PdfChunkConfig = PdfChunkConfig.forModelContext()
    ): List<PdfTextChunk> {
        val normalized = fullText.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()

        if (normalized.length <= config.maxChunkChars) {
            return listOf(
                PdfTextChunk(
                    index = 0,
                    totalChunks = 1,
                    text = normalized,
                    startCharOffset = 0,
                    endCharOffset = normalized.length
                )
            )
        }

        val rawRanges = mutableListOf<IntRange>()
        var startIndex = 0
        val textLength = normalized.length

        while (startIndex < textLength) {
            val remaining = textLength - startIndex
            if (remaining <= config.maxChunkChars) {
                rawRanges.add(startIndex until textLength)
                break
            }

            val windowStart = (startIndex + config.minChunkChars).coerceAtMost(textLength)
            val windowEnd = (startIndex + config.maxChunkChars).coerceAtMost(textLength)
            val idealSplit = (startIndex + config.targetChunkChars).coerceIn(windowStart, windowEnd)

            val endIndex = findBestSplitIndex(
                text = normalized,
                windowStart = windowStart,
                windowEnd = windowEnd,
                idealSplit = idealSplit
            )

            rawRanges.add(startIndex until endIndex)
            if (endIndex >= textLength) break

            val rawNextStart = (endIndex - config.overlapChars).coerceAtLeast(startIndex + 1)
            val alignedNextStart = alignOverlapStart(
                text = normalized,
                minStart = startIndex + 1,
                rawOverlapStart = rawNextStart,
                chunkEnd = endIndex
            )
            startIndex = alignedNextStart.coerceAtLeast(startIndex + 1)
        }

        val total = rawRanges.size
        return rawRanges.mapIndexed { idx, range ->
            PdfTextChunk(
                index = idx,
                totalChunks = total,
                text = normalized.substring(range.first, range.last + 1).trim(),
                startCharOffset = range.first,
                endCharOffset = range.last + 1
            )
        }
    }

    private fun findBestSplitIndex(
        text: String,
        windowStart: Int,
        windowEnd: Int,
        idealSplit: Int
    ): Int {
        val slice = text.substring(windowStart, windowEnd)
        val idealOffset = idealSplit - windowStart

        // 1. Page boundary
        findClosestMatchOffset(PAGE_BOUNDARY_REGEX, slice, idealOffset)?.let {
            return windowStart + it
        }

        // 2. Recipe heading boundary
        findClosestMatchOffset(RECIPE_HEADING_REGEX, slice, idealOffset)?.let {
            return windowStart + it
        }

        // 3. Paragraph boundary (\n\n)
        findClosestMatchOffset(PARAGRAPH_BOUNDARY_REGEX, slice, idealOffset)?.let {
            return windowStart + it
        }

        // 4. Single line break
        val newlineBefore = slice.lastIndexOf('\n', idealOffset)
        val newlineAfter = slice.indexOf('\n', idealOffset)
        val bestNewline = listOf(newlineBefore, newlineAfter)
            .filter { it > 0 }
            .minByOrNull { abs(it - idealOffset) }
        if (bestNewline != null) {
            return windowStart + bestNewline
        }

        return idealSplit
    }

    private fun findClosestMatchOffset(regex: Regex, slice: String, idealOffset: Int): Int? {
        val matches = regex.findAll(slice).map { it.range.first }.filter { it > 0 }.toList()
        if (matches.isEmpty()) return null
        return matches.minByOrNull { abs(it - idealOffset) }
    }

    private fun alignOverlapStart(
        text: String,
        minStart: Int,
        rawOverlapStart: Int,
        chunkEnd: Int
    ): Int {
        val searchFrom = max(minStart, rawOverlapStart - 400)
        val searchTo = min(chunkEnd - 100, rawOverlapStart + 400)
        if (searchFrom >= searchTo) return rawOverlapStart

        val slice = text.substring(searchFrom, searchTo)
        val targetOffset = rawOverlapStart - searchFrom

        findClosestMatchOffset(PAGE_BOUNDARY_REGEX, slice, targetOffset)?.let {
            return (searchFrom + it).trimLeadingNewlines(text, chunkEnd)
        }
        findClosestMatchOffset(RECIPE_HEADING_REGEX, slice, targetOffset)?.let {
            return (searchFrom + it).trimLeadingNewlines(text, chunkEnd)
        }
        findClosestMatchOffset(PARAGRAPH_BOUNDARY_REGEX, slice, targetOffset)?.let {
            return (searchFrom + it).trimLeadingNewlines(text, chunkEnd)
        }

        val newlinePos = slice.indexOf('\n')
        if (newlinePos >= 0) {
            return (searchFrom + newlinePos).trimLeadingNewlines(text, chunkEnd)
        }

        return rawOverlapStart
    }

    private fun Int.trimLeadingNewlines(text: String, maxIndex: Int): Int {
        var idx = this
        while (idx < maxIndex && (text[idx] == '\n' || text[idx] == '\r')) {
            idx++
        }
        return idx
    }

    /**
     * Builds the strict structured JSON extraction prompt for a single PDF chunk.
     */
    fun buildPdfChunkExtractionPrompt(
        documentTitle: String,
        chunkIndex: Int,
        totalChunks: Int,
        chunkText: String
    ): String {
        val chunkLabel = "Część ${chunkIndex + 1}/$totalChunks"
        return """
            Wyodrębnij WSZYSTKIE przepisy kulinarne znajdujące się w poniższym fragmencie dokumentu PDF "$documentTitle" ($chunkLabel).
            Nie pomijaj żadnego kompletnego przepisu w tym fragmencie.
            Pomiń teksty nienależące do przepisów (np. spis treści bez składników, wstęp, ogólne porady).
            Jeśli na samym początku lub na samym końcu fragmentu przepis jest ucięty i nie posiada ani listy składników, ani sposobu przygotowania, pomiń ten niekompletny ułamek.

            Zwróć WYŁĄCZNIE poprawny obiekt JSON (bez znaczników markdown ```json i bez żadnego dodatkowego tekstu ani komentarzy) w dokładnie następującym formacie:
            {
              "recipes": [
                {
                  "title": "",
                  "servings": "",
                  "ingredients": [
                    {
                      "name": "",
                      "quantity": "",
                      "unit": ""
                    }
                  ],
                  "instructions": [
                    ""
                  ],
                  "calories": null,
                  "protein": null,
                  "carbs": null,
                  "fat": null,
                  "sourcePageOrChunk": "$chunkLabel"
                }
              ]
            }

            Jeśli w tym fragmencie nie ma żadnego przepisu, zwróć:
            {"recipes": []}

            Tekst fragmentu ($chunkLabel):
            $chunkText
        """.trimIndent()
    }

    /**
     * Builds a prompt asking Gemini once to repair malformed JSON into the strict schema.
     */
    fun buildJsonRepairPrompt(malformedJson: String, chunkLabel: String): String {
        return """
            Napraw poniższy uszkodzony zapis JSON i zwróć WYŁĄCZNIE poprawny składniowo obiekt JSON (bez znaczników markdown i bez dodatkowego tekstu) zgodny ze schematem:
            {
              "recipes": [
                {
                  "title": "",
                  "servings": "",
                  "ingredients": [
                    {
                      "name": "",
                      "quantity": "",
                      "unit": ""
                    }
                  ],
                  "instructions": [
                    ""
                  ],
                  "calories": null,
                  "protein": null,
                  "carbs": null,
                  "fat": null,
                  "sourcePageOrChunk": "$chunkLabel"
                }
              ]
            }

            Uszkodzony JSON do naprawy:
            ${malformedJson.take(24_000)}
        """.trimIndent()
    }

    /**
     * Safely parses Gemini's structured JSON response into [StructuredPdfRecipe] items.
     * Returns null ONLY if the JSON is completely unparseable so the caller knows to trigger JSON repair.
     * Returns an empty list if valid JSON had `"recipes": []`.
     */
    fun tryParseStructuredRecipesJson(
        rawResponse: String,
        defaultChunkLabel: String = ""
    ): List<StructuredPdfRecipe>? {
        val trimmed = rawResponse.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("NO_RECIPES_FOUND", ignoreCase = true)) return emptyList()

        val cleaned = stripMarkdownFences(trimmed)
        val candidatesToTry = listOfNotNull(
            cleaned,
            extractOutermostJsonStructure(cleaned),
            repairCommonJsonSyntaxIssues(cleaned),
            extractOutermostJsonStructure(cleaned)?.let { repairCommonJsonSyntaxIssues(it) }
        ).distinct()

        for (candidate in candidatesToTry) {
            parseJsonCandidate(candidate, defaultChunkLabel)?.let { return it }
        }

        // Try salvaging individual recipe objects if the outer array was truncated
        val salvaged = salvageRecipeObjectsFromTruncatedJson(cleaned, defaultChunkLabel)
        if (salvaged != null && salvaged.isNotEmpty()) {
            return salvaged
        }

        return null
    }

    private fun stripMarkdownFences(input: String): String {
        return input
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun extractOutermostJsonStructure(input: String): String? {
        val firstBrace = input.indexOf('{')
        val firstBracket = input.indexOf('[')
        val start = when {
            firstBrace >= 0 && firstBracket >= 0 -> min(firstBrace, firstBracket)
            firstBrace >= 0 -> firstBrace
            firstBracket >= 0 -> firstBracket
            else -> return null
        }
        val expectedClose = if (input[start] == '{') '}' else ']'
        val end = input.lastIndexOf(expectedClose)
        if (end <= start) return null
        return input.substring(start, end + 1)
    }

    private fun repairCommonJsonSyntaxIssues(input: String): String {
        // Remove trailing commas before closing } or ]
        return input.replace(Regex(""",\s*([}\]])"""), "$1")
    }

    private fun parseJsonCandidate(
        jsonText: String,
        defaultChunkLabel: String
    ): List<StructuredPdfRecipe>? {
        return try {
            val root = JSONTokener(jsonText).nextValue()
            val recipesArray: JSONArray = when (root) {
                is JSONObject -> {
                    when {
                        root.has("recipes") -> root.optJSONArray("recipes") ?: JSONArray()
                        root.has("title") && (root.has("ingredients") || root.has("instructions")) -> {
                            JSONArray().put(root)
                        }
                        else -> return null
                    }
                }
                is JSONArray -> root
                else -> return null
            }

            val parsedList = mutableListOf<StructuredPdfRecipe>()
            for (i in 0 until recipesArray.length()) {
                val obj = recipesArray.optJSONObject(i) ?: continue
                parseSingleRecipeObject(obj, defaultChunkLabel)?.let { parsedList.add(it) }
            }
            parsedList
        } catch (_: Exception) {
            null
        }
    }

    private fun salvageRecipeObjectsFromTruncatedJson(
        input: String,
        defaultChunkLabel: String
    ): List<StructuredPdfRecipe>? {
        val recipesKeyIndex = input.indexOf("\"recipes\"")
        val arrayStart = if (recipesKeyIndex >= 0) {
            input.indexOf('[', recipesKeyIndex)
        } else {
            input.indexOf('[')
        }
        if (arrayStart < 0) return null

        val salvaged = mutableListOf<StructuredPdfRecipe>()
        var idx = arrayStart + 1
        var braceDepth = 0
        var objectStart = -1
        var inString = false
        var escaped = false

        while (idx < input.length) {
            val ch = input[idx]
            if (escaped) {
                escaped = false
            } else if (ch == '\\' && inString) {
                escaped = true
            } else if (ch == '"') {
                inString = !inString
            } else if (!inString) {
                if (ch == '{') {
                    if (braceDepth == 0) {
                        objectStart = idx
                    }
                    braceDepth++
                } else if (ch == '}') {
                    braceDepth--
                    if (braceDepth == 0 && objectStart >= 0) {
                        val objStr = repairCommonJsonSyntaxIssues(input.substring(objectStart, idx + 1))
                        try {
                            val obj = JSONObject(objStr)
                            parseSingleRecipeObject(obj, defaultChunkLabel)?.let { salvaged.add(it) }
                        } catch (_: Exception) {
                            // Ignore unparseable fragment
                        }
                        objectStart = -1
                    }
                }
            }
            idx++
        }

        return salvaged.takeIf { it.isNotEmpty() }
    }

    private fun parseSingleRecipeObject(
        obj: JSONObject,
        defaultChunkLabel: String
    ): StructuredPdfRecipe? {
        val title = obj.optString("title").trim()
        if (title.isBlank()) return null

        val servingsRaw = obj.opt("servings")
        val servings = when (servingsRaw) {
            null, JSONObject.NULL -> "1"
            else -> servingsRaw.toString().trim().takeUnless { it.isBlank() || it == "null" } ?: "1"
        }

        val ingredients = parseStructuredIngredients(obj.opt("ingredients"))
        val instructions = parseStructuredInstructions(obj.opt("instructions"))

        // A valid recipe must have at least ingredients or instructions
        if (ingredients.isEmpty() && instructions.isEmpty()) return null

        val calories = parseNullableInt(obj.opt("calories"))
        val protein = parseNullableInt(obj.opt("protein"))
        val carbs = parseNullableInt(obj.opt("carbs"))
        val fat = parseNullableInt(obj.opt("fat"))
        val sourceChunk = obj.optString("sourcePageOrChunk").trim()
            .takeUnless { it.isBlank() || it == "null" }
            ?: defaultChunkLabel

        return StructuredPdfRecipe(
            title = title,
            servings = servings,
            ingredients = ingredients,
            instructions = instructions,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            sourcePageOrChunk = sourceChunk
        )
    }

    private fun parseStructuredIngredients(rawIngredients: Any?): List<StructuredPdfIngredient> {
        val result = mutableListOf<StructuredPdfIngredient>()
        when (rawIngredients) {
            is JSONArray -> {
                for (i in 0 until rawIngredients.length()) {
                    when (val item = rawIngredients.opt(i)) {
                        is JSONObject -> {
                            val name = item.optString("name").trim()
                                .takeUnless { it == "null" }.orEmpty()
                            val qty = item.opt("quantity")?.takeUnless { it == JSONObject.NULL }
                                ?.toString()?.trim()?.takeUnless { it == "null" }.orEmpty()
                            val unit = item.opt("unit")?.takeUnless { it == JSONObject.NULL }
                                ?.toString()?.trim()?.takeUnless { it == "null" }.orEmpty()
                            if (name.isNotBlank()) {
                                result.add(StructuredPdfIngredient(name = name, quantity = qty, unit = unit))
                            }
                        }
                        is String -> {
                            val clean = item.trim()
                            if (clean.isNotBlank()) {
                                result.add(parseIngredientLineToStructured(clean))
                            }
                        }
                    }
                }
            }
            is String -> {
                rawIngredients.split(Regex("[\n;]"))
                    .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        result.add(parseIngredientLineToStructured(line))
                    }
            }
        }
        return result
    }

    private fun parseIngredientLineToStructured(line: String): StructuredPdfIngredient {
        val leadingQtyMatch = Regex("""^(\d+(?:[.,/]\d+)?)\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ.]{1,12})?\s+(.+)$""")
            .find(line)
        if (leadingQtyMatch != null) {
            val qty = leadingQtyMatch.groupValues[1].trim()
            val unit = leadingQtyMatch.groupValues[2].trim()
            val name = leadingQtyMatch.groupValues[3].trim()
            return StructuredPdfIngredient(name = name, quantity = qty, unit = unit)
        }
        return StructuredPdfIngredient(name = line)
    }

    private fun parseStructuredInstructions(rawInstructions: Any?): List<String> {
        val steps = mutableListOf<String>()
        when (rawInstructions) {
            is JSONArray -> {
                for (i in 0 until rawInstructions.length()) {
                    when (val item = rawInstructions.opt(i)) {
                        is String -> {
                            val clean = item.trim()
                            if (clean.isNotBlank() && clean != "null") {
                                steps.add(clean)
                            }
                        }
                        is JSONObject -> {
                            val text = item.optString("text").ifBlank { item.optString("step") }.trim()
                            if (text.isNotBlank() && text != "null") {
                                steps.add(text)
                            }
                        }
                    }
                }
            }
            is String -> {
                rawInstructions.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != "null" }
                    .forEach { steps.add(it) }
            }
        }
        return steps
    }

    private fun parseNullableInt(raw: Any?): Int? {
        return when (raw) {
            null, JSONObject.NULL -> null
            is Number -> raw.toDouble().roundToInt()
            is String -> {
                val match = Regex("""\d+(?:[.,]\d+)?""").find(raw)?.value
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                match?.roundToInt()
            }
            else -> null
        }
    }

    // ========================================================================
    // DEDUPLICATION (Title + Major Ingredient Set Similarity)
    // ========================================================================

    private val TITLE_STOPWORDS = setOf(
        "z", "ze", "i", "w", "na", "do", "po", "pod", "nad", "bez", "oraz", "lub", "a", "o"
    )

    private val INGREDIENT_UNITS_AND_STOPWORDS = setOf(
        "g", "gr", "gram", "gramy", "gramów", "dag", "kg", "mg",
        "ml", "l", "litr", "litry",
        "szt", "szt.", "sztuk", "sztuki", "sztuka",
        "łyżka", "łyżki", "łyżek", "łyżeczka", "łyżeczki", "łyżeczek",
        "szklanka", "szklanki", "szklanek", "szczypta", "szczypty",
        "garść", "garście", "opakowanie", "opakowania", "paczka", "puszka", "puszki",
        "plaster", "plastry", "plasterek", "plasterki", "kromka", "kromki",
        "ząbek", "ząbki", "ząbków", "porcja", "porcje", "porcji",
        "ok", "około", "wg", "według", "smaku", "opcjonalnie", "do", "na", "z", "ze", "i", "lub", "w"
    )

    fun normalizeRecipeTitle(title: String): String {
        val lower = title.lowercase(Locale.forLanguageTag("pl-PL")).trim()
        // Strip leading numbering like "1.", "Przepis 12 -", etc.
        val withoutPrefix = lower
            .replace(Regex("""^(?:przepis\s*\d*[:.\-–\s]*|\d+[.):\-–\s]+)"""), "")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return withoutPrefix
    }

    private fun stemPolishToken(word: String): String {
        val w = word.lowercase(Locale.forLanguageTag("pl-PL")).trim()
        if (w.length <= 4) return w
        val suffixes = listOf(
            "ami", "ach", "om", "owi", "em", "ie", "ów", "ą", "ę", "u", "y", "i", "a", "e", "o"
        )
        for (suffix in suffixes) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 4) {
                return w.dropLast(suffix.length)
            }
        }
        return w
    }

    fun extractTitleTokens(title: String): Set<String> {
        val norm = normalizeRecipeTitle(title)
        if (norm.isBlank()) return emptySet()
        return norm.split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in TITLE_STOPWORDS }
            .map { stemPolishToken(it) }
            .toSet()
    }

    fun extractMajorIngredientSet(ingredientsText: String): Set<String> {
        if (ingredientsText.isBlank()) return emptySet()
        val lower = ingredientsText.lowercase(Locale.forLanguageTag("pl-PL"))
        val tokens = lower
            .replace(Regex("""\d+(?:[.,/]\d+)?"""), " ")
            .replace(Regex("""[^\p{L}\s]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in INGREDIENT_UNITS_AND_STOPWORDS }
            .map { stemPolishToken(it) }
            .filter { it.length >= 3 }
        return tokens.toSet()
    }

    fun areTitlesVerySimilar(titleA: String, titleB: String): Boolean {
        val normA = normalizeRecipeTitle(titleA)
        val normB = normalizeRecipeTitle(titleB)
        if (normA.isEmpty() || normB.isEmpty()) return false
        if (normA == normB) return true

        val tokensA = extractTitleTokens(titleA)
        val tokensB = extractTitleTokens(titleB)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return false

        val intersectionSize = tokensA.intersect(tokensB).size
        val unionSize = tokensA.union(tokensB).size
        val tokenJaccard = intersectionSize.toDouble() / unionSize.toDouble()

        val charSimilarity = normalizedLevenshteinSimilarity(normA, normB)

        // Both token Jaccard and character similarity must be high so that:
        // "Placki z jabłkami" vs "Placki z bananem" -> tokenJaccard = 1/3 = 0.33 -> FALSE
        // "Zupa pomidorowa klasyczna" vs "Zupa pomidorowa krem" -> tokenJaccard = 2/4 = 0.50 -> FALSE
        return tokenJaccard >= 0.85 && charSimilarity >= 0.88
    }

    fun areIngredientSetsVerySimilar(ingredientsA: String, ingredientsB: String): Boolean {
        val setA = extractMajorIngredientSet(ingredientsA)
        val setB = extractMajorIngredientSet(ingredientsB)
        if (setA.isEmpty() || setB.isEmpty()) return false

        val intersection = setA.intersect(setB).size.toDouble()
        val union = setA.union(setB).size.toDouble()
        val minSize = min(setA.size, setB.size).toDouble()

        val jaccard = if (union > 0) intersection / union else 0.0
        val overlap = if (minSize > 0) intersection / minSize else 0.0

        return jaccard >= 0.60 || (jaccard >= 0.50 && overlap >= 0.85)
    }

    /**
     * A recipe is a duplicate ONLY IF:
     * 1. Normalized title is very similar
     * AND
     * 2. Major ingredient set is also very similar
     */
    fun areRecipesDuplicates(a: ParsedRecipe, b: ParsedRecipe): Boolean {
        if (!areTitlesVerySimilar(a.title, b.title)) return false
        return areIngredientSetsVerySimilar(a.ingredients, b.ingredients)
    }

    fun deduplicateParsedRecipes(recipes: List<ParsedRecipe>): List<ParsedRecipe> {
        val result = mutableListOf<ParsedRecipe>()
        for (candidate in recipes) {
            if (candidate.title.isBlank()) continue
            val existingIndex = result.indexOfFirst { existing ->
                areRecipesDuplicates(existing, candidate)
            }
            if (existingIndex < 0) {
                result.add(candidate)
            } else {
                // Keep the richer / more complete recipe when merging an overlap duplicate
                result[existingIndex] = mergeDuplicateRecipes(result[existingIndex], candidate)
            }
        }
        return result
    }

    private fun mergeDuplicateRecipes(primary: ParsedRecipe, secondary: ParsedRecipe): ParsedRecipe {
        val betterIngredients = if (secondary.ingredients.lines().size > primary.ingredients.lines().size) {
            secondary.ingredients
        } else {
            primary.ingredients
        }
        val betterInstructions = if (secondary.instructions.length > primary.instructions.length) {
            secondary.instructions
        } else {
            primary.instructions
        }
        return primary.copy(
            servings = primary.servings.ifBlank { secondary.servings },
            ingredients = betterIngredients,
            instructions = betterInstructions,
            calories = if (primary.calories > 0) primary.calories else secondary.calories,
            protein = if (primary.protein > 0) primary.protein else secondary.protein,
            carbs = if (primary.carbs > 0) primary.carbs else secondary.carbs,
            fat = if (primary.fat > 0) primary.fat else secondary.fat
        )
    }

    private fun normalizedLevenshteinSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(a, b)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }

    /**
     * Processes all PDF chunks sequentially from [startChunkIndex] to `chunks.size - 1`.
     * Never processes chunks in parallel.
     * Never stops early on a 0-recipe chunk or malformed JSON chunk.
     * Repairs malformed JSON locally and via one Gemini repair request if needed.
     * Preserves partial results after every completed chunk and supports resuming on HTTP 429/503.
     */
    suspend fun executePdfRecipeExtraction(
        documentTitle: String,
        chunks: List<PdfTextChunk>,
        startChunkIndex: Int = 0,
        initialRawRecipes: List<ParsedRecipe> = emptyList(),
        initialPerChunkCounts: Map<Int, Int> = emptyMap(),
        chunkCall: suspend (prompt: String) -> Result<String>,
        repairCall: suspend (repairPrompt: String) -> Result<String> = chunkCall,
        onProgress: (
            currentChunkOneBased: Int,
            totalChunks: Int,
            foundSoFarCount: Int,
            partialDeduplicated: List<ParsedRecipe>,
            partialRaw: List<ParsedRecipe>,
            nextChunkIndex: Int,
            perChunkCounts: Map<Int, Int>
        ) -> Unit = { _, _, _, _, _, _, _ -> }
    ): PdfChunkExtractionOutcome {
        if (chunks.isEmpty()) {
            return PdfChunkExtractionOutcome(
                completedAllChunks = true,
                rateLimitedOrPaused = false,
                nextChunkIndex = 0,
                totalChunks = 0,
                perChunkCounts = emptyMap(),
                recipesBeforeDeduplication = emptyList(),
                deduplicatedRecipes = emptyList()
            )
        }

        val rawRecipes = initialRawRecipes.toMutableList()
        val perChunkCounts = initialPerChunkCounts.toMutableMap()
        var currentDeduplicated = deduplicateParsedRecipes(rawRecipes)
        val safeStart = startChunkIndex.coerceIn(0, chunks.size)

        for (i in safeStart until chunks.size) {
            val chunk = chunks[i]
            val chunkLabel = "Część ${i + 1}/${chunks.size}"

            // Notify start of chunk (i + 1) / totalChunks with current recipe count
            onProgress(
                i + 1,
                chunks.size,
                currentDeduplicated.size,
                currentDeduplicated,
                rawRecipes.toList(),
                i,
                perChunkCounts.toMap()
            )

            val prompt = buildPdfChunkExtractionPrompt(
                documentTitle = documentTitle,
                chunkIndex = i,
                totalChunks = chunks.size,
                chunkText = chunk.text
            )

            val responseResult = chunkCall(prompt)
            if (responseResult.isFailure) {
                val err = responseResult.exceptionOrNull()
                val errMsg = err?.message ?: "Błąd analizy AI."
                val isRateLimitOr503 = err is com.example.ai.GeminiRateLimitOrOverloadException ||
                    errMsg.contains("Limit zapytań", ignoreCase = true) ||
                    errMsg.contains("429") ||
                    errMsg.contains("503") ||
                    errMsg.contains("chwilowo niedostępna", ignoreCase = true)

                return PdfChunkExtractionOutcome(
                    completedAllChunks = false,
                    rateLimitedOrPaused = isRateLimitOr503,
                    nextChunkIndex = i,
                    totalChunks = chunks.size,
                    perChunkCounts = perChunkCounts.toMap(),
                    recipesBeforeDeduplication = rawRecipes.toList(),
                    deduplicatedRecipes = currentDeduplicated,
                    errorMessage = errMsg
                )
            }

            val rawResponse = responseResult.getOrNull().orEmpty()
            var structuredRecipes = tryParseStructuredRecipesJson(rawResponse, chunkLabel)

            // If JSON is malformed, ask Gemini once to repair the JSON, then continue with next chunk
            if (structuredRecipes == null) {
                val repairPrompt = buildJsonRepairPrompt(rawResponse, chunkLabel)
                val repairResult = repairCall(repairPrompt)
                val repairedText = repairResult.getOrNull().orEmpty()
                structuredRecipes = tryParseStructuredRecipesJson(repairedText, chunkLabel) ?: emptyList()
            }

            val parsedFromChunk = structuredRecipes.map { it.toParsedRecipe(documentTitle) }
            perChunkCounts[i] = parsedFromChunk.size
            rawRecipes.addAll(parsedFromChunk)
            currentDeduplicated = deduplicateParsedRecipes(rawRecipes)

            // Preserve partial results immediately after every completed chunk
            onProgress(
                i + 1,
                chunks.size,
                currentDeduplicated.size,
                currentDeduplicated,
                rawRecipes.toList(),
                i + 1,
                perChunkCounts.toMap()
            )
        }

        return PdfChunkExtractionOutcome(
            completedAllChunks = true,
            rateLimitedOrPaused = false,
            nextChunkIndex = chunks.size,
            totalChunks = chunks.size,
            perChunkCounts = perChunkCounts.toMap(),
            recipesBeforeDeduplication = rawRecipes.toList(),
            deduplicatedRecipes = currentDeduplicated,
            errorMessage = null
        )
    }
}
