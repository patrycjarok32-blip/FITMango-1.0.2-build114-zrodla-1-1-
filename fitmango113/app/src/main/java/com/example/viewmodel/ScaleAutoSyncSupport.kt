package com.example.viewmodel

import android.content.Context
import com.example.data.PersonalDataGate
import com.example.health.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object ScaleAutoSyncSupport {
    private val mutex = Mutex()
    private fun prefs(context: Context) = context.getSharedPreferences("FITMangoUsability", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("scale_automatic", true)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("scale_automatic", value).apply() }
    fun lastSync(context: Context) = prefs(context).getString("scale_last_sync", "0")?.toLongOrNull() ?: 0L
    suspend fun sync(context: Context, force: Boolean = false, now: Long = System.currentTimeMillis(),
        read: suspend () -> Pair<List<ScaleMeasurement>, List<String>> = { HealthConnectManager(context).readScaleMeasurements() }): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!force && (!enabled(context) || now - lastSync(context) in 0 until 120_000L)) return@withLock null
            val (incoming, warnings) = read()
            PersonalDataGate.mutex.withLock {
                val current = ScaleMeasurementsSupport.read(context)
                ScaleMeasurementsSupport.write(context, ScaleMeasurementsSupport.merge(current, incoming))
                prefs(context).edit().putString("scale_last_sync", now.toString()).apply()
            }
            (if (incoming.isEmpty()) "Brak nowych dostępnych pomiarów w Health Connect."
             else "Pobrano ${incoming.size} rekordów. Ponowny odczyt nie tworzy kopii tych samych pomiarów.") +
                if (warnings.isEmpty()) "" else " " + warnings.joinToString(". ")
        }
    }
    fun format(value: Double): String = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')
}
