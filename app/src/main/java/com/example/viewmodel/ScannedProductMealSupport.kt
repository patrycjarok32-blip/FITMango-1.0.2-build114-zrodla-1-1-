package com.example.viewmodel

import com.example.data.RecipeEntity
import com.example.data.RecipeIngredientEntity

object ScannedProductMealSupport {
    fun recipe(product: SubstituteCandidate, quantity: Float, unit: String): RecipeEntity {
        require(product.name.isNotBlank() && quantity.isFinite() && quantity > 0 && quantity <= 10000)
        require(unit in listOf("g", "ml"))
        require(product.caloriesPer100g?.let { it.isFinite() && it >= 0 } == true) { "Produkt nie ma kalorii. Uzupełnij dane produktu przed dodaniem." }
        val label = product.source == SubstituteOptionMode.LOCAL_DATABASE && product.barcode.isBlank()
        val origin = if (label) "Produkt z etykiety (wartości sprawdzone przez użytkownika)" else "Produkt z Open Food Facts: ${product.barcode}"
        val nutrition = product.nutritionForQuantity(quantity)
        val incomplete = listOf(product.proteinPer100g, product.carbsPer100g, product.fatPer100g).any { it == null }
        return RecipeEntity(title = product.name.trim(), description = "Porcje: 1\n$origin. Ilość: $quantity $unit." +
            if (incomplete) "\nNiepełne makro w bazie produktu — zera dla brakujących danych nie są potwierdzonymi wartościami." else "",
            ingredients = "${product.name.trim()} — ${MealPlannerSupport.formatQuantityNumber(quantity)} $unit",
            instructions = if (label) "Produkt gotowy do spożycia. Wartości z etykiety." else "Produkt gotowy do spożycia. Kod: ${product.barcode}.", calories = nutrition.calories ?: 0,
            protein = nutrition.protein ?: 0, carbs = nutrition.carbs ?: 0, fat = nutrition.fat ?: 0,
            category = "Przekąska", source = if (label) "Etykieta produktu" else "https://world.openfoodfacts.org/product/${product.barcode}", imageUrl = product.imageUrl)
    }

    fun ingredient(product: SubstituteCandidate, quantity: Float, unit: String) = RecipeIngredientEntity(
        recipeId = 0, foodName = product.name.trim(), quantity = quantity, grams = quantity, unit = unit,
        caloriesPer100g = product.caloriesPer100g ?: -1f, proteinPer100g = product.proteinPer100g ?: -1f,
        carbsPer100g = product.carbsPer100g ?: -1f, fatPer100g = product.fatPer100g ?: -1f)
}
