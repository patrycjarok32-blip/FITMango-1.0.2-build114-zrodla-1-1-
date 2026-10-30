package com.example.ui.components

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.RecipePhotoSettings

@Composable
fun RecipePhotoSettingsCard(viewModel: DietaViewModel) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val settings = remember(context) { RecipePhotoSettings(context) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var pixabay by remember { mutableStateOf(settings.token("pixabay")) }
    var hf by remember { mutableStateOf(settings.token("hf")) }
    var notice by remember { mutableStateOf(settings.prefs.getString("status", "Zdjęcia są dobierane automatycznie po imporcie.").orEmpty()) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "status") notice = prefs.getString("status", "").orEmpty()
            if (key == "enabled") enabled = settings.enabled
        }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CollapsibleMangoCard("Automatyczne zdjęcia potraw", "photos") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Dobieraj po imporcie", Modifier.weight(1f))
                Switch(enabled, onCheckedChange = {
                    enabled = it; settings.enabled = it
                    if (it) viewModel.fillMissingRecipePhotos(context) else viewModel.cancelRecipePhotoFill()
                })
            }
            Text("Najpierw szukamy pasującego zdjęcia w Pixabay. Jeśli go nie ma, generujemy ilustrację AI w darmowym limicie. Zdjęcia dodane przez Ciebie i pobrane z przepisu zachowujemy.")
            OutlinedTextField(pixabay, onValueChange = { pixabay = it }, label = { Text("Darmowy klucz Pixabay") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { links.openUri("https://pixabay.com/api/docs/") }) { Text("Uzyskaj darmowy klucz Pixabay") }
            OutlinedTextField(hf, onValueChange = { hf = it }, label = { Text("Token Hugging Face — opcjonalnie") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("AI działa też bez tokenu, ze wspólnym limitem. Własny token daje limit Twojego konta. Nie korzystamy z płatnej usługi.")
            TextButton(onClick = { links.openUri("https://huggingface.co/settings/tokens") }) { Text("Ustawienia tokenu Hugging Face") }
            Button(onClick = {
                try {
                    // Saving unchanged credentials must not reset the provider's rate limit.
                    if (settings.token("pixabay") != pixabay.trim()) settings.saveToken("pixabay", pixabay)
                    if (settings.token("hf") != hf.trim()) settings.saveToken("hf", hf)
                    settings.enabled = enabled
                    if (enabled) viewModel.fillMissingRecipePhotos(context)
                    settings.status("Zapisano ustawienia. Klucze są przechowywane tylko na telefonie.")
                } catch (_: Exception) { notice = "Nie udało się bezpiecznie zapisać klucza. Spróbuj ponownie." }
            }) { Text("Zapisz i uzupełnij zdjęcia") }
            Text(notice, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { links.openUri("https://pixabay.com") }) { Text("Zdjęcia: Pixabay i autorzy") }
        }
    }
}
