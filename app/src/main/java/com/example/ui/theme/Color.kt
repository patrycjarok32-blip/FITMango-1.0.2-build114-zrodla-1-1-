package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object MangoAppearance {
    var light by mutableStateOf(true)
    const val PREFS = "FITMangoUsability"
    const val KEY = "lightTheme"
}
private fun role(dark: Long, light: Long) = Color(if (MangoAppearance.light) light else dark)
val MangoYellow get() = role(0xFFFFB74D, 0xFFB65E00)
val JuicyOrange get() = Color(0xFFFF922B)
val MangoSunset get() = Color(0xFFFFD58B)
val MangoEmerald get() = Color(0xFF37B98B)
val MangoPremiumGlow get() = role(0xFF223D36, 0xFFFFE9C4)
val Cream get() = role(0xFFF5F6F1, 0xFF243E30)
val LeafGreen get() = role(0xFF65C98E, 0xFF258557)
val DarkBackground get() = role(0xFF10291D, 0xFFFAF7F0)
val WarmBrown get() = role(0xFF334155, 0xFFE6D9CA)
val GoldAccent get() = role(0xFFB3C7B8, 0xFF647A69)
val MutedSurface1 get() = role(0xFF193727, 0xFFFFFFFF)
val MutedSurface2 get() = role(0xFF244735, 0xFFFFF0DD)
val SurfaceDark get() = role(0xFF070D1C, 0xFFFFF1E2)
val RipeYellow get() = Color(0xFFFFB629)
val MangoButtonInk get() = Color(0xFF243E30)
val ActivityAccent get() = LeafGreen
val SleepAccent get() = DinnerAccent
val ProteinAccent get() = LeafGreen
val CarbsAccent get() = MangoYellow
val FatAccent get() = role(0xFFFF4CA4, 0xFFF66C80)
val WaterAccent get() = role(0xFF4FA3FF, 0xFF38A9E8)
val DinnerAccent get() = role(0xFFB696FF, 0xFF7866D8)
val OrangeInk get() = role(0xFFFFBC52, 0xFFB85A27)
val Pistachio get() = Color(0xFFA5D6A7)

val ErrorInk get() = role(0xFFFF8C95, 0xFFB3261E)

// Fresh Mango semantic accents, shared across dashboard, meal plan and navigation.
val MangoCoral get() = Color(0xFFFF922B)
val MangoMint get() = Color(0xFF37B98B)
val MangoLavender get() = Color(0xFF7866D8)
val MangoPeach get() = Color(0xFFFFD58B)
