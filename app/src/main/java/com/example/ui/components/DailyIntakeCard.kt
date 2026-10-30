package com.example.ui.components

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel

private fun beverageIcon(type: String): Int = when (type) {
    "Woda", "Napój zero" -> com.example.R.drawable.fit_icon_water
    "Mleko", "Kefir" -> com.example.R.drawable.fit_icon_dairy
    "Sok" -> com.example.R.drawable.fit_icon_mango
    "Zioła / napar" -> com.example.R.drawable.fit_icon_vegetable
    else -> com.example.R.drawable.fit_icon_drink
}

@Composable
fun DailyIntakeCard(viewModel: DietaViewModel) {
    val logs by viewModel.beverageLogs.collectAsStateWithLifecycle()
    val habitDays by viewModel.habits.days.collectAsStateWithLifecycle()
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val waterGoal = habitDays.find { it.date == date }?.waterGoal ?: 2500
    val total by viewModel.waterDrankMl.collectAsStateWithLifecycle()
    val taken by viewModel.creatineTakenToday.collectAsStateWithLifecycle()
    val creatineHistory by viewModel.creatineLogs.collectAsStateWithLifecycle()
    var showDrinks by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val reportError: (String) -> Unit = { error = it }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MutedSurface1)) {
        Column(Modifier.fillMaxWidth().mangoExpand().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MangoIncreaseMotion(total, drop = true) { FitMangoAssetIcon(com.example.R.drawable.fit_icon_water, modifier = Modifier.size(32.dp)) }
                Text("Nawodnienie", color = MangoYellow, style = MaterialTheme.typography.titleMedium)
            }
            Text("Napoje dzisiaj • $total / $waterGoal ml", color = Cream)
            MangoProgressBar(progress = (total / waterGoal.toFloat()).coerceIn(0f, 1f), color = Color(0xFF42B8F5))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.addSpecificBeverage("Woda", 250, reportError) },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MutedSurface2)) {
                    Text("Woda +250 ml", color = Cream)
                }
                Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f).testTag("add_custom_drink"),
                    colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk)) {
                    Text("Dodaj napój")
                }
            }
            TextButton(onClick = { showDrinks = !showDrinks },
                modifier = Modifier.fillMaxWidth().testTag("toggle_drink_history")) {
                Text(if (showDrinks) "Zwiń listę napojów −" else "Pokaż napoje (${logs.size}) +", color = LeafGreen)
            }
            if (showDrinks) {
            if (logs.isEmpty()) Text("Brak zapisanych napojów dzisiaj.", color = Cream)
            logs.forEach { log ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FitMangoAssetIcon(beverageIcon(log.type), modifier = Modifier.padding(end = 8.dp).size(28.dp))
                    Text("${log.type} • ${log.amountMl} ml", Modifier.weight(1f), color = Cream)
                    IconButton(onClick = { viewModel.deleteBeverageLog(log.id, reportError) },
                        modifier = Modifier.size(48.dp).testTag("delete_drink_${log.id}")) {
                        Icon(Icons.Default.Delete, "Usuń ${log.type}, ${log.amountMl} ml", tint = MangoYellow)
                    }
                }
            }
            }
            HorizontalDivider(color = WarmBrown)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FitMangoAssetIcon(com.example.R.drawable.fit_icon_creatine)
                Text("Kreatyna • cel 5 g dziennie", color = MangoYellow, style = MaterialTheme.typography.titleMedium)
            }
            Text(if (taken) "Dzisiaj: 5 / 5 g ✓" else "Dzisiaj: 0 / 5 g", color = if (taken) LeafGreen else Cream)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.markCreatineTaken(reportError) }, enabled = !taken,
                    modifier = Modifier.weight(1f).testTag("creatine_taken"),
                    colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk)) {
                    Text(if (taken) "Zapisano 5 g" else "Przyjęto 5 g")
                }
                TextButton(onClick = { showHistory = !showHistory }) { Text("Historia", color = MangoYellow) }
            }
            if (taken) TextButton(onClick = {
                viewModel.deleteCreatineLog(java.time.LocalDate.now().toString(), reportError)
            }, modifier = Modifier.testTag("undo_creatine")) { Text("Cofnij dzisiejszy wpis", color = MangoYellow) }
            if (showHistory) {
                if (creatineHistory.isEmpty()) Text("Brak wpisów kreatyny.", color = Cream)
                creatineHistory.take(14).forEach { log ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${log.date} • ${log.amountMg / 1000f} g", Modifier.weight(1f), color = Cream)
                        IconButton(onClick = { viewModel.deleteCreatineLog(log.date, reportError) }, Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "Usuń kreatynę z ${log.date}", tint = MangoYellow)
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (showAdd) {
        var type by remember { mutableStateOf("Woda") }
        var customType by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("250") }
        var inputError by remember { mutableStateOf<String?>(null) }
        val types = listOf("Woda", "Kawa", "Herbata", "Napój zero", "Zioła / napar", "Sok", "Mleko", "Kefir", "Napój izotoniczny", "Inny")
        AlertDialog(onDismissRequest = { showAdd = false }, containerColor = MutedSurface1,
            title = { Text("Dodaj napój", color = Cream) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    types.forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { FitMangoAssetIcon(beverageIcon(option), modifier = Modifier.size(28.dp)) },
                            label = { Text(option) })
                    }
                    if (type == "Inny") OutlinedTextField(customType, { customType = it.take(40) },
                        label = { Text("Nazwa napoju") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(amount, { amount = it }, label = { Text("Ilość w ml") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("drink_amount_ml"))
                    inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ml = amount.trim().toIntOrNull()
                    val name = if (type == "Inny") customType.trim() else type
                    if (ml == null || ml !in 1..10000 || name.isBlank()) {
                        inputError = "Podaj nazwę i ilość od 1 do 10000 ml."
                    } else {
                        viewModel.addSpecificBeverage(name, ml, reportError)
                        showAdd = false
                    }
                }) { Text("Zapisz", color = MangoYellow) }
            }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Anuluj", color = Cream) } })
    }
}
