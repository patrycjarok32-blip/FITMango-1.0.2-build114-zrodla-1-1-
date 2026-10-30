package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.*

/** Packaged 3D artwork: one consistent size and soft halo, no network or animation loop. */
@Composable
fun MangoSectionArt(resource: Int, accent: Color, size: Dp = 48.dp) {
    Box(Modifier.size(size).background(Brush.radialGradient(listOf(accent.copy(alpha = .24f), accent.copy(alpha = .07f), Color.Transparent)), CircleShape),
        contentAlignment = Alignment.Center) {
        FitMangoAssetIcon(resource, contentDescription = null, modifier = Modifier.size(size * .82f))
    }
}

fun sectionArtwork(id: String): Int = when (id) {
    "calories" -> R.drawable.fit_icon_meal
    "produce" -> R.drawable.fit_icon_vegetable
    "water" -> R.drawable.fit_icon_water
    "health" -> R.drawable.fit_icon_health
    "sport" -> R.drawable.fit_icon_mass
    "effects", "weekly_summary" -> R.drawable.fit_icon_progress
    "ai", "gemini", "analysis", "motion" -> R.drawable.fit_icon_ai
    "tools", "photos" -> R.drawable.fit_icon_camera
    "day_history", "reminder_settings" -> R.drawable.fit_icon_calendar
    "challenges" -> R.drawable.fit_clay_medal
    "calculator", "body_photos" -> R.drawable.fit_icon_profile
    "measurements", "renpho" -> R.drawable.fit_icon_measure
    "backup" -> R.drawable.fit_icon_document
    else -> R.drawable.fit_icon_recipes
}

fun sectionArtworkAccent(id: String): Color = when (id) {
    "water", "tools", "photos" -> WaterAccent
    "produce", "health", "sport" -> LeafGreen
    "effects", "weekly_summary", "ai", "gemini", "analysis", "motion" -> DinnerAccent
    else -> MangoYellow
}
