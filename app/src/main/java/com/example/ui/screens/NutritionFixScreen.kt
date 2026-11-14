package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.NutritionAttentionCard
import com.example.viewmodel.DietaViewModel

/** Focused entry from the dashboard warning; existing meal and recipe data remain unchanged. */
@Composable
fun NutritionFixScreen(viewModel: DietaViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Wróć") }
        Text("Uzupełnij brakujące składniki", style = MaterialTheme.typography.headlineSmall)
        Text("Otwieramy pierwszy produkt z niepełnymi danymi. Po zapisaniu lista braków zostanie przeliczona; możesz też wybrać inny produkt poniżej.")
        NutritionAttentionCard(viewModel, openFirstMissing = true)
    }
}
