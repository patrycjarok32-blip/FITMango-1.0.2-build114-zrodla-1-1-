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
val MangoYellow get() = role(0xFFFFC46B, 0xFF9D571B)
val JuicyOrange get() = Color(0xFFFF922B)
val MangoSunset get() = Color(0xFFFFD58B)
val MangoEmerald get() = Color(0xFF23865A)
val MangoPremiumGlow get() = role(0xFF243B34, 0xFFF7E9D9)
val Cream get() = role(0xFFF4F2E9, 0xFF20362E)
val LeafGreen get() = role(0xFF9FC9AC, 0xFF39745B)
val DarkBackground get() = role(0xFF14271F, 0xFFF7F6EF)
val WarmBrown get() = role(0xFF334155, 0xFFE7DDCF)
val GoldAccent get() = role(0xFFB3C7B8, 0xFF61776A)
val MutedSurface1 get() = role(0xFF21382F, 0xFFFFFFFF)
val MutedSurface2 get() = role(0xFF2B473B, 0xFFF1F3E9)
val SurfaceDark get() = role(0xFF14271F, 0xFFF8F4EB)
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
val MangoCoral get() = Color(0xFFE88F73)
val MangoMint get() = Color(0xFF568C6B)
val MangoLavender get() = Color(0xFF7866D8)
val MangoPeach get() = Color(0xFFFFC18D)

// Fresh Mango 2.0 decorative palette; semantic colors above remain accessible in both themes.
val MangoLime get() = Color(0xFFD7EDC2)
val MangoCreamAccent get() = Color(0xFFFFF7E9)

// Fresh Mango 3.0 Soft & Smart — calm accents, shared by both themes.
val MangoSage get() = Color(0xFFA9C5A0)
val MangoSoftPeach get() = Color(0xFFFFC5A3)
val MangoSoftCream get() = Color(0xFFF6F5ED)
val MangoForest get() = Color(0xFF39745B)
