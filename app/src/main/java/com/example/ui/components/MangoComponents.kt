package com.example.ui.components

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*

// 1. Press Scale modifier
@Composable
fun Modifier.pressScaleEffect(): Modifier {
    // Observe the same pointer stream as the actual Button/clickable without consuming it.
    // A private InteractionSource was never attached to the clickable, so it never pressed.
    val reduced = LocalReducedMangoMotion.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed && !reduced) .97f else 1f,
        androidx.compose.animation.core.tween(if (reduced) 0 else 150), label = "pressScale")
    return this.scale(scale).pointerInput(reduced) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            try { waitForUpOrCancellation(pass = PointerEventPass.Initial) }
            finally { pressed = false }
        }
    }
}

// 2. MangoPrimaryButton
@Composable
fun MangoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !LocalReducedMangoMotion.current) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "press"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(if (enabled) 1.dp else 0.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (enabled) listOf(RipeYellow, JuicyOrange, Color(0xFFF39717))
                        else listOf(WarmBrown, WarmBrown)
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MangoButtonInk,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.5.sp
        )
    }
}

// 3. MangoSecondaryButton
@Composable
fun MangoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !LocalReducedMangoMotion.current) 0.96f else 1f,
        label = "press"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(if (enabled) 1.dp else 0.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, if (enabled) JuicyOrange else GoldAccent, CircleShape)
            .background(Brush.verticalGradient(listOf(MutedSurface1, MutedSurface2)))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Cream,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

// 4. MangoCard
@Composable
fun MangoCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) JuicyOrange else WarmBrown.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MutedSurface1
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

// 5. MangoMealCard
@Composable
fun MangoMealCard(
    mealType: String,
    timeRange: String,
    recipeTitle: String,
    calories: Int,
    protein: Int,
    fat: Int,
    carbs: Int,
    imageUrl: String,
    isCompleted: Boolean = false,
    onChangeClick: () -> Unit = {},
    onSkipClick: () -> Unit = {},
    onEatenClick: () -> Unit = {}
) {
    MangoCard(
        isSelected = isCompleted,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isCompleted) LeafGreen else JuicyOrange)
                    )
                    Text(
                        text = "$mealType • $timeRange",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) LeafGreen else OrangeInk
                    )
                }
                if (isCompleted) {
                    Text(
                        text = "Zjedzone ✓",
                        color = LeafGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecipePhoto(
                    imageUrl = imageUrl,
                    title = recipeTitle,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = recipeTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Cream
                    )
                    Text(
                        text = "$calories kcal • B: ${protein}g • T: ${fat}g • W: ${carbs}g",
                        fontSize = 12.sp,
                        color = Cream.copy(alpha = 0.7f)
                    )
                }
            }

            HorizontalDivider(color = WarmBrown.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MangoYellow),
                    border = BorderStroke(1.dp, WarmBrown)
                ) {
                    Text("Zmień", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSkipClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, WarmBrown.copy(alpha = 0.3f))
                ) {
                    Text("Pomiń", fontSize = 11.sp)
                }
                Button(
                    onClick = onEatenClick,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) LeafGreen else JuicyOrange,
                        contentColor = MangoButtonInk
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isCompleted) "Cofnij" else "Zjedzony", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 6. MangoRecipeCard
@Composable
fun MangoRecipeCard(
    title: String,
    category: String,
    calories: Int,
    imageUrl: String,
    onClick: () -> Unit,
    onPhotoClick: () -> Unit = {},
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    preparationMinutes: Int? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressScaleEffect()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface1),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                // Photo and favorite actions have separate accessible touch targets.
                RecipePhoto(
                    imageUrl = imageUrl,
                    title = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                    onChoosePhoto = onPhotoClick
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clickable { onPhotoClick() },
                    shape = CircleShape,
                    color = MutedSurface1.copy(alpha = 0.95f),
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = if (imageUrl.isBlank()) "Dodaj zdjęcie" else "Zmień zdjęcie",
                            tint = LeafGreen,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = RoundedCornerShape(8.dp), color = SurfaceDark.copy(alpha = .95f)) {
                    Text((preparationMinutes?.let { "$it min • " } ?: "") + "$calories kcal",
                        color = MangoYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                onFavoriteClick?.let { action ->
                    Surface(Modifier.align(Alignment.TopStart).padding(4.dp), shape = CircleShape,
                        color = MutedSurface1.copy(alpha = 0.95f)) {
                        IconButton(onClick = action, modifier = Modifier.testTag("favorite_$title")) {
                            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (isFavorite) "Usuń z ulubionych: $title" else "Dodaj do ulubionych: $title",
                                tint = if (isFavorite) JuicyOrange else LeafGreen)
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    color = Cream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 34.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(category, color = LeafGreen, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Text("$calories kcal", color = OrangeInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(preparationMinutes?.let { "◷ $it min" } ?: "◷ Brak czasu", color = GoldAccent, fontSize = 11.sp)
                if (onEditClick != null || onDeleteClick != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        onEditClick?.let { action -> TextButton(onClick = action, contentPadding = PaddingValues(2.dp)) { Text("Zmień nazwę", fontSize = 11.sp) } }
                        onDeleteClick?.let { action -> IconButton(onClick = action) { Icon(Icons.Default.Delete, "Usuń przepis", tint = ErrorInk) } }
                    }
                }
            }
        }
    }
}

// 7. MangoSectionTitle
@Composable
fun MangoSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    showSparkle: Boolean = false
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = text,
            color = Cream,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
        if (showSparkle) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MangoYellow,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// 8. MangoProgressBar
@Composable
fun MangoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = JuicyOrange,
    trackColor: Color = WarmBrown
) {
    val animated = mangoFloat(progress.coerceIn(0f, 1f))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.70f), color, color.copy(alpha = 0.88f))
                    )
                )
        )
    }
}

// 9. MangoChip
@Composable
fun MangoChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .pressScaleEffect()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) JuicyOrange else MutedSurface1)
            .border(1.dp, if (selected) MangoYellow else WarmBrown.copy(alpha = 0.35f), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            fitMangoCategoryResource(text)?.let { resource ->
                FitMangoAssetIcon(resource, modifier = Modifier.size(18.dp))
            }
            Text(text = text, color = if (selected) MangoButtonInk else Cream,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
        }
    }
}

// 10. MangoIconButton
@Composable
fun MangoIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MangoYellow
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.pressScaleEffect()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}
