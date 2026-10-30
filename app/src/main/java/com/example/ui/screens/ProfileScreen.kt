package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ai.SecureKeyStorage
import com.example.ai.diagnoseGemini
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.health.HealthConnectManager
import androidx.health.connect.client.PermissionController
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: DietaViewModel,
    onNavigateToMeasurements: (() -> Unit)? = null,
    onNavigateToAiCoach: (() -> Unit)? = null,
    onNavigateToEffects: (() -> Unit)? = null
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val weightLogs by viewModel.weightLogs.collectAsStateWithLifecycle()
    val bodyMeasurements by viewModel.bodyMeasurements.collectAsStateWithLifecycle()
    val bodyPhotos by viewModel.bodyPhotos.collectAsStateWithLifecycle()
    val weeklyAnalysis by viewModel.weeklyAiAnalysis.collectAsStateWithLifecycle()
    val isLoading by viewModel.effectsAiBusy.collectAsStateWithLifecycle()

    var name by remember(profile) { mutableStateOf(profile?.name ?: "Anna") }
    var age by remember(profile) { mutableStateOf(profile?.age?.toString() ?: "28") }
    var height by remember(profile) { mutableStateOf(profile?.heightCm?.toString() ?: "168") }
    var weight by remember(profile) { mutableStateOf(profile?.currentWeightKg?.toString() ?: "65") }
    var gender by remember(profile) { mutableStateOf(profile?.gender ?: "Kobieta") }
    var activity by remember(profile) { mutableStateOf(profile?.activityLevel ?: "Umiarkowana aktywność") }
    var goal by remember(profile) { mutableStateOf(profile?.goal ?: "Redukcja") }
    var showTargetDialog by remember { mutableStateOf(false) }
    var customCalories by remember(profile) { mutableStateOf((profile?.targetCalories ?: 2000).toString()) }
    var customProtein by remember(profile) { mutableStateOf((profile?.targetProtein ?: 100).toString()) }
    var customCarbs by remember(profile) { mutableStateOf((profile?.targetCarbs ?: 250).toString()) }
    var customFat by remember(profile) { mutableStateOf((profile?.targetFat ?: 67).toString()) }
    var targetError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    var geminiKeyInput by remember { mutableStateOf(SecureKeyStorage.getKey() ?: "") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testStatusColor by remember { mutableStateOf(Color.Gray) }
    var isTesting by remember { mutableStateOf(false) }

    // Measurement inputs
    var waist by remember { mutableStateOf("") }
    var hips by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var arms by remember { mutableStateOf("") }
    var thighs by remember { mutableStateOf("") }

    if (showTargetDialog) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            containerColor = MutedSurface1,
            title = { Text("Własne cele kalorii i makro", color = Cream) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(customCalories, { customCalories = it }, label = { Text("Kalorie (kcal)") })
                    OutlinedTextField(customProtein, { customProtein = it }, label = { Text("Białko (g)") })
                    OutlinedTextField(customCarbs, { customCarbs = it }, label = { Text("Węglowodany (g)") })
                    OutlinedTextField(customFat, { customFat = it }, label = { Text("Tłuszcz (g)") })
                    targetError?.let { Text(it, color = Color.Red) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = customCalories.toIntOrNull()
                    val protein = customProtein.toIntOrNull()
                    val carbs = customCarbs.toIntOrNull()
                    val fat = customFat.toIntOrNull()
                    if (cal == null || cal !in 1..15000 || protein == null || protein !in 0..1000 ||
                        carbs == null || carbs !in 0..2000 || fat == null || fat !in 0..1000) {
                        targetError = "Wprowadź poprawne liczby: dodatnie kalorie i nieujemne makro."
                    } else {
                        viewModel.updateProfile(name, cal, protein, carbs, fat, goal)
                        showTargetDialog = false
                    }
                }) { Text("Zapisz", color = MangoYellow) }
            },
            dismissButton = { TextButton(onClick = { showTargetDialog = false }) { Text("Anuluj") } }
        )
    }
    val context = LocalContext.current
    val navPrefs = remember(context) { context.getSharedPreferences("FITMangoProfileNavigation", 0) }
    var tab by rememberSaveable { mutableStateOf(navPrefs.getString("tab", "profile").takeIf { it in ProfileHubCatalog.tabs } ?: "profile") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var parentDetails by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val detailStates = rememberSaveableStateHolder()
    val profileList = rememberLazyListState(navPrefs.getInt("profile_index", 0).coerceAtLeast(0), navPrefs.getInt("profile_offset", 0).coerceAtLeast(0))
    val progressList = rememberLazyListState(navPrefs.getInt("progress_index", 0).coerceAtLeast(0), navPrefs.getInt("progress_offset", 0).coerceAtLeast(0))
    val settingsList = rememberLazyListState(navPrefs.getInt("settings_index", 0).coerceAtLeast(0), navPrefs.getInt("settings_offset", 0).coerceAtLeast(0))
    val listStates = mapOf("profile" to profileList, "progress" to progressList, "settings" to settingsList)
    LaunchedEffect(tab) { navPrefs.edit().putString("tab", tab).apply() }
    listStates.forEach { (key, state) ->
        LaunchedEffect(state) {
            snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collectLatest { (index, offset) ->
                kotlinx.coroutines.delay(250)
                navPrefs.edit().putInt(key + "_index", index).putInt(key + "_offset", offset).apply()
            }
        }
        DisposableEffect(state) { onDispose {
            navPrefs.edit().putInt(key + "_index", state.firstVisibleItemIndex).putInt(key + "_offset", state.firstVisibleItemScrollOffset).apply()
        } }
    }
    val revision by viewModel.usability.revision.collectAsStateWithLifecycle()
    val hubHealth by viewModel.healthConnect.healthConnectSync.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backup.status.state.collectAsStateWithLifecycle()
    val mealsForRepair by viewModel.mealPlans.collectAsStateWithLifecycle()
    val recipesForRepair by viewModel.recipes.collectAsStateWithLifecycle()
    val ingredientsForRepair by viewModel.recipeIngredients.collectAsStateWithLifecycle()
    val missingCount by produceState<Int?>(null, mealsForRepair, recipesForRepair, ingredientsForRepair) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            UniversalNutritionRepair.collect(recipesForRepair, mealsForRepair, ingredientsForRepair).size
        }
    }
    val latestScale by produceState<String?>(null, revision, detail) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ScaleMeasurementsSupport.read(context).maxByOrNull { it.time }?.date }.getOrNull()
        }
    }
    val effectsSettings by produceState(EffectsSettings(), revision, detail) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { EffectsSupport.read(context) }.getOrDefault(EffectsSettings()) }
    }
    val reminders = remember(detail, revision) { runCatching { com.example.reminders.ReminderSupport.read(context) }.getOrDefault(com.example.reminders.ReminderSettings()) }
    val enabledReminders = listOf(reminders.water, reminders.creatine, reminders.day, reminders.weight, reminders.activity, reminders.weekly).count { it }
    val photoAuto = remember(detail, revision) { RecipePhotoSettings(context).enabled }
    val reducedMotion = remember(detail, revision) { context.getSharedPreferences("FITMangoMotion", 0).getBoolean("reduced", false) }
    val localGemini = remember(detail, revision) { SecureKeyStorage.localOnly() }
    val hasGeminiKey = remember(detail, revision) { !SecureKeyStorage.getKey().isNullOrBlank() }
    val summaries = remember(profile, hubHealth, missingCount, bodyMeasurements, bodyPhotos, latestScale,
        effectsSettings, isLoading, reminders, photoAuto, reducedMotion, localGemini, hasGeminiKey, backupStatus, MangoAppearance.light) { mapOf(
        "calculator" to (profile?.let { "${it.name} • ${it.goal} • ${it.targetCalories} kcal/dzień" } ?: "Uzupełnij dane i ustaw swój cel"),
        "health" to (if (hubHealth?.isConnected == true) "Połączono • synchronizacja: ${viewModel.backup.status.date(hubHealth?.lastSyncTimeMillis ?: 0L)}" else "Health Connect: niepołączone"),
        "nutrition_attention" to (missingCount?.let { if (it == 0) "Nie wykryto braków kalorii ani makro" else "Produkty do sprawdzenia: $it" } ?: "Sprawdzanie produktów…"),
        "history" to "Ostatnie zmiany posiłków i pomiarów • cofanie",
        "measurements" to (bodyMeasurements.maxByOrNull { it.id }?.let { "Ostatni zapis: ${it.date} • ${bodyMeasurements.size} pomiarów" } ?: "Dodaj pierwszy pomiar"),
        "body_photos" to (bodyPhotos.maxByOrNull { it.id }?.let { "${bodyPhotos.size} zestawów • ostatni: ${it.date}" } ?: "Dodaj pierwszy zestaw zdjęć"),
        "renpho" to (latestScale?.let { "Ostatni pomiar: $it" } ?: "Brak zapisanych danych składu ciała"),
        "effects" to (effectsSettings.reductionPlan?.let { "Plan: ${it.stages.size} etapów • ${it.stages.sumOf { stage -> stage.weeks }} tygodni" } ?: "Trend wagi, bilans i plan etapów redukcji"),
        "nutrition_week_review" to "Ocena ostatnich 7 zakończonych dni",
        "analysis" to if (isLoading) "Trwa przygotowanie raportu…" else "Raport tygodnia względem Twojego celu",
        "appearance" to "${if (MangoAppearance.light) "Jasny kremowy" else "Ciemny"} • ${if (reducedMotion) "ograniczone animacje" else "animacje włączone"}",
        "reminder_settings" to "$enabledReminders aktywnych • cisza ${reminders.quietStart}–${reminders.quietEnd}",
        "recipe_photos" to if (photoAuto) "Automatyczne zdjęcia: włączone" else "Automatyczne zdjęcia: wyłączone",
        "gemini" to if (localGemini) "Tryb lokalny • Gemini wyłączone" else if (!hasGeminiKey) "Brak klucza Gemini" else "Klucz zapisany • tryb Gemini",
        "backup" to if (maxOf(backupStatus.lastAutomatic, backupStatus.lastExport) == 0L) "Kopia jeszcze niewykonana" else "${if (backupStatus.lastAutomatic >= backupStatus.lastExport) "Kopia lokalna" else "Kopia do pliku"}: ${viewModel.backup.status.date(maxOf(backupStatus.lastAutomatic, backupStatus.lastExport))}",
        "about" to "FITMango ${com.example.BuildConfig.VERSION_NAME} • build ${com.example.BuildConfig.VERSION_CODE}"
    ) }
    fun openDetail(id: String) {
        detail?.let { parentDetails = parentDetails + it }
        detail = id
    }
    val returnToHub: () -> Unit = {
        detail = parentDetails.lastOrNull()
        parentDetails = parentDetails.dropLast(1)
    }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(detail) { focus.clearFocus(); keyboard?.hide() }
    BackHandler(enabled = detail != null, onBack = returnToHub)
    if (detail == "effects") {
        detailStates.SaveableStateProvider("profile_detail_effects") {
            EffectsScreen(viewModel, onBack = returnToHub, onProfile = { openDetail("calculator") })
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ProfileHubCatalog.title(detail), fontWeight = FontWeight.Black) },
                navigationIcon = { if (detail != null) TextButton(onClick = returnToHub, modifier = Modifier.testTag("profile_detail_back")) { Text("Wróć") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Cream
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                ProfileHub(tab, { tab = it }, listStates.getValue(tab), summaries, { openDetail(it) })
            }
        } else detailStates.SaveableStateProvider("profile_detail_" + detail) {
            CompositionLocalProvider(LocalStandaloneProfileSection provides true) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .testTag("profile_content")
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, WarmBrown.copy(alpha = 0.2f))
                    )
                )
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (detail == "history") item { ChangeHistoryCard(viewModel) }
            if (detail == "nutrition_attention") item { NutritionAttentionCard(viewModel) }
            if (detail == "about") item {
                Text("FITMango ${com.example.BuildConfig.VERSION_NAME} • build ${com.example.BuildConfig.VERSION_CODE}",
                    modifier = Modifier.testTag("installed_build_label"), color = GoldAccent)
                ReleaseNotesControl()
            }
            if (detail == "calculator") {
            // Profile & TDEE Calculator Card
            item {
                CollapsibleMangoCard(title = "Kalkulator kalorii i profil", sectionId = "calculator") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Kalkulator TDEE i Cel Kaloryczny", fontWeight = FontWeight.Bold, color = MangoYellow, fontSize = 16.sp)
                        Text("Szacunek Mifflin–St Jeor, opis aktywności i późniejsza weryfikacja trendem. Cel zapisujesz po podglądzie.", fontSize = 11.sp, color = Cream.copy(alpha = 0.7f))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Imię") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Wiek") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                            OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Wzrost (cm)") }, modifier = Modifier.weight(1.2f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                            OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Waga (kg)") }, modifier = Modifier.weight(1.2f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                        }

                        com.example.ui.components.EnergyChoice("Płeć do wzoru", gender, linkedMapOf("Kobieta" to "Kobieta", "Mężczyzna" to "Mężczyzna")) { gender = it }
                        com.example.ui.components.EnergyChoice("Dotychczasowy poziom aktywności", activity,
                            listOf("Niska aktywność", "Umiarkowana aktywność", "Wysoka aktywność", "Bardzo wysoka aktywność").associateWith { it }) { activity = it }
                        Text("Cel:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Cream)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("Redukcja", "Utrzymanie", "Budowanie masy").forEach { g ->
                                FilterChip(
                                    selected = goal == g,
                                    onClick = { goal = g },
                                    label = { Text(g, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = JuicyOrange, selectedLabelColor = DarkBackground)
                                )
                            }
                        }

                        com.example.ui.components.EnergyCalculatorPanel(viewModel,
                            com.example.data.UserProfileEntity(name = name, age = age.toIntOrNull() ?: 0,
                                heightCm = height.replace(',', '.').toFloatOrNull() ?: 0f,
                                currentWeightKg = weight.replace(',', '.').toFloatOrNull() ?: 0f,
                                gender = gender, activityLevel = activity, goal = goal), { openDetail("effects") })

                        profile?.let { p ->
                            HorizontalDivider(color = WarmBrown.copy(alpha = 0.3f))
                            Text("Obecnie zapisane cele:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MangoYellow)
                            Text("Cel jedzenia: ${p.targetCalories} kcal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Cream)
                            Text("💪 Białko: ${p.targetProtein}g | Węglowodany: ${p.targetCarbs}g | Tłuszcz: ${p.targetFat}g", fontSize = 11.sp, color = Cream.copy(alpha = 0.8f))
                            TextButton(onClick = { targetError = null; showTargetDialog = true }) {
                                Text("Ustaw własne cele kalorii i makro", color = MangoYellow)
                            }
                        }
                    }
                }
            }

            }
            if (detail == "reminder_settings") item { ReminderSettingsCard() }
            if (detail == "appearance") {
                item { AppearanceSettingsCard() }
                item { MangoMotionSettings() }
            }
            if (detail == "backup") item { BackupSettingsCard(viewModel) }
            if (detail == "recipe_photos") item { RecipePhotoSettingsCard(viewModel) }

            if (detail == "gemini") {
            // Ustawienia Gemini AI Card
            item {
                CollapsibleMangoCard(title = "Ustawienia Gemini AI", sectionId = "gemini") {
                    var localOnly by remember { mutableStateOf(com.example.ai.SecureKeyStorage.localOnly()) }
                    Text("FITMango jest bezpłatne do Twojego użytku. Tryb lokalny blokuje zapytania do Gemini; nie wymaga klucza ani płatnej subskrypcji. Rozpoznawanie zdjęć przez AI i czat Gemini są wtedy wyłączone.")
                    Row(Modifier.fillMaxWidth()) {
                        Text("Tryb lokalny bez Gemini", Modifier.weight(1f))
                        Switch(localOnly, { localOnly = it; com.example.ai.SecureKeyStorage.setLocalOnly(it) })
                    }
                    if (!localOnly) Text("Koszt Gemini zależy od konta Google i rozliczeń przypisanych do klucza. Aplikacja nie potrafi sprawdzić, czy klucz korzysta wyłącznie z darmowego limitu.")
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = OrangeInk,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Ustawienia Gemini AI",
                                fontWeight = FontWeight.Bold,
                                color = MangoYellow,
                                fontSize = 16.sp
                            )
                        }

                        Text(
                            text = "Aplikacja MangoFit nie przechowuje Twojego klucza API na zewnętrznych serwerach. Jest on szyfrowany lokalnie w zabezpieczonej pamięci telefonu (Android Keystore).",
                            fontSize = 11.sp,
                            color = Cream.copy(alpha = 0.7f)
                        )

                        OutlinedTextField(
                            value = geminiKeyInput,
                            onValueChange = { geminiKeyInput = it },
                            label = { Text("Klucz API Gemini") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_key_input"),
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isKeyVisible) "Ukryj" else "Pokaż",
                                        tint = Cream.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JuicyOrange,
                                unfocusedBorderColor = WarmBrown,
                                focusedTextColor = Cream,
                                unfocusedTextColor = Cream
                            )
                        )

                        // Status Indicator
                        testStatus?.let { status ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MutedSurface2,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    color = testStatusColor,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .testTag("gemini_test_status_text")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        SecureKeyStorage.saveKey(geminiKeyInput.trim())
                                        testStatus = "Klucz został zapisany pomyślnie w bezpiecznym pęku kluczy (Android Keystore)."
                                        testStatusColor = LeafGreen
                                    } catch (_: Exception) {
                                        testStatus = "Nie udało się zapisać klucza w Android Keystore. Spróbuj ponownie."
                                        testStatusColor = Color.Red
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .pressScaleEffect()
                                    .testTag("gemini_save_key_button")
                            ) {
                                Text("Zapisz", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (geminiKeyInput.isBlank()) {
                                        testStatus = "Wpisz najpierw klucz API Gemini, aby go przetestować."
                                        testStatusColor = ErrorInk
                                        return@Button
                                    }
                                    isTesting = true
                                    testStatus = "Sprawdzanie listy modeli i generowania odpowiedzi..."
                                    testStatusColor = MangoYellow
                                    scope.launch {
                                        val result = diagnoseGemini(geminiKeyInput.trim())
                                        isTesting = false
                                        result.onSuccess {
                                            testStatus = it
                                            testStatusColor = LeafGreen
                                        }.onFailure { e ->
                                            testStatus = e.message ?: "Błąd połączenia."
                                            testStatusColor = ErrorInk
                                        }
                                    }
                                },
                                enabled = !isTesting,
                                colors = ButtonDefaults.buttonColors(containerColor = MutedSurface2, contentColor = Cream),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .pressScaleEffect()
                                    .testTag("gemini_test_connection_button")
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Cream)
                                } else {
                                    Text("Testuj", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        if (SecureKeyStorage.hasKey()) {
                            TextButton(
                                onClick = {
                                    SecureKeyStorage.deleteKey()
                                    geminiKeyInput = ""
                                    testStatus = "Klucz został usunięty z bezpiecznego pęku kluczy."
                                    testStatusColor = Color.Gray
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("gemini_delete_key_button")
                            ) {
                                Text("Usuń klucz", color = ErrorInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            }
            if (detail == "health") {
            // Health Connect Card ("Zdrowie i aktywność")
            item {
                val context = LocalContext.current
                val healthSync by viewModel.healthConnect.healthConnectSync.collectAsStateWithLifecycle()
                val isSyncing by viewModel.healthConnect.isSyncing.collectAsStateWithLifecycle()
                val statusMessage by viewModel.healthConnect.statusMessage.collectAsStateWithLifecycle()
                val isConnected = healthSync?.isConnected == true
                val lastSyncTime = healthSync?.lastSyncTimeMillis ?: 0L

                val healthPermissionsLauncher = rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract()
                ) { granted ->
                    viewModel.healthConnect.onPermissionsResult(granted)
                }

                val backgroundPermissionsLauncher = rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract()
                ) { viewModel.healthConnect.onBackgroundPermissionsResult() }

                LaunchedEffect(Unit) {
                    viewModel.healthConnect.checkStatus()
                }

                val sleepReadMessage by viewModel.healthConnect.sleepReadMessage.collectAsStateWithLifecycle()
                val healthGrantedPermissions by viewModel.healthConnect.grantedPermissions.collectAsStateWithLifecycle()
                CollapsibleMangoCard(title = "Zdrowie i aktywność", sectionId = "health", modifier = Modifier.testTag("health_connect_section_card")) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.example.ui.components.FitMangoAssetIcon(com.example.R.drawable.fit_icon_health, modifier = Modifier.size(24.dp))
                            Text(
                                text = "Zdrowie i aktywność",
                                fontWeight = FontWeight.Bold,
                                color = MangoYellow,
                                fontSize = 16.sp
                            )
                        }

                        if (sleepReadMessage.isNotBlank()) Text(sleepReadMessage, style = MaterialTheme.typography.bodySmall)
                        if (isConnected && androidx.health.connect.client.permission.HealthPermission.getReadPermission(
                            androidx.health.connect.client.records.SleepSessionRecord::class) !in healthGrantedPermissions) {
                            OutlinedButton(onClick = {
                                healthPermissionsLauncher.launch(setOf(androidx.health.connect.client.permission.HealthPermission.getReadPermission(
                                    androidx.health.connect.client.records.SleepSessionRecord::class)))
                            }, modifier = Modifier.fillMaxWidth().testTag("health_sleep_permission")) { Text("Zezwól na odczyt snu") }
                        }
                        Text(
                            text = "Połącz z Google Health Connect, aby bezpiecznie pobierać kroki, spalone kalorie, wagę i sen z Twojego zegarka lub opaski.",
                            fontSize = 12.sp,
                            color = Cream.copy(alpha = 0.8f)
                        )

                        // Status Info
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MutedSurface2,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isConnected) LeafGreen else Color.Gray)
                                    )
                                    Text(
                                        text = if (isConnected) "Połączono" else "Niepołączono",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isConnected) LeafGreen else Cream.copy(alpha = 0.6f),
                                        modifier = Modifier.testTag("health_connect_status_text")
                                    )
                                }
                                statusMessage?.let { msg ->
                                    Text(
                                        text = msg,
                                        fontSize = 11.sp,
                                        color = Cream.copy(alpha = 0.75f)
                                    )
                                }
                                Text(
                                    text = "Ostatnia synchronizacja: ${HealthConnectSupport.formatLastSyncTime(lastSyncTime)}",
                                    fontSize = 11.sp,
                                    color = GoldAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("health_connect_last_sync_text")
                                )
                            }
                        }

                        val automaticSync by viewModel.healthConnect.automaticSync.collectAsStateWithLifecycle()
                        if (isConnected) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Automatyczna synchronizacja", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = automaticSync, onCheckedChange = { viewModel.healthConnect.setAutomaticSync(it) },
                                    modifier = Modifier.testTag("health_connect_auto_sync_switch"))
                            }
                            Text("Podczas używania aplikacji: co 5 minut. W tle: zwykle co około 15 minut, gdy telefon na to pozwala. Odczyt w tle wymaga dodatkowej zgody.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                            if (automaticSync) OutlinedButton(onClick = {
                                viewModel.healthConnect.requestBackgroundRead { backgroundPermissionsLauncher.launch(it) }
                            }, modifier = Modifier.fillMaxWidth().testTag("health_connect_background_permission")) {
                                Text("Zezwól na synchronizację w tle")
                            }
                        }

                        if (!isConnected) {
                            MangoPrimaryButton(
                                text = "Połącz z Health Connect",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("health_connect_connect_button"),
                                onClick = {
                                    viewModel.healthConnect.requestConnect { perms ->
                                        healthPermissionsLauncher.launch(perms)
                                    }
                                }
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.healthConnect.sync(force = true) },
                                    enabled = !isSyncing,
                                    colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .pressScaleEffect()
                                        .testTag("health_connect_sync_now_button")
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                                    } else {
                                        Text("Synchronizuj teraz", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        val settingsIntent = HealthConnectManager.createHealthConnectSettingsIntent(context)
                                        try {
                                            context.startActivity(settingsIntent)
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MutedSurface2, contentColor = Cream),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .pressScaleEffect()
                                        .testTag("health_connect_manage_permissions_button")
                                ) {
                                    Text("Zarządzaj uprawnieniami", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            TextButton(
                                onClick = { viewModel.healthConnect.disconnect() },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("health_connect_disconnect_button")
                            ) {
                                Text("Odłącz", color = ErrorInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            }
            if (detail == "nutrition_week_review") item { NutritionWeekCard(viewModel) }

            if (detail == "analysis") {
            // Weekly AI Progress Analysis Card
            item {
                CollapsibleMangoCard(title = "Analiza dietetyczna AI", sectionId = "analysis") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MangoYellow, modifier = Modifier.size(20.dp))
                            Text("Analiza dietetyczna AI", fontWeight = FontWeight.Bold, color = Cream, fontSize = 16.sp)
                        }
                        Text("Analiza w stylu konsultacji dietetycznej: AI łączy zapisane jedzenie i makroskładniki z celem, trendem masy ciała, obwodami, aktywnością, snem i samopoczuciem. Sprawdza ostatnie 7 zakończonych dni i porównuje jedzenie z poprzednim tygodniem. Wskazuje mocne strony, powtarzające się wzorce i maksymalnie 3 konkretne priorytety na kolejny tydzień. Dzisiejszy dzień nie wchodzi do raportu. Zatwierdzone dni pozostają pełnym, potwierdzonym zapisem; szacunkowe wartości odżywcze są osobno oznaczane.", fontSize = 13.sp)
                        Text("Po naciśnięciu przycisku te dane zostaną wysłane do Gemini. Używane są ostatnio zsynchronizowane dane aktywności; odśwież je w sekcji Sport lub Health Connect. Tryb lokalny blokuje wysyłanie.", fontSize = 12.sp)
                        Text("Raport pokazuje lokalne obliczenia i ograniczenia danych przed interpretacją AI. AI nie dopowiada niezmierzonych składników ani nie zmienia samodzielnie Twojego celu. To narzędzie wspierające, nie diagnoza ani zamiennik indywidualnej konsultacji z dietetykiem. Po zmianie posiłków, pomiarów lub synchronizacji wygeneruj raport ponownie.", fontSize = 12.sp)
                        Text(weeklyAnalysis, fontSize = 13.sp, color = Cream.copy(alpha = 0.9f))

                        Button(
                            onClick = { viewModel.runWeeklyAiAnalysis() },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.pressScaleEffect()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                            } else {
                                Text("Przygotuj analizę dietetyczną", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            }
            if (detail == "measurements") {
            // Body Measurements Tracker Card
            item {
                CollapsibleMangoCard(title = "Pomiary ciała", sectionId = "measurements") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Wymiary Ciała i Obwody", fontWeight = FontWeight.Bold, color = MangoYellow, fontSize = 16.sp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Waga (kg)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                            OutlinedTextField(value = waist, onValueChange = { waist = it }, label = { Text("Talia (cm)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = hips, onValueChange = { hips = it }, label = { Text("Biodra (cm)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                            OutlinedTextField(value = chest, onValueChange = { chest = it }, label = { Text("Klatka (cm)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = arms, onValueChange = { arms = it }, label = { Text("Ramię (cm)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                            OutlinedTextField(value = thighs, onValueChange = { thighs = it }, label = { Text("Udo (cm)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                        }

                        MangoPrimaryButton(
                            text = "Zapisz Nowy Wpis",
                            onClick = {
                                viewModel.addBodyMeasurement(
                                    weightKg = weight.toFloatOrNull() ?: 70f,
                                    waist = waist.toFloatOrNull() ?: 0f,
                                    hips = hips.toFloatOrNull() ?: 0f,
                                    chest = chest.toFloatOrNull() ?: 0f,
                                    arms = arms.toFloatOrNull() ?: 0f,
                                    thighs = thighs.toFloatOrNull() ?: 0f
                                )
                                waist = ""; hips = ""; chest = ""; arms = ""; thighs = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Historia pomiarów:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldAccent)
                        bodyMeasurements.take(3).forEach { m ->
                            Text("• ${m.date}: ${m.weightKg}kg | Talia: ${m.waistCm}cm | Biodra: ${m.hipsCm}cm", fontSize = 12.sp, color = Cream.copy(alpha = 0.8f))
                        }

                        onNavigateToMeasurements?.let { navigate ->
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = navigate,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Zobacz pełną historię i wykresy 📊", color = MangoYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            }
            if (detail == "renpho") item {
                CollapsibleMangoCard(title = "Renpho — skład ciała", sectionId = "renpho") {
                    ScaleMeasurementsPanel(viewModel)
                }
            }

            // Body Silhouette Photos
            if (detail == "body_photos" || detail == "body_photos_new") item {
                CollapsibleMangoCard(title = "Zdjęcia sylwetki", sectionId = "body_photos") {
                    BodyPhotosCard(bodyPhotos,
                        onSave = { front, side, back, done -> viewModel.addBodyPhoto(front, side, back, done) },
                        onReplace = { id, angle, uri, done -> viewModel.replaceBodyPhoto(id, angle, uri, done) },
                        onDelete = { id, done -> viewModel.deleteBodyPhoto(id, done) },
                        onSecureLegacy = { viewModel.secureLegacyBodyPhotos() }, newFirst = detail == "body_photos_new")
                }
            }
        }
            }
        }
    }
}
