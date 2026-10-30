package com.example.viewmodel

import com.example.api.OFFProduct
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.data.RecipeIngredientEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

enum class SubstituteOptionMode(val key: String, val label: String) {
    OPEN_FOOD_FACTS("OFF", "Szukaj w Open Food Facts"),
    BARCODE_SCAN("SCAN", "Skanuj kod kreskowy"),
    LOCAL_DATABASE("LOCAL", "Wybierz z lokalnej bazy"),
    AI_SUGGESTION("AI", "Zapytaj AI o zamiennik")
}

data class SubstituteCandidate(
    val name: String,
    val brand: String = "",
    val barcode: String = "",
    val imageUrl: String = "",
    val caloriesPer100g: Float? = null,
    val proteinPer100g: Float? = null,
    val carbsPer100g: Float? = null,
    val fatPer100g: Float? = null,
    val defaultQuantity: Float = 100f,
    val defaultUnit: String = "g",
    val aiReason: String = "",
    val source: SubstituteOptionMode = SubstituteOptionMode.OPEN_FOOD_FACTS
) {
    fun nutritionForQuantity(quantityGramsOrMl: Float): ScaledMealNutrition {
        val ratio = (quantityGramsOrMl.coerceAtLeast(0f)) / 100f
        return ScaledMealNutrition(
            calories = caloriesPer100g?.let { (it * ratio).roundToInt() },
            protein = proteinPer100g?.let { (it * ratio).roundToInt() },
            carbs = carbsPer100g?.let { (it * ratio).roundToInt() },
            fat = fatPer100g?.let { (it * ratio).roundToInt() }
        )
    }
}

data class MealIngredientSubstitution(
    val entityId: Long = 0L,
    val mealPlanId: Long,
    val originalIngredientName: String,
    val originalBaseQuantity: Float,
    val originalUnit: String,
    val replacementName: String,
    val brand: String = "",
    val barcode: String = "",
    val imageUrl: String = "",
    val baseQuantityPerServing: Float,
    val unit: String = "g",
    val caloriesPer100g: Float? = null,
    val proteinPer100g: Float? = null,
    val carbsPer100g: Float? = null,
    val fatPer100g: Float? = null
) {
    fun quantityForServings(servings: Float): Float {
        return baseQuantityPerServing * MealPlannerSupport.safeServings(servings)
    }

    fun formatReplacementForServings(servings: Float): String {
        val scaledQty = quantityForServings(servings)
        val qtyStr = MealPlannerSupport.formatQuantityNumber(scaledQty)
        val cleanUnit = unit.trim().ifBlank { "g" }
        return "${replacementName.trim()} — $qtyStr $cleanUnit"
    }

    fun formatOriginalForServings(servings: Float): String {
        val scaledQty = originalBaseQuantity * MealPlannerSupport.safeServings(servings)
        val qtyStr = MealPlannerSupport.formatQuantityNumber(scaledQty)
        val cleanUnit = originalUnit.trim().ifBlank { "g" }
        return "${originalIngredientName.trim()} — $qtyStr $cleanUnit"
    }
}

data class ResolvedMealIngredientItem(
    val displayLine: String,
    val ingredientName: String,
    val quantityForMeal: Float?,
    val unit: String,
    val originalIngredientName: String,
    val originalBaseQuantity: Float?,
    val originalUnit: String,
    val substitution: MealIngredientSubstitution? = null
)

object IngredientSubstitutionSupport {

    const val EXTRA_PREFIX = "__FITMANGO_EXTRA_"
    fun isExtra(sub: MealIngredientSubstitution) = sub.originalIngredientName.startsWith(EXTRA_PREFIX)

    private const val SUB_PREFIX = "SUB_V1"
    private const val DELIM = "::"
    private const val UNAVAILABLE_NUTRIENT = -1f

    data class KnownMacroProfile(
        val caloriesPer100g: Float,
        val proteinPer100g: Float?,
        val carbsPer100g: Float?,
        val fatPer100g: Float?
    )

    val defaultLocalCatalog: List<SubstituteCandidate> = listOf(
        SubstituteCandidate(
            name = "Kasza bulgur",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 342f,
            proteinPer100g = 12.3f,
            carbsPer100g = 69.0f,
            fatPer100g = 1.3f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Kasza gryczana",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 343f,
            proteinPer100g = 13.3f,
            carbsPer100g = 71.5f,
            fatPer100g = 3.4f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Ryż brązowy",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 350f,
            proteinPer100g = 7.5f,
            carbsPer100g = 76.0f,
            fatPer100g = 2.7f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Ryż biały",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 350f,
            proteinPer100g = 6.7f,
            carbsPer100g = 78.9f,
            fatPer100g = 0.7f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Komosa ryżowa (Quinoa)",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 368f,
            proteinPer100g = 14.1f,
            carbsPer100g = 64.2f,
            fatPer100g = 6.1f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Makaron pełnoziarnisty",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 348f,
            proteinPer100g = 13.0f,
            carbsPer100g = 67.0f,
            fatPer100g = 2.5f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Płatki owsiane górskie",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 372f,
            proteinPer100g = 13.5f,
            carbsPer100g = 58.7f,
            fatPer100g = 7.0f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Pierś z indyka",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 104f,
            proteinPer100g = 24.0f,
            carbsPer100g = 0.0f,
            fatPer100g = 1.0f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Pierś z kurczaka",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 165f,
            proteinPer100g = 31.0f,
            carbsPer100g = 0.0f,
            fatPer100g = 3.6f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Tofu naturalne",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 124f,
            proteinPer100g = 14.0f,
            carbsPer100g = 1.5f,
            fatPer100g = 7.0f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Łosoś świeży",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 208f,
            proteinPer100g = 20.0f,
            carbsPer100g = 0.0f,
            fatPer100g = 13.0f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Twaróg chudy",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 99f,
            proteinPer100g = 19.8f,
            carbsPer100g = 3.5f,
            fatPer100g = 0.5f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Jogurt Skyr naturalny",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 64f,
            proteinPer100g = 12.0f,
            carbsPer100g = 4.1f,
            fatPer100g = 0.0f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Mleko migdałowe niesłodzone",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 13f,
            proteinPer100g = 0.4f,
            carbsPer100g = 0.1f,
            fatPer100g = 1.1f,
            defaultUnit = "ml",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Mleko kokosowe",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 185f,
            proteinPer100g = 1.6f,
            carbsPer100g = 2.8f,
            fatPer100g = 19.0f,
            defaultUnit = "ml",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Oliwa z oliwek",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 884f,
            proteinPer100g = 0.0f,
            carbsPer100g = 0.0f,
            fatPer100g = 100.0f,
            defaultUnit = "ml",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Bataty (słodkie ziemniaki)",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 86f,
            proteinPer100g = 1.6f,
            carbsPer100g = 20.1f,
            fatPer100g = 0.1f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        ),
        SubstituteCandidate(
            name = "Chleb żytni razowy",
            brand = "Baza lokalna MangoFit",
            caloriesPer100g = 223f,
            proteinPer100g = 5.6f,
            carbsPer100g = 45.5f,
            fatPer100g = 1.7f,
            defaultUnit = "g",
            source = SubstituteOptionMode.LOCAL_DATABASE
        )
    )

