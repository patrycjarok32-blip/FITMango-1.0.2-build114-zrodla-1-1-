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
            .shadow(if (enabled) 1.dp else 0.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (enabled) listOf(RipeYellow, MangoPeach, JuicyOrange)
                        else listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
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
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
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
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                        color = if (isCompleted) MangoMint else MangoCoral
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
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = recipeTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
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

// 6. Compact recipe card: photo, name, macros, and contextual actions.
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
    preparationMinutes: Int? = null,
    protein: Int? = null,
    onAddToPlanClick: (() -> Unit)? = null,
    photoNeedsReview: Boolean = false,
    onReviewPhotoClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth().pressScaleEffect().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                RecipePhoto(
                    imageUrl = imageUrl,
                    title = title,
                    modifier = Modifier.fillMaxWidth().height(176.dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    onChoosePhoto = onPhotoClick
                )
                onFavoriteClick?.let { action ->
                    Surface(Modifier.align(Alignment.TopStart).padding(6.dp), shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                        IconButton(onClick = action, modifier = Modifier.size(40.dp).testTag("favorite_$title")) {
                            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (isFavorite) "Usuń z ulubionych: $title" else "Dodaj do ulubionych: $title",
                                tint = if (isFavorite) JuicyOrange else LeafGreen)
                        }
                    }
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(6.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                    IconButton(onClick = onPhotoClick, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.CameraAlt, "Sprawdź lub zmień zdjęcie: $title",
                            tint = MangoCoral, modifier = Modifier.size(20.dp))
                    }
                }
                if (photoNeedsReview) {
                    Surface(Modifier.align(Alignment.BottomStart).padding(6.dp),
                        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .95f)) {
                        Text("Sprawdź zdjęcie", fontSize = 10.sp, color = OrangeInk,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(category, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Text((if (calories > 0) "$calories kcal" else "Kalorie nieznane") + (protein?.let { " • B: ${it} g" } ?: ""),
                    color = OrangeInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(preparationMinutes?.let { "$it min" } ?: "Czas niepodany",
                    color = GoldAccent, fontSize = 11.sp)
                onReviewPhotoClick?.let { action ->
                    TextButton(onClick = action, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(2.dp)) {
                        Text("Sprawdź zdjęcie AI", fontSize = 11.sp)
                    }
                }
                onAddToPlanClick?.let { action ->
                    OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                        Text("+ Do jadłospisu", fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (onEditClick != null || onDeleteClick != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        onEditClick?.let { action ->
                            TextButton(onClick = action, contentPadding = PaddingValues(horizontal = 2.dp)) {
                                Text("Zmień nazwę", fontSize = 10.sp)
                            }
                        }
                        onDeleteClick?.let { action ->
                            IconButton(onClick = action, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Delete, "Usuń przepis", tint = ErrorInk)
                            }
                        }
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
            .clip(RoundedCornerShape(18.dp))
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
            .clip(RoundedCornerShape(18.dp))
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
