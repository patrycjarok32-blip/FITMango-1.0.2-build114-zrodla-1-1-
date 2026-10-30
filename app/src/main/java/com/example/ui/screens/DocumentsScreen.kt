package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DocumentEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.ParsedRecipe
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(viewModel: DietaViewModel, onNavigateHome: () -> Unit = {}) {
    BackHandler(onBack = onNavigateHome)
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    var savedAnimationEvent by remember { mutableIntStateOf(0) }
    val isLoading by viewModel.isLoadingAi.collectAsStateWithLifecycle()
    val nextChunkIndexMap by viewModel.pdfNextChunkIndex.collectAsStateWithLifecycle()
    val foundRecipesMap by viewModel.pdfFoundRecipes.collectAsStateWithLifecycle()

    var selectedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var parsedRecipesPreview by remember { mutableStateOf<List<ParsedRecipe>?>(null) }
    var selectedRecipeIndices by remember { mutableStateOf(setOf<Int>()) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            var fileName = "Imported_Document.pdf"
            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && nameIndex != -1) {
                    fileName = c.getString(nameIndex)
                }
            }
            viewModel.importPdf(context, it, fileName) { success, error ->
                if (success) savedAnimationEvent++
                if (!success) {
                    errorMessage = error ?: "Nie udało się zaimportować pliku PDF."
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje Dokumenty (PDF)", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome, modifier = Modifier.testTag("documents_home")) {
                        Icon(Icons.Default.Home, contentDescription = "Powrót do strony głównej")
                    }
                },
                actions = {
                    Button(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.pressScaleEffect()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dodaj PDF", fontWeight = FontWeight.Bold)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ImportSavedFeedback(savedAnimationEvent, "Zapisano")
            if (isLoading) {
                ImportProgress()
            }

            errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MutedSurface2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Red, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(err, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { errorMessage = null }) {
                            Text("OK", color = MangoYellow)
                        }
                    }
                }
            }

            if (documents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(48.dp), tint = WarmBrown)
                        Text("Brak zaimportowanych dokumentów PDF.", color = Cream.copy(alpha = 0.6f))
                        Text("Dodaj plik jadłospisu, aby AI wyodrębniło z niego posiłki.", fontSize = 12.sp, color = GoldAccent)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(documents) { doc ->
                        MangoCard(
                            modifier = Modifier.clickable { selectedDoc = doc }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = OrangeInk, modifier = Modifier.size(36.dp))
                                    Column {
                                        Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Cream, maxLines = 1)
                                        Text("Dodano: ${doc.dateAdded}", fontSize = 11.sp, color = Cream.copy(alpha = 0.6f))
                                        
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        // Analysis Status Bar
                                        val analysisStates by viewModel.pdfAnalysisStates.collectAsStateWithLifecycle()
                                        val state = analysisStates[doc.id] ?: com.example.viewmodel.PdfAnalysisState.Idle

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            when (state) {
                                                is com.example.viewmodel.PdfAnalysisState.Idle -> {
                                                    if (doc.aiAnalysisResult.isNotBlank()) {
                                                        Text(doc.aiAnalysisResult, color = LeafGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    } else {
                                                        Text("Gotowy do analizy", color = Color.Gray, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                                                    }
                                                }
                                                is com.example.viewmodel.PdfAnalysisState.Extracting -> {
                                                    Text("Wyodrębnianie tekstu...", color = MangoYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                                is com.example.viewmodel.PdfAnalysisState.Analyzing -> {
                                                    Text("Analiza PDF: część ${state.current}/${state.total}", color = MangoYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    Text("Znaleziono dotąd: ${state.foundCount} przepisów", color = LeafGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                                is com.example.viewmodel.PdfAnalysisState.RateLimited -> {
                                                    Text("Analiza PDF: część ${state.current}/${state.total}", color = OrangeInk, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    Text("Znaleziono dotąd: ${state.foundCount} przepisów", color = LeafGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                                is com.example.viewmodel.PdfAnalysisState.Completed -> {
                                                    Text("Znaleziono ${state.recipeCount} przepisów", color = LeafGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                                is com.example.viewmodel.PdfAnalysisState.Failed -> {
                                                    Text("Błąd: ${state.error}", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    if (state.foundCount > 0) {
                                                        Text("Znaleziono dotąd: ${state.foundCount} przepisów", color = MangoYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteDocument(doc) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = Color.Red.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. Document Detail and Analysis Options
    selectedDoc?.let { initialDoc ->
        val doc = documents.find { it.id == initialDoc.id } ?: initialDoc
        val analysisStates by viewModel.pdfAnalysisStates.collectAsStateWithLifecycle()
        val state = analysisStates[doc.id] ?: com.example.viewmodel.PdfAnalysisState.Idle
        val nextChunk = nextChunkIndexMap[doc.id] ?: 0
        val partialFound = foundRecipesMap[doc.id].orEmpty()
        val isAnalyzing = state is com.example.viewmodel.PdfAnalysisState.Analyzing || state is com.example.viewmodel.PdfAnalysisState.Extracting

        AlertDialog(
            onDismissRequest = { if (!isAnalyzing) selectedDoc = null },
            containerColor = MutedSurface1,
            title = { Text(doc.title, color = Cream, fontWeight = FontWeight.Black) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (state) {
                        is com.example.viewmodel.PdfAnalysisState.Analyzing -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MutedSurface2,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Analiza PDF: część ${state.current}/${state.total}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MangoYellow)
                                    Text("Znaleziono dotąd: ${state.foundCount} przepisów", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LeafGreen)
                                    LinearProgressIndicator(
                                        progress = { state.current.toFloat() / state.total.coerceAtLeast(1).toFloat() },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        color = OrangeInk,
                                        trackColor = WarmBrown
                                    )
                                }
                            }
                        }
                        is com.example.viewmodel.PdfAnalysisState.RateLimited -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MutedSurface2,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Analiza PDF: część ${state.current}/${state.total}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OrangeInk)
                                    Text("Znaleziono dotąd: ${state.foundCount} przepisów", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LeafGreen)
                                }
                            }
                        }
                        is com.example.viewmodel.PdfAnalysisState.Completed -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MutedSurface2,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Znaleziono ${state.recipeCount} przepisów", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LeafGreen)
                                }
                            }
                        }
                        else -> {}
                    }

                    Text("Tekst z dokumentu PDF (${doc.extractedText.length} znaków):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Cream)
                    val textParagraphs = remember(doc.extractedText) {
                        if (doc.extractedText.isBlank()) listOf("Brak tekstu.")
                        else doc.extractedText.split("\n\n").filter { it.isNotBlank() }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        color = MutedSurface2,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(textParagraphs) { paragraph ->
                                Text(paragraph, fontSize = 12.sp, color = Cream)
                            }
                        }
                    }

                    if (doc.aiAnalysisResult.isNotBlank()) {
                        Text("Analiza AI:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MangoYellow)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            color = MutedSurface2,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                item {
                                    Text(doc.aiAnalysisResult, fontSize = 12.sp, color = Cream)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (nextChunk > 0 || state is com.example.viewmodel.PdfAnalysisState.RateLimited) {
                        MangoSecondaryButton(
                            text = "Kontynuuj analizę",
                            enabled = !isAnalyzing,
                            onClick = {
                                errorMessage = null
                                viewModel.parsePdfRecipes(doc, resume = true) { recipes, err ->
                                    if (err != null) {
                                        errorMessage = err
                                    } else if (recipes.isNotEmpty()) {
                                        parsedRecipesPreview = recipes
                                        selectedRecipeIndices = recipes.indices.toSet()
                                        selectedDoc = null
                                    }
                                }
                            }
                        )
                    }
                    MangoPrimaryButton(
                        text = "Analizuj z AI",
                        enabled = !isAnalyzing,
                        onClick = {
                            errorMessage = null
                            viewModel.parsePdfRecipes(doc, resume = false) { recipes, err ->
                                if (err != null) {
                                    errorMessage = err
                                } else if (recipes.isNotEmpty()) {
                                    parsedRecipesPreview = recipes
                                    selectedRecipeIndices = recipes.indices.toSet()
                                    selectedDoc = null
                                }
                            }
                        }
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (partialFound.isNotEmpty() && !isAnalyzing) {
                        TextButton(
                            onClick = {
                                parsedRecipesPreview = partialFound
                                selectedRecipeIndices = partialFound.indices.toSet()
                                selectedDoc = null
                            }
                        ) {
                            Text("Podgląd (${partialFound.size})", color = MangoYellow)
                        }
                    }
                    TextButton(
                        enabled = !isAnalyzing,
                        onClick = { selectedDoc = null }
                    ) {
                        Text("Zamknij", color = Color.Gray)
                    }
                }
            }
        )
    }

    // 2. Preview Dialog before saving parsed PDF recipes
    parsedRecipesPreview?.let { recipesList ->
        AlertDialog(
            onDismissRequest = { parsedRecipesPreview = null },
            containerColor = MutedSurface1,
            title = { Text("Znaleziono ${recipesList.size} przepisów", color = Cream, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Wybrano: ${selectedRecipeIndices.size}/${recipesList.size}",
                            fontSize = 12.sp,
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { selectedRecipeIndices = recipesList.indices.toSet() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Zaznacz wszystkie", fontSize = 11.sp, color = MangoYellow, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { selectedRecipeIndices = emptySet() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Odznacz wszystkie", fontSize = 11.sp, color = Cream.copy(alpha = 0.75f))
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recipesList.indices.toList()) { index ->
                            val recipe = recipesList[index]
                            val isSelected = selectedRecipeIndices.contains(index)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedRecipeIndices = if (isSelected) {
                                            selectedRecipeIndices - index
                                        } else {
                                            selectedRecipeIndices + index
                                        }
                                    }
                                    .border(1.dp, if (isSelected) JuicyOrange else Color.Transparent, RoundedCornerShape(16.dp)),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MutedSurface2 else WarmBrown.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${recipe.title}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Cream,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedRecipeIndices = if (checked) {
                                                    selectedRecipeIndices + index
                                                } else {
                                                    selectedRecipeIndices - index
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = JuicyOrange)
                                        )
                                    }
                                    Text(
                                        text = "Składniki: ${recipe.ingredients.replace("\n", ", ").take(110)}",
                                        fontSize = 11.sp,
                                        color = Cream.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "🔥 ${recipe.calories} kcal (B:${recipe.protein}g W:${recipe.carbs}g T:${recipe.fat}g) | 🍽️ ${recipe.servings} porcji",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MangoYellow
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MangoPrimaryButton(
                    text = "Zapisz zaznaczone",
                    enabled = selectedRecipeIndices.isNotEmpty(),
                    onClick = {
                        val selected = recipesList.filterIndexed { idx, _ -> selectedRecipeIndices.contains(idx) }
                        if (selected.isNotEmpty()) {
                            viewModel.saveParsedRecipesToRoom(selected) { error ->
                                if (error == null) savedAnimationEvent++
                                if (error != null) { errorMessage = error; parsedRecipesPreview = recipesList }
                            }
                        }
                        parsedRecipesPreview = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { parsedRecipesPreview = null }) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }
}