    fun fromOpenFoodFactsProduct(
        product: OFFProduct,
        source: SubstituteOptionMode = SubstituteOptionMode.OPEN_FOOD_FACTS
    ): SubstituteCandidate? {
        val rawName = product.productName?.trim().orEmpty()
        if (rawName.isBlank()) return null

        val nutriments = product.nutriments
        val defaultUnit = if (rawName.lowercase(Locale.forLanguageTag("pl-PL")).let {
                it.contains("mleko") || it.contains("napój") || it.contains("oliwa") || it.contains("olej") || it.contains("sok")
            }) "ml" else "g"

        return SubstituteCandidate(
            name = rawName,
            brand = product.brands?.trim().orEmpty(),
            barcode = product.code?.trim().orEmpty(),
            imageUrl = product.imageUrl?.trim().orEmpty(),
            caloriesPer100g = nutriments?.energyKcal100g,
            proteinPer100g = nutriments?.proteins100g,
            carbsPer100g = nutriments?.carbohydrates100g,
            fatPer100g = nutriments?.fat100g,
            defaultUnit = defaultUnit,
            source = source
        )
    }

    fun buildLocalDatabaseCandidates(
        roomIngredients: List<RecipeIngredientEntity>,
        searchQuery: String = ""
    ): List<SubstituteCandidate> {
        val fromRoom = roomIngredients
            .filter { it.recipeId >= 0L && !it.foodName.startsWith(SUB_PREFIX) && it.foodName.isNotBlank() }
            .map { entity ->
                val cleanUnit = normalizeMassOrVolumeUnit(entity.unit)
                val remembered = NutritionAttentionSupport.profile(entity.foodName)
                SubstituteCandidate(
                    name = entity.foodName.trim(),
                    brand = "Zapisany składnik w bazie Room",
                    caloriesPer100g = entity.caloriesPer100g.takeIf { it >= 0f } ?: remembered?.caloriesPer100g,
                    proteinPer100g = entity.proteinPer100g.takeIf { it >= 0f } ?: remembered?.proteinPer100g,
                    carbsPer100g = entity.carbsPer100g.takeIf { it >= 0f } ?: remembered?.carbsPer100g,
                    fatPer100g = entity.fatPer100g.takeIf { it >= 0f } ?: remembered?.fatPer100g,
                    defaultQuantity = entity.grams.takeIf { it > 0f } ?: entity.quantity.takeIf { it > 0f } ?: 100f,
                    defaultUnit = cleanUnit,
                    source = SubstituteOptionMode.LOCAL_DATABASE
                )
            }

        val combined = (defaultLocalCatalog + fromRoom)
            .distinctBy { it.name.trim().lowercase(Locale.forLanguageTag("pl-PL")) }

        val trimmedQuery = searchQuery.trim().lowercase(Locale.forLanguageTag("pl-PL"))
        if (trimmedQuery.isBlank()) return combined
        return combined.filter {
            it.name.lowercase(Locale.forLanguageTag("pl-PL")).contains(trimmedQuery) ||
                it.brand.lowercase(Locale.forLanguageTag("pl-PL")).contains(trimmedQuery)
        }
    }

    fun normalizeMassOrVolumeUnit(rawUnit: String): String {
        val lower = rawUnit.trim().lowercase(Locale.forLanguageTag("pl-PL"))
        return when {
            lower == "ml" || lower == "mililitr" || lower == "mililitry" || lower == "mililitrów" -> "ml"
            lower == "l" || lower == "litr" -> "ml"
            else -> "g"
        }
    }

    fun isValidSupportedBarcode(rawBarcode: String): Boolean {
        val digits = rawBarcode.trim()
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return false
        // Support EAN-8 (8), UPC-E (6..8), UPC-A (12), EAN-13 (13), GTIN-14 (14)
        return digits.length in 6..14
    }

    fun detectBarcodeFormatLabel(rawBarcode: String): String {
        val digits = rawBarcode.trim()
        return when (digits.length) {
            13 -> "EAN-13"
            8 -> "EAN-8"
            12 -> "UPC-A"
            6, 7 -> "UPC-E"
            else -> "EAN/UPC"
        }
    }

    fun formatOriginalVsReplacementPreview(
        originalName: String,
        originalQty: Float,
        originalUnit: String,
        replacementName: String,
        replacementQty: Float,
        replacementUnit: String
    ): Pair<String, String> {
        val origQtyStr = MealPlannerSupport.formatQuantityNumber(originalQty.coerceAtLeast(1f))
        val origUnitStr = originalUnit.trim().ifBlank { "g" }
        val repQtyStr = MealPlannerSupport.formatQuantityNumber(replacementQty.coerceAtLeast(1f))
        val repUnitStr = normalizeMassOrVolumeUnit(replacementUnit)
        val origLine = "${originalName.trim()} — $origQtyStr $origUnitStr"
        val repLine = "${replacementName.trim()} — $repQtyStr $repUnitStr"
        return origLine to repLine
    }

