package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentProductsAndAutoScaleTest {
    private fun context(): Context = ApplicationProvider.getApplicationContext()
    @Test fun legacyDuplicatesMergeWithoutLosingPortionsOrFavoritesAndDifferentBarcodesRemainDistinct() {
        val context = context(); val prefs = context.getSharedPreferences("FITMangoUsability", 0)
        prefs.edit().clear().commit()
        val first = SubstituteCandidate(" SOURDOUGH ", brand = "Bakery", barcode = " 123 ", caloriesPer100g = 240f)
        val second = first.copy(name = "sourdough", brand = "bakery", barcode = "123")
        val local = second.copy(barcode = "")
        val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter<List<SubstituteCandidate>>(Types.newParameterizedType(List::class.java, SubstituteCandidate::class.java))
        prefs.edit().putString("recent", adapter.toJson(listOf(first, second, local))).putString("favorites", adapter.toJson(listOf(second, local)))
            .putString("last_portions", "{\" 123 \":{\"quantity\":100,\"unit\":\"g\"}}").commit()
        val store = UsabilityPreferences(context)
        assertEquals(1, store.recent().size); assertEquals(1, store.favorites().size)
        assertEquals(100f to "g", store.lastPortion(store.recent().single()))
        store.rememberProduct(local, 170f, "g")
        assertEquals(1, store.recent().size); assertEquals(170f to "g", store.lastPortion(first))
        assertTrue(store.isFavorite(local))
        val snapshot = UsabilityPreferences.export(context)
        prefs.edit().clear().commit(); UsabilityPreferences.restore(context, snapshot)
        assertEquals(170f to "g", UsabilityPreferences(context).lastPortion(first))
        val otherBarcode = second.copy(barcode = "999")
        assertEquals(2, ProductHistorySupport.unique(listOf(second, otherBarcode)).size)
        store.removeRecent(local); assertTrue(store.recent().isEmpty()); assertEquals(1, store.favorites().size)
    }
    @Test fun automaticScaleSyncMergesStableIdsAndRespectsThrottleAndSwitchWithoutFabricatingMetrics() = runBlocking {
        val context = context(); context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        var reads = 0
        val row = ScaleMeasurement("weight|com.renpho.health|record1", "2026-10-20", 1L, "com.renpho.health", mapOf("weight" to 61.0499923706055), "health")
        val provider: suspend () -> Pair<List<ScaleMeasurement>, List<String>> = { reads++; listOf(row) to emptyList() }
        assertNotNull(ScaleAutoSyncSupport.sync(context, now = 1_000_000L, read = provider))
        assertEquals(1, reads); assertEquals(1, ScaleMeasurementsSupport.read(context).size)
        assertNull(ScaleAutoSyncSupport.sync(context, now = 1_010_000L, read = provider)); assertEquals(1, reads)
        ScaleAutoSyncSupport.sync(context, now = 1_200_000L, read = provider)
        assertEquals(2, reads); assertEquals(1, ScaleMeasurementsSupport.read(context).size)
        assertEquals(setOf("weight"), ScaleMeasurementsSupport.read(context).single().values.keys)
        ScaleAutoSyncSupport.setEnabled(context, false)
        assertNull(ScaleAutoSyncSupport.sync(context, now = 1_400_000L, read = provider)); assertEquals(2, reads)
        ScaleAutoSyncSupport.sync(context, force = true, now = 1_400_000L, read = provider); assertEquals(3, reads)
        assertEquals("61,05", ScaleAutoSyncSupport.format(row.values.getValue("weight")))
        assertEquals("29", ScaleAutoSyncSupport.format(29.0))
    }
}
