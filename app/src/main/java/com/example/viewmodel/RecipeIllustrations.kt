package com.example.viewmodel

object RecipeIllustrations {
    fun isIllustration(imageUrl: String): Boolean = imageUrl.contains("/recipe_ai_generated/")
}