    fun encodeMealSubstitutionEntity(
        existingId: Long = 0L,
        mealPlanId: Long,
        originalIngredientName: String,
        originalBaseQuantity: Float,
        originalUnit: String,
        candidate: SubstituteCandidate,
        baseQuantityPerServing: Float,
        unit: String
    ): RecipeIngredientEntity {
        val safeOrigName = originalIngredientName.trim().replace(DELIM, " ")
        val safeRepName = candidate.name.trim().replace(DELIM, " ")
        val safeBrand = candidate.brand.trim().replace(DELIM, " ")
        val safeBarcode = candidate.barcode.trim().replace(DELIM, " ")
        val safeImage = candidate.imageUrl.trim().replace(DELIM, " ")
        val safeOrigUnit = originalUnit.trim().ifBlank { "g" }.replace(DELIM, " ")
        val safeOrigQty = originalBaseQuantity.coerceAtLeast(0f)

        val encodedFoodName = listOf(
            SUB_PREFIX,
            safeOrigName,
            safeRepName,
            safeBrand,
            safeBarcode,
            safeImage,
            safeOrigQty.toString(),
            safeOrigUnit
        ).joinToString(DELIM)

        val normalizedUnit = normalizeMassOrVolumeUnit(unit)
        val safeBaseQty = baseQuantityPerServing.coerceAtLeast(0f)

        return RecipeIngredientEntity(
            id = existingId,
            recipeId = -mealPlanId,
            foodName = encodedFoodName,
            quantity = safeBaseQty,
            unit = normalizedUnit,
            grams = safeBaseQty,
            caloriesPer100g = candidate.caloriesPer100g ?: UNAVAILABLE_NUTRIENT,
            proteinPer100g = candidate.proteinPer100g ?: UNAVAILABLE_NUTRIENT,
            carbsPer100g = candidate.carbsPer100g ?: UNAVAILABLE_NUTRIENT,
            fatPer100g = candidate.fatPer100g ?: UNAVAILABLE_NUTRIENT
        )
    }

    fun decodeMealSubstitutionEntity(entity: RecipeIngredientEntity): MealIngredientSubstitution? {
        if (entity.recipeId >= 0L || !entity.foodName.startsWith("$SUB_PREFIX$DELIM")) {
            return null
        }
        val parts = entity.foodName.split(DELIM)
        if (parts.size < 6) return null

        val origName = parts.getOrNull(1)?.trim().orEmpty()
        val repName = parts.getOrNull(2)?.trim().orEmpty()
        if (origName.isBlank() || repName.isBlank()) return null

        val brand = parts.getOrNull(3)?.trim().orEmpty()
        val barcode = parts.getOrNull(4)?.trim().orEmpty()
        val imageUrl = parts.getOrNull(5)?.trim().orEmpty()
        val origBaseQty = parts.getOrNull(6)?.toFloatOrNull() ?: entity.quantity.coerceAtLeast(1f)
        val origUnit = parts.getOrNull(7)?.trim()?.ifBlank { "g" } ?: "g"

        return MealIngredientSubstitution(
            entityId = entity.id,
            mealPlanId = -entity.recipeId,
            originalIngredientName = origName,
            originalBaseQuantity = origBaseQty,
            originalUnit = origUnit,
            replacementName = repName,
            brand = brand,
            barcode = barcode,
            imageUrl = imageUrl,
            baseQuantityPerServing = entity.quantity.coerceAtLeast(0f),
            unit = normalizeMassOrVolumeUnit(entity.unit),
            caloriesPer100g = entity.caloriesPer100g.takeIf { it >= 0f },
            proteinPer100g = entity.proteinPer100g.takeIf { it >= 0f },
            carbsPer100g = entity.carbsPer100g.takeIf { it >= 0f },
            fatPer100g = entity.fatPer100g.takeIf { it >= 0f }
        )
    }

    fun getSubstitutionsForMeal(
        mealPlanId: Long,
        allEntities: List<RecipeIngredientEntity>
    ): List<MealIngredientSubstitution> {
        if (mealPlanId <= 0L) return emptyList()
        return allEntities
            .filter { it.recipeId == -mealPlanId }
            .mapNotNull { decodeMealSubstitutionEntity(it) }
    }

    fun resolveMealIngredientsWithSubstitutions(
        recipe: RecipeEntity?,
        meal: MealPlanEntity,
        allIngredientEntities: List<RecipeIngredientEntity>
    ): List<ResolvedMealIngredientItem> {
        val structuredForRecipe = if (recipe != null) {
            allIngredientEntities.filter { it.recipeId == recipe.id }
        } else {
            emptyList()
        }
        val baseParsed = MealPlannerSupport.parseRecipeIngredients(
            ingredientsText = recipe?.ingredients.orEmpty(),
            fallbackEntities = structuredForRecipe
        )
        val substitutions = getSubstitutionsForMeal(meal.id, allIngredientEntities)
        val servings = MealPlannerSupport.safeServings(meal.servings)

        val resolved = baseParsed.mapNotNull { parsed ->
            val matchedSub = substitutions.find { sub ->
                !isExtra(sub) && normalizeIngredientKey(sub.originalIngredientName) == normalizeIngredientKey(parsed.name)
            } ?: substitutions.find { sub ->
                !isExtra(sub) && ingredientNamesMatch(sub.originalIngredientName, parsed.name)
            }
            if (matchedSub != null) {
                if (matchedSub.baseQuantityPerServing <= 0f) return@mapNotNull null
                val scaledRepQty = matchedSub.quantityForServings(servings)
                ResolvedMealIngredientItem(
                    displayLine = matchedSub.formatReplacementForServings(servings),
                    ingredientName = matchedSub.replacementName,
                    quantityForMeal = scaledRepQty,
                    unit = matchedSub.unit,
                    originalIngredientName = parsed.name,
                    originalBaseQuantity = parsed.baseQuantity,
                    originalUnit = parsed.unit.ifBlank { "g" },
                    substitution = matchedSub
                )
            } else {
                val scaledOrigQty = parsed.scaledQuantity(servings)
                ResolvedMealIngredientItem(
                    displayLine = parsed.formatForServings(servings),
                    ingredientName = parsed.name,
                    quantityForMeal = scaledOrigQty,
                    unit = parsed.unit.ifBlank { "g" },
                    originalIngredientName = parsed.name,
                    originalBaseQuantity = parsed.baseQuantity,
                    originalUnit = parsed.unit.ifBlank { "g" },
                    substitution = null
                )
            }
        }
        return resolved + substitutions.filter { isExtra(it) && it.baseQuantityPerServing > 0f }.map { sub ->
            ResolvedMealIngredientItem(sub.formatReplacementForServings(servings), sub.replacementName,
                sub.quantityForServings(servings), sub.unit, sub.originalIngredientName, 0f, sub.originalUnit, sub)
        }
    }

