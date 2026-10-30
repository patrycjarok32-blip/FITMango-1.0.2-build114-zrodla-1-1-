package com.example.viewmodel

object ProductHistorySupport {
    private fun normalized(text: String) = ProduceSupport.normalize(text.trim()).replace(Regex("[^a-z0-9]+"), " ").trim()
    fun barcode(p: SubstituteCandidate) = p.barcode.filterNot { it.isWhitespace() }
    fun key(p: SubstituteCandidate) = barcode(p).ifBlank { normalized(p.name) + "|" + normalized(p.brand) }
    fun same(a: SubstituteCandidate, b: SubstituteCandidate): Boolean {
        val ac = barcode(a); val bc = barcode(b)
        if (ac.isNotBlank() && bc.isNotBlank()) return ac == bc
        if (normalized(a.name) != normalized(b.name)) return false
        val ab = normalized(a.brand); val bb = normalized(b.brand)
        if (ab == bb) return true
        if (ab.isNotBlank() && bb.isNotBlank()) return false
        // A missing brand alone must not collapse distinct products.
        return a.caloriesPer100g != null && b.caloriesPer100g != null &&
            kotlin.math.abs(a.caloriesPer100g - b.caloriesPer100g) < 0.1f &&
            a.proteinPer100g == b.proteinPer100g && a.carbsPer100g == b.carbsPer100g && a.fatPer100g == b.fatPer100g
    }
    fun unique(products: List<SubstituteCandidate>): List<SubstituteCandidate> {
        val out = mutableListOf<SubstituteCandidate>()
        for (product in products) {
            val matches = out.indices.filter { same(out[it], product) }
            if (matches.size != 1) out.add(product)
            else {
                val index = matches.single(); val first = out[index]
                out[index] = first.copy(barcode = barcode(first).ifBlank { barcode(product) }, brand = first.brand.ifBlank { product.brand })
            }
        }
        return out
    }
}
