package com.example.viewmodel

/** Preview-only grocery consolidation. No database writes and no invented quantities. */
data class SmartShoppingIngredient(val name: String, val quantity: Double?, val unit: String)
data class SmartShoppingEntry(val name: String, val quantity: Double?, val unit: String, val needsReview: Boolean)

object SmartShoppingSupport {
    fun consolidate(ingredients: List<SmartShoppingIngredient>): List<SmartShoppingEntry> {
        return ingredients.filter { it.name.isNotBlank() }.groupBy {
            it.name.trim().lowercase(java.util.Locale.ROOT) to it.unit.trim().lowercase(java.util.Locale.ROOT)
        }.map { (key, rows) ->
            val valid = rows.all { it.quantity != null && it.quantity.isFinite() && it.quantity >= 0.0 }
            SmartShoppingEntry(
                name = rows.first().name.trim(),
                quantity = if (valid) rows.sumOf { it.quantity!! } else null,
                unit = key.second,
                needsReview = !valid || key.second.isBlank()
            )
        }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }
}
