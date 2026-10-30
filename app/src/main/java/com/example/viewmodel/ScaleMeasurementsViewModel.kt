package com.example.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PersonalDataGate
import com.example.health.HealthConnectManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

class ScaleMeasurementsViewModel(app: Application) : AndroidViewModel(app) {
    private val _rows = MutableStateFlow<List<ScaleMeasurement>>(emptyList()); val rows = _rows.asStateFlow()
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val prefs = app.getSharedPreferences("FITMangoUsability", 0)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == ScaleMeasurementsSupport.KEY || key == null) load() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener); load() }
    private fun load() { viewModelScope.launch(Dispatchers.IO) {
        runCatching { ScaleMeasurementsSupport.read(getApplication<Application>()) }.onSuccess { _rows.value = it }
            .onFailure { _message.value = "Nie można odczytać pomiarów wagi. Zachowano dane; sprawdź kopię zapasową." }
    } }
    fun save(date: String, values: Map<String, String>, source: String = "Renpho — wpis ręczny", onDone: (String?) -> Unit) = change(onDone) { old ->
        ScaleMeasurementsSupport.merge(old, listOf(ScaleMeasurementsSupport.manual(date, values).copy(source = source))) to "Zapisano pomiar Renpho."
    }
    fun remove(id: String, onDone: (String?) -> Unit) = change(onDone) { old -> old.filterNot { it.id == id } to "Usunięto pomiar." }
    fun sync(onDone: (String?) -> Unit) { importMeasurements(true, onDone) }
    fun syncAutomatically() { importMeasurements(false) {} }
    private fun importMeasurements(force: Boolean, onDone: (String?) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                ScaleAutoSyncSupport.sync(getApplication<Application>(), force)?.let { _message.value = it }
                _rows.value = withContext(Dispatchers.IO) { ScaleMeasurementsSupport.read(getApplication<Application>()) }
                onDone(null)
            } catch (_: TimeoutCancellationException) {
                _message.value = "Odczyt pomiarów trwał zbyt długo. Zachowano poprzednie dane; spróbuj odświeżyć później."
                onDone(_message.value)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Nie udało się odczytać Health Connect. Zachowano poprzednie pomiary."; onDone(_message.value) }
            finally { _busy.value = false }
        }
    }
    private fun change(onDone: (String?) -> Unit, block: suspend (List<ScaleMeasurement>) -> Pair<List<ScaleMeasurement>, String>) {
        if (_busy.value) { onDone("Trwa zapis lub odczyt pomiarów."); return }
        _busy.value = true; _message.value = null
        viewModelScope.launch {
            try {
                // Network/provider read does not hold the global data gate.
                val old = withContext(Dispatchers.IO) { ScaleMeasurementsSupport.read(getApplication<Application>()) }
                val (next, text) = withContext(Dispatchers.IO) { block(old) }
                withContext(Dispatchers.IO) { PersonalDataGate.mutex.withLock {
                    val current = ScaleMeasurementsSupport.read(getApplication<Application>())
                    require(current == old) { "Pomiary zmieniły się podczas odczytu. Spróbuj ponownie." }
                    ScaleMeasurementsSupport.write(getApplication<Application>(), next); _rows.value = next
                    if (old != next) {
                        val context = getApplication<Application>()
                        MeasurementChangeHistory.record(text) {
                            PersonalDataGate.mutex.withLock {
                                require(ScaleMeasurementsSupport.read(context) == next) { "Pomiary Renpho zmieniły się ponownie. Cofnij nowszą zmianę." }
                                ScaleMeasurementsSupport.write(context, old)
                            }
                        }
                    }
                } }
                _message.value = text; onDone(null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { val text = e.message ?: "Nie udało się zapisać pomiarów. Poprzednie dane pozostają."; _message.value = text; onDone(text) }
            finally { _busy.value = false }
        }
    }
    override fun onCleared() { prefs.unregisterOnSharedPreferenceChangeListener(listener); super.onCleared() }
}
