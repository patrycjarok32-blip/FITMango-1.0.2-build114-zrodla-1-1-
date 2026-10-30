package com.example

import com.example.reminders.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderSupportTest {
    @Test fun quietHoursCrossMidnightAndRespectBoundary() {
        val s = ReminderSettings()
        assertTrue(ReminderSupport.quiet(LocalTime.parse("22:00"), s))
        assertTrue(ReminderSupport.quiet(LocalTime.parse("07:59"), s))
        assertFalse(ReminderSupport.quiet(LocalTime.parse("08:00"), s))
        assertFalse(ReminderSupport.quiet(LocalTime.parse("23:00"), s.copy(quietStart = "10:00", quietEnd = "10:00")))
    }
    @Test fun dueSlotsDoNotReplayOldRemindersAndChangeOnLocalDay() {
        val s = ReminderSettings(water = true, creatine = true, day = true)
        assertEquals(mapOf("water" to "2026-10-10:1"), ReminderSupport.due(s, LocalDateTime.parse("2026-10-10T11:15")))
        assertTrue(ReminderSupport.due(s, LocalDateTime.parse("2026-10-10T12:30")).isEmpty())
        assertTrue(ReminderSupport.due(s, LocalDateTime.parse("2026-10-10T23:00")).isEmpty())
        assertTrue(ReminderSupport.due(s, LocalDateTime.parse("2026-10-11T18:15")).containsKey("creatine"))
        assertEquals("2026-10-11:0", ReminderSupport.due(s, LocalDateTime.parse("2026-10-11T09:10"))["water"])
        assertTrue(ReminderSupport.due(ReminderSettings(), LocalDateTime.parse("2026-10-11T09:10")).isEmpty())
    }
    @Test fun settingsBackupKeepsChoicesAndRejectsInvalidClocks() {
        val s = ReminderSettings(water = true, waterInterval = 180, creatineTime = "19:30")
        assertEquals(s, ReminderSupport.decode(ReminderSupport.encode(s)))
        assertEquals(ReminderSettings(), ReminderSupport.decode(""))
        assertTrue(runCatching { ReminderSupport.validate(s.copy(waterTime = "25:00")) }.isFailure)
        assertTrue(runCatching { ReminderSupport.validate(s.copy(waterInterval = 1)) }.isFailure)
    }
}
