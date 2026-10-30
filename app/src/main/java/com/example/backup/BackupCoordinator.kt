package com.example.backup

import android.app.Application
import android.net.Uri
import com.example.data.AppDatabase
import com.example.viewmodel.HealthConnectCoordinator
import com.example.viewmodel.RecipePhotoQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackupCoordinator(
    application: Application, private val scope: CoroutineScope,
    private val usability: com.example.viewmodel.UsabilityPreferences,
    private val afterRestore: () -> Unit
) {
    private val database = AppDatabase.getDatabase(application)
    private val service = BackupService(application, database)
    val status = BackupStatusStore(application)
    private var automaticJob: Job? = null
    private val mutex = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _preview = MutableStateFlow<PreparedBackup?>(null)
    val preview = _preview.asStateFlow()
    private val _hasRecovery = MutableStateFlow(service.recoveryFile.isFile)
    val hasRecovery = _hasRecovery.asStateFlow()

    init {
        scope.launch(Dispatchers.Default) {
            val dao = database.dietaDao()
            val core = kotlinx.coroutines.flow.combine(dao.getAllRecipes(), dao.getAllMealPlans(), dao.getAllRecipeIngredients()) { recipes, meals, ingredients ->
                31 * (31 * recipes.hashCode() + meals.hashCode()) + ingredients.hashCode()
            }
            val media = kotlinx.coroutines.flow.combine(dao.getAllDocuments(), dao.getAllBodyMeasurements(), dao.getAllBodyPhotos()) { documents, measurements, photos ->
                31 * (31 * documents.hashCode() + measurements.hashCode()) + photos.hashCode()
            }
            kotlinx.coroutines.flow.combine(core, media, dao.getAllWeightLogs(), usability.revision) { coreHash, mediaHash, weights, _ ->
                31 * (31 * (31 * coreHash + mediaHash) + weights.hashCode()) + com.example.viewmodel.UsabilityPreferences.export(application).hashCode()
            }.collect { fingerprint ->
                val changed = status.fingerprint(fingerprint)
                if (status.state.value.automatic && (changed || status.pending() || status.state.value.lastAutomatic == 0L)) {
                    automaticJob?.cancel()
                    automaticJob = scope.launch {
                        delay(20_000)
                        while (_busy.value) delay(1000)
                        operation {
                            if (status.state.value.automatic) {
                                val missing = service.saveAutomatic()
                                status.savedAutomatic()
                                if (missing > 0) _message.value = "Kopia lokalna zapisana; niedostępne pliki: $missing."
                            }
                        }
                    }
                }
            }
        }
    }

    private fun operation(block: suspend () -> Unit): Job = scope.launch {
        if (!mutex.tryLock()) return@launch
        _busy.value = true; _message.value = null
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _message.value = "Nie udało się wykonać operacji: ${e.message ?: "spróbuj ponownie"}" }
        finally { _busy.value = false; _hasRecovery.value = service.recoveryFile.isFile; mutex.unlock() }
    }
    fun export(uri: Uri) = operation { _message.value = service.export(uri); status.exported() }
    fun saveAutomaticNow() = operation { service.saveAutomatic(); status.savedAutomatic(); _message.value = "Zapisano kopię na telefonie." }
    fun exportAutomatic(uri: Uri) = operation { service.exportAutomatic(uri); _message.value = "Zapisano plik kopii automatycznej." }
    fun prepareAutomatic() = prepare(Uri.fromFile(service.automaticFile))
    fun prepare(uri: Uri) = operation {
        val old = _preview.value; _preview.value = null
        withContext(Dispatchers.IO) { old?.dispose() }
        _preview.value = service.prepare(uri)
    }
    fun cancelPreview() { if (!_busy.value) { val old = _preview.value; _preview.value = null; scope.launch(Dispatchers.IO) { old?.dispose() } } }
    fun restore() = operation {
        val prepared = _preview.value ?: error("Najpierw wybierz kopię.")
        // Lock order matches sync -> database and photos -> database writers.
        com.example.data.PersonalDataGate.mutex.withLock {
            HealthConnectCoordinator.syncMutex.withLock {
                RecipePhotoQueue.mutex.withLock {
                    _message.value = service.restore(prepared)
                    afterRestore()
                }
            }
        }
        withContext(Dispatchers.IO) { prepared.dispose() }; _preview.value = null
    }
    fun exportRecovery(uri: Uri) = operation {
        service.exportRecovery(uri); _message.value = "Zapisano kopię danych sprzed przywrócenia."
    }
}
