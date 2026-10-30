package com.example.viewmodel

import com.example.data.ShoppingItemEntity

/** Pure presentation stages; no repository writes, clocks or Android dependencies. */
data class ShoppingMotionStages(
    val beforeMove: List<ShoppingItemEntity>,
    val afterMove: List<ShoppingItemEntity>,
    val showStrikeFirst: Boolean
)

object ShoppingMotionSupport {
    const val STRIKE_MILLIS = 160

    fun project(order: List<ShoppingItemEntity>, current: List<ShoppingItemEntity>): List<ShoppingItemEntity> {
        val byId = current.associateBy { it.id }
        return order.mapNotNull { byId[it.id] }
    }

    fun stages(previous: List<ShoppingItemEntity>, current: List<ShoppingItemEntity>,
        showOwned: Boolean, reduced: Boolean): ShoppingMotionStages {
        val byId = current.associateBy { it.id }
        val checkedNow = previous.any { !it.isChecked && byId[it.id]?.isChecked == true }
        val final = current.filter { showOwned || !it.isChecked }
        val strikeFirst = checkedNow && !reduced
        return ShoppingMotionStages(if (strikeFirst) project(previous, current) else final, final, strikeFirst)
    }
}