    fun ingredientNamesMatch(a: String, b: String): Boolean {
        val normA = normalizeIngredientKey(a)
        val normB = normalizeIngredientKey(b)
        if (normA.isBlank() || normB.isBlank()) return false
        return normA == normB || normA.contains(normB) || normB.contains(normA)
    }

    fun normalizeIngredientKey(raw: String): String {
        val parsed = MealPlannerSupport.parseSingleIngredientLine(raw)
        val nameOnly = if (parsed.name.isNotBlank()) parsed.name else raw
        return nameOnly.trim().lowercase(Locale.forLanguageTag("pl-PL"))
            .replace(Regex("\\s+"), " ")
    }

    /** Culinary seasonings are deliberately excluded by the user's preference. */
    private val seasoningPattern = Regex("^(sol|pieprz|cynamon|kurkuma|kumin|kminek|imbir (mielony|w proszku)|galka muszkatolowa|kardamon|gozdziki|oregano|bazylia|tymianek|majeranek|rozmaryn|lubczyk|koperek|koper|pietruszka (suszona|natka)|natka pietruszki|lisc laurowy|liscie laurowe|ziele angielskie|chili|chilli|curry|ziola prowansalskie|ziola suszone|estragon|szalwia|czaber|szafran|przyprawa|przyprawy)(\\b|$)")
    private val driedPaprikaPattern = Regex("^papryk[aie].*(mielon|prosz|wedzon|przypraw)")
    private val driedGarlicPattern = Regex("^czosn(ek|ku|kiem).*(granulowan|prosz|susz)")
    private val declinedSeasonings = setOf("soli", "sola", "pieprzu", "pieprzem", "cynamonu", "cynamonem", "kurkumy", "kurkume",
        "kuminu", "kminu", "kminku", "kardamonu", "gozdzikow", "bazylii", "tymianku", "majeranku", "rozmarynu", "lubczyku",
        "koperku", "estragonu", "szalwii", "czabru", "szafranu")
    private val declinedCompoundSeasoning = Regex("^(galki muszkatolowej|ziol prowansalskich|ziol suszonych|liscia laurowego|lisci laurowych|ziela angielskiego|natki pietruszki|imbiru mielonego)(\\b|$)")
    private val combiningMarksPattern = Regex("\\p{M}+")
    fun isIgnoredSeasoning(name: String): Boolean {
        val key = java.text.Normalizer.normalize(name.trim().lowercase(Locale.forLanguageTag("pl-PL")), java.text.Normalizer.Form.NFD)
            .replace(combiningMarksPattern, "").replace('ł', 'l')
        return seasoningPattern.containsMatchIn(key) || key.substringBefore(' ') in declinedSeasonings ||
            declinedCompoundSeasoning.containsMatchIn(key) || driedPaprikaPattern.containsMatchIn(key) || driedGarlicPattern.containsMatchIn(key)
    }

