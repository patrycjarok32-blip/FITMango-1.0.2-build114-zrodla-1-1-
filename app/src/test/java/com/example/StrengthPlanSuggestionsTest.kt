package com.example.ui.components

import org.junit.Assert.*
import org.junit.Test

class StrengthPlanSuggestionsTest {
    @Test fun twoDaysLeavesFiveRestDays() {
        val plan = StrengthPlanSuggestions.generate("muscle", 2, "beginner", "gym")
        assertEquals(7, plan.size)
        assertEquals(2, plan.count { it != "Odpoczynek" })
    }

    @Test fun fiveDaysLeavesTwoRestDays() {
        val plan = StrengthPlanSuggestions.generate("glutes", 5, "advanced", "home")
        assertEquals(5, plan.count { it != "Odpoczynek" })
        assertTrue(plan.any { it.contains("pośladki", ignoreCase = true) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedDays() {
        StrengthPlanSuggestions.generate("strength", 7, "beginner", "gym")
    }
}
