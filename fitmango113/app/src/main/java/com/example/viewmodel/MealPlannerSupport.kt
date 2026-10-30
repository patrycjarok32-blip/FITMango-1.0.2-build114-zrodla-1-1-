package com.example.viewmodel

import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.data.RecipeIngredientEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeekDaySpec(
    val canonicalName: String,
    val englishName: String,
    val shortLetter: String,
    val dayNumber: Int,
    val dateLabel: String
) {
    val displayWithDate: String
        get() = "$canonicalName, $dateLabel"

    val chipLabelWithDate: String
        get() = "$canonicalName ($dateLabel)"
}

data class MealCategorySpec(
    val canonicalName: String,
    val englishName: String,
    val timeRange: String
)

data class PlannedMealIngredient(
    val name: String,
    val baseQuantity: Float?,
    val unit: String
) {
    fun scaledQuantity(servings: Float): Float? {
        val base = baseQuantity ?: return null
        return base * MealPlannerSupport.safeServings(servings)
    }

    fun formattedQuantityAndUnit(servings: Float): String {
        val scaled = scaledQuantity(servings)
        val qtyStr = scaled?.let { MealPlannerSupport.formatQuantityNumber(it) }.orEmpty()
        val cleanUnit = unit.trim()
        return when {
            qtyStr.isNotEmpty() && cleanUnit.isNotEmpty() -> "$qtyStr $cleanUnit"
            qtyStr.isNotEmpty() -> qtyStr
            cleanUnit.isNotEmpty() -> cleanUnit
            else -> ""
        }
    }

    fun formatForServings(servings: Float): String {
        val qtyAndUnit = formattedQuantityAndUnit(servings)
        return if (qtyAndUnit.isNotEmpty()) {
            "$name — $qtyAndUnit"
        } else {
            name
        }
    }

    fun formatBulletForServings(servings: Float): String {
        return "- ${formatForServings(servings)}"
    }
}

data class ScaledMealNutrition(
    val calories: Int?,
    val protein: Int?,
    val carbs: Int?,
    val fat: Int?,
    val note: String? = null,
    val isPartial: Boolean = false
) {
    val hasMacros: Boolean
        get() = protein != null || carbs != null || fat != null
}

object MealPlannerSupport {

    private val WEEK_DAY_NAMES: List<WeekDaySpec> = listOf(
        WeekDaySpec("Poniedziałek", "Monday", "P", 9, "09.09"),
        WeekDaySpec("Wtorek", "Tuesday", "W", 10, "10.09"),
        WeekDaySpec("Środa", "Wednesday", "Ś", 11, "11.09"),
        WeekDaySpec("Czwartek", "Thursday", "C", 12, "12.09"),
        WeekDaySpec("Piątek", "Friday", "P", 13, "13.09"),
        WeekDaySpec("Sobota", "Saturday", "S", 14, "14.09"),
        WeekDaySpec("Niedziela", "Sunday", "N", 15, "15.09")
    )

    val WEEK_DAYS: List<WeekDaySpec>
        get() = weekDays(java.time.LocalDate.now())

