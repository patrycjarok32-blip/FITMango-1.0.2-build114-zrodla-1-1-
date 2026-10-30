package com.example.viewmodel

object RecipeCategories {
    val options = listOf("Śniadanie", "Obiad", "Kolacja", "Przekąska", "Inne")
    fun normalized(value: String): String = java.text.Normalizer.normalize(
        value.lowercase(java.util.Locale.ROOT).replace('ł', 'l'), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()
    private fun single(value: String): String = when (normalized(value).trim('"', '[', ']')) {
        "breakfast", "breakfasts", "sniadanie", "sniadania", "ii sniadanie", "drugie sniadanie", "brunch" -> "Śniadanie"
        "lunch", "lunches", "obiad", "obiady", "main course", "main dish" -> "Obiad"
        "dinner", "dinners", "supper", "kolacja", "kolacje" -> "Kolacja"
        "snack", "snacks", "dessert", "desserts", "przekaska", "przekaski", "deser", "desery" -> "Przekąska"
        else -> "Inne"
    }
    fun categories(value: String): Set<String> = value.split(Regex("[/,;|]")).map(::single).toSet()
    fun canonical(value: String): String = categories(value).firstOrNull { it != "Inne" } ?: "Inne"
    fun matches(value: String, selection: String): Boolean = selection == "Wszystkie" || canonical(selection) in categories(value)
    fun label(category: String) = when (category) {
        "Śniadanie" -> "Śniadania"
        "Obiad" -> "Obiady"
        "Kolacja" -> "Kolacje"
        "Przekąska" -> "Przekąski"
        else -> category
    }
}
