package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel

@Composable
fun StudioHydrationCards(viewModel: DietaViewModel, details: @Composable () -> Unit) {
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val days by viewModel.habits.days.collectAsStateWithLifecycle()
    val water by viewModel.waterDrankMl.collectAsStateWithLifecycle()
    val taken by viewModel.creatineTakenToday.collectAsStateWithLifecycle()
    val goal = days.find { it.date == date }?.waterGoal ?: 2500
    var expanded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.mangoExpand(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MangoCard(Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MangoSectionArt(com.example.R.drawable.fit_icon_water, WaterAccent, size = 30.dp)
                        Text("Woda", color = WaterAccent, style = MaterialTheme.typography.titleMedium)
                    }
                    Text("$water / $goal ml", style = MaterialTheme.typography.bodyMedium)
                    MangoProgressBar(water / goal.coerceAtLeast(1).toFloat(), color = WaterAccent)
                    Row {
                        TextButton(onClick = { viewModel.addSpecificBeverage("Woda", 250) { error = it } },
                            modifier = Modifier.weight(1f).testTag("compact_water_add"), contentPadding = PaddingValues(0.dp)) { Text("+250", color = WaterAccent) }
                        TextButton(onClick = { viewModel.addSpecificBeverage("Woda", 500) { error = it } },
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("+500", color = WaterAccent) }
                    }
                }
            }
            MangoCard(Modifier.weight(1f), isSelected = taken) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MangoSectionArt(com.example.R.drawable.fit_icon_creatine, MangoYellow, size = 30.dp)
                        Text("Kreatyna", color = MangoYellow, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(if (taken) "Przyjęto 5 g ✓" else "Dzisiaj: 0 / 5 g", style = MaterialTheme.typography.bodyMedium)
                    Text(if (taken) "Zapisano dzisiaj" else "Twój dzienny wpis", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                    TextButton(onClick = { viewModel.markCreatineTaken { error = it } }, enabled = !taken,
                        modifier = Modifier.testTag("studio_creatine_quick"), contentPadding = PaddingValues(0.dp)) {
                        Text(if (taken) "Zaliczona ✓" else "Przyjęto 5 g")
                    }
                }
            }
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("compact_start_water")) {
            Text(if (expanded) "Zwiń napoje i historię −" else "Dodaj napój / historia +")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (expanded) details()
    }
}
