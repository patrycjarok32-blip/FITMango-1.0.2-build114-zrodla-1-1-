package com.example.viewmodel

import java.text.Normalizer
import java.util.Locale

object ShoppingCategories {
    val all = listOf("Warzywa i owoce", "Białko", "Nabiał", "Produkty zbożowe", "Orzechy i nasiona", "Przyprawy", "Tłuszcze", "Napoje", "Dodatki i słodziki", "Inne")
    private fun normalize(name: String) = Normalizer.normalize(name.lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    private val fruits = listOf("malin", "raspber", "borow", "blueber", "trusk", "strawber", "banan", "apple", "jabl", "gruszk", "pear", "pomarancz", "orange", "mandaryn", "tangerine", "winogron", "grape", "kiwi", "mango", "ananas", "pineapple", "cytryn", "lemon", "brzoskw", "peach", "sliwk", "plum", "arbuz", "watermelon")
    private val vegetables = listOf("pomidor", "tomato", "ogor", "cucumber", "salat", "lettuce", "avocado", "awok", "ziemni", "potato", "batat", "carrot", "marchew", "cebula", "onion", "czosnek", "garlic", "szparag", "asparagus", "brokul", "broccoli", "szpinak", "spinach", "cukini", "zucchini", "kapust", "cabbage", "papryka", "bell pepper", "baklazan", "eggplant")

    fun icon(name: String, category: String): Pair<String, String> {
        val text = normalize(name)
        return when (category) {
            "Warzywa i owoce" -> if (fruits.any { text.contains(it) }) "🍎" to "Owoce" else "🥕" to "Warzywa"
            "Nabiał" -> "🥛" to "Nabiał"
            "Białko" -> "🥚" to "Produkty białkowe"
            "Produkty zbożowe" -> "🌾" to "Produkty zbożowe"
            "Orzechy i nasiona" -> "🥜" to "Orzechy i nasiona"
            "Tłuszcze" -> "🫒" to "Tłuszcze"
            "Napoje" -> "🥤" to "Napoje"
            "Przyprawy" -> "🧂" to "Przyprawy"
            else -> "🛒" to category
        }
    }
    fun infer(name: String): String {
        val text = Normalizer.normalize(name.lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        fun has(vararg parts: String) = parts.any { text.contains(it) }
        fun word(vararg parts: String) = parts.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(text) }
        return when {
            has("bell pepper", "eggplant", "baklazan") -> "Warzywa i owoce"
            has("almond milk", "oat milk", "soy milk", "coconut milk", "mleko kokos", "mleko migdal", "mleko owsian", "mleko soj", "napoj migdal", "napoj owsian", "napoj soj") -> "Napoje"
            has("erytr", "erythrit", "xylit", "ksylit", "slodzik", "cukier", "sugar", "baking powder", "proszek do pieczenia") -> "Dodatki i słodziki"
            word("sol", "salt", "pepper") || has("kurkum", "turmeric", "pieprz", "cynamon", "cinnamon", "curry", "papryka miel", "oregano") -> "Przyprawy"
            has("pistac", "pistach", "orzech", "nuts", "almond", "migdal", "nasion", "seeds", "chia", "sezam", "sesame") -> "Orzechy i nasiona"
            word("tea", "water") || has("napoj", "drink", "herbata", "kawa", "coffee", "woda", "water", "juice", "sok") -> "Napoje"
            has("oliwa", "olive oil", "olej", " oil", "maslo", "butter") -> "Tłuszcze"
            word("ser") || has("mlek", "milk", "serek", "ser ", "cheese", "twar", "jogurt", "yogurt", "skyr", "kefir", "smietan", "cream") -> "Nabiał"
            has("jaj", "egg", "kurcz", "chicken", "indyk", "turkey", "wolow", "beef", "wieprz", "pork", "mieso", "meat", "losos", "salmon", "ryb", "fish", "tuncz", "tuna", "tofu", "soczew", "lentil", "fasol", "beans", "steak", "stek") -> "Białko"
            has("chleb", "bread", "bulk", "ryz", "rice", "platk", "oat", "makaron", "pasta", "maka", "flour", "quinoa", "komosa", "kasza", "bulgur") -> "Produkty zbożowe"
            has("malin", "raspber", "borow", "blueber", "trusk", "strawber", "banan", "apple", "jabl", "pomidor", "tomato", "ogor", "cucumber", "salat", "lettuce", "greens", "avocado", "awok", "ziemni", "potato", "batat", "carrot", "marchew", "cebula", "onion", "czosnek", "garlic", "lemon", "cytryn", "asparagus", "szparag") -> "Warzywa i owoce"
            fruits.any { text.contains(it) } || vegetables.any { text.contains(it) } -> "Warzywa i owoce"
            else -> "Inne"
        }
    }
}
