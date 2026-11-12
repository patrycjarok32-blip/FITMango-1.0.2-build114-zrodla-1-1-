package com.example.viewmodel

import com.example.data.ShoppingItemEntity
import java.util.Locale
import kotlin.math.round

data class ShoppingTotal(val name: String, val category: String, val amount: Double,
    val unit: String, val sourceCount: Int) {
    fun label(): String {
        val display = if (amount == round(amount)) amount.toLong().toString()
            else String.format(Locale.ROOT, "%.1f", amount)
        return "$name — $display $unit"
    }
}

/** Display-only totals; never merge or overwrite original shopping rows. */
object MangoShoppingTotals {
    private val amountPattern = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(kg|g|l|ml|szt\.?|pcs|unit|units)\s*$""",
        RegexOption.IGNORE_CASE)
    fun totals(items: List<ShoppingItemEntity>): List<ShoppingTotal> {
        data class Row(val name: String, val category: String, val value: Double,
            val unit: String)
        val parsed = items.filter { !it.isChecked }.mapNotNull { item ->
            val match = amountPattern.matchEntire(item.amount) ?: return@mapNotNull null
            val quantity = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0 && it <= 100000 } ?: return@mapNotNull null
            val rawUnit = match.groupValues[2].lowercase(Locale.ROOT).removeSuffix(".")
            val (value, unit) = when (rawUnit) {
                "kg" -> quantity * 1000 to "g"
                "g" -> quantity to "g"
                "l" -> quantity * 1000 to "ml"
                "ml" -> quantity to "ml"
                "szt", "pcs", "unit", "units" -> quantity to "szt"
                else -> return@mapNotNull null
            }
            Row(item.itemName.trim(), item.category, value, unit)
        }.filter { it.name.isNotBlank() }
        return parsed.groupBy {
            Triple(it.name.lowercase(Locale.ROOT), it.category.lowercase(Locale.ROOT), it.unit)
        }.values.map { group ->
            ShoppingTotal(group.first().name, group.first().category,
                group.sumOf { it.value }, group.first().unit, group.size)
        }.sortedWith(compareBy({ it.category }, { it.name }))
    }
}
