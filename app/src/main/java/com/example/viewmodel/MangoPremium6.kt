package com.example.viewmodel

/** Cosmetic progress only. Never subtract points for missed days. */
data class MangoTreeStage(val icon: String, val title: String)
object MangoPremium6 {
    fun treeStage(recordedDays: Int): MangoTreeStage = when {
        recordedDays >= 30 -> MangoTreeStage("🌳🥭", "Owocujące drzewko")
        recordedDays >= 14 -> MangoTreeStage("🌳", "Młode drzewko")
        recordedDays >= 5 -> MangoTreeStage("🌱", "Rośnie")
        else -> MangoTreeStage("🌰", "Nasionko")
    }
}
