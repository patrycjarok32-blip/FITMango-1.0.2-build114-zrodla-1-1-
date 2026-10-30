package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun MangoMotionPreview() {
    val context = LocalContext.current
    val reduced = LocalReducedMangoMotion.current
    var expanded by remember { mutableStateOf(false) }
    val duration = if (reduced) 0 else 220
    Text(if (reduced) "Ruch jest ograniczony przez ustawienie aplikacji lub Androida." else "Animacje są włączone.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("motion_status"))
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().pressScaleEffect().testTag("motion_preview")) {
        Text("Sprawdź animacje")
    }
    AnimatedVisibility(expanded, enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Podgląd ✓", modifier = Modifier.testTag("motion_preview_result"))
            MangoProgressBar(.8f, color = ActivityAccent)
            Text("Tak rozwijają się karty. To podgląd — nie zapisuje danych.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (reduced) TextButton(onClick = {
        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) { Text("Ustawienia dostępności Androida") }
}
