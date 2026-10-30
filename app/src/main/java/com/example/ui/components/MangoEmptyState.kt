package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun MangoEmptyState(message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(MangoYellow.copy(alpha = .045f), RoundedCornerShape(18.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MangoSectionArt(com.example.R.drawable.fit_icon_mango, MangoYellow, 56.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onAction, modifier = Modifier.pressScaleEffect()) { Text(actionLabel) }
    }
}
