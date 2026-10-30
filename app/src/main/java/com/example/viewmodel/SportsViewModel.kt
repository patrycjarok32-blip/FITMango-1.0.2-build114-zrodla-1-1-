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

class SportsViewModel(app: Application) : AndroidViewModel(app) {
    private val _data = MutableStateFlow(SportsData())
    val data = _data.asStateFlow()
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val prefs = app.getSharedPreferences("FITMangoUsability", 0)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SportsSupport.KEY || key == null) load()
    }
    init { prefs.registerOnSharedPreferenceChangeListener(listener); load() }
    private fun load() { viewModelScope.launch(Dispatchers.IO) {
        try { _data.value = SportsSupport.read(getApplication<Application>()) }
        catch (_: Exception) { _message.value = "Nie można odczytać danych sportowych. Przywróć kopię danych; zapisane wpisy nie zostały usunięte." }
    } }
    private fun edit(change: (SportsData) -> SportsData, onSaved: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true; _message.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { PersonalDataGate.mutex.withLock {
                    val value = change(SportsSupport.read(getApplication<Application>()))
                    SportsSupport.write(getApplication<Application>(), value); _data.value = value
                } }
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Nie udało się zapisać treningu." }
            finally { _busy.value = false }
        }
    }
    fun addSet(date: String, name: String, reps: Int, kg: Double, onSaved: () -> Unit) = edit({ current ->
        require(java.time.LocalDate.parse(date) <= java.time.LocalDate.now()) { "Wykonaną serię zapisz na dziś lub wcześniejszy dzień." }
        val set = SportSet(java.util.UUID.randomUUID().toString(), date, name.trim(), reps, kg, System.currentTimeMillis())
        current.copy(sets = current.sets + set)
    }, onSaved)
    fun removeSet(id: String, onSaved: () -> Unit) = edit({ it.copy(sets = it.sets.filterNot { s -> s.id == id }) }, onSaved)
    fun plan(date: String, title: String, rest: Boolean, onSaved: () -> Unit) = edit({ current ->
        current.copy(plans = current.plans.filterNot { it.date == date } + SportPlan(date, if (rest) "Odpoczynek" else title.trim(), rest))
    }, onSaved)
    fun removePlan(date: String, onSaved: () -> Unit) = edit({ it.copy(plans = it.plans.filterNot { p -> p.date == date }) }, onSaved)
    fun goal(value: Int, onSaved: () -> Unit) = edit({ it.copy(goal = value) }, onSaved)
    fun setGoal(mode: String, target: Int, onSaved: () -> Unit) = edit({ current ->
        when (mode) {
            "days" -> current.copy(goalMode = mode, goal = target)
            "minutes" -> current.copy(goalMode = mode, minuteGoal = target)
            "walks" -> current.copy(goalMode = mode, walkGoal = target)
            else -> error("Nieprawidłowy rodzaj celu.")
        }
    }, onSaved)
    fun recovery(date: String, energy: Int, fatigue: Int, onSaved: () -> Unit) = edit({ current ->
        require(java.time.LocalDate.parse(date) <= java.time.LocalDate.now())
        current.copy(recovery = (current.recovery.filterNot { it.date == date } + RecoveryCheck(date, energy, fatigue)).sortedByDescending { it.date }.take(730))
    }, onSaved)
    fun removeRecovery(date: String, onSaved: () -> Unit) = edit({ it.copy(recovery = it.recovery.filterNot { r -> r.date == date }) }, onSaved)
    fun addActivity(date: String, time: String, minutes: Int, title: String, type: Int, onSaved: () -> Unit) = edit({ current ->
        val session = ActivityRecoverySupport.manualSession(date, time, minutes, title, type, java.time.Instant.now())
        require(current.history.none { ActivityRecoverySupport.duplicate(it, session) }) { "Podobna aktywność jest już zapisana w tych godzinach. Nie dodano powtórzenia." }
        current.copy(history = current.history + session)
    }, onSaved)
    fun removeActivity(id: String, onSaved: () -> Unit) = edit({ current ->
        current.copy(history = current.history.filterNot { it.id == id && ActivityRecoverySupport.manual(it) })
    }, onSaved)
    fun refreshIfGranted(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            if (_busy.value) return@launch
            try {
                val granted = withContext(Dispatchers.IO) {
                    val cached = SportsSupport.read(getApplication<Application>())
                    if (System.currentTimeMillis() - maxOf(cached.updated, cached.healthUpdated) < 15 * 60_000L) emptySet()
                    else HealthConnectManager(getApplication<Application>()).getGrantedPermissions()
                }
                if (listOf(androidx.health.connect.client.records.ExerciseSessionRecord::class,
                        androidx.health.connect.client.records.StepsRecord::class, androidx.health.connect.client.records.SleepSessionRecord::class)
                        .any { androidx.health.connect.client.permission.HealthPermission.getReadPermission(it) in granted }) refresh(onSaved)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Manual refresh reports errors. */ }
        }
    }
    fun refresh(onSaved: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true; _message.value = null
        viewModelScope.launch {
            try {
                val manager = HealthConnectManager(getApplication<Application>())
                val failures = mutableListOf<String>()
                suspend fun <T> read(block: suspend () -> T): T? = try { withContext(Dispatchers.IO) { block() } }
                    catch (_: TimeoutCancellationException) { failures.add("Odczyt trwał zbyt długo; zachowano poprzednie dane."); null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failures.add(e.message ?: "Odczyt nie powiódł się; zachowano poprzednie dane."); null }
                val sessions = read { manager.readWorkoutHistory() }
                val days = read { manager.readActivityDays() }
                withContext(Dispatchers.IO) { PersonalDataGate.mutex.withLock {
                    val current = SportsSupport.read(getApplication<Application>())
                    val now = System.currentTimeMillis()
                    val value = ActivityRecoverySupport.mergeImported(current, sessions, days, now, failures.distinct().joinToString("\n"))
                    SportsSupport.write(getApplication<Application>(), value); _data.value = value
                } }
                _message.value = if (failures.isEmpty()) "Aktywność i sen odświeżone. Brak odczytu nie oznacza braku aktywności." else failures.distinct().joinToString("\n")
                onSaved()
            } catch (_: TimeoutCancellationException) { _message.value = "Odczyt treningów trwał zbyt długo. Zachowano historię; spróbuj ponownie." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Nie udało się odczytać treningów. Zachowano poprzednią historię." }
            finally { _busy.value = false }
        }
    }
    override fun onCleared() { prefs.unregisterOnSharedPreferenceChangeListener(listener); super.onCleared() }
}