    fun weekDays(today: java.time.LocalDate): List<WeekDaySpec> {
            val monday = today.with(
                java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM")
            return WEEK_DAY_NAMES.mapIndexed { index, day ->
                val date = monday.plusDays(index.toLong())
                day.copy(dayNumber = date.dayOfMonth, dateLabel = date.format(formatter))
            }
        }

    fun dateForDay(day: String, today: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate? {
        parsePlanDate(day)?.let { return it }
        val index = WEEK_DAY_NAMES.indexOfFirst { normalizeDayOfWeek(day) == it.canonicalName }
        if (index < 0) return null
        return today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).plusDays(index.toLong())
    }

    fun completedInCurrentWeek(meal: MealPlanEntity, today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
        meal.isCompleted && meal.completedDate != null && meal.completedDate == dateForDay(meal.dayOfWeek, today)?.toString()

    fun consumedCaloriesForDate(meals: List<MealPlanEntity>, today: java.time.LocalDate): Int = meals.filter {
        it.isCompleted && it.completedDate == today.toString() && dateForDay(it.dayOfWeek, today) == today
    }.sumOf { it.calories }

    val MEAL_CATEGORIES: List<MealCategorySpec> = listOf(
        MealCategorySpec("Śniadanie", "Breakfast", "08:00 - 09:30"),
        MealCategorySpec("II śniadanie", "Second breakfast", "11:00 - 12:00"),
        MealCategorySpec("Obiad", "Lunch", "14:00 - 15:30"),
        MealCategorySpec("Przekąska", "Snack", "17:00 - 17:30"),
        MealCategorySpec("Kolacja", "Dinner", "19:30 - 20:30")
    )

    private val KNOWN_UNITS = setOf(
        "g", "gr", "gram", "gramy", "gramów", "gramow", "dag", "kg", "mg",
        "ml", "l", "litr", "litry", "litrów", "litrow",
        "szt", "szt.", "sztuk", "sztuki", "sztuka",
        "łyżka", "łyżki", "łyżek", "lyzka", "lyzki", "lyzek",
        "łyżeczka", "łyżeczki", "łyżeczek", "lyzeczka", "lyzeczki", "lyzeczek",
        "szklanka", "szklanki", "szklanek",
        "szczypta", "szczypty",
        "garść", "garście", "garsc", "garscie",
        "opakowanie", "opakowania", "paczka", "puszka", "puszki",
        "plaster", "plastry", "plasterek", "plasterki",
        "kromka", "kromki", "kromek",
        "ząbek", "ząbki", "ząbków", "zabek", "zabki", "zabkow",
        "porcja", "porcje", "porcji",
        "liść", "liście", "lisc", "liscie",
        "gałązka", "gałązki", "galazka", "galazki",
        "pęczek", "pęczki", "peczek", "peczki",
        "slice", "slices", "cup", "cups", "tbsp", "tsp", "oz", "lb",
        "bunch", "clove", "cloves", "piece", "pieces", "unit", "units"
    )

    fun normalizeDayOfWeek(rawDay: String): String {
        val cleaned = rawDay.trim()
        val lower = cleaned.lowercase(Locale.forLanguageTag("pl-PL"))
        if (lower in listOf("today", "dzisiaj", "dziś", "dzis")) return WEEK_DAY_NAMES[java.time.LocalDate.now().dayOfWeek.value - 1].canonicalName
        return when {
            lower.startsWith("poniedzia") || lower.startsWith("monday") || lower == "pon" -> "Poniedziałek"
            lower.startsWith("wtorek") || lower.startsWith("tuesday") || lower == "wt" -> "Wtorek"
            lower.startsWith("środ") || lower.startsWith("srod") || lower.startsWith("wednesday") || lower == "śr" || lower == "sr" -> "Środa"
            lower.startsWith("czwartek") || lower.startsWith("thursday") || lower == "czw" -> "Czwartek"
            lower.startsWith("piąt") || lower.startsWith("piat") || lower.startsWith("friday") || lower == "pt" -> "Piątek"
            lower.startsWith("sobot") || lower.startsWith("saturday") || lower == "sob" -> "Sobota"
            lower.startsWith("niedziel") || lower.startsWith("sunday") || lower == "nd" -> "Niedziela"
            else -> WEEK_DAYS.find { cleaned.contains(it.canonicalName, ignoreCase = true) || cleaned.contains(it.englishName, ignoreCase = true) }?.canonicalName
                ?: cleaned.ifBlank { "Poniedziałek" }
        }
    }

    fun normalizeMealType(rawMealType: String): String {
        val cleaned = rawMealType.trim()
        val lower = cleaned.lowercase(Locale.forLanguageTag("pl-PL"))
        return when {
            lower.contains("ii śniad") || lower.contains("ii sniad") || lower.contains("drugie śniad") ||
                lower.contains("drugie sniad") || lower.contains("second breakfast") -> "II śniadanie"
            lower.startsWith("śniad") || lower.startsWith("sniad") || lower.equals("breakfast", ignoreCase = true) -> "Śniadanie"
            lower.startsWith("obiad") || lower.equals("lunch", ignoreCase = true) -> "Obiad"
            lower.startsWith("przekąs") || lower.startsWith("przekas") || lower.startsWith("podwieczor") ||
                lower.equals("snack", ignoreCase = true) -> "Przekąska"
            lower.startsWith("kolac") || lower.equals("dinner", ignoreCase = true) || lower.equals("supper", ignoreCase = true) -> "Kolacja"
            else -> cleaned.ifBlank { "Obiad" }
        }
    }

    fun parsePlanDate(value: String): java.time.LocalDate? =
        runCatching { java.time.LocalDate.parse(value.trim()) }.getOrNull()

    fun isPlanDay(value: String): Boolean = value.isNotBlank() && (parsePlanDate(value) != null ||
        WEEK_DAY_NAMES.any { it.canonicalName == normalizeDayOfWeek(value) })

    fun planDateLabel(value: String): String {
        val date = dateForDay(value) ?: return value
        return "${WEEK_DAY_NAMES[date.dayOfWeek.value - 1].canonicalName}, ${date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"
    }

    fun matchesDay(mealDay: String, selectedDay: String): Boolean {
        val storedDate = parsePlanDate(mealDay)
        if (storedDate != null) return storedDate == dateForDay(selectedDay)
        // Undated legacy rows must never follow the calendar into a new week.
        if (parsePlanDate(selectedDay) != null) return false
        return normalizeDayOfWeek(mealDay).equals(normalizeDayOfWeek(selectedDay), ignoreCase = true)
    }

    fun matchesMealCategory(mealType: String, categoryName: String): Boolean {
        return normalizeMealType(mealType).equals(normalizeMealType(categoryName), ignoreCase = true)
    }

    fun formatQuantityNumber(value: Float): String {
        val roundedInt = value.roundToInt()
        if (abs(value - roundedInt) < 0.01f) {
            return roundedInt.toString()
        }
        val formatted = String.format(Locale.US, "%.1f", value)
        return if (formatted.endsWith(".0")) formatted.dropLast(2) else formatted
    }

    fun safeServings(value: Float): Float = if (value.isFinite() && value > 0f) value else 0.25f

    fun servingsFromGrams(eatenGrams: Float, portionGrams: Float): Float {
        require(eatenGrams.isFinite() && portionGrams.isFinite() && eatenGrams > 0f && portionGrams > 0f) {
            "Podaj dodatnią wagę porcji i zjedzoną ilość."
        }
        val servings = eatenGrams / portionGrams
        require(servings.isFinite() && servings > 0f && servings <= 10000f) { "Sprawdź wpisane gramatury." }
        return servings
    }

    fun formatServingsLabel(servings: Float): String {
        val safe = MealPlannerSupport.safeServings(servings)
        val roundedInt = safe.roundToInt()
        if (abs(safe - roundedInt) >= 0.01f) {
            return "${String.format(Locale.US, "%.3f", safe).trimEnd('0').trimEnd('.')} porcji"
        }
        return when {
            roundedInt == 1 -> "1 porcja"
            roundedInt % 10 in 2..4 && (roundedInt % 100 !in 12..14) -> "$roundedInt porcje"
            else -> "$roundedInt porcji"
        }
    }

    fun formatMealHeader(recipeTitle: String, servings: Float): String {
        return "${recipeTitle.trim()} — ${formatServingsLabel(servings)}"
    }

    fun calculateScaledNutrition(meal: MealPlanEntity, recipe: RecipeEntity?): ScaledMealNutrition {
        val servings = MealPlannerSupport.safeServings(meal.servings)
        if (recipe != null) {
            val hasRecipeCalories = recipe.calories > 0
            val hasRecipeMacros = recipe.protein > 0 || recipe.carbs > 0 || recipe.fat > 0

            val scaledCalories = when {
                hasRecipeCalories -> (recipe.calories * servings).roundToInt()
                meal.calories > 0 -> meal.calories
                else -> null
            }
            val scaledProtein = if (hasRecipeMacros) (recipe.protein * servings).roundToInt() else null
            val scaledCarbs = if (hasRecipeMacros) (recipe.carbs * servings).roundToInt() else null
            val scaledFat = if (hasRecipeMacros) (recipe.fat * servings).roundToInt() else null

            return ScaledMealNutrition(
                calories = scaledCalories,
                protein = scaledProtein,
                carbs = scaledCarbs,
                fat = scaledFat
            )
        }

        return ScaledMealNutrition(
            calories = meal.calories.takeIf { it > 0 },
            protein = null,
            carbs = null,
            fat = null
        )
    }

    fun parseRecipeIngredients(
        ingredientsText: String,
        fallbackEntities: List<RecipeIngredientEntity> = emptyList()
    ): List<PlannedMealIngredient> {
        val trimmed = ingredientsText.trim()
        if (trimmed.isEmpty()) {
            return fallbackEntities.map { entity ->
                PlannedMealIngredient(
                    name = cleanIngredientName(entity.foodName),
                    baseQuantity = entity.quantity.takeIf { it > 0f },
                    unit = entity.unit.trim()
                )
            }
        }

        val rawLines = if (trimmed.contains('\n')) {
            trimmed.lines().flatMap { line ->
                if (line.contains(';')) line.split(';') else listOf(line)
            }
        } else {
            trimmed.split(Regex("[,;]"))
        }

        return rawLines
            .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
            .filter { it.isNotBlank() }
            .map { parseSingleIngredientLine(it) }
    }

    fun parseSingleIngredientLine(rawLine: String): PlannedMealIngredient {
        val line = rawLine.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim()
        if (line.isBlank()) {
            return PlannedMealIngredient(name = "", baseQuantity = null, unit = "")
        }

        // 1. Pattern: "Name — 150 g" or "Name - 150 g" or "Name: 150 g"
        val dashNumberRegex = Regex("""^(.+?)\s*(?:[—–:]|\s-\s)\s*(\d+(?:[.,/]\d+)?)\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ.]*)$""")
        dashNumberRegex.find(line)?.let { match ->
            val rawName = match.groupValues[1].trim()
            val qty = parseQuantityToken(match.groupValues[2])
            val unit = match.groupValues[3].trim()
            if (rawName.isNotBlank() && qty != null) {
                return PlannedMealIngredient(
                    name = cleanIngredientName(rawName),
                    baseQuantity = qty,
                    unit = unit
                )
            }
        }

        // 2. Pattern: Leading quantity, e.g. "150 g Pierś z kurczaka", "200g chicken breast", "2 eggs"
        val leadingQtyRegex = Regex("""^(\d+(?:[.,/]\d+)?)\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ.]+)?\s+(.+)$""")
        leadingQtyRegex.find(line)?.let { match ->
            val qty = parseQuantityToken(match.groupValues[1])
            val potentialUnit = match.groupValues[2].trim()
            val rest = match.groupValues[3].trim()
            if (qty != null) {
                val lowerUnit = potentialUnit.lowercase(Locale.forLanguageTag("pl-PL"))
                if (potentialUnit.isNotEmpty() && (lowerUnit in KNOWN_UNITS || lowerUnit.removeSuffix(".") in KNOWN_UNITS)) {
                    return PlannedMealIngredient(
                        name = cleanIngredientName(rest),
                        baseQuantity = qty,
                        unit = potentialUnit
                    )
                } else {
                    val combinedName = if (potentialUnit.isNotEmpty()) "$potentialUnit $rest" else rest
                    return PlannedMealIngredient(
                        name = cleanIngredientName(combinedName),
                        baseQuantity = qty,
                        unit = "szt."
                    )
                }
            }
        }

        // 3. Pattern: "Name (150 g)" or "Name (szczypta)"
        val parenRegex = Regex("""^(.+?)\s*\(\s*(\d+(?:[.,/]\d+)?)?\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ.\s]+?)\s*\)$""")
        parenRegex.find(line)?.let { match ->
            val rawName = match.groupValues[1].trim()
            val qtyToken = match.groupValues[2].trim()
            val unitToken = match.groupValues[3].trim()
            val qty = parseQuantityToken(qtyToken)
            if (rawName.isNotBlank() && (qty != null || unitToken.lowercase(Locale.forLanguageTag("pl-PL")) in KNOWN_UNITS)) {
                return PlannedMealIngredient(
                    name = cleanIngredientName(rawName),
                    baseQuantity = qty,
                    unit = unitToken
                )
            }
        }

        // 4. Pattern: Trailing number + unit, e.g. "Pierś z kurczaka 150 g"
        val trailingQtyRegex = Regex("""^(.+?)\s+(\d+(?:[.,/]\d+)?)\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ.]+)$""")
        trailingQtyRegex.find(line)?.let { match ->
            val rawName = match.groupValues[1].trim()
            val qty = parseQuantityToken(match.groupValues[2])
            val unit = match.groupValues[3].trim()
            val lowerUnit = unit.lowercase(Locale.forLanguageTag("pl-PL"))
            if (rawName.isNotBlank() && qty != null && (lowerUnit in KNOWN_UNITS || lowerUnit.removeSuffix(".") in KNOWN_UNITS)) {
                return PlannedMealIngredient(
                    name = cleanIngredientName(rawName),
                    baseQuantity = qty,
                    unit = unit
                )
            }
        }

        return PlannedMealIngredient(
            name = cleanIngredientName(line),
            baseQuantity = null,
            unit = ""
        )
    }

    private fun parseQuantityToken(token: String): Float? {
        val cleaned = token.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        if (cleaned.contains('/')) {
            val parts = cleaned.split('/')
            if (parts.size == 2) {
                val num = parts[0].trim().toFloatOrNull()
                val den = parts[1].trim().toFloatOrNull()
                if (num != null && den != null && den != 0f) {
                    return num / den
                }
            }
        }
        return cleaned.toFloatOrNull()
    }

    private fun cleanIngredientName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.forLanguageTag("pl-PL")) else ch.toString()
        }
    }
}