    fun lookupKnownIngredientNutrition(
        ingredientName: String,
        recipeStructuredIngredients: List<RecipeIngredientEntity> = emptyList()
    ): KnownMacroProfile? {
        val norm = normalizeIngredientKey(ingredientName)
        if (norm.isBlank()) return null

        val remembered = NutritionAttentionSupport.profile(ingredientName)
        val automatic = ImportedIngredientReferences.find(norm)?.profile
        // 1. Check structured RecipeIngredientEntity for this recipe first
        (recipeStructuredIngredients.find { it.recipeId >= 0L && normalizeIngredientKey(it.foodName) == norm }
            ?: recipeStructuredIngredients.find {
            it.recipeId >= 0L && (if (automatic != null) ImportedIngredientReferences.sameFood(it.foodName, norm)
                else ingredientNamesMatch(it.foodName, norm))
        })?.let { entity ->
            val calories = entity.caloriesPer100g.takeIf { it.isFinite() && it >= 0f }
                ?: remembered?.caloriesPer100g ?: automatic?.caloriesPer100g
            if (calories != null) {
                return KnownMacroProfile(
                    caloriesPer100g = calories,
                    proteinPer100g = entity.proteinPer100g.takeIf { it.isFinite() && it >= 0f } ?: remembered?.proteinPer100g ?: automatic?.proteinPer100g,
                    carbsPer100g = entity.carbsPer100g.takeIf { it.isFinite() && it >= 0f } ?: remembered?.carbsPer100g ?: automatic?.carbsPer100g,
                    fatPer100g = entity.fatPer100g.takeIf { it.isFinite() && it >= 0f } ?: remembered?.fatPer100g ?: automatic?.fatPer100g
                )
            }
        }

        if (remembered != null) return remembered
        if (automatic != null) return automatic
        // Beverage names must not match dry oats through the word "owsiany".
        val oatDrink = (norm.contains("napój") || norm.contains("napoj") || norm.contains("drink") || norm.contains("milk")) && (norm.contains("owsian") || norm.contains("oat"))
        if (oatDrink) return null

        // 2. Check default local catalog
        defaultLocalCatalog.find { ingredientNamesMatch(it.name, norm) }?.let { cand ->
            if (cand.caloriesPer100g != null) {
                return KnownMacroProfile(
                    caloriesPer100g = cand.caloriesPer100g,
                    proteinPer100g = cand.proteinPer100g,
                    carbsPer100g = cand.carbsPer100g,
                    fatPer100g = cand.fatPer100g
                )
            }
        }

        // 3. Common Polish & English ingredient reference values per 100g
        return when {
            norm.contains("ryż") || norm.contains("ryz") || norm.contains("rice") ->
                KnownMacroProfile(350f, 6.7f, 78.9f, 0.7f)
            norm.contains("kurczak") || norm.contains("chicken") ->
                KnownMacroProfile(165f, 31.0f, 0.0f, 3.6f)
            norm.contains("indyk") || norm.contains("turkey") ->
                KnownMacroProfile(104f, 24.0f, 0.0f, 1.0f)
            norm.contains("łosoś") || norm.contains("losos") || norm.contains("salmon") ->
                KnownMacroProfile(208f, 20.0f, 0.0f, 13.0f)
            norm.contains("makaron") || norm.contains("pasta") ->
                KnownMacroProfile(352f, 12.5f, 71.0f, 1.5f)
            norm.contains("kasza") || norm.contains("quinoa") || norm.contains("komosa") ->
                KnownMacroProfile(345f, 12.5f, 69.0f, 2.2f)
            norm.contains("płatki") || norm.contains("owsian") || norm.contains("oats") ->
                KnownMacroProfile(372f, 13.5f, 58.7f, 7.0f)
            norm.contains("chleb") || norm.contains("bread") || norm.contains("tost") ->
                KnownMacroProfile(250f, 9.0f, 45.0f, 3.0f)
            norm.contains("awokado") || norm.contains("avocado") ->
                KnownMacroProfile(160f, 2.0f, 9.0f, 15.0f)
            norm.contains("jajk") || norm.contains("egg") ->
                KnownMacroProfile(155f, 13.0f, 1.1f, 11.0f)
            norm.contains("mleko kokosowe") || norm.contains("kokos") ->
                KnownMacroProfile(185f, 1.6f, 2.8f, 19.0f)
            norm.contains("mleko") || norm.contains("milk") ->
                KnownMacroProfile(50f, 3.3f, 4.8f, 2.0f)
            norm.contains("papryka") || norm.contains("pepper") ->
                KnownMacroProfile(31f, 1.0f, 6.0f, 0.3f)
            // Match the oil word, not a substring of an unrelated name such as "kolejka".
            norm.contains("oliwa") || norm.contains("olive oil") || norm.split(Regex("[^\\p{L}]+")).any { it.startsWith("olej") } ->
                KnownMacroProfile(884f, 0.0f, 0.0f, 100.0f)
            norm.contains("twaróg") || norm.contains("twarog") || norm.contains("ser") ->
                KnownMacroProfile(99f, 19.8f, 3.5f, 0.5f)
            norm.contains("ziemniak") || norm.contains("potato") ->
                KnownMacroProfile(77f, 2.0f, 17.0f, 0.1f)
            else -> null
        }
    }

    fun quantityInGramsOrMl(name: String, quantity: Float, unit: String, structured: List<RecipeIngredientEntity>): Float? =
        knownQuantity(name, quantity, unit, structured)

    /** Unknown quantity or energy stays unknown, rather than looking like a zero-calorie ingredient. */
    fun ingredientCalories(name: String, quantity: Float?, unit: String,
        structured: List<RecipeIngredientEntity>, replacement: MealIngredientSubstitution? = null): Int? {
        val amount = quantity?.takeIf { it.isFinite() && it > 0f } ?: return null
        val grams = knownQuantity(name, amount, unit, structured)?.takeIf { it.isFinite() && it > 0f } ?: return null
        val energy = if (replacement != null) replacement.caloriesPer100g
            else lookupKnownIngredientNutrition(name, structured)?.caloriesPer100g
        val kcal = energy?.takeIf { it.isFinite() && it >= 0f }?.let { it * grams / 100f } ?: return null
        return kcal.takeIf { it.isFinite() && it >= 0f && it < Int.MAX_VALUE.toFloat() }?.roundToInt()
    }

    fun ingredientEnergyLabel(name: String, quantity: Float?, unit: String,
        structured: List<RecipeIngredientEntity>, replacement: MealIngredientSubstitution? = null): String {
        if (isIgnoredSeasoning(name)) return "Przyprawa • pominięta w bilansie"
        val kcal = ingredientCalories(name, quantity, unit, structured, replacement) ?: run {
            val profileEnergy = if (replacement != null) replacement.caloriesPer100g else lookupKnownIngredientNutrition(name, structured)?.caloriesPer100g
            return profileEnergy?.takeIf { it.isFinite() && it >= 0f }?.let {
                "${MealPlannerSupport.formatQuantityNumber(it)} kcal / 100 ${UniversalNutritionRepair.basis(unit)} • podaj ilość lub masę"
            } ?: "kcal: brak danych"
        }
        val estimatedMass = quantity != null && structuredAmount(name, quantity, unit, structured) == null &&
            ImportedIngredientReferences.estimatedGrams(name, quantity, unit) != null
        val reference = replacement == null && ImportedIngredientReferences.find(name) != null &&
            NutritionAttentionSupport.profile(name) == null && structured.none { row ->
                (normalizeIngredientKey(row.foodName) == normalizeIngredientKey(name) || ImportedIngredientReferences.sameFood(row.foodName, name)) && row.caloriesPer100g.isFinite() && row.caloriesPer100g >= 0f
            }
        return "$kcal kcal" + if (estimatedMass) " • ${ImportedIngredientReferences.massEstimateLabel(name, unit)}" else if (reference) " • szacunek z tabeli" else ""
    }

