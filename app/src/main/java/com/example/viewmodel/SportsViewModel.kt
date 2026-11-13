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
    private val _workouts = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val workouts = _workouts.asStateFlow()
    private val prefs = app.getSharedPreferences("FITMangoUsability", 0)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SportsSupport.KEY || key == null) load()
        if (key == "strength_workouts_v1" || key == null) loadWorkouts()
        if (key == "video_challenges_v1" || key == null) loadVideoChallenges()
    }
    init { prefs.registerOnSharedPreferenceChangeListener(listener); load(); loadWorkouts() }
    private fun load() { viewModelScope.launch(Dispatchers.IO) {
        try { _data.value = SportsSupport.read(getApplication<Application>()) }
        catch (_: Exception) { _message.value = "Nie można odczytać danych sportowych. Przywróć kopię danych; zapisane wpisy nie zostały usunięte." }
    } }
    private val _videoChallenges = MutableStateFlow<List<VideoChallenge>>(emptyList())
    val videoChallenges = _videoChallenges.asStateFlow()
    init { loadVideoChallenges() }
    private fun loadVideoChallenges() { viewModelScope.launch(Dispatchers.IO) {
        runCatching { VideoChallengeStore.read(getApplication<Application>()) }
            .onSuccess { _videoChallenges.value = it }
            .onFailure { _message.value = "Nie udało się odczytać wyzwań wideo." }
    } }
    private fun editVideoChallenges(change: (List<VideoChallenge>) -> List<VideoChallenge>, onSaved: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { PersonalDataGate.mutex.withLock {
                    val next = change(VideoChallengeStore.read(getApplication<Application>()))
                    VideoChallengeStore.write(getApplication<Application>(), next)
                    _videoChallenges.value = next
                } }
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Nie udało się zapisać wyzwania." }
            finally { _busy.value = false }
        }
    }
    fun addVideoChallenge(title: String, weeks: Int, done: () -> Unit = {}) =
        editVideoChallenges({ VideoChallengeStore.addChallenge(it, title, weeks) }, done)
    fun duplicateVideoChallenge(id: String, done: () -> Unit = {}) =
        editVideoChallenges({ VideoChallengeStore.duplicate(it, id) }, done)
    fun deleteVideoChallenge(id: String, done: () -> Unit = {}) =
        editVideoChallenges({ it.filterNot { c -> c.id == id } }, done)
    fun duplicateVideoWeek(id: String, from: Int, to: Int, done: () -> Unit = {}) =
        editVideoChallenges({ VideoChallengeStore.duplicateWeek(it, id, from, to) }, done)
    fun addVideoWorkout(id: String, day: Int, title: String, minutes: Int, category: String,
        url: String, done: () -> Unit = {}) = editVideoChallenges({ items ->
        items.map { c -> if (c.id != id) c else c.copy(workouts = c.workouts +
            VideoWorkout(java.util.UUID.randomUUID().toString(), day, title.trim(), minutes, category.trim(), url.trim())) }
    }, done)
    fun toggleVideoWorkout(challengeId: String, workoutId: String, done: () -> Unit = {}) =
        editVideoChallenges({ items -> items.map { c ->
            if (c.id != challengeId) c else c.copy(workouts = c.workouts.map { w ->
                if (w.id != workoutId) w else w.copy(completedAt = if (w.completedAt == 0L) System.currentTimeMillis() else 0)
            })
        } }, done)
    fun deleteVideoWorkout(challengeId: String, workoutId: String, done: () -> Unit = {}) =
        editVideoChallenges({ items -> items.map { c ->
            if (c.id != challengeId) c else c.copy(workouts = c.workouts.filterNot { it.id == workoutId })
        } }, done)
    private fun loadWorkouts() { viewModelScope.launch(Dispatchers.IO) {
        runCatching { WorkoutSessionStore.read(getApplication<Application>()) }
            .onSuccess { _workouts.value = it }
            .onFailure { _message.value = "Nie można odczytać zapisanej sesji treningowej." }
    } }
    private fun editWorkout(change: (List<WorkoutSession>) -> List<WorkoutSession>, onSaved: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { PersonalDataGate.mutex.withLock {
                    val next = change(WorkoutSessionStore.read(getApplication<Application>()))
                    WorkoutSessionStore.write(getApplication<Application>(), next)
                    _workouts.value = next
                } }
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Nie udało się zapisać sesji." }
            finally { _busy.value = false }
        }
    }
    fun startWorkout(plan: SportPlan, onSaved: () -> Unit = {}) = editWorkout({ sessions ->
        require(sessions.none { it.ended == 0L }) { "Najpierw dokończ rozpoczęty trening." }
        (sessions + WorkoutSessionStore.create(plan, plan.date)).takeLast(200)
    }, onSaved)
    fun replaceWorkoutExercise(id: String, oldName: String, newName: String, onSaved: () -> Unit = {}) =
        editWorkout({ sessions ->
            require(newName.isNotBlank() && newName.length <= 120)
            sessions.map { w ->
                if (w.id != id || w.ended != 0L) w else {
                    require(w.entries.any { it.exercise == oldName })
                    require(w.entries.none { it.exercise == oldName && it.done }) {
                        "Nie można zamienić ćwiczenia z ukończonymi seriami."
                    }
                    require(w.entries.none { it.exercise == newName }) {
                        "To ćwiczenie jest już w sesji."
                    }
                    w.copy(entries = w.entries.map { e ->
                        if (e.exercise == oldName) e.copy(exercise = newName) else e
                    })
                }
            }
        }, onSaved)
    fun updateWorkoutEntry(id: String, index: Int, reps: Int, kg: Double, done: Boolean, onSaved: () -> Unit = {}) =
        editWorkout({ sessions ->
            require(reps in 1..1000 && kg.isFinite() && kg in 0.0..2000.0)
            sessions.map { w ->
                if (w.id != id || w.ended != 0L) w else {
                    require(index in w.entries.indices)
                    w.copy(entries = w.entries.toMutableList().also { it[index] =
                        it[index].copy(reps = reps, kg = kg, done = done) })
                }
            }
        }, onSaved)
    fun setWorkoutPaused(id: String, paused: Boolean, onSaved: () -> Unit = {}) =
        editWorkout({ sessions ->
            val now = System.currentTimeMillis()
            sessions.map { session ->
                if (session.id != id || session.ended != 0L) session
                else if (paused) WorkoutSessionStore.pause(session, now)
                else WorkoutSessionStore.resume(session, now)
            }
        }, onSaved)
    fun discardWorkout(id: String, onSaved: () -> Unit = {}) =
        editWorkout({ sessions ->
            require(sessions.any { it.id == id && it.ended == 0L }) { "Brak aktywnego treningu." }
            sessions.filterNot { it.id == id && it.ended == 0L }
        }, onSaved)
    fun finishWorkout(id: String, effort: Int, onSaved: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { PersonalDataGate.mutex.withLock {
                    val context = getApplication<Application>()
                    val sessions = WorkoutSessionStore.read(context)
                    val w = sessions.firstOrNull { it.id == id && it.ended == 0L } ?: error("Brak aktywnego treningu.")
                    require(effort in 1..10)
                    val now = System.currentTimeMillis()
                    val sports = SportsSupport.read(context)
                    val added = w.entries.filter { it.done }.map { e ->
                        SportSet(java.util.UUID.randomUUID().toString(), w.date, e.exercise, e.reps, e.kg, now)
                    }
                    SportsSupport.write(context, sports.copy(sets = sports.sets + added))
                    val finished = sessions.map { if (it.id == id) WorkoutSessionStore.finish(it, now, effort) else it }
                    WorkoutSessionStore.write(context, finished)
                    _workouts.value = finished
                    _data.value = SportsSupport.read(context)
                } }
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Nie udało się zakończyć treningu." }
            finally { _busy.value = false }
        }
    }
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
        current.copy(plans = current.plans.filterNot { it.date == date && it.sessionId.isBlank() } + SportPlan(date, if (rest) "Odpoczynek" else title.trim(), rest))
    }, onSaved)
    /** Adds another independent planned activity; planning never writes a completed Health Connect record. */
    fun addPlannedActivity(date: String, title: String, category: String, minutes: Int?,
        distanceKm: Double?, description: String, onSaved: () -> Unit) = edit({ current ->
        java.time.LocalDate.parse(date)
        require(title.isNotBlank() && title.length <= 120)
        require(category in setOf("Siłowy", "Cardio", "Wzmacnianie", "Mobilność i stretching", "Trening z filmem", "Inna aktywność"))
        require(minutes == null || minutes in 1..1440)
        require(distanceKm == null || (distanceKm.isFinite() && distanceKm > 0 && distanceKm <= 1000))
        require(description.length <= 1000)
        val session = SportPlan(date, title.trim(), false, category = category,
            description = description.trim(), targetMinutes = minutes, targetDistanceKm = distanceKm,
            sessionId = java.util.UUID.randomUUID().toString())
        current.copy(plans = current.plans + session)
    }, onSaved)
    fun removePlannedActivity(sessionId: String, onSaved: () -> Unit) = edit({ current ->
        require(sessionId.isNotBlank())
        current.copy(plans = current.plans.filterNot { it.sessionId == sessionId })
    }, onSaved)
    fun saveSessionDetails(date: String, title: String, category: String, description: String,
        distanceKm: Double?, minutes: Int?, distanceRange: String, onSaved: () -> Unit) = edit({ current ->
        java.time.LocalDate.parse(date)
        require(title.isNotBlank() && title.length <= 120)
        require(category in setOf("Siłowy", "Cardio", "Wzmacnianie", "Mobilność i stretching", "Trening z filmem", "Inna aktywność"))
        require(description.length <= 1000 && distanceRange.length <= 80)
        require(distanceKm == null || (distanceKm.isFinite() && distanceKm > 0.0 && distanceKm <= 1000.0))
        require(minutes == null || minutes in 1..1440)
        val existing = current.plans.firstOrNull { it.date == date }
        val plan = (existing ?: SportPlan(date, title, false)).copy(
            title = title.trim(), rest = false, category = category, description = description.trim(),
            targetDistanceKm = distanceKm, targetMinutes = minutes, distanceRange = distanceRange.trim())
        current.copy(plans = current.plans.filterNot { it.date == date && it.sessionId.isBlank() } + plan)
    }, onSaved)
    /** Edit exactly one planned session; legacy sessions are identified by date. */
    fun updatePlannedActivity(sessionId: String, date: String, title: String, category: String,
        minutes: Int?, distanceKm: Double?, description: String, onSaved: () -> Unit) = edit({ current ->
        require(title.isNotBlank() && title.length <= 120 && description.length <= 1000)
        require(category in setOf("Siłowy", "Cardio", "Wzmacnianie", "Mobilność i stretching", "Trening z filmem", "Inna aktywność"))
        require(minutes == null || minutes in 1..1440)
        require(distanceKm == null || (distanceKm.isFinite() && distanceKm > 0 && distanceKm <= 1000))
        val index = current.plans.indexOfFirst { it.date == date && it.sessionId == sessionId }
        require(index >= 0) { "Nie znaleziono sesji." }
        current.copy(plans = current.plans.mapIndexed { i, plan ->
            if (i == index) plan.copy(title = title.trim(), category = category,
                description = description.trim(), targetMinutes = minutes, targetDistanceKm = distanceKm)
            else plan
        })
    }, onSaved)
    /** Move or copy exactly one session; existing sessions on target day are untouched. */
    fun transferPlannedActivity(sessionId: String, fromDate: String, toDate: String,
        copy: Boolean, onSaved: () -> Unit) = edit({ current ->
        java.time.LocalDate.parse(fromDate)
        java.time.LocalDate.parse(toDate)
        require(fromDate != toDate) { "Wybierz inny dzień." }
        val index = current.plans.indexOfFirst { it.date == fromDate && it.sessionId == sessionId }
        require(index >= 0) { "Nie znaleziono sesji do przeniesienia." }
        val source = current.plans[index]
        val transferred = source.copy(date = toDate,
            sessionId = if (copy) java.util.UUID.randomUUID().toString() else source.sessionId)
        current.copy(plans = if (copy) current.plans + transferred
            else current.plans.mapIndexed { i, p -> if (i == index) transferred else p })
    }, onSaved)
    /** Saves the complete generated week atomically, without deleting recorded sets or activities. */
    fun saveTrainingWeek(plans: List<SportPlan>, onSaved: () -> Unit) = edit({ current ->
        require(plans.isNotEmpty() && plans.size <= 7)
        require(plans.map { it.date }.distinct().size == plans.size)
        plans.forEach {
            java.time.LocalDate.parse(it.date)
            require(it.title.isNotBlank() && it.title.length <= 120)
        }
        val dates = plans.map { it.date }.toSet()
        current.copy(plans = current.plans.filterNot { it.date in dates && it.sessionId.isBlank() } + plans)
    }, onSaved)
    /** Move or swap complete planned sessions across weeks, keeping their exercises and all logged history. */
    fun movePlannedWorkout(from: String, to: String, swap: Boolean, onSaved: () -> Unit) = edit({ current ->
        val source = java.time.LocalDate.parse(from)
        val target = java.time.LocalDate.parse(to)
        require(source != target) { "Wybierz inny dzień." }
        val moving = current.plans.firstOrNull { it.date == from }
            ?: error("Nie ma planu w wybranym dniu.")
        val existing = current.plans.firstOrNull { it.date == to }
        require(swap || existing == null) {
            "Dzień docelowy jest zajęty. Wybierz zamianę treningów lub wolny dzień."
        }
        val unchanged = current.plans.filterNot { it.date == from || it.date == to }
        val moved = moving.copy(date = target.toString())
        val other = if (swap) existing?.copy(date = source.toString()) else null
        current.copy(plans = unchanged + moved + listOfNotNull(other))
    }, onSaved)
    /** Copy one planned week to 1–4 future weeks, refusing to overwrite existing plans. */
    fun copyWorkoutWeeks(start: String, count: Int, onSaved: () -> Unit) = edit({ current ->
        require(count in 1..4)
        val monday = SportsSupport.week(java.time.LocalDate.parse(start))
        val source = current.plans.filter { p ->
            val date = java.time.LocalDate.parse(p.date)
            !date.isBefore(monday) && date.isBefore(monday.plusDays(7))
        }
        require(source.isNotEmpty()) { "Brak planów w wybranym tygodniu." }
        val copies = (1..count).flatMap { offset ->
            source.map { it.copy(date = java.time.LocalDate.parse(it.date).plusWeeks(offset.toLong()).toString(), sessionId = java.util.UUID.randomUUID().toString()) }
        }
        val existing = current.plans.map { it.date }.toSet()
        require(copies.none { it.date in existing && current.plans.any { old -> old.date == it.date && old.sessionId.isBlank() } }) {
            "W kolejnych tygodniach są już plany. Zmień zakres lub przenieś je przed kopiowaniem."
        }
        current.copy(plans = current.plans + copies)
    }, onSaved)

    /** Shift upcoming planned sessions forward, never touching completed history. */
    fun shiftUpcomingWorkouts(from: String, days: Int, onSaved: () -> Unit) = edit({ current ->
        require(days in 1..7)
        val start = java.time.LocalDate.parse(from)
        val moving = current.plans.filter { !java.time.LocalDate.parse(it.date).isBefore(start) }
        require(moving.isNotEmpty()) { "Brak kolejnych treningów do przesunięcia." }
        val stationary = current.plans.filterNot { it in moving }
        val shifted = moving.map { it.copy(date = java.time.LocalDate.parse(it.date).plusDays(days.toLong()).toString()) }
        val fixedDates = stationary.map { it.date }.toSet()
        require(shifted.none { it.date in fixedDates }) { "Przesunięcie koliduje z wcześniejszym planem." }
        current.copy(plans = stationary + shifted)
    }, onSaved)

    fun updatePlannedExercises(date: String, exercises: List<PlannedExercise>, onSaved: () -> Unit) = edit({ current ->
        require(exercises.size <= 30)
        require(current.plans.any { it.date == date && !it.rest })
        current.copy(plans = current.plans.map { plan ->
            if (plan.date == date) plan.copy(exercises = exercises) else plan
        })
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
    fun copyActivity(id: String, targetDate: String, onSaved: () -> Unit) = edit({ current ->
        val source = current.history.firstOrNull { it.id == id }
            ?: error("Nie znaleziono aktywności do skopiowania.")
        val copied = ActivityRecoverySupport.copyManual(source, java.time.LocalDate.parse(targetDate),
            java.time.Instant.now())
        require(current.history.none { ActivityRecoverySupport.duplicate(it, copied) }) {
            "Podobna aktywność jest już zapisana w tych godzinach."
        }
        current.copy(history = current.history + copied)
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
