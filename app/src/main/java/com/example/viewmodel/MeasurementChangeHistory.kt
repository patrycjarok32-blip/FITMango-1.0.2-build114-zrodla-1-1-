package com.example.viewmodel

import android.content.Context
import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class MeasurementChangeLabel(val id: String, val label: String, val time: Long)
object MeasurementChangeHistory {
    private val mutex = Mutex()
    private val actions = linkedMapOf<String, suspend () -> Unit>()
    private val _entries = MutableStateFlow<List<MeasurementChangeLabel>>(emptyList())
    val entries = _entries.asStateFlow()
    fun clear() { synchronized(actions) { actions.clear(); _entries.value = emptyList() } }
    fun record(label: String, restore: suspend () -> Unit) {
        val entry = MeasurementChangeLabel(UUID.randomUUID().toString(), label, System.currentTimeMillis())
        synchronized(actions) {
            actions[entry.id] = restore
            _entries.value = (listOf(entry) + _entries.value).take(10)
            actions.keys.retainAll(_entries.value.map { it.id }.toSet())
        }
    }
    suspend fun undo(id: String) = mutex.withLock {
        val restore = synchronized(actions) { actions[id] } ?: error("Ta zmiana nie jest już dostępna.")
        restore()
        synchronized(actions) { actions.remove(id); _entries.value = _entries.value.filterNot { it.id == id } }
    }
    data class Snapshot(val measurements: List<BodyMeasurementEntity>, val weights: List<WeightLogEntity>, val hidden: Set<String>)
    suspend fun snapshot(context: Context): Snapshot {
        val dao = AppDatabase.getDatabase(context).backupDao()
        return Snapshot(dao.read_measurements().sortedBy { it.id }, dao.read_weights().sortedBy { it.id }, MeasurementRemovalSupport.hidden(context))
    }
    fun recordBody(context: Context, label: String, before: Snapshot, after: Snapshot) {
        if (before == after) return
        val app = context.applicationContext
        record(label) {
            PersonalDataGate.mutex.withLock {
                val db = AppDatabase.getDatabase(app)
                require(snapshot(app) == after) { "Pomiary zmieniły się ponownie. Cofnij nowszą zmianę, aby zachować dane." }
                val dao = db.backupDao()
                try {
                    MeasurementRemovalSupport.write(app, before.hidden)
                    db.withTransaction {
                        dao.clear_measurements(); dao.clear_weights()
                        dao.write_measurements(before.measurements); dao.write_weights(before.weights)
                    }
                } catch (e: Exception) { MeasurementRemovalSupport.write(app, after.hidden); throw e }
            }
        }
    }
}