    private fun structuredAmount(name: String, quantity: Float, unit: String, structured: List<RecipeIngredientEntity>): Float? {
        fun valid(row: RecipeIngredientEntity) = row.quantity.isFinite() && row.quantity > 0f && row.grams.isFinite() && row.grams > 0f &&
            (row.unit.trim().equals(unit.trim(), ignoreCase = true) || ImportedIngredientReferences.sameCountUnit(row.unit, unit))
        val row = structured.firstOrNull { valid(it) && normalizeIngredientKey(it.foodName) == normalizeIngredientKey(name) }
            ?: structured.firstOrNull { valid(it) && ImportedIngredientReferences.sameFood(it.foodName, name) }
        return row?.let { it.grams * quantity / it.quantity }?.takeIf { it.isFinite() && it > 0f }
    }

    private fun knownQuantity(name: String, quantity: Float, unit: String, structured: List<RecipeIngredientEntity>): Float? {
        return when (unit.trim().lowercase()) {
            "g", "gram", "gramy", "gramow", "gramów", "ml" -> quantity
            "kg", "l" -> quantity * 1000f
            "mg" -> quantity / 1000f
            else -> structuredAmount(name, quantity, unit, structured)
                ?: ImportedIngredientReferences.estimatedGrams(name, quantity, unit)
        }
    }

