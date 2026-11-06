package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/** Shared visual language of the approved FITMango mockup. */
@Composable
fun FreshMangoPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface1),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun FreshMangoTabs(
    labels: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth().background(MutedSurface2, RoundedCornerShape(18.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEach { label ->
            val active = label == selected
            Box(Modifier.weight(1f)
                .background(if (active) MutedSurface1 else MutedSurface2, RoundedCornerShape(14.dp))
                .clickable { onSelected(label) }.padding(vertical = 10.dp, horizontal = 3.dp),
                contentAlignment = Alignment.Center) {
                Text(label, color = if (active) JuicyOrange else GoldAccent,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun FreshMangoRing(
    value: Float,
    center: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 136.dp
) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(9.dp)) {
            val width = 11.dp.toPx()
            val inset = width / 2
            val sizeArc = Size(size.width - width, size.height - width)
            drawArc(LeafGreen.copy(alpha = 0.13f), -90f, 360f, false,
                topLeft = Offset(inset, inset), size = sizeArc,
                style = Stroke(width, cap = StrokeCap.Round))
            drawArc(LeafGreen, -90f, 360f * value.coerceIn(0f, 1f), false,
                topLeft = Offset(inset, inset), size = sizeArc,
                style = Stroke(width, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Cream)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = GoldAccent)
        }
    }
}

@Composable
fun FreshMangoStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MutedSurface2, RoundedCornerShape(16.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = GoldAccent)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Cream)
    }
}
