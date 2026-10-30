package com.example.ui.components

import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.R

/** One short wave per event; never a permanent animation or a notification loop. */
@Composable
fun MangoGuide(message: String? = null, event: Int = 0, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val prefs = LocalContext.current.getSharedPreferences("FITMangoUsability", 0)
    var enabled by remember { mutableStateOf(prefs.getBoolean("mangoGuide", true)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == null || key == "mangoGuide") enabled = prefs.getBoolean("mangoGuide", true) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    if (!enabled) return
    val reduced = LocalReducedMangoMotion.current
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(event, reduced) {
        if (reduced) tilt.snapTo(0f)
        else { tilt.snapTo(-6f); tilt.animateTo(5f, tween(120)); tilt.animateTo(0f, tween(160)) }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FitMangoAssetIcon(R.drawable.fit_icon_mango, null, Modifier.size(size).graphicsLayer { rotationZ = tilt.value })
        if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
