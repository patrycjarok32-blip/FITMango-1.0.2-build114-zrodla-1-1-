package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.UserProfileEntity
import com.example.ui.theme.MutedSurface1

data class ProfileHubSection(
    val id: String,
    val title: String,
    val tab: String,
    val artwork: String
)

object ProfileHubCatalog {
    val tabs = linkedMapOf(
        "profile" to "Mój profil",
        "progress" to "Postępy",
        "settings" to "Ustawienia"
    )

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

    fun title(id: String?) =
        if (id == "body_photos_new") {
            "Dodaj zdjęcia sylwetki"
        } else {
            sections.find { it.id == id }?.title ?: "Profil i postępy"
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHub(
    tab: String,
    onTab: (String) -> Unit,
    listState: LazyListState,
    summaries: Map<String, String>,
    onOpen: (String) -> Unit,
    profile: UserProfileEntity?
) {
    val keys = ProfileHubCatalog.tabs.keys.toList()

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = keys.indexOf(tab).coerceAtLeast(0)
        ) {
            ProfileHubCatalog.tabs.forEach { (id, label) ->
                Tab(
                    selected = tab == id,
                    onClick = { onTab(id) },
                    modifier = Modifier.testTag("profile_tab_$id"),
                    text = { Text(label) }
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "measurements" to "Dodaj pomiar",
                "body_photos_new" to "Dodaj zdjęcia",
                "effects" to "Zobacz postępy"
            ).forEach { (id, label) ->
                OutlinedButton(
                    onClick = { onOpen(id) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .testTag("profile_quick_$id"),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("profile_content"),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tab == "profile" && profile != null) {
                item(key = "profile_summary_hero") {
                    ProfileSummaryHero(profile)
                }
            }

            items(
                ProfileHubCatalog.sections.filter { it.tab == tab },
                key = { it.id }
            ) { section ->
                Card(
                    onClick = { onOpen(section.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MutedSurface1
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_tile_${section.id}")
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MangoSectionArt(
                            sectionArtwork(section.artwork),
                            sectionArtworkAccent(section.artwork),
                            size = 36.dp
                        )

                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                summaries[section.id].orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            "›",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSummaryHero(profile: UserProfileEntity) {
    val heroGreen = Color(0xFF245C43)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = heroGreen
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_summary_hero")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "TWÓJ PLAN DIETETYCZNY",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Text(
                text = profile.name.ifBlank { "Twój profil" },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Text(
                text = "${profile.goal}  •  ${profile.currentWeightKg} kg",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = profile.targetCalories.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                Text(
                    "kcal / dzień",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.22f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MacroTarget(
                    "BIAŁKO",
                    profile.targetProtein,
                    Modifier.weight(1f)
                )
                MacroTarget(
                    "WĘGLOW.",
                    profile.targetCarbs,
                    Modifier.weight(1f)
                )
                MacroTarget(
                    "TŁUSZCZ",
                    profile.targetFat,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MacroTarget(
    label: String,
    grams: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )

        Text(
            "$grams g",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}
