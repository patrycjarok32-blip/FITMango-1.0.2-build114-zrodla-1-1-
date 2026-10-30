package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object MangoAppearance {
    var light by mutableStateOf(false)
    const val PREFS = "FITMangoUsability"
    const val KEY = "lightTheme"
}
private fun role(dark: Long, light: Long) = Color(if (MangoAppearance.light) light else dark)
val MangoYellow get() = role(0xFFFFC107, 0xFF8E5900)
val JuicyOrange get() = Color(0xFFFFA000)
val Cream get() = role(0xFFE8EDF5, 0xFF203C30)
val LeafGreen get() = role(0xFF00C99A, 0xFF08755D)
val DarkBackground get() = role(0xFF0B1017, 0xFFFFF8E7)
val WarmBrown get() = role(0xFF334155, 0xFFD6DDCF)
val GoldAccent get() = role(0xFF9BAAC0, 0xFF526B5B)
val MutedSurface1 get() = role(0xFF101A2C, 0xFFFFFFFF)
val MutedSurface2 get() = role(0xFF1D2A3E, 0xFFEEF3E7)
val SurfaceDark get() = role(0xFF070D1C, 0xFFF6F3E7)
val RipeYellow get() = Color(0xFFFFCA28)
val MangoButtonInk get() = Color(0xFF101827)
val ActivityAccent get() = LeafGreen
val SleepAccent get() = DinnerAccent
val ProteinAccent get() = LeafGreen
val CarbsAccent get() = MangoYellow
val FatAccent get() = role(0xFFFF4CA4, 0xFFB31867)
val WaterAccent get() = role(0xFF4FA3FF, 0xFF1768AB)
val DinnerAccent get() = role(0xFFB696FF, 0xFF694BA6)
val OrangeInk get() = role(0xFFFFBC52, 0xFF955500)
val Pistachio get() = Color(0xFFA5D6A7)

val ErrorInk get() = role(0xFFFF8C95, 0xFFB3261E)
