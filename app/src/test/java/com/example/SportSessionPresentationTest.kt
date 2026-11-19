package com.example

import com.example.viewmodel.SportSession
import com.example.viewmodel.SportSessionPresentation
import org.junit.Assert.*
import org.junit.Test

class SportSessionPresentationTest {
    private fun session(title: String, type: Int? = null) =
        SportSession("watch|1", title, "2026-11-19T12:00:00Z", "2026-11-19T12:27:00Z", "com.zepp", activityType = type)

    @Test fun retainsSpecificWatchWorkoutName() {
        assertEquals("Intensywny trening interwałowy – Zepp trener",
            SportSessionPresentation.label(session("Intensywny trening interwałowy – Zepp trener")))
    }

    @Test fun unknownTypeIsVisibleForTroubleshooting() {
        assertEquals("Trening (typ HC: 98765)",
            SportSessionPresentation.label(session("Trening", 98765)))
    }

    @Test fun missingTypeIsNotMislabelledAsHiit() {
        assertEquals("Trening (typ HC: brak)",
            SportSessionPresentation.label(session("Trening")))
    }
}
