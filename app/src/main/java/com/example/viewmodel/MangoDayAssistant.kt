package com.example.viewmodel

/** Lightweight, offline, deterministic suggestions. Never invents nutritional or health records. */
object MangoDayAssistant {
    fun message(consumed: Int, target: Int, waterMl: Int, trainingPlanned: Boolean): String {
        if (target <= 0) return "Uzupełnij cel kalorii, aby zobaczyć wskazówki na dziś."
        val remaining = (target - consumed).coerceAtLeast(0)
        return when {
            consumed >= target -> "Cel kalorii na dziś osiągnięty. Sprawdź podsumowanie dnia."
            trainingPlanned && remaining > 0 -> "Masz zaplanowany trening. Do celu pozostało $remaining kcal."
            waterMl == 0 -> "Nie zapisano jeszcze wody. Możesz dodać pierwszą porcję."
            else -> "Do celu pozostało $remaining kcal. Sprawdź kolejny posiłek."
        }
    }
}
