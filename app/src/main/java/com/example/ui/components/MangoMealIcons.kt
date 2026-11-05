package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Five original shaded, vector-drawn food illustrations. No network or bitmap assets.
 * Decorative icons only; text labels remain accessible.
 */
@Composable
fun MangoMealIcon(categoryIndex: Int, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / 100f
        fun pos(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun dimensions(w: Float, h: Float) = Size(w * scale, h * scale)
        val cream = Color(0xFFFFF4DD)
        val shadow = Color(0xFFB47A49)
        val orange = Color(0xFFFF9F37)
        val green = Color(0xFF6BA56A)
        val red = Color(0xFFEB7255)
        drawCircle(Color(0x1F754B20), 43f * scale, pos(52f, 55f))
        when (categoryIndex) {
            0 -> { // Breakfast: sunny-side-up egg on a plate
                drawCircle(shadow, 39f * scale, pos(50f, 53f))
                drawCircle(cream, 37f * scale, pos(49f, 48f))
                drawOval(Brush.linearGradient(listOf(Color.White, Color(0xFFFFE8C1))),
                    pos(19f, 27f), dimensions(61f, 42f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFDC55), orange)),
                    15f * scale, pos(50f, 47f))
                drawCircle(Color(0xAFFFFFFF), 5f * scale, pos(45f, 42f))
            }
            1 -> { // Second breakfast: apple
                drawOval(Brush.linearGradient(listOf(Color(0xFFFFBA79), red, Color(0xFFB94338))),
                    pos(23f, 27f), dimensions(54f, 57f))
                drawOval(Brush.linearGradient(listOf(Color(0xFFBFE78D), green)),
                    pos(48f, 15f), dimensions(27f, 14f))
                drawLine(shadow, pos(48f, 32f), pos(52f, 17f), 4f * scale)
                drawOval(Color(0x8FFFFFFF), pos(31f, 36f), dimensions(12f, 25f))
            }
            2 -> { // Lunch: plated vegetables
                drawCircle(shadow, 39f * scale, pos(51f, 54f))
                drawCircle(cream, 37f * scale, pos(50f, 48f))
                drawCircle(Color(0xFF8ACF7A), 19f * scale, pos(38f, 44f))
                drawCircle(orange, 16f * scale, pos(61f, 48f))
                drawCircle(red, 12f * scale, pos(47f, 63f))
                drawCircle(Color(0x91FFFFFF), 5f * scale, pos(34f, 38f))
            }
            3 -> { // Snack: berry cupcake
                drawOval(Color(0x555B3827), pos(23f, 78f), dimensions(55f, 9f))
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFDCA0), orange)),
                    pos(27f, 45f), dimensions(47f, 35f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * scale))
                drawCircle(cream, 23f * scale, pos(50f, 43f))
                drawCircle(red, 10f * scale, pos(50f, 25f))
                drawCircle(Color(0xBFFFFFFF), 3.5f * scale, pos(46f, 21f))
            }
            else -> { // Dinner: soup bowl
                drawOval(Color(0x555B3827), pos(20f, 79f), dimensions(62f, 9f))
                drawOval(shadow, pos(16f, 34f), dimensions(68f, 46f))
                drawOval(Brush.verticalGradient(listOf(cream, Color(0xFFE5A976))),
                    pos(17f, 30f), dimensions(66f, 42f))
                drawOval(Brush.linearGradient(listOf(orange, Color(0xFFE66C32))),
                    pos(23f, 34f), dimensions(54f, 25f))
                drawCircle(green, 6f * scale, pos(47f, 44f))
                drawCircle(Color(0xBFFFFFFF), 3f * scale, pos(36f, 39f))
            }
        }
    }
}
