package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.viewmodel.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Stable
class QuantityReviewGuard {
    var message by mutableStateOf<String?>(null)
        private set
    private var pending: (() -> Unit)? = null
    fun run(product: SubstituteCandidate, quantity: Float, unit: String, previous: Float? = null, action: () -> Unit) {
        val warning = QuantityReviewSupport.warning(product, quantity, unit, previous)
        if (warning == null) action() else { pending = action; message = warning }
    }
    fun keep() { val action = pending; cancel(); action?.invoke() }
    fun cancel() { pending = null; message = null }
}

@Composable
fun QuantityReviewPrompt(guard: QuantityReviewGuard) {
    guard.message?.let { message -> AlertDialog(onDismissRequest = guard::cancel,
        title = { Text("Sprawdź ilość") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = guard::keep, modifier = Modifier.testTag("keep_reviewed_quantity")) { Text("Zostaw tę ilość") } },
        dismissButton = { TextButton(onClick = guard::cancel, modifier = Modifier.testTag("edit_reviewed_quantity")) { Text("Popraw") } }) }
}

@Composable
fun EnergyExplanationButton(viewModel: DietaViewModel, date: String, fromProfile: Boolean? = null) {
    var open by remember(date) { mutableStateOf(false) }
    val days by viewModel.habits.days.collectAsState()
    val energy by viewModel.healthConnect.weeklyEnergy.energy.collectAsState()
    val confirmations by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsState()
    val refreshing by viewModel.healthConnect.weeklyEnergyLoading.collectAsState()
    val readMessage by viewModel.healthConnect.weeklyEnergyMessage.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    val revision by viewModel.usability.revision.collectAsState()
    val energySettings = remember(revision) { runCatching { EnergyCalculatorSupport.read(context) }.getOrNull() }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("explain_energy_$date")) { Text("Wyjaśnij bilans") }
    if (open) {
        val useProfile = fromProfile ?: context.getSharedPreferences("FITMangoHabits", Context.MODE_PRIVATE).getBoolean("weeklyUseMaintenance", true)
        val day = days.find { it.date == date }
        val source = energy.find { it.date == date }
        val finished = LocalDate.parse(date) < LocalDate.now()
        val value = if (useProfile) WeeklyEnergySupport.maintenanceCalories(profile, energySettings) else source?.totalKcal
        AlertDialog(onDismissRequest = { open = false }, title = { Text("Bilans • $date") }, text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(EnergyExplanationSupport.describe(day?.takeIf { it.meals.isNotEmpty() }?.calories,
                    profile?.targetCalories ?: 0, value, !useProfile, finished,
                    day?.nutritionComplete ?: false, day?.let { WeeklyEnergySupport.isFoodConfirmed(it, confirmations.find { c -> c.date == date }) } ?: false,
                    if (useProfile) EnergyCalculatorSupport.base(profile, energySettings)?.explanation else null))
                if (!useProfile) {
                    TextButton(enabled = !refreshing, onClick = {
                        val selectedDay = LocalDate.parse(date)
                        viewModel.healthConnect.loadWeeklyEnergy(selectedDay, selectedDay)
                    }) { Text(if (refreshing) "Odświeżanie…" else "Odśwież dane tego dnia") }
                    readMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                if (!useProfile && source != null) Text("Ostatni odczyt: " + Instant.ofEpochMilli(source.syncedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm")))
                if (!useProfile && finished && source?.fullDayWindow != true) Text("Odczyt nie obejmuje pełnego przedziału dnia. Dzień nie kwalifikuje się do bilansu tygodnia.")
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text("Rozumiem") } })
    }
}

@Composable
fun ReleaseNotesControl(autoShow: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("FITMangoReleaseNotes", Context.MODE_PRIVATE) }
    val upgraded = remember(context) { runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    }.getOrDefault(false) }
    var open by remember { mutableStateOf(autoShow && (upgraded || prefs.contains("acknowledged")) &&
        ReleaseNotesSupport.shouldShow(BuildConfig.VERSION_CODE, prefs.getInt("acknowledged", 0))) }
    LaunchedEffect(autoShow) {
        if (autoShow && !open && !prefs.contains("acknowledged")) prefs.edit().putInt("acknowledged", BuildConfig.VERSION_CODE).apply()
    }
    fun close() { prefs.edit().putInt("acknowledged", BuildConfig.VERSION_CODE).apply(); open = false }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("release_notes_button")) {
        Text("Build ${BuildConfig.VERSION_CODE} • Co nowego?", style = MaterialTheme.typography.labelMedium)
    }
    if (open) AlertDialog(onDismissRequest = ::close, title = { Text("FITMango ${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReleaseNotesSupport.notes.forEach { Text("• $it") }
        } }, confirmButton = { TextButton(onClick = ::close) { Text("Gotowe") } })
}
