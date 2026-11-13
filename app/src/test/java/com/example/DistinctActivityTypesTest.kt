package com.example

import com.example.viewmodel.ActivityRecoverySupport
import com.example.viewmodel.SportSession
import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctActivityTypesTest {
    @Test fun differentManualActivitiesWithGenericTypeAreNotMerged() {
        val a = SportSession("manual|cardio", "Cardio", "2026-11-12T10:00:00Z",
            "2026-11-12T10:30:00Z", "Wpis ręczny", activityType = 0)
        val b = a.copy(id = "manual|strength", title = "Wzmacnianie")
        assertEquals(2, ActivityRecoverySupport.unique(listOf(a, b)).size)
    }
    @Test fun exactManualDuplicateIsStillMerged() {
        val a = SportSession("manual|first", "Cardio", "2026-11-12T10:00:00Z",
            "2026-11-12T10:30:00Z", "Wpis ręczny", activityType = 0)
        assertEquals(1, ActivityRecoverySupport.unique(
            listOf(a, a.copy(id = "manual|second"))).size)
    }
}
