package com.example.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

val LocalReducedMangoMotion = staticCompositionLocalOf { false }

@Composable
fun MangoMotionProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("FITMangoMotion", Context.MODE_PRIVATE) }
    var reduced by remember { mutableStateOf(prefs.getBoolean("reduced", false)) }
    var systemReduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        systemReduced = !ValueAnimator.areAnimatorsEnabled()
    }
    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemReduced = !ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "reduced") reduced = prefs.getBoolean("reduced", false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CompositionLocalProvider(LocalReducedMangoMotion provides (reduced || systemReduced), content = content)
}

@Composable fun mangoFloat(target: Float): Float {
    val value by animateFloatAsState(target, tween(if (LocalReducedMangoMotion.current) 0 else 240), label = "mango_progress")
    return value
}

@Composable fun mangoNumber(target: Int): Int {
    val value by animateIntAsState(target, tween(if (LocalReducedMangoMotion.current) 0 else 450), label = "mango_number")
    return value
}

@Composable fun Modifier.mangoExpand(): Modifier = if (LocalReducedMangoMotion.current) this
    else animateContentSize(animationSpec = tween(220))

/** One finite movement after an increase; entering the screen never starts a loop. */
@Composable
fun MangoIncreaseMotion(value: Int, drop: Boolean = false, content: @Composable () -> Unit) {
    val reduced = LocalReducedMangoMotion.current
    val phase = remember { Animatable(1f) }
    var previous by remember { mutableIntStateOf(value) }
    LaunchedEffect(value, reduced) {
        val increased = value > previous
        previous = value
        if (increased && !reduced) { phase.snapTo(0f); phase.animateTo(1f, tween(480)) }
        else phase.snapTo(1f)
    }
    Box(Modifier.graphicsLayer {
        val wave = kotlin.math.sin(phase.value * Math.PI).toFloat()
        scaleX = if (reduced) 1f else 1f + wave * 0.14f
        scaleY = scaleX
        translationY = if (reduced) 0f else if (drop) -28f * (1f - phase.value) else -10f * wave
    }) { content() }
}

@Composable
fun MangoBadge(name: String, earned: Boolean) {
    val prefs = LocalContext.current.getSharedPreferences("FITMangoMotion", Context.MODE_PRIVATE)
    val reduced = LocalReducedMangoMotion.current
    val phase = remember(name) { Animatable(1f) }
    LaunchedEffect(name, earned, reduced) {
        if (earned && !prefs.getBoolean("badge_$name", false)) {
            prefs.edit().putBoolean("badge_$name", true).apply()
            if (!reduced) { phase.snapTo(0f); phase.animateTo(1f, tween(800)) }
        }
        if (reduced) phase.snapTo(1f)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.graphicsLayer {
            scaleX = if (reduced) 1f else 1f + 0.07f * kotlin.math.sin(phase.value * Math.PI).toFloat()
            scaleY = scaleX
        }) {
        FitMangoAssetIcon(com.example.R.drawable.fit_clay_medal, modifier = Modifier.size(28.dp))
        Text((if (earned) "★ Zdobyta: " else "☆ Do zdobycia: ") + name +
            if (!reduced && phase.value < 1f) " ✨" else "",
            color = if (earned) LeafGreen else GoldAccent, modifier = Modifier.weight(1f))
    }
}

@Composable fun ImportProgress() {
    if (LocalReducedMangoMotion.current) Text("Przetwarzanie…", color = LeafGreen)
    else LinearProgressIndicator(Modifier.fillMaxWidth(), color = LeafGreen)
}

/** The check only follows a real saved-count increase, never a loading timeout. */
@Composable fun ImportSavedFeedback(savedCount: Int, message: String = "Zapisano przepis") {
    val view = androidx.compose.ui.platform.LocalView.current
    var previous by remember { mutableIntStateOf(savedCount) }
    var show by remember { mutableStateOf(false) }
    val reduced = LocalReducedMangoMotion.current
    val phase = remember { Animatable(1f) }
    LaunchedEffect(savedCount) {
        val increased = savedCount > previous
        previous = savedCount
        if (increased) {
            runCatching { view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
            show = true
            if (!reduced) { phase.snapTo(0f); phase.animateTo(1f, tween(350)) }
            delay(2200); show = false
        }
    }
    if (show) Text("✓ $message", color = LeafGreen, modifier = Modifier.testTag("import_saved_feedback").graphicsLayer {
        alpha = if (reduced) 1f else phase.value
        translationY = if (reduced) 0f else 10f * (1f - phase.value)
    })
}

@Composable fun MangoMotionSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FITMangoMotion", Context.MODE_PRIVATE) }
    var reduced by remember { mutableStateOf(prefs.getBoolean("reduced", false)) }
    CollapsibleMangoCard("Wygląd i animacje", "motion") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ogranicz animacje", Modifier.weight(1f).padding(top = 12.dp))
            Switch(reduced, { reduced = it; prefs.edit().putBoolean("reduced", it).apply() }, Modifier.testTag("reduce_animations"))
        }
        Text("Wyłącza dekoracyjne ruchy i płynne przejścia. Postęp i potwierdzenia pozostają widoczne. Uwzględniamy też wyłączenie animacji w Androidzie.")
        MangoMotionPreview()
    }
}
