package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*
import com.example.data.RecipeEntity

@Composable
fun ProductShortcuts(recent: List<SubstituteCandidate>, favorites: List<SubstituteCandidate>, onSelect: (SubstituteCandidate) -> Unit,
    onFavorite: (SubstituteCandidate) -> Unit, portion: (SubstituteCandidate) -> Pair<Float, String>? = { null },
    onRemoveRecent: (SubstituteCandidate) -> Unit = {}, enabled: Boolean = true) {
    var tab by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var expanded by remember(tab, query) { mutableStateOf(false) }
    if (recent.isNotEmpty() || favorites.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !tab, onClick = { tab = false }, enabled = enabled, label = { Text("Ostatnie") }, leadingIcon = { Icon(Icons.Outlined.History, null, Modifier.size(18.dp)) })
            FilterChip(selected = tab, onClick = { tab = true }, enabled = enabled, label = { Text("Ulubione") }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null, Modifier.size(18.dp)) })
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Szukaj produktu...") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
        val products = ProductHistorySupport.unique(if (tab) favorites else recent).filter {
            ProduceSupport.normalize(it.name + " " + it.brand).contains(ProduceSupport.normalize(query.trim())) }
        for (product in products.take(if (expanded) 30 else 3)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(enabled = enabled, onClick = { onSelect(product) }, modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(product.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val saved = portion(product)
                        val subtitle = listOf(product.brand.takeIf { it.isNotBlank() }, saved?.let { "${MealPlannerSupport.formatQuantityNumber(it.first)} ${it.second}" }).filterNotNull().joinToString(" • ")
                        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(enabled = enabled, onClick = { onFavorite(product) }) { Text(if (favorites.any { ProductHistorySupport.same(it, product) }) "★" else "☆") }
                if (!tab) { var confirmRemove by remember(product.name) { mutableStateOf(false) }
                    if (confirmRemove) TextButton(enabled = enabled, onClick = { onRemoveRecent(product); confirmRemove = false }) { Text("Potwierdź") }
                    else IconButton(enabled = enabled, onClick = { confirmRemove = true }) { Icon(Icons.Outlined.DeleteOutline, "Usuń z ostatnich") }
                }
            }
        }
        if (products.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Pokaż mniej" else "Pokaż więcej (${products.size})") }
        if (products.isEmpty()) Text("Brak pasujących produktów.", style = MaterialTheme.typography.bodySmall)
        Text("Wybierz produkt, sprawdź ilość i zatwierdź na dole.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DashboardCustomization(viewModel: DietaViewModel) {
    val store = viewModel.usability
    val revision by store.revision.collectAsState()
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Zwiń układ Start" else "Dostosuj ekran Start") }
    if (open) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Widoczność i kolejność kart")
            val order = remember(revision) { store.order() }
            order.forEachIndexed { index, id ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(!store.hidden(id), { store.show(id, it) })
                    Text(store.cards[id].orEmpty(), modifier = Modifier.weight(1f))
                    TextButton(enabled = index > 0, onClick = { store.move(id, -1) }) { Text("↑") }
                    TextButton(enabled = index < order.lastIndex, onClick = { store.move(id, 1) }) { Text("↓") }
                }
            }
        }
    }
}

@Composable
fun RecipePreparationSettings(recipe: RecipeEntity, viewModel: DietaViewModel) {
    val store = viewModel.usability
    var open by remember(recipe.id) { mutableStateOf(false) }
    var minutes by remember(recipe.id) { mutableStateOf(store.minutes(recipe)?.toString().orEmpty()) }
    var noCook by remember(recipe.id) { mutableStateOf(store.tag(recipe.id, "nocook")) }
    var work by remember(recipe.id) { mutableStateOf(store.tag(recipe.id, "work")) }
    TextButton(onClick = { open = !open }) { Text("Czas i sposób przygotowania") }
    if (open) {
        OutlinedTextField(minutes, { minutes = it }, label = { Text("Czas całkowity (minuty)") }, singleLine = true)
        Row { Checkbox(noCook, { noCook = it }); Text("Bez gotowania") }
        Row { Checkbox(work, { work = it }); Text("Do zabrania do pracy") }
        Button(enabled = minutes.isBlank() || minutes.toIntOrNull()?.let { it in 0..1440 } == true, onClick = {
            store.setRecipe(recipe.id, minutes.toIntOrNull(), noCook, work); open = false
        }) { Text("Zapisz oznaczenia") }
    }
}

@Composable
fun BackupReminderCard(viewModel: DietaViewModel) {
    val backup = viewModel.backup
    val status by backup.status.state.collectAsState()
    val busy by backup.busy.collectAsState()
    var backupPassword by remember { mutableStateOf("") }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { backup.export(it, backupPassword) }
    }
    if (status.changes >= 5 || status.lastExport == 0L && status.lastAutomatic > 0) {
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Zadbaj o kopię danych")
                Text("Ostatnia kopia do pliku: ${backup.status.date(status.lastExport)}", style = MaterialTheme.typography.bodySmall)
                Text("Kopia na telefonie: ${backup.status.date(status.lastAutomatic)}", style = MaterialTheme.typography.bodySmall)
                Text("Zmiany od ostatniego eksportu: ${status.changes}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text("Hasło kopii (minimum 8 znaków)") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(enabled = !busy && backupPassword.length >= 8, onClick = { save.launch("FITMango-kopia-${java.time.LocalDate.now()}.zip") }) { Text("Zapisz szyfrowaną kopię do pliku") }
            }
        }
    }
}
