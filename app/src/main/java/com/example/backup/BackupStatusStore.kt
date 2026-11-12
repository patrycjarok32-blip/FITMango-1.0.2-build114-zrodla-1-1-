package com.example.backup

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackupStatus(val lastExport: Long = 0, val lastAutomatic: Long = 0, val changes: Int = 0, val automatic: Boolean = true)
class BackupStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("FITMangoBackupStatus", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()
    private fun read() = BackupStatus(prefs.getLong("export", 0), prefs.getLong("autoDate", 0), prefs.getInt("changes", 0), prefs.getBoolean("automatic", true))
    @Synchronized fun automatic(enabled: Boolean) { prefs.edit().putBoolean("automatic", enabled).apply(); _state.value = read() }
    @Synchronized fun fingerprint(value: Int): Boolean {
        val existing = prefs.contains("fingerprint")
        val changed = existing && prefs.getInt("fingerprint", 0) != value
        prefs.edit().putInt("fingerprint", value).apply()
        if (changed) prefs.edit().putBoolean("pending", true).putInt("changes", (state.value.changes + 1).coerceAtMost(100000)).apply()
        _state.value = read()
        return changed
    }
    @Synchronized fun exported() { prefs.edit().putLong("export", System.currentTimeMillis()).putInt("changes", 0).apply(); _state.value = read() }
    @Synchronized fun savedAutomatic() { prefs.edit().putLong("autoDate", System.currentTimeMillis()).putBoolean("pending", false).apply(); _state.value = read() }
    /** Read-only status summary; never treats a missing automatic backup as a success. */
    fun healthMessage(now: Long = System.currentTimeMillis()): String {
        val current = state.value
        if (!current.automatic) return "Automatyczna kopia jest wyłączona."
        if (current.lastAutomatic <= 0L) return "Brak potwierdzonej kopii automatycznej."
        if (pending()) return "Zmiany oczekują na zapis w kopii automatycznej."
        val age = (now - current.lastAutomatic).coerceAtLeast(0L)
        return if (age > 7L * 24 * 60 * 60 * 1000)
            "Ostatnia kopia ma ponad 7 dni. Sprawdź możliwość jej odtworzenia."
        else "Kopia automatyczna została zapisana. Warto okresowo sprawdzać przywracanie."
    }
    fun pending() = prefs.getBoolean("pending", false)
    fun date(value: Long): String = if (value == 0L) "jeszcze nie zapisano" else java.time.Instant.ofEpochMilli(value)
        .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}
