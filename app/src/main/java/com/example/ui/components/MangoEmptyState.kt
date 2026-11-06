package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

/** Shared Fresh Mango empty state; keeps each screen's original action callback. */
@Composable
fun MangoEmptyState(message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MutedSurface2,
        border = BorderStroke(1.dp, MangoPeach.copy(alpha = 0.50f))
    ) {
        Column(
            Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MangoSectionArt(com.example.R.drawable.fit_icon_mango, MangoCoral, 76.dp)
            Text("Tutaj zaczyna się Twój plan", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = Cream, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = GoldAccent, textAlign = TextAlign.Center)
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                shape = RoundedCornerShape(18.dp)
            ) { Text(actionLabel, fontWeight = FontWeight.Bold) }
        }
    }
}
