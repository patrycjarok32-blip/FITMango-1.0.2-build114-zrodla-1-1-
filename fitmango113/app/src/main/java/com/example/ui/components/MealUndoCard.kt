package com.example.ui.components

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.DietaViewModel

@Composable
fun MealUndoCard(viewModel: DietaViewModel) {
    val label by viewModel.mealUndoLabel.collectAsStateWithLifecycle()
    val message by viewModel.mealUndoMessage.collectAsStateWithLifecycle()
    if (label != null || message != null) {
        Surface(Modifier.fillMaxWidth().testTag("meal_undo_card"), shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer) {
            Column {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label ?: message.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (label != null) TextButton(onClick = { viewModel.undoLastMealChange() }, modifier = Modifier.testTag("meal_undo_button")) { Text("Cofnij") }
                    IconButton(onClick = { viewModel.dismissMealUndo() }) { Icon(Icons.Default.Close, "Zamknij informację o zmianie") }
                }
                if (label != null && message != null) Text(message.orEmpty(), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
