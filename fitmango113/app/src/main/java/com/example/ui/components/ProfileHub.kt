package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.MutedSurface1
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class ProfileHubSection(val id: String, val title: String, val tab: String, val artwork: String)

object ProfileHubCatalog {
    val tabs = linkedMapOf("profile" to "Mój profil", "progress" to "Postępy", "settings" to "Ustawienia")
    val sections = listOf(
        ProfileHubSection("calculator", "Profil, kalorie i makro", "profile", "calculator"),
        ProfileHubSection("health", "Zdrowie i aktywność", "profile", "health"),
        ProfileHubSection("nutrition_attention", "Uzupełnij brakujące produkty", "profile", "calories"),
        ProfileHubSection("history", "Historia zmian", "profile", "backup"),
        ProfileHubSection("measurements", "Pomiary ciała", "progress", "measurements"),
        ProfileHubSection("body_photos", "Zdjęcia sylwetki", "progress", "body_photos"),
        ProfileHubSection("renpho", "Renpho — skład ciała", "progress", "renpho"),
        ProfileHubSection("effects", "Efekty i etapy redukcji", "progress", "effects"),
        ProfileHubSection("nutrition_week_review", "Ocena jadłospisu tygodnia", "progress", "weekly_summary"),
        ProfileHubSection("analysis", "Analiza postępów AI", "progress", "analysis"),
        ProfileHubSection("appearance", "Wygląd i animacje", "settings", "appearance"),
        ProfileHubSection("reminder_settings", "Przypomnienia i godziny ciszy", "settings", "reminder_settings"),
        ProfileHubSection("recipe_photos", "Automatyczne zdjęcia potraw", "settings", "photos"),
        ProfileHubSection("gemini", "Ustawienia Gemini AI", "settings", "gemini"),
        ProfileHubSection("backup", "Kopia zapasowa", "settings", "backup"),
        ProfileHubSection("about", "Informacje o aplikacji", "settings", "calculator")
    )
    fun title(id: String?) = if (id == "body_photos_new") "Dodaj zdjęcia sylwetki" else sections.find { it.id == id }?.title ?: "Profil i postępy"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHub(tab: String, onTab: (String) -> Unit, listState: LazyListState,
    summaries: Map<String, String>, onOpen: (String) -> Unit) {
    val keys = ProfileHubCatalog.tabs.keys.toList()
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = keys.indexOf(tab).coerceAtLeast(0)) {
            ProfileHubCatalog.tabs.forEach { (id, label) ->
                Tab(selected = tab == id, onClick = { onTab(id) }, modifier = Modifier.testTag("profile_tab_$id"), text = { Text(label) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("measurements" to "Dodaj pomiar", "body_photos_new" to "Dodaj zdjęcia", "effects" to "Zobacz postępy").forEach { (id, label) ->
                OutlinedButton(onClick = { onOpen(id) }, modifier = Modifier.weight(1f).heightIn(min = 60.dp).testTag("profile_quick_$id"),
                    contentPadding = PaddingValues(6.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("profile_content"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ProfileHubCatalog.sections.filter { it.tab == tab }, key = { it.id }) { section ->
                Card(onClick = { onOpen(section.id) }, colors = CardDefaults.cardColors(containerColor = MutedSurface1), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("profile_tile_${section.id}")) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MangoSectionArt(sectionArtwork(section.artwork), sectionArtworkAccent(section.artwork), size = 32.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall)
                            Text(summaries[section.id].orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}
