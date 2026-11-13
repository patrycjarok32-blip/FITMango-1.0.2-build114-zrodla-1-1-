package com.example

import org.junit.Assert.assertTrue
import org.junit.Test

/** Smoke test for the four independently registered home-screen widget providers. */
class MangoHomeWidgetTest {
    @Test fun allWidgetVariantsUseSharedProviderContract() {
        assertTrue(MangoHomeWidget() is android.appwidget.AppWidgetProvider)
        assertTrue(MangoMealsWidget() is MangoHomeWidget)
        assertTrue(MangoWorkoutWidget() is MangoHomeWidget)
        assertTrue(MangoWaterWidget() is MangoHomeWidget)
    }
}
