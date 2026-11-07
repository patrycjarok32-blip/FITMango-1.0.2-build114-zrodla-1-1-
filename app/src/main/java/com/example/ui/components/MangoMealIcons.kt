package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JuicyOrange

/** Consistent outlined category icons, replacing mixed 3D meal illustrations. */
@Composable
fun MangoMealIcon(categoryIndex: Int, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val image = when (categoryIndex) {
        0 -> Icons.Outlined.WbSunny
        1 -> Icons.Outlined.LocalCafe
        2 -> Icons.Outlined.RestaurantMenu
        3 -> Icons.Outlined.FavoriteBorder
        else -> Icons.Outlined.NightsStay
    }
    Box(modifier.size(size).background(JuicyOrange.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center) {
        Icon(imageVector = image, contentDescription = null,
            tint = JuicyOrange, modifier = Modifier.size(size * 0.55f))
    }
}
