package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsScreen(
    viewModel: DietaViewModel,
    onBack: () -> Unit
) {
    val bodyMeasurements by viewModel.bodyMeasurements.collectAsStateWithLifecycle()

    var weightInput by remember { mutableStateOf("") }
    var waistInput by remember { mutableStateOf("") }
    var hipsInput by remember { mutableStateOf("") }
    var chestInput by remember { mutableStateOf("") }
    var armsInput by remember { mutableStateOf("") }
    var thighsInput by remember { mutableStateOf("") }

    var deletion by remember { mutableStateOf<com.example.data.BodyMeasurementEntity?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

    fun String.parsePolishFloat(): Float? {
        return this.replace(",", ".").trim().toFloatOrNull()
    }

    val latestMeasurement = bodyMeasurements.firstOrNull()
    val previousMeasurement = bodyMeasurements.getOrNull(1)
    val weightChange = if (latestMeasurement != null && previousMeasurement != null) {
        latestMeasurement.weightKg - previousMeasurement.weightKg
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pomiary & Progres", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz", tint = MangoYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Cream
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, WarmBrown.copy(alpha = 0.2f))
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Trend / Summary stats card
            item { com.example.ui.components.ChangeHistoryCard(viewModel) }
            item {
                MangoCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Twoje postępy",
                            fontWeight = FontWeight.Bold,
                            color = MangoYellow,
                            fontSize = 16.sp
                        )

                        if (latestMeasurement != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Ostatnia waga", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
                                    Text("${latestMeasurement.weightKg} kg", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Cream)
                                }
                                if (weightChange != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Zmiana", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
                                        val changeText = if (weightChange > 0) "+${String.format("%.1f", weightChange)} kg 📈" else "${String.format("%.1f", weightChange)} kg 📉"
                                        val changeColor = if (weightChange > 0) JuicyOrange else LeafGreen
                                        Text(changeText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = changeColor)
                                    }
                                }
                            }
                        } else {
                            Text("Brak wprowadzonych pomiarów. Dodaj swój pierwszy pomiar poniżej!", color = Cream.copy(alpha = 0.8f), fontSize = 13.sp)
                        }

                        val firstWeight = bodyMeasurements.lastOrNull()?.weightKg
                        if (latestMeasurement != null && firstWeight != null && bodyMeasurements.size > 1) {
                            val change = latestMeasurement.weightKg - firstWeight
                            Text("Od pierwszego pomiaru: ${if (change > 0) "+" else ""}${String.format("%.1f", change)} kg • ${bodyMeasurements.size} zapisów",
                                color = GoldAccent, style = MaterialTheme.typography.bodySmall)
                        }


                        // Custom canvas-drawn weight trend chart
                        val weightValues = bodyMeasurements.take(7).map { it.weightKg }.reversed()
                        val dateValues = bodyMeasurements.take(7).map { it.date }.reversed()
                        WeightTrendChart(weights = weightValues, dates = dateValues)
                    }
                }
            }

            // Input Form Card
            item {
                MangoCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Nowy Pomiar Ciała",
                            fontWeight = FontWeight.Bold,
                            color = MangoYellow,
                            fontSize = 16.sp
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = { weightInput = it; saveErrorMessage = null },
                                label = { Text("Waga (kg)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream)
                            )
                            OutlinedTextField(
                                value = waistInput,
                                onValueChange = { waistInput = it },
                                label = { Text("Talia (cm)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = hipsInput,
                                onValueChange = { hipsInput = it },
                                label = { Text("Biodra (cm)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream)
                            )
                            OutlinedTextField(
                                value = chestInput,
                                onValueChange = { chestInput = it },
                                label = { Text("Klatka (cm)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = armsInput,
                                onValueChange = { armsInput = it },
                                label = { Text("Ramię (cm)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream)
                            )
                            OutlinedTextField(
                                value = thighsInput,
                                onValueChange = { thighsInput = it },
                                label = { Text("Udo (cm)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream)
                            )
                        }

                        saveErrorMessage?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        MangoPrimaryButton(
                            text = "Zapisz Pomiar",
                            onClick = {
                                val parsedWeight = weightInput.parsePolishFloat()
                                if (parsedWeight == null) {
                                    saveErrorMessage = "Wprowadź poprawną wartość wagi (np. 61.5 lub 61,0)"
                                    return@MangoPrimaryButton
                                }
                                val parsedWaist = waistInput.parsePolishFloat() ?: 0f
                                val parsedHips = hipsInput.parsePolishFloat() ?: 0f
                                val parsedChest = chestInput.parsePolishFloat() ?: 0f
                                val parsedArms = armsInput.parsePolishFloat() ?: 0f
                                val parsedThighs = thighsInput.parsePolishFloat() ?: 0f

                                viewModel.addBodyMeasurement(
                                    weightKg = parsedWeight,
                                    waist = parsedWaist,
                                    hips = parsedHips,
                                    chest = parsedChest,
                                    arms = parsedArms,
                                    thighs = parsedThighs
                                )

                                // Clear inputs
                                weightInput = ""
                                waistInput = ""
                                hipsInput = ""
                                chestInput = ""
                                armsInput = ""
                                thighsInput = ""
                                saveErrorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // History List Card
            item {
                MangoSectionTitle(text = "Historia Pomiarów", showSparkle = true)
                saveErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            if (bodyMeasurements.isEmpty()) {
                item {
                    Text("Brak zapisanych pomiarów.", color = Cream.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            } else {
                items(bodyMeasurements) { m ->
                    MangoCard(modifier = Modifier.padding(vertical = 4.dp).testTag("measurement_item_${m.id}")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(m.date, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Cream)
                                    if (m.source == "HEALTH_CONNECT") {
                                        Surface(
                                            color = LeafGreen.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, LeafGreen)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Favorite,
                                                    contentDescription = null,
                                                    tint = LeafGreen,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = "Health Connect",
                                                    fontSize = 10.sp,
                                                    color = LeafGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            color = MutedSurface2,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Ręczny",
                                                fontSize = 10.sp,
                                                color = Cream.copy(alpha = 0.6f),
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (m.waistCm > 0 || m.hipsCm > 0 || m.chestCm > 0) {
                                    Text(
                                        text = "Talia: ${m.waistCm}cm | Biodra: ${m.hipsCm}cm | Klatka: ${m.chestCm}cm",
                                        fontSize = 11.sp,
                                        color = Cream.copy(alpha = 0.7f)
                                    )
                                }
                                if (m.armsCm > 0 || m.thighsCm > 0) {
                                    Text(
                                        text = "Ramię: ${m.armsCm}cm | Udo: ${m.thighsCm}cm",
                                        fontSize = 11.sp,
                                        color = Cream.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${m.weightKg} kg", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MangoYellow)
                                TextButton(enabled = !deleting, onClick = { deletion = m }, modifier = Modifier.testTag("delete_measurement_${m.id}")) { Text("Usuń") }
                            }
                        }
                    }
                }
            }
        }
    }
    deletion?.let { row -> AlertDialog(onDismissRequest = { if (!deleting) deletion = null },
        title = { Text("Usunąć ten pomiar?") },
        text = { Text("${row.date} • ${row.weightKg} kg. Usuniemy wybrany wpis i odpowiadający zapis wagi, jeśli nie jest potrzebny innemu pomiarowi. Inne wpisy z tego dnia pozostają. Dane w Renpho i Health Connect pozostają bez zmian.") },
        confirmButton = { TextButton(enabled = !deleting, onClick = {
            deleting = true
            viewModel.deleteBodyMeasurement(row.id) { error -> deleting = false; saveErrorMessage = error; deletion = null }
        }) { Text(if (deleting) "Usuwanie…" else "Usuń pomiar") } },
        dismissButton = { TextButton(enabled = !deleting, onClick = { deletion = null }) { Text("Anuluj") } }) }

}

@Composable
fun WeightTrendChart(weights: List<Float>, dates: List<String>) {
    if (weights.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(MutedSurface2, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Dodaj co najmniej 2 pomiary wagi, aby zobaczyć wykres trendu.",
                color = Cream.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        return
    }

    val minWeight = weights.minOrNull() ?: 0f
    val maxWeight = weights.maxOrNull() ?: 100f
    val range = (maxWeight - minWeight).coerceAtLeast(1f)

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / (weights.size - 1)

        val points = weights.mapIndexed { idx, w ->
            val x = idx * stepX
            val y = height - ((w - minWeight) / range * height)
            androidx.compose.ui.geometry.Offset(x, y)
        }

        // Draw grid lines
        for (i in 0..4) {
            val yGrid = height / 4 * i
            drawLine(
                color = WarmBrown.copy(alpha = 0.15f),
                start = androidx.compose.ui.geometry.Offset(0f, yGrid),
                end = androidx.compose.ui.geometry.Offset(width, yGrid),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw trend line
        for (i in 0 until points.size - 1) {
            drawLine(
                color = OrangeInk,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3.dp.toPx()
            )
        }

        // Draw glow and points
        points.forEach { pt ->
            drawCircle(
                color = MangoYellow,
                radius = 5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = OrangeInk,
                radius = 2.5.dp.toPx(),
                center = pt
            )
        }
    }
}
