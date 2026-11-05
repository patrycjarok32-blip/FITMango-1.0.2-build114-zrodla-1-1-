
package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.ScaledMealNutrition
import kotlin.math.abs

private val BalanceBackground = Color(0xFFFFFDF8)
private val BalanceSurface = Color(0xFFFFFFFF)
private val BalanceText = Color(0xFF29352B)
private val BalanceSecondary = Color(0xFF788176)
private val BalanceOrange = Color(0xFFF38B32)
private val BalanceGreen = Color(0xFF5A9472)
private val BalanceTrack = Color(0xFFF1EDE5)
private val BalanceBorder = Color(0xFFF0E8DB)

@Composable
fun StudioBalanceCard(
    consumed: ScaledMealNutrition,
    targetCalories: Int,
    targetProtein: Int,
    targetCarbs: Int,
    targetFat: Int,
    plannedCalories: Int?,
    burnedCalories: Int? = null
) {
    val calories = mangoNumber(consumed.calories ?: 0)
    val remaining = targetCalories - calories
    val progress = (
        calories.toFloat() / targetCalories.coerceAtLeast(1)
    ).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 700),
        label = "balance_calories_progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_daily_balance"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = BalanceBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Twój bilans dnia",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BalanceText
                    )
                    Text(
                        "Codziennie bliżej celu",
                        fontSize = 12.sp,
                        color = BalanceSecondary
                    )
                }

                Surface(
                    color = Color(0xFFFFEEDB),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "DZIŚ",
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        ),
                        color = BalanceOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(152.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 13.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(
                            size.width - stroke,
                            size.height - stroke
                        )

                        drawArc(
                            color = BalanceTrack,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(
                                width = stroke,
                                cap = StrokeCap.Round
                            )
                        )

                        drawArc(
                            color = BalanceOrange,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(
                                width = stroke,
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "$calories",
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Black,
                            color = BalanceText,
                            modifier = Modifier.testTag(
                                "dashboard_consumed_calories"
                            )
                        )
                        Text(
                            "zjedzone kcal",
                            fontSize = 11.sp,
                            color = BalanceSecondary
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (remaining >= 0)
                            "Pozostało"
                        else
                            "Ponad cel",
                        color = BalanceSecondary,
                        fontSize = 12.sp
                    )

                    Text(
                        "${abs(remaining)} kcal" +
                            if (consumed.isPartial) "*" else "",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BalanceText,
                        modifier = Modifier.testTag(
                            "dashboard_remaining_calories"
                        )
                    )

                    HorizontalDivider(color = BalanceBorder)

                    Text(
                        "Cel: $targetCalories kcal",
                        color = BalanceSecondary,
                        fontSize = 12.sp
                    )

                    Text(
                        "Plan: ${plannedCalories ?: "—"} kcal",
                        color = BalanceSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = BalanceBorder)

            Text(
                "Makroskładniki",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = BalanceText
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioMacro(
                    title = "Białko",
                    value = consumed.protein ?: 0,
                    target = targetProtein,
                    accent = BalanceGreen,
                    modifier = Modifier.weight(1f)
                )

                StudioMacro(
                    title = "Węgle",
                    value = consumed.carbs ?: 0,
                    target = targetCarbs,
                    accent = Color(0xFFE8A544),
                    modifier = Modifier.weight(1f)
                )

                StudioMacro(
                    title = "Tłuszcze",
                    value = consumed.fat ?: 0,
                    target = targetFat,
                    accent = Color(0xFF8D83CE),
                    modifier = Modifier.weight(1f)
                )
            }

            if (consumed.isPartial) {
                Text(
                    "* Niepełne dane składników — bilans orientacyjny.",
                    color = BalanceOrange,
                    fontSize = 11.sp,
                    modifier = Modifier.testTag(
                        "dashboard_partial_nutrition"
                    )
                )
            }

            burnedCalories?.let { burned ->
                Surface(
                    color = Color(0xFFEAF5EE),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector =
                                Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = BalanceGreen
                        )

                        Text(
                            "Spalone dzisiaj",
                            modifier = Modifier.weight(1f),
                            color = BalanceText,
                            fontSize = 12.sp
                        )

                        Text(
                            "$burned kcal",
                            color = BalanceGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioMacro(
    title: String,
    value: Int,
    target: Int,
    accent: Color,
    modifier: Modifier
) {
    val displayed = mangoNumber(value)
    val remaining = target - displayed
    val progress = (
        displayed.toFloat() / target.coerceAtLeast(1)
    ).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "macro_$title"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BalanceSurface)
            .border(
                1.dp,
                BalanceBorder,
                RoundedCornerShape(18.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )

        Text(
            title,
            color = BalanceText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Text(
            "$displayed g",
            color = BalanceText,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = accent,
            trackColor = BalanceTrack
        )

        Text(
            "Cel $target g",
            color = BalanceSecondary,
            fontSize = 10.sp
        )

        Text(
            if (remaining >= 0)
                "$remaining g zostało"
            else
                "${abs(remaining)} g ponad",
            color = accent,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}
