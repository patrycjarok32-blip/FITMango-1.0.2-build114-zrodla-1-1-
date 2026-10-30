package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DailyConvenienceTest {
    @Test fun lastPortionSurvivesRestartAndBackupWithoutInventingLegacyQuantities() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        val product = SubstituteCandidate("Jogurt", barcode = "123", caloriesPer100g = 70f)
        val store = UsabilityPreferences(context)
        store.rememberProduct(product)
        assertNull(store.lastPortion(product))
        store.toggleFavorite(product)
        store.rememberProduct(product, 175f, "g")
        val restarted = UsabilityPreferences(context)
        assertEquals(175f, restarted.lastPortion(product)!!.first, 0f)
        assertEquals("g", restarted.lastPortion(product)!!.second)
        assertEquals(1, restarted.recent().size)
        val backup = UsabilityPreferences.export(context)
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        UsabilityPreferences.restore(context, backup)
        assertEquals(175f, UsabilityPreferences(context).lastPortion(product)!!.first, 0f)
        assertTrue(UsabilityPreferences(context).isFavorite(product))
    }
    @Test fun quantitiesStayWithTheirProductAndUnit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        val store = UsabilityPreferences(context)
        val milk = SubstituteCandidate("Mleko", barcode = "1")
        val nuts = SubstituteCandidate("Orzechy", barcode = "2")
        store.rememberProduct(milk, 200f, "ml")
        store.rememberProduct(nuts, 15f, "g")
        store.rememberProduct(milk)
        assertEquals(200f to "ml", store.lastPortion(milk))
        assertEquals(15f to "g", store.lastPortion(nuts))
        store.rememberProduct(nuts, Float.NaN, "g")
        assertEquals(15f to "g", store.lastPortion(nuts))
    }
    @Test fun balanceUsesExpenditureRatherThanFoodTargetAndMissingIsNotZero() {
        val estimate = EnergyExplanationSupport.describe(1675, 1800, 1990.0, false, true, true, true)
        assertTrue(estimate.contains("Deficyt: 315 kcal"))
        val rounded = EnergyExplanationSupport.describe(1675, 1800, 1543.5, true, true, true, true)
        assertTrue(rounded.contains("1544 − 1675 = -131 kcal"))
        assertTrue(rounded.contains("Nadwyżka: 131 kcal"))
        val watch = EnergyExplanationSupport.describe(1675, 1800, 1543.0, true, true, false, true)
        assertTrue(watch.contains("Nadwyżka: 132 kcal"))
        assertTrue(watch.contains("przybliżone"))
        val missing = EnergyExplanationSupport.describe(null, 1800, 1990.0, false, true, true, false)
        assertFalse(missing.contains("Deficyt:"))
        assertTrue(missing.contains("brak wpisów"))
        assertTrue(EnergyExplanationSupport.describe(0, 1800, 1990.0, false, true, true, true).contains("Deficyt: 1990 kcal"))
        assertTrue(EnergyExplanationSupport.describe(500, 1800, 800.0, true, false, true, false).contains("do tej chwili"))
    }
    @Test fun quantityWarningsCheckUnitsAndDecimalMistakesWithoutTreatingEveryFoodAsSuspicious() {
        val butter = SubstituteCandidate("Masło orzechowe crunchy")
        assertNotNull(QuantityReviewSupport.warning(butter, 100f, "g"))
        assertNotNull(QuantityReviewSupport.warning(butter, .1f, "kg"))
        assertNull(QuantityReviewSupport.warning(butter, 15f, "g"))
        assertNull(QuantityReviewSupport.warning(SubstituteCandidate("Woda"), 500f, "ml"))
        assertNotNull(QuantityReviewSupport.warning(SubstituteCandidate("Jogurt"), 500f, "g", 50f))
        assertNull(QuantityReviewSupport.warning(SubstituteCandidate("Jogurt"), 50f, "g", 50f))
    }
    @Test fun releaseNotesBelongToInstalledBuildAndAreNotShownAgainAfterAcknowledgment() {
        assertEquals(BuildConfig.VERSION_CODE, ReleaseNotesSupport.BUILD)
        val current = BuildConfig.VERSION_CODE
        assertTrue(ReleaseNotesSupport.shouldShow(current, current - 1))
        assertFalse(ReleaseNotesSupport.shouldShow(current, current))
        assertFalse(ReleaseNotesSupport.shouldShow(current - 1, current - 2))
        assertTrue(ReleaseNotesSupport.notes.isNotEmpty() && ReleaseNotesSupport.notes.all { it.isNotBlank() })
    }
}
