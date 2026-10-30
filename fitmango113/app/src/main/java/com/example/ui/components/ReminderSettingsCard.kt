package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.reminders.*

@Composable
fun ReminderSettingsCard() {
    val context = LocalContext.current
    val saved = remember { ReminderSupport.read(context) }
    var water by rememberSaveable { mutableStateOf(saved.water) }
    var waterTime by rememberSaveable { mutableStateOf(saved.waterTime) }
    var interval by rememberSaveable { mutableStateOf(saved.waterInterval) }
    var creatine by rememberSaveable { mutableStateOf(saved.creatine) }
    var creatineTime by rememberSaveable { mutableStateOf(saved.creatineTime) }
    var day by rememberSaveable { mutableStateOf(saved.day) }
    var dayTime by rememberSaveable { mutableStateOf(saved.dayTime) }
    var quietStart by rememberSaveable { mutableStateOf(saved.quietStart) }
    var quietEnd by rememberSaveable { mutableStateOf(saved.quietEnd) }
    var weight by rememberSaveable { mutableStateOf(saved.weight) }
    var weightTime by rememberSaveable { mutableStateOf(saved.weightTime) }
    var weightDay by rememberSaveable { mutableIntStateOf(saved.weightDay) }
    var activity by rememberSaveable { mutableStateOf(saved.activity) }
    var activityTime by rememberSaveable { mutableStateOf(saved.activityTime) }
    var activityDay by rememberSaveable { mutableIntStateOf(saved.activityDay) }
    var weekly by rememberSaveable { mutableStateOf(saved.weekly) }
    var weeklyTime by rememberSaveable { mutableStateOf(saved.weeklyTime) }
    var weeklyDay by rememberSaveable { mutableIntStateOf(saved.weeklyDay) }
    var feedback by remember { mutableIntStateOf(0) }
    var savedPulse by remember { mutableStateOf(false) }
    LaunchedEffect(feedback) { if (feedback > 0) { savedPulse = true; kotlinx.coroutines.delay(1500); savedPulse = false } }
    var message by remember { mutableStateOf<String?>(null) }
    var allowed by remember { mutableStateOf(HabitNotifications.allowed(context)) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { allowed = HabitNotifications.allowed(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && HabitNotifications.allowed(context)
        message = if (allowed) "Powiadomienia dozwolone. Zapisz wybrane przypomnienia." else "Powiadomienia są wyłączone. Możesz włączyć je w ustawieniach telefonu."
    }
    val settings = ReminderSettings(water, waterTime, interval, creatine, creatineTime, day, dayTime, quietStart, quietEnd,
        weight, weightTime, weightDay, activity, activityTime, activityDay, weekly, weeklyTime, weeklyDay)
    val valid = runCatching { ReminderSupport.validate(settings) }.isSuccess
    CollapsibleMangoCard("Przypomnienia i godziny ciszy", "reminder_settings") {
        Text("Ustaw godziny lokalne telefonu w formacie HH:mm. Android może opóźnić powiadomienie, np. podczas oszczędzania baterii. Zmiany zatwierdź przyciskiem Zapisz.")
        @Composable fun toggle(title: String, checked: Boolean, changed: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f).padding(top = 12.dp)); Switch(checked, changed)
        } }
        @Composable fun time(label: String, value: String, changed: (String) -> Unit) { OutlinedTextField(value, { changed(it.take(5)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        toggle("Nawodnienie", water) { water = it }
        if (water) {
            time("Pierwsze przypomnienie", waterTime) { waterTime = it }
            listOf(0 to "Raz dziennie", 120 to "Co 2 godziny", 180 to "Co 3 godziny", 240 to "Co 4 godziny").forEach { (minutes, label) ->
                FilterChip(interval == minutes, { interval = minutes }, label = { Text(label) })
            }
            Text("Pomijamy powiadomienia po osiągnięciu zapisanego celu napojów oraz w godzinach ciszy.")
        }
        toggle("Kreatyna", creatine) { creatine = it }
        if (creatine) { time("Godzina kreatyny", creatineTime) { creatineTime = it }; Text("Pomijamy przypomnienie, gdy dzisiejszy wpis kreatyny już jest zapisany.") }
        toggle("Zakończenie dnia", day) { day = it }
        if (day) time("Godzina podsumowania dnia", dayTime) { dayTime = it }
        @Composable fun weekdays(value: Int, daily: Boolean, onChange: (Int) -> Unit) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(if (daily) 8 else 7) { i ->
                    val index = if (daily) i else i + 1
                    val label = listOf("Codziennie", "Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")[index]
                    FilterChip(selected = value == index, onClick = { onChange(index) }, label = { Text(label) })
                }
            }
        }
        toggle("Ważenie / pomiary", weight) { weight = it }
        if (weight) { time("Godzina pomiaru", weightTime) { weightTime = it }; weekdays(weightDay, true) { weightDay = it } }
        toggle("Czas na aktywność", activity) { activity = it }
        if (activity) { time("Godzina aktywności", activityTime) { activityTime = it }; weekdays(activityDay, true) { activityDay = it } }
        toggle("Raport tygodniowy", weekly) { weekly = it }
        if (weekly) {
            time("Godzina przypomnienia o raporcie", weeklyTime) { weeklyTime = it }; weekdays(weeklyDay, false) { weeklyDay = it }
            Text("Powiadomienie przypomina o raporcie. Analizę AI uruchamiasz samodzielnie w aplikacji.", style = MaterialTheme.typography.bodySmall)
        }
        time("Cisza od", quietStart) { quietStart = it }
        time("Cisza do", quietEnd) { quietEnd = it }
        Text("Takie same godziny początku i końca wyłączają ciszę. Przypomnienia przypadające w ciszy są pomijane.")
        Text(if (allowed) "Telefon zezwala na powiadomienia." else "Telefon nie zezwala na powiadomienia.")
        if (!allowed) OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("Zezwól na powiadomienia") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Ustawienia powiadomień telefonu") }
        Button(enabled = valid && !savedPulse, onClick = {
            try { ReminderSupport.save(context, settings); feedback++; message = if (allowed || !(water || creatine || day || weight || activity || weekly)) "Zapisano przypomnienia." else "Zapisano. Włącz zgodę telefonu, aby otrzymywać powiadomienia." }
            catch (_: Exception) { message = "Nie udało się zapisać przypomnień. Spróbuj ponownie." }
        }, modifier = Modifier.testTag("save_reminder_settings")) { Text(if (savedPulse) "Zapisano ✓" else "Zapisz przypomnienia") }
        SuccessFeedback(feedback, "Zapisano przypomnienia", "reminders_saved")
        OutlinedButton(enabled = allowed, onClick = { message = if (HabitNotifications.show(context, "test")) "Wysłano powiadomienie testowe." else "Kanał powiadomień jest wyłączony w telefonie." }) { Text("Wyślij powiadomienie testowe") }
        if (!valid) Text("Sprawdź godziny, np. 09:00 lub 21:30.")
        message?.let { Text(it) }
    }
}