    fun calculateMealNutritionWithSubstitutions(
        meal: MealPlanEntity,
        recipe: RecipeEntity?,
        allIngredientEntities: List<RecipeIngredientEntity>
    ): ScaledMealNutrition {
        val storedProduct = recipe?.let { QuickUseSupport.product(it, allIngredientEntities) }
        val baseNutrition = storedProduct?.nutritionForQuantity(100f * MealPlannerSupport.safeServings(meal.servings))
            ?: MealPlannerSupport.calculateScaledNutrition(meal, recipe)
        val structured = allIngredientEntities.filter { it.recipeId == recipe?.id }
        val baseIngredients = MealPlannerSupport.parseRecipeIngredients(recipe?.ingredients.orEmpty(), structured)
        val activeNames = baseIngredients.map { normalizeIngredientKey(it.name) }
        val substitutions = getSubstitutionsForMeal(meal.id, allIngredientEntities).filter { isExtra(it) || normalizeIngredientKey(it.originalIngredientName) in activeNames }
        // A newly completed product profile must also update unmodified meals.
        // Preserve imported totals only while no explicit saved profile applies.
        val usesRememberedProfile = baseIngredients.any { NutritionAttentionSupport.profile(it.name) != null }
        val needsIngredientTotals = baseIngredients.isNotEmpty() &&
            (baseNutrition.calories == null || baseNutrition.protein == null || baseNutrition.carbs == null || baseNutrition.fat == null)
        if (needsIngredientTotals && substitutions.isEmpty() && !usesRememberedProfile) {
            // Imported ingredient amounts can describe the whole recipe. Respect the saved yield.
            val portions = Regex("Porcje:\\s*(\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE).find(recipe?.description.orEmpty())?.groupValues?.get(1) ?: "1"
            val inferred = ImportedNutritionSupport.availableFromLocalIngredients(ImportedRecipe(recipe?.title.orEmpty(), portions,
                recipe?.ingredients.orEmpty(), recipe?.instructions.orEmpty(), 0, 0, 0, 0, "Baza lokalna"), structured)
            if (inferred != null) {
                val servings = MealPlannerSupport.safeServings(meal.servings)
                val kcal = baseNutrition.calories ?: inferred.calories?.let { (it * servings).roundToInt() }
                val protein = baseNutrition.protein ?: inferred.protein?.let { (it * servings).roundToInt() }
                val carbs = baseNutrition.carbs ?: inferred.carbs?.let { (it * servings).roundToInt() }
                val fat = baseNutrition.fat ?: inferred.fat?.let { (it * servings).roundToInt() }
                return ScaledMealNutrition(kcal, protein, carbs, fat,
                    "Dostępne sumy uzupełniono z rozpoznanych składników i zapisanej liczby porcji. Brakujące makro nie jest zerem. Wartości z tabel i masy sztuk są szacunkowe.",
                    isPartial = listOf(kcal, protein, carbs, fat).any { it == null })
            }
            return baseNutrition.copy(note = "Niepełne dane: nie wszystkie ilości lub wartości składników są znane. Zachowano dostępne sumy źródłowe.", isPartial = true)
        }
        if (substitutions.isEmpty() && baseIngredients.none { isIgnoredSeasoning(it.name) } && !usesRememberedProfile && !needsIngredientTotals) {
            return baseNutrition
        }
        val currentItems = resolveMealIngredientsWithSubstitutions(recipe, meal, allIngredientEntities)

        // An edited meal is the food currently shown in its ingredient list, not the
        // imported recipe total plus deltas against potentially missing old products.
        val amountsAndProfiles = currentItems.filterNot { isIgnoredSeasoning(it.ingredientName) }.map { item ->
            val amount = item.quantityForMeal?.let { knownQuantity(item.ingredientName, it, item.unit, structured) }
                ?.takeIf { it.isFinite() && it >= 0f }
            val profile = item.substitution?.let { sub ->
                sub.caloriesPer100g?.let { KnownMacroProfile(it, sub.proteinPer100g, sub.carbsPer100g, sub.fatPer100g) }
            } ?: if (item.substitution == null) lookupKnownIngredientNutrition(item.ingredientName, structured) else null
            amount to profile
        }
        fun sumCurrent(select: (KnownMacroProfile) -> Float?): Int? {
            var total = 0f
            for ((amount, profile) in amountsAndProfiles) {
                val nutrient = profile?.let(select)?.takeIf { it.isFinite() && it >= 0f } ?: return null
                if (amount == null) return null
                total += amount * nutrient / 100f
            }
            return total.takeIf { it.isFinite() && it < Int.MAX_VALUE.toFloat() }?.roundToInt()
        }
        val currentCalories = sumCurrent { it.caloriesPer100g }
        val currentProtein = sumCurrent { it.proteinPer100g }
        val currentCarbs = sumCurrent { it.carbsPer100g }
        val currentFat = sumCurrent { it.fatPer100g }
        if (currentCalories != null && currentProtein != null && currentCarbs != null && currentFat != null) {
            return ScaledMealNutrition(currentCalories, currentProtein, currentCarbs, currentFat,
                "Suma aktualnych składników. Wartości z tabel lub AI są szacunkowe; drobne różnice mogą wynikać z zaokrągleń.")
        }

        // Partial sums are current lower bounds, never the old imported meal total.
        fun sumKnown(select: (KnownMacroProfile) -> Float?): Int? {
            val values = amountsAndProfiles.mapNotNull { (amount, profile) ->
                val nutrient = profile?.let(select)?.takeIf { it.isFinite() && it >= 0f }
                if (amount == null || nutrient == null) null else amount * nutrient / 100f
            }
            return if (values.isEmpty()) null else values.sum().takeIf { it.isFinite() && it < Int.MAX_VALUE.toFloat() }?.roundToInt()
        }
        return ScaledMealNutrition(
            calories = currentCalories ?: sumKnown { it.caloriesPer100g },
            protein = currentProtein ?: sumKnown { it.proteinPer100g },
            carbs = currentCarbs ?: sumKnown { it.carbsPer100g },
            fat = currentFat ?: sumKnown { it.fatPer100g },
            note = "Niepełne dane: suma tylko aktualnych składników ze znanymi wartościami. Brakujące wartości nie są liczone. Uzupełnij dane z etykiety, aby uzyskać pełną sumę.",
            isPartial = true
        )
    }

    fun totalMealNutrition(meals: List<MealPlanEntity>, recipes: List<RecipeEntity>, ingredients: List<RecipeIngredientEntity>): ScaledMealNutrition {
        val values = meals.map { meal -> calculateMealNutritionWithSubstitutions(meal,
            recipes.find { it.id == meal.recipeId } ?: recipes.find { it.title.equals(meal.recipeTitle, ignoreCase = true) }, ingredients) }
        return ScaledMealNutrition(values.sumOf { it.calories ?: 0 }, values.sumOf { it.protein ?: 0 },
            values.sumOf { it.carbs ?: 0 }, values.sumOf { it.fat ?: 0 },
            isPartial = values.any { it.isPartial || it.calories == null || it.protein == null || it.carbs == null || it.fat == null })
    }

    fun replaceIngredientInRecipeEntity(
        recipe: RecipeEntity,
        originalIngredientRaw: String,
        candidate: SubstituteCandidate,
        enteredQuantity: Float,
        enteredUnit: String,
        recipeStructuredIngredients: List<RecipeIngredientEntity> = emptyList()
    ): RecipeEntity {
        val parsedOrig = MealPlannerSupport.parseSingleIngredientLine(originalIngredientRaw)
        val origName = parsedOrig.name.ifBlank { originalIngredientRaw.trim() }
        val origQty = parsedOrig.baseQuantity ?: enteredQuantity
        val normUnit = normalizeMassOrVolumeUnit(enteredUnit)
        val safeQty = enteredQuantity.coerceAtLeast(0.01f)
        val qtyStr = MealPlannerSupport.formatQuantityNumber(safeQty)
        val newIngredientFormatted = "${candidate.name.trim()} — $qtyStr $normUnit"

        val currentParsed = MealPlannerSupport.parseRecipeIngredients(recipe.ingredients, recipeStructuredIngredients)
        val exactTarget = currentParsed.any { normalizeIngredientKey(it.name) == normalizeIngredientKey(origName) }
        var replacedAny = false
        val updatedLines = currentParsed.map { item ->
            if (!replacedAny && (if (exactTarget) normalizeIngredientKey(item.name) == normalizeIngredientKey(origName) else ingredientNamesMatch(item.name, origName))) {
                replacedAny = true
                newIngredientFormatted
            } else {
                item.formatForServings(1.0f)
            }
        }.toMutableList()

        if (!replacedAny) {
            updatedLines.add(newIngredientFormatted)
        }

        val origProfile = lookupKnownIngredientNutrition(origName, recipeStructuredIngredients)
        val originalAmount = knownQuantity(origName, origQty, parsedOrig.unit, recipeStructuredIngredients)
        val origRatio = (originalAmount ?: 0f).coerceAtLeast(0f) / 100f
        val repRatio = safeQty / 100f

        val updatedCalories = if (candidate.caloriesPer100g != null && origProfile != null && originalAmount != null && recipe.calories > 0) {
            val origKcal = (origProfile?.caloriesPer100g ?: 0f) * origRatio
            val repKcal = candidate.caloriesPer100g * repRatio
            (recipe.calories - origKcal + repKcal).takeIf { it >= 0f }?.roundToInt() ?: recipe.calories
        } else {
            recipe.calories
        }

        val updatedProtein = if (candidate.proteinPer100g != null && origProfile?.proteinPer100g != null && originalAmount != null && (recipe.protein > 0 || recipe.carbs > 0 || recipe.fat > 0)) {
            val origP = (origProfile?.proteinPer100g ?: 0f) * origRatio
            val repP = candidate.proteinPer100g * repRatio
            (recipe.protein - origP + repP).takeIf { it >= 0f }?.roundToInt() ?: recipe.protein
        } else {
            recipe.protein
        }

        val updatedCarbs = if (candidate.carbsPer100g != null && origProfile?.carbsPer100g != null && originalAmount != null && (recipe.protein > 0 || recipe.carbs > 0 || recipe.fat > 0)) {
            val origC = (origProfile?.carbsPer100g ?: 0f) * origRatio
            val repC = candidate.carbsPer100g * repRatio
            (recipe.carbs - origC + repC).takeIf { it >= 0f }?.roundToInt() ?: recipe.carbs
        } else {
            recipe.carbs
        }

        val updatedFat = if (candidate.fatPer100g != null && origProfile?.fatPer100g != null && originalAmount != null && (recipe.protein > 0 || recipe.carbs > 0 || recipe.fat > 0)) {
            val origF = (origProfile?.fatPer100g ?: 0f) * origRatio
            val repF = candidate.fatPer100g * repRatio
            (recipe.fat - origF + repF).takeIf { it >= 0f }?.roundToInt() ?: recipe.fat
        } else {
            recipe.fat
        }

        return recipe.copy(
            ingredients = updatedLines.joinToString("\n"),
            calories = updatedCalories,
            protein = updatedProtein,
            carbs = updatedCarbs,
            fat = updatedFat
        )
    }

    /** Keep the imported total as baseline and apply only supported changes, not a partial sum. */
    fun recalculateEditedIngredients(recipe: RecipeEntity, text: String, structured: List<RecipeIngredientEntity>): RecipeEntity {
        val before = MealPlannerSupport.parseRecipeIngredients(recipe.ingredients, structured)
        val after = MealPlannerSupport.parseRecipeIngredients(text)
        var result = recipe
        val remaining = after.toMutableList()
        val portions = Regex("Porcje:\\s*(\\d+(?:[.,]\\d+)?)").find(recipe.description)?.groupValues?.get(1)
            ?.replace(',', '.')?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 1f
        for (old in before) {
            val exact = remaining.indexOfFirst { normalizeIngredientKey(it.name) == normalizeIngredientKey(old.name) && it.baseQuantity == old.baseQuantity && it.unit == old.unit }
            val index = if (exact >= 0) exact else remaining.indexOfFirst { normalizeIngredientKey(it.name) == normalizeIngredientKey(old.name) }
            val changed = if (index >= 0) remaining.removeAt(index) else old.copy(baseQuantity = 0f)
            if (old.baseQuantity == changed.baseQuantity && old.unit == changed.unit) continue
            if (old.baseQuantity == null || changed.baseQuantity == null) continue
            val oldAmount = knownQuantity(old.name, old.baseQuantity, old.unit, structured) ?: continue
            val newAmount = if (changed.baseQuantity == 0f) 0f else knownQuantity(changed.name, changed.baseQuantity, changed.unit, structured) ?: continue
            val profile = lookupKnownIngredientNutrition(old.name, structured) ?: continue
            val difference = (newAmount - oldAmount) / 100f / portions
            fun adjust(value: Int, per100: Float?) = if (value > 0 && per100 != null) (value + per100 * difference).takeIf { it >= 0f }?.roundToInt() ?: value else value
            result = result.copy(calories = adjust(result.calories, profile.caloriesPer100g),
                protein = adjust(result.protein, profile.proteinPer100g), carbs = adjust(result.carbs, profile.carbsPer100g), fat = adjust(result.fat, profile.fatPer100g))
        }
        return result.copy(ingredients = text)
    }

    fun addIngredientNutrition(recipe: RecipeEntity, candidate: SubstituteCandidate, quantity: Float): RecipeEntity {
        val extra = candidate.nutritionForQuantity(quantity)
        return recipe.copy(calories = recipe.calories + (extra.calories ?: 0),
            protein = recipe.protein + (extra.protein ?: 0), carbs = recipe.carbs + (extra.carbs ?: 0), fat = recipe.fat + (extra.fat ?: 0))
    }

    fun parseAiSubstituteSuggestions(
        rawResponse: String,
        defaultQuantity: Float,
        defaultUnit: String
    ): List<SubstituteCandidate> {
        val cleaned = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val candidates = mutableListOf<SubstituteCandidate>()
        val normalizedDefaultUnit = normalizeMassOrVolumeUnit(defaultUnit)

        try {
            val array = when {
                cleaned.startsWith("[") -> JSONArray(cleaned)
                cleaned.startsWith("{") -> {
                    val obj = JSONObject(cleaned)
                    obj.optJSONArray("substitutes")
                        ?: obj.optJSONArray("alternatives")
                        ?: obj.optJSONArray("items")
                        ?: JSONArray()
                }
                cleaned.contains("[") && cleaned.contains("]") -> {
                    val sub = cleaned.substring(cleaned.indexOf('['), cleaned.lastIndexOf(']') + 1)
                    JSONArray(sub)
                }
                else -> JSONArray()
            }

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                    .ifBlank { obj.optString("title").trim() }
                if (name.isBlank()) continue

                val reason = obj.optString("reason").trim()
                    .ifBlank { obj.optString("description").trim() }
                val unit = normalizeMassOrVolumeUnit(obj.optString("unit", normalizedDefaultUnit))
                val suggestedQty = obj.optDouble("suggestedQuantity", Double.NaN)
                    .takeIf { !it.isNaN() && it > 0.0 }?.toFloat() ?: defaultQuantity

                val kcal = obj.optDouble("caloriesPer100g", Double.NaN)
                    .takeIf { !it.isNaN() && it >= 0.0 }?.toFloat()
                val protein = obj.optDouble("proteinPer100g", Double.NaN)
                    .takeIf { !it.isNaN() && it >= 0.0 }?.toFloat()
                val carbs = obj.optDouble("carbsPer100g", Double.NaN)
                    .takeIf { !it.isNaN() && it >= 0.0 }?.toFloat()
                val fat = obj.optDouble("fatPer100g", Double.NaN)
                    .takeIf { !it.isNaN() && it >= 0.0 }?.toFloat()

                candidates.add(
                    SubstituteCandidate(
                        name = name,
                        brand = "Propozycja AI Dietetyka",
                        caloriesPer100g = kcal,
                        proteinPer100g = protein,
                        carbsPer100g = carbs,
                        fatPer100g = fat,
                        defaultQuantity = suggestedQty,
                        defaultUnit = unit,
                        aiReason = reason,
                        source = SubstituteOptionMode.AI_SUGGESTION
                    )
                )
            }
        } catch (_: Exception) {
            // Do not fabricate if JSON parsing fails
        }

        return candidates.take(5)
    }
}
