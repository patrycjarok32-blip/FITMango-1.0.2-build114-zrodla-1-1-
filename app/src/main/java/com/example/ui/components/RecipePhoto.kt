package com.example.ui.components

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.viewmodel.RecipeIllustrations
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import java.io.File
import com.example.viewmodel.RecipePhotoHealth
import com.example.ui.theme.*

@Composable
fun RecipePhoto(imageUrl: String, title: String, modifier: Modifier = Modifier, showIllustrationLabel: Boolean = true,
    onChoosePhoto: (() -> Unit)? = null) {
    val context = LocalContext.current
    var failed by remember(imageUrl) { mutableStateOf(false) }
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    val photoAlpha by animateFloatAsState(if (loaded) 1f else 0f,
        tween(if (LocalReducedMangoMotion.current) 0 else 220), label = "recipe_photo_appearance")
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MutedSurface2).then(if (onChoosePhoto != null) Modifier.clickable(onClick = onChoosePhoto) else Modifier), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Restaurant, "Brak zdjęcia: $title", Modifier.size(24.dp), tint = MangoYellow)
                Text(if (onChoosePhoto != null) "Wybierz zdjęcie" else if (failed) "Brak zdjęcia" else if (imageUrl.isBlank()) "Brak zdjęcia" else "Wczytywanie…",
                    fontSize = 9.sp, color = GoldAccent, maxLines = 2)
            }
        }
    }
    if (imageUrl.isBlank()) {
        Box(modifier.clip(RoundedCornerShape(14.dp)).background(MutedSurface2), contentAlignment = Alignment.Center) { placeholder() }
    } else Box(modifier.clip(RoundedCornerShape(14.dp))) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() }
        val request = remember(context, imageUrl) {
            ImageRequest.Builder(context).data(if (imageUrl.startsWith("/")) File(imageUrl) else imageUrl).size(768).crossfade(false).build()
        }
        AsyncImage(model = request, contentDescription =
            if (RecipeIllustrations.isIllustration(imageUrl)) "Ilustracja AI: $title" else "Zdjęcie: $title",
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = photoAlpha }, contentScale = ContentScale.Crop,
            onSuccess = { loaded = true; failed = false; RecipePhotoHealth.failed(context, imageUrl, false) },
            onError = { failed = true; loaded = false; RecipePhotoHealth.failed(context, imageUrl, true) })
        if (failed) placeholder()
        if (loaded && showIllustrationLabel && RecipeIllustrations.isIllustration(imageUrl)) {
            Text("Ilustracja AI", color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 3.dp))
        }
    }
}
