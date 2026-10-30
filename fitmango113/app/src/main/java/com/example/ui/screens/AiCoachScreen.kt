package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(viewModel: DietaViewModel, onEffects: () -> Unit = {}) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingAi.collectAsStateWithLifecycle()
    var recipeText by remember { mutableStateOf<String?>(null) }
    var savedFeedback by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                actions = { IconButton(onClick = onEffects) { Icon(Icons.Default.TrendingUp, "Efekty i analiza") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MangoYellow, modifier = Modifier.size(24.dp))
                        Column {
                            Text("AI Dietetyk Mango", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Cream)
                            Text("Twój osobisty przewodnik zdrowia", fontSize = 10.sp, color = GoldAccent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Cream
                )
            )
        },
        bottomBar = {
            ChatComposer(isLoading) { viewModel.sendChatMessage(it) }
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, WarmBrown.copy(alpha = 0.3f))
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SuccessFeedback(savedFeedback, "Zapisano przepis")
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                reverseLayout = false
            ) {
                items(messages) { msg ->
                    val isUser = msg.role == "user"
                    val canImport = remember(msg.content, isUser) { !isUser && !msg.content.startsWith("Błąd:") &&
                        (com.example.viewmodel.ChatRecipeSupport.draft(msg.content) != null ||
                            (Regex("składniki|skladniki|ingredients", RegexOption.IGNORE_CASE).containsMatchIn(msg.content) && Regex("przygotowanie|instructions|wykonanie", RegexOption.IGNORE_CASE).containsMatchIn(msg.content))) }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomStart = if (isUser) 20.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 20.dp
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isUser) MangoYellow.copy(alpha = 0.3f) else WarmBrown.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomStart = if (isUser) 20.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 20.dp
                                    )
                                ),
                            color = if (isUser) JuicyOrange else MutedSurface1
                        ) {
                            Column {
                            Text(
                                text = msg.content,
                                modifier = Modifier.padding(14.dp),
                                color = if (isUser) DarkBackground else Cream,
                                fontSize = 14.sp,
                                fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal
                            )
                            if (canImport) {
                                TextButton(enabled = !isLoading, onClick = { recipeText = msg.content }) { Text("Dodaj przepis do jadłospisu →") }
                            }
                            }
                        }
                    }
                }
                if (isLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OrangeInk)
                            Text("Pisze...", fontSize = 12.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    recipeText?.let { text -> ChatRecipeDialog(text, viewModel, onDismiss = { recipeText = null }, onSaved = { recipeText = null; savedFeedback++ }) }
}

@Composable
internal fun ChatComposer(isLoading: Boolean, onSend: (String) -> Unit) {
    var inputMessage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkBackground,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        modifier = Modifier.weight(1f).testTag("chat_message_input"),
                        placeholder = { Text("Zapytaj o dietę, przepisy, kalorie...", color = Cream.copy(alpha = 0.4f)) },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JuicyOrange,
                            unfocusedBorderColor = WarmBrown,
                            focusedContainerColor = MutedSurface1,
                            unfocusedContainerColor = MutedSurface1,
                            focusedTextColor = Cream,
                            unfocusedTextColor = Cream
                        )
                    )
                    IconButton(
                        enabled = !isLoading,
                        onClick = {
                            if (inputMessage.isNotBlank()) {
                                onSend(inputMessage)
                                inputMessage = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(MangoYellow, JuicyOrange)))
                            .pressScaleEffect()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Wyślij", tint = Cream, modifier = Modifier.size(20.dp))
                    }
                }
            }
}
