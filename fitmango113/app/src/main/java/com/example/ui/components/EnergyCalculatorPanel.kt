package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.UserProfileEntity
import com.example.viewmodel.*
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun EnergyChoice(label: String, value: String, choices: Map<String, String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${choices[value] ?: value}") }
        DropdownMenu(open, { open = false }) { choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onChange(key) }) } }
    }
}

@Composable
fun EnergyCalculatorPanel(viewModel: DietaViewModel, draft: UserProfileEntity, onEffects: (() -> Unit)? = null) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val revision by viewModel.usability.revision.collectAsStateWithLifecycle()
    val stored = remember(revision) { runCatching { EnergyCalculatorSupport.read(context) }.getOrNull() }
    var settings by remember(stored) { mutableStateOf(stored ?: EnergyProfileSettings()) }
    var steps by remember(stored) { mutableStateOf(stored?.averageSteps?.toString().orEmpty()) }
    var minutes by remember(stored) { mutableStateOf((stored?.minutes ?: 45).toString()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<Triple<UserProfileEntity, EnergyProfileSettings, UserProfileEntity?>?>(null) }
    val input = settings.copy(averageSteps = if (steps.isBlank()) null else steps.toIntOrNull() ?: -1, minutes = minutes.toIntOrNull() ?: -1)
    val result = remember(draft, input) { runCatching { EnergyCalculatorSupport.plan(draft, input) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { Checkbox(settings.detailed, { settings = settings.copy(detailed = it) }); Text("Dokładniejszy opis aktywności", Modifier.weight(1f).padding(top = 12.dp)) }
        if (settings.detailed) {
            EnergyChoice("Codzienny ruch / praca", settings.work, EnergyCalculatorSupport.works) { settings = settings.copy(work = it) }
            OutlinedTextField(steps, { steps = it.take(5) }, label = { Text("Średnie kroki z typowego tygodnia") },
                supportingText = { Text("Opcjonalnie. Nie wpisuj pojedynczego niepełnego dnia z zegarka.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("energy_steps"))
            EnergyChoice("Typowy trening", settings.trainingType, EnergyCalculatorSupport.types) { settings = settings.copy(trainingType = it) }
            Row {
                Text("Treningi w tygodniu: ${settings.sessions}", Modifier.weight(1f).padding(top = 12.dp))
                TextButton(enabled = settings.sessions > 0, onClick = { settings = settings.copy(sessions = settings.sessions - 1) }) { Text("−") }
                TextButton(enabled = settings.sessions < 14, onClick = { settings = settings.copy(sessions = settings.sessions + 1) }) { Text("+") }
            }
            if (settings.sessions > 0) {
                OutlinedTextField(minutes, { minutes = it.take(3) }, label = { Text("Średni czas treningu (minuty)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("energy_minutes"))
                EnergyChoice("Intensywność", settings.intensity, EnergyCalculatorSupport.intensities) { settings = settings.copy(intensity = it) }
                if (settings.trainingType in listOf("walkrun", "mixed")) Row {
                    Checkbox(settings.trainingStepsIncluded, { settings = settings.copy(trainingStepsIncluded = it) })
                    Text("Podane kroki obejmują te treningi", Modifier.weight(1f).padding(top = 12.dp))
                }
            }
            Text("Praca i kroki opisują ten sam codzienny ruch. Nie sumujemy ich jako oddzielnych spalonych kalorii. Dobór PAL jest orientacyjny, nie pomiarem.", style = MaterialTheme.typography.bodySmall)
        }
        if (draft.goal == "Redukcja") EnergyChoice("Deficyt", settings.deficitPercent.toString(), listOf(5, 10, 15, 20).associate { it.toString() to "$it% zapotrzebowania" }) { settings = settings.copy(deficitPercent = it.toInt()) }
        if (draft.goal == "Budowanie masy") EnergyChoice("Nadwyżka", settings.surplusPercent.toString(), listOf(2, 3, 5, 8, 10).associate { it.toString() to "$it% zapotrzebowania" }) { settings = settings.copy(surplusPercent = it.toInt()) }
        if (draft.goal == "Utrzymanie") Text("Utrzymanie: cel jedzenia równy szacowanemu zapotrzebowaniu. Przedział wagi ustawisz w Efektach i analizie.")
        EnergyChoice("Białko", settings.proteinPerKg.toString(), listOf(1.2, 1.6, 1.8, 2.0).associate { it.toString() to "$it g/kg (z limitem 30% kalorii)" }) { settings = settings.copy(proteinPerKg = it.toDouble()) }
        Text("Propozycja makro: białko według wyboru, tłuszcze około 25% kalorii, reszta węglowodany. Własne makro możesz nadal ustawić osobno.", style = MaterialTheme.typography.bodySmall)
        Row { Checkbox(settings.individualPlan, { settings = settings.copy(individualPlan = it) }); Text("Potrzebuję indywidualnego planu, np. przy ciąży, karmieniu lub zaleceniach medycznych", Modifier.weight(1f).padding(top = 12.dp)) }
        val plan = result.getOrNull()
        if (plan != null) {
            EnergyPlanPreview(plan)
            Text(plan.base.explanation, style = MaterialTheme.typography.bodySmall)
            Text(if (settings.detailed && input.averageSteps == null) "Pewność: szacunek z profilu, brak średniej kroków. Zweryfikuj trendem kilku tygodni." else "Pewność: szacunek z profilu. Więcej pól nie oznacza dokładnego pomiaru metabolizmu.", style = MaterialTheme.typography.bodySmall)
        } else if (settings.individualPlan) Text("Tryb indywidualny: kalkulator nie ustala kalorii. Możesz zapisać ten tryb i zachować dotychczasowe cele.")
        else Text(result.exceptionOrNull()?.message ?: "Uzupełnij profil.", color = MaterialTheme.colorScheme.error)
        if (settings.individualPlan) Button(enabled = !busy && runCatching { EnergyCalculatorSupport.validate(input) }.isSuccess, onClick = {
            busy = true
            viewModel.saveIndividualEnergyMode(input) { error -> busy = false; message = error ?: "Zapisano tryb indywidualny. Cele jedzenia pozostają bez zmian." }
        }) { Text("Zapisz tryb indywidualny bez zmiany celów") }
        else Button(enabled = !busy && plan != null, onClick = { review = Triple(draft, input, profile) }, modifier = Modifier.fillMaxWidth().testTag("review_energy_profile")) { Text("Sprawdź i zatwierdź wynik") }
        message?.let { Text(it) }
        profile?.let { Text("Zapisany cel jedzenia: ${it.targetCalories} kcal • B ${it.targetProtein} g • W ${it.targetCarbs} g • T ${it.targetFat} g.", style = MaterialTheme.typography.bodySmall) }
        ProfileEnergyCalibration(viewModel, onEffects)
    }
    review?.let { snapshot ->
        val proposal = remember(snapshot) { EnergyCalculatorSupport.plan(snapshot.first, snapshot.second) }
        AlertDialog(onDismissRequest = { if (!busy) review = null }, title = { Text("Zatwierdzić nowy cel?") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cel: ${snapshot.first.goal}. Zapis zmieni kalorie i makro w całej aplikacji.")
                Text("Kalorie: ${snapshot.third?.targetCalories ?: "brak"} → ${proposal.calories} kcal")
                EnergyPlanPreview(proposal)
                Text("Po zmianie zaczynamy nowy okres obserwacji. Korekta z trendu nie zostanie zastosowana automatycznie.")
                message?.let { Text(it) }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; message = null
            viewModel.saveCalculatedEnergyProfile(snapshot.first, snapshot.second, snapshot.third) { error ->
                busy = false; message = error ?: "Zapisano profil i nowy cel."; if (error == null) review = null
            }
        }, modifier = Modifier.testTag("confirm_energy_profile")) { Text(if (busy) "Zapisywanie…" else "Zatwierdź") } }, dismissButton = { TextButton(enabled = !busy, onClick = { review = null }) { Text("Anuluj") } })
    }
}

@Composable
fun EnergyPlanPreview(plan: EnergyPlan) {
    Column(Modifier.testTag("energy_plan_preview"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Podstawa spoczynkowa: ${plan.base.basal.roundToInt()} kcal")
        Text("Zapotrzebowanie przed deficytem: ${plan.base.maintenance.roundToInt()} kcal")
        Text("Korekta celu: ${if (plan.adjustment > 0) "+" else ""}${plan.adjustment} kcal")
        Text("Cel jedzenia: ${plan.calories} kcal", style = MaterialTheme.typography.titleMedium)
        Text("B: ${plan.protein} g • W: ${plan.carbs} g • T: ${plan.fat} g")
    }
}

@Composable
private fun ProfileEnergyCalibration(viewModel: DietaViewModel, onEffects: (() -> Unit)?) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val weights by viewModel.weightLogs.collectAsStateWithLifecycle()
    val measurements by viewModel.bodyMeasurements.collectAsStateWithLifecycle()
    val days by viewModel.habits.days.collectAsStateWithLifecycle()
    val confirmations by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsStateWithLifecycle()
    val revision by viewModel.usability.revision.collectAsStateWithLifecycle()
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val report by produceState<EffectsReport?>(null, profile, weights, measurements, days, confirmations, revision, date) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { EffectsSupport.analyze(ScaleMeasurementsSupport.withScaleWeights(weights, ScaleMeasurementsSupport.read(context)), measurements, days, confirmations, profile, SportsSupport.read(context), EffectsSupport.read(context), LocalDate.parse(date), 30, EnergyCalculatorSupport.read(context)) }.getOrNull()
        }
    }
    HorizontalDivider()
    Text("Dopasowanie z 30 dni", style = MaterialTheme.typography.titleMedium)
    report?.let { r ->
        Text("Kompletne, zatwierdzone dni: ${r.completeDays}/${r.periodDays}. Pomiary wagi: ${r.points.size}.")
        Text(r.reasons, style = MaterialTheme.typography.bodySmall)
        r.referenceIntake?.let { Text("Przy stabilnej wadze średnie zapisane spożycie: ${it.roundToInt()} kcal. To punkt odniesienia, nie pomiar metabolizmu.") }
    }
    Text("W Efektach i analizie sprawdzisz trend, obwody, wyniki siłowe, sen, głód i energię. Te informacje nie są arbitralnie przeliczane na dodatkowe kalorie.", style = MaterialTheme.typography.bodySmall)
    if (onEffects != null) TextButton(onClick = onEffects) { Text("Otwórz ocenę i propozycję korekty →") }
}
