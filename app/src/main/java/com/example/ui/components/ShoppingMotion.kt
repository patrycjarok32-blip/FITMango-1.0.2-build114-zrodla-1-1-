package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.example.data.ShoppingItemEntity
import com.example.viewmodel.ShoppingMotionSupport

/** UI-only staging. The repository has already saved the real checked state. */
@Composable
fun rememberShoppingRows(items: List<ShoppingItemEntity>, showOwned: Boolean, scope: String): List<ShoppingItemEntity> {
    val rendered = remember(scope, showOwned) { items.filter { showOwned || !it.isChecked }.toMutableStateList() }
    val reduced = LocalReducedMangoMotion.current
    LaunchedEffect(items, showOwned, scope, reduced) {
        val stage = ShoppingMotionSupport.stages(rendered.toList(), items, showOwned, reduced)
        rendered.clear(); rendered.addAll(stage.beforeMove)
        if (stage.showStrikeFirst) {
            Animatable(0f).animateTo(1f, tween(ShoppingMotionSupport.STRIKE_MILLIS))
        }
        rendered.clear(); rendered.addAll(stage.afterMove)
    }
    // Animate only order. Values and deletions always reflect the latest source.
    return ShoppingMotionSupport.project(rendered.toList(), items)
}
