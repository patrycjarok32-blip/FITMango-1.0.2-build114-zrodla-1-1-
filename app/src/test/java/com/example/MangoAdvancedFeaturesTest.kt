package com.example

import com.example.viewmodel.*
import com.example.data.ShoppingItemEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MangoAdvancedFeaturesTest {
    @Test fun rewardPurchaseCannotOverspendOrDoubleCharge() {
        val empty = MangoRewardState()
        assertNull(MangoRewardShop.purchase(empty, "royal", 200))
        val cap = MangoRewardShop.purchase(empty, "cap", 150)!!
        assertEquals(50, MangoRewardShop.balance(150, cap))
        assertEquals(cap.purchased, MangoRewardShop.purchase(cap, "cap", 150)!!.purchased)
        assertNull(MangoRewardShop.purchase(cap, "sport", 150))
        assertEquals(cap, MangoRewardShop.decode(MangoRewardShop.encode(cap)))
    }

    @Test fun invalidRewardRestoreIsRejected() {
        try {
            MangoRewardShop.decode("""{"purchased":["unknown"],"equipped":"unknown"}""")
            fail("Expected validation")
        } catch (_: IllegalArgumentException) { }
    }

    private fun session(id: String, effort: Int, kg: Double, done: Boolean = true) =
        WorkoutSession(id, "2026-11-" + (id.length + 1).toString().padStart(2, '0'), "A", 100L, listOf(
            WorkoutEntry("Squat", 1, 8, kg, done),
            WorkoutEntry("Squat", 2, 8, kg, done)), 200L + id.length, effort)

    @Test fun overloadNeedsTwoEasyCompleteSessions() {
        val one = session("1", 5, 40.0)
        assertEquals(40.0, MangoProgressiveOverload.suggestions(listOf(one))
            .single().suggestedKg, 0.001)
        val two = session("22", 5, 40.0)
        assertEquals(42.5, MangoProgressiveOverload.suggestions(listOf(one, two))
            .single().suggestedKg, 0.001)
        val hard = session("333", 9, 40.0)
        assertEquals(40.0, MangoProgressiveOverload.suggestions(listOf(one, two, hard))
            .single().suggestedKg, 0.001)
    }

    @Test fun shoppingSumsCompatibleUnitsAndLeavesUnknownUntouched() {
        fun item(name: String, amount: String, checked: Boolean = false) =
            ShoppingItemEntity(itemName = name, category = "Dairy",
                amount = amount, isChecked = checked, weekStart = "2026-11-09")
        val totals = MangoShoppingTotals.totals(listOf(
            item("Mleko", "1 l"), item("mleko", "250 ml"),
            item("Mleko", "2 boxes"), item("Mleko", "100 ml", true)))
        assertEquals(1, totals.size)
        assertEquals(1250.0, totals.single().amount, 0.001)
        assertEquals("ml", totals.single().unit)
        assertEquals(2, totals.single().sourceCount)
    }
}
