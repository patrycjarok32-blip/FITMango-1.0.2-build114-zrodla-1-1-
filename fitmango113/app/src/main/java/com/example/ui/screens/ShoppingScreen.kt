package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.ui.text.style.TextDecoration
import com.example.viewmodel.ShoppingWeekSupport
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.example.viewmodel.ShoppingCategories
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(viewModel: DietaViewModel) {
    val allItems by viewModel.shoppingItems.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FITMangoShopping", 0) }
    val defaultFrom = ShoppingWeekSupport.start(LocalDate.parse(currentDate))
    val savedBounds = remember {
        runCatching { ShoppingWeekSupport.bounds(prefs.getString("selected_range", "").orEmpty()) }
            .getOrElse { defaultFrom to defaultFrom.plusDays(6) }
    }
    var rangeFrom by rememberSaveable { mutableStateOf(savedBounds.first.toString()) }
    var rangeTo by rememberSaveable { mutableStateOf(savedBounds.second.toString()) }
    val currentWeek = ShoppingWeekSupport.rangeKey(LocalDate.parse(rangeFrom), LocalDate.parse(rangeTo))
    val items = allItems.filter { it.weekStart == currentWeek }
    var showRangePicker by remember { mutableStateOf(false) }
    LaunchedEffect(currentWeek) {
        prefs.edit().putString("selected_range", currentWeek).apply()
        viewModel.generateShoppingList(currentWeek)
    }

    var showOwned by remember(currentWeek) { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var newItemCategory by remember { mutableStateOf("Warzywa i owoce") }

    var selectedCategoryFilter by remember { mutableStateOf("Wszystkie") }
    LaunchedEffect(Unit) { viewModel.repairShoppingCategories() }
    val categories = listOf("Wszystkie") + com.example.viewmodel.ShoppingCategories.all

    val categoryItems = ShoppingWeekSupport.visible(allItems, currentWeek, selectedCategoryFilter)
    val filteredItems = rememberShoppingRows(categoryItems, showOwned, "$currentWeek:$selectedCategoryFilter")
    val reducedMotion = LocalReducedMangoMotion.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista Zakupów", fontWeight = FontWeight.Black) },
                actions = {
                    Button(
                        onClick = { viewModel.generateShoppingList(currentWeek) },
                        colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.pressScaleEffect()
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generuj z jadłospisu", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Cream
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = JuicyOrange,
                contentColor = MangoButtonInk,
                shape = CircleShape,
                modifier = Modifier.pressScaleEffect()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj produkt", modifier = Modifier.size(24.dp))
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            Text("Zakupy z jadłospisu • wybierz daty", fontWeight = FontWeight.Bold, color = Cream)
            Text("Od ${LocalDate.parse(rangeFrom).format(dateFormat)} do ${LocalDate.parse(rangeTo).format(dateFormat)}", color = GoldAccent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showRangePicker = true }) { Text("Zmień daty od–do") }
                TextButton(onClick = {
                    rangeFrom = defaultFrom.toString()
                    rangeTo = defaultFrom.plusDays(6).toString()
                }) { Text("Bieżący tydzień") }
            }
            MangoCard {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Brakuje: ${items.count { !it.isChecked }}", color = MangoYellow, fontWeight = FontWeight.Bold)
                    Text("Mam: ${items.count { it.isChecked }}", color = GoldAccent)
                }
            }
            // Category Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    MangoChip(
                        text = cat,
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat }
                    )
                }
            }

            Text("Do kupienia: ${categoryItems.count { !it.isChecked }}", color = Cream, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showOwned = !showOwned }) { Text(if (showOwned) "Zwiń Mam (${categoryItems.count { it.isChecked }})" else "Pokaż Mam (${categoryItems.count { it.isChecked }})") }
            HorizontalDivider(color = WarmBrown.copy(alpha = 0.3f))

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MangoGuide(size = 56.dp)
                        Text(if (categoryItems.any { it.isChecked }) "Wszystko masz — rozwiń Mam, aby odznaczyć produkt." else "Brak produktów w wybranym zakresie dat.", color = Cream.copy(alpha = 0.6f))
                        TextButton(onClick = { if (categoryItems.any { it.isChecked }) showOwned = true else viewModel.generateShoppingList(currentWeek) }) {
                            Text(if (categoryItems.any { it.isChecked }) "Pokaż posiadane produkty" else "Generuj z jadłospisu")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        MangoCard(
                            modifier = if (reducedMotion) Modifier else Modifier.animateItem(
                                fadeInSpec = androidx.compose.animation.core.tween(160),
                                placementSpec = androidx.compose.animation.core.tween(220),
                                fadeOutSpec = androidx.compose.animation.core.tween(160)),
                            isSelected = item.isChecked
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(
                                        checked = item.isChecked,
                                        onCheckedChange = { viewModel.toggleShoppingItem(item) },
                                        colors = CheckboxDefaults.colors(checkedColor = JuicyOrange, uncheckedColor = WarmBrown)
                                    )
                                    val categoryIcon = ShoppingCategories.icon(item.itemName, item.category)
                                    com.example.ui.components.FitMangoAssetIcon(
                                        com.example.ui.components.fitMangoCategoryResource(categoryIcon.second) ?: com.example.R.drawable.fit_icon_shopping,
                                        modifier = Modifier.size(28.dp).clearAndSetSemantics { contentDescription = categoryIcon.second })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = item.itemName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (item.isChecked) Cream.copy(alpha = 0.5f) else Cream,
                                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                        Text(
                                            text = item.category,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.isChecked) Color.Gray else MangoYellow
                                        )
                                    }
                                }
                                Text(
                                    text = item.amount.ifBlank { "1 szt." },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isChecked) Color.Gray else GoldAccent,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRangePicker) {
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = LocalDate.parse(rangeFrom).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = LocalDate.parse(rangeTo).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                    onClick = {
                        val from = picker.selectedStartDateMillis
                        val to = picker.selectedEndDateMillis
                        if (from != null && to != null && to >= from) {
                            rangeFrom = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            rangeTo = Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            showRangePicker = false
                        }
                    }) { Text("Zastosuj") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("Anuluj") } }
        ) {
            DateRangePicker(state = picker, title = { Text("Wybierz daty jadłospisu", modifier = Modifier.padding(16.dp)) },
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp))
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = MutedSurface1,
            title = { Text("Dodaj produkt do wybranego zakresu", color = Cream, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        placeholder = { Text("Nazwa produktu (np. Awokado)", color = Cream.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream)
                    )

                    Text("Wybierz kategorię:", fontSize = 12.sp, color = GoldAccent)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories.filter { it != "Wszystkie" }) { cat ->
                            FilterChip(
                                selected = newItemCategory == cat,
                                onClick = { newItemCategory = cat },
                                label = { Text(cat) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = JuicyOrange, selectedLabelColor = DarkBackground)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                MangoPrimaryButton(
                    text = "Dodaj",
                    onClick = {
                        if (newItemName.isNotBlank()) {
                            viewModel.addShoppingItem(newItemName, newItemCategory, currentWeek)
                            newItemName = ""
                            showAddDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }
}
