package com.example

import com.example.data.ShoppingItemEntity
import com.example.viewmodel.ShoppingMotionSupport
import org.junit.Assert.*
import org.junit.Test

class ShoppingMotionStagesTest {
    private val a = ShoppingItemEntity(id = 1, itemName = "Jabłko", category = "Warzywa i owoce", amount = "100 g")
    private val b = a.copy(id = 2, itemName = "Banan")

    @Test fun checkedProductIsStruckAtItsOldPositionBeforeMovingAndUndoRestoresOrder() {
        val checked = a.copy(isChecked = true)
        val stages = ShoppingMotionSupport.stages(listOf(a, b), listOf(b, checked), true, false)
        assertTrue(stages.showStrikeFirst)
        assertEquals(listOf(1L, 2L), stages.beforeMove.map { it.id })
        assertTrue(stages.beforeMove.first().isChecked)
        assertEquals(listOf(2L, 1L), stages.afterMove.map { it.id })
        assertTrue(stages.afterMove.last().isChecked)
        val undo = ShoppingMotionSupport.stages(stages.afterMove, listOf(a, b), true, false)
        assertFalse(undo.showStrikeFirst)
        assertEquals(listOf(a, b), undo.beforeMove)
        assertEquals(listOf(a, b), undo.afterMove)
    }

    @Test fun collapsedOwnedSectionKeepsStrikeStageThenHidesTheRow() {
        val checked = a.copy(isChecked = true)
        val stages = ShoppingMotionSupport.stages(listOf(a, b), listOf(b, checked), false, false)
        assertEquals(listOf(checked, b), stages.beforeMove)
        assertEquals(listOf(b), stages.afterMove)
        val reduced = ShoppingMotionSupport.stages(listOf(a, b), listOf(b, checked), false, true)
        assertFalse(reduced.showStrikeFirst)
        assertEquals(listOf(b), reduced.beforeMove)
        assertEquals(listOf(b), reduced.afterMove)
    }

    @Test fun changedValuesAndDeletionsAreCurrentWhileOrderIsHeld() {
        val changed = a.copy(isChecked = true, itemName = "Jabłko czerwone", amount = "250 g")
        val stages = ShoppingMotionSupport.stages(listOf(a, b), listOf(b, changed), true, false)
        assertEquals(changed, stages.beforeMove.first())
        val newer = changed.copy(amount = "300 g")
        assertEquals(listOf(newer), ShoppingMotionSupport.project(stages.beforeMove, listOf(newer)))
        assertTrue(ShoppingMotionSupport.project(stages.beforeMove, emptyList()).isEmpty())
    }
}
