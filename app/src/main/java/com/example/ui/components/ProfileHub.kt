package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.MutedSurface1
import com.example.ui.theme.MutedSurface2
import com.example.ui.theme.JuicyOrange
import com.example.ui.theme.LeafGreen
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var expanded by remember { mutableStateOf(false) }
    val featured = listOf(
        "calculator" to "Mój profil",
        "health" to "Health Connect",
        "measurements" to "Pomiary ciała",
        "effects" to "Moje postępy",
        "backup" to "Kopia danych",
        "appearance" to "Ustawienia aplikacji",
        "about" to "O aplikacji"
    )
    val featuredIds = featured.map { it.first }.toSet()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("profile_content"),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text("Wszystko w jednym miejscu", style = MaterialTheme.typography.bodyMedium,
                color = LeafGreen, modifier = Modifier.padding(bottom = 6.dp))
        }
        items(featured, key = { "featured_" + it.first }) { (id, label) ->
            val section = ProfileHubCatalog.sections.first { it.id == id }
            Card(onClick = { onOpen(id) },
                colors = CardDefaults.cardColors(containerColor = MutedSurface1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().testTag("profile_tile_$id")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MangoSectionArt(sectionArtwork(section.artwork), sectionArtworkAccent(section.artwork), size = 32.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(summaries[id].orEmpty().ifBlank { section.title },
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = JuicyOrange)
                }
            }
        }
        item {
            OutlinedButton(onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().testTag("profile_more_sections")) {
                Text(if (expanded) "Zwiń pozostałe funkcje" else "Wszystkie pozostałe funkcje")
            }
        }
        if (expanded) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileHubCatalog.tabs.forEach { (id, label) ->
                        FilterChip(selected = tab == id, onClick = { onTab(id) },
                            label = { Text(label) })
                    }
                }
            }
            items(ProfileHubCatalog.sections.filter { it.id !in featuredIds && it.tab == tab },
                key = { "extra_" + it.id }) { section ->
                Card(onClick = { onOpen(section.id) },
                    colors = CardDefaults.cardColors(containerColor = MutedSurface1),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("profile_tile_${section.id}")) {
                    Row(Modifier.fillMaxWidth().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MangoSectionArt(sectionArtwork(section.artwork), sectionArtworkAccent(section.artwork), size = 28.dp)
                        Column(Modifier.weight(1f)) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall)
                            Text(summaries[section.id].orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", color = JuicyOrange)
                    }
                }
            }
        }
    }
}
