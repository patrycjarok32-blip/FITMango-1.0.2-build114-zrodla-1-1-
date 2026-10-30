package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.ScaledMealNutrition
import kotlin.math.abs

/** Presentation only: all values are supplied by the existing nutrition calculator. */
@Composable
fun StudioBalanceCard(consumed: ScaledMealNutrition, targetCalories: Int,
    targetProtein: Int, targetCarbs: Int, targetFat: Int, plannedCalories: Int?,
    burnedCalories: Int? = null) {
    val calories = mangoNumber(consumed.calories ?: 0)
    val remaining = targetCalories - calories
    MangoCard(Modifier.testTag("studio_daily_balance")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MangoSectionArt(com.example.R.drawable.fit_icon_meal, MangoYellow, size = 42.dp)
                Column(Modifier.weight(1f)) {
                    Text("Bilans dnia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Cel: $targetCalories kcal", color = GoldAccent, style = MaterialTheme.typography.bodySmall)
                    Text("Zaplanowano: ${plannedCalories?.toString() ?: "—"} kcal", color = GoldAccent, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (remaining >= 0) "Pozostało" else "Ponad cel", color = GoldAccent,
                        style = MaterialTheme.typography.bodySmall)
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 28.sp)) { append(abs(remaining).toString()) }
                        withStyle(SpanStyle(fontSize = 14.sp)) { append(" kcal${if (consumed.isPartial) "*" else ""}") }
                    },
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MangoYellow,
                        modifier = Modifier.testTag("dashboard_remaining_calories"))
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceDark) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Zjedzone kalorie", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("$calories", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("dashboard_consumed_calories"))
                        Text(" / $targetCalories kcal", color = GoldAccent, style = MaterialTheme.typography.bodySmall)
                    }
                    MangoProgressBar((calories.toFloat() / targetCalories.coerceAtLeast(1)).coerceIn(0f, 1f),
                        color = JuicyOrange, trackColor = MutedSurface2)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioMacro("Białko", consumed.protein ?: 0, targetProtein, ProteinAccent, Modifier.weight(1f))
                StudioMacro("Węgle", consumed.carbs ?: 0, targetCarbs, CarbsAccent, Modifier.weight(1f))
                StudioMacro("Tłuszcze", consumed.fat ?: 0, targetFat, FatAccent, Modifier.weight(1f))
            }
            if (consumed.isPartial) Text("* Niepełne dane składników — bilans orientacyjny.",
                style = MaterialTheme.typography.bodySmall, color = OrangeInk,
                modifier = Modifier.testTag("dashboard_partial_nutrition"))
            burnedCalories?.let {
                Text("Spalone dzisiaj: $it kcal", color = GoldAccent, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StudioMacro(title: String, value: Int, target: Int, accent: Color, modifier: Modifier) {
    val displayed = mangoNumber(value)
    val remaining = target - displayed
    Column(modifier.background(SurfaceDark, RoundedCornerShape(14.dp))
        .border(1.dp, accent.copy(alpha = .25f), RoundedCornerShape(14.dp)).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val art = when (title) { "Białko" -> com.example.R.drawable.fit_icon_protein; "Węgle" -> com.example.R.drawable.fit_icon_carbs; else -> com.example.R.drawable.fit_icon_fat }
        MangoSectionArt(art, accent, size = 30.dp)
        Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("$displayed / $target g", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        MangoProgressBar((displayed.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f), color = accent,
            trackColor = MutedSurface2, modifier = Modifier.padding(vertical = 2.dp))
        Text(if (remaining >= 0) "$remaining g do celu" else "${abs(remaining)} g ponad cel",
            color = GoldAccent, fontSize = 11.sp)
    }
}
