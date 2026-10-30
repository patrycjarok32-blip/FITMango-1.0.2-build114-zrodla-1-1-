package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.RecipeCategories
import com.example.viewmodel.RecipeFilterSupport

@Composable
fun RecipeFilterChips(category: String, onCategory: (String) -> Unit,
    preparation: String, onPreparation: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("picker_category_filters")) {
            items(listOf("Wszystkie") + RecipeCategories.options) { option ->
                FilterChip(category == option, { onCategory(option) }, label = { Text(RecipeCategories.label(option)) },
                    modifier = Modifier.testTag("picker_category_$option"))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("picker_time_filters")) {
            items(RecipeFilterSupport.preparationOptions) { option ->
                FilterChip(preparation == option, { onPreparation(option) }, label = { Text(option) },
                    modifier = Modifier.testTag("picker_time_$option"))
            }
        }
    }
}
