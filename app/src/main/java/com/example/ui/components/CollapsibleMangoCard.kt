package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

/** Profile detail screens show their controls immediately. Other screens keep collapsible cards. */
val LocalStandaloneProfileSection = staticCompositionLocalOf { false }

/** Compact settings section; collapsing only changes presentation. */
@Composable
fun CollapsibleMangoCard(
    title: String,
    sectionId: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalStandaloneProfileSection.current) {
        MangoCard(modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content()
            }
        }
        return
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by rememberSaveable(sectionId) { mutableStateOf(false) }
    val duration = if (LocalReducedMangoMotion.current) 0 else 220
    MangoCard(modifier) {
        TextButton(
            onClick = {
                if (expanded) { focus.clearFocus(); keyboard?.hide() }
                expanded = !expanded
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .testTag("section_" + sectionId)
                .semantics { stateDescription = if (expanded) "Rozwinięte" else "Zwinięte" }
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MangoSectionArt(sectionArtwork(sectionId), sectionArtworkAccent(sectionId), size = 44.dp)
                Text(title, Modifier.weight(1f), color = Cream,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (expanded) "Zwiń −" else "Rozwiń +", color = LeafGreen,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
            exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
            Column {
            HorizontalDivider(color = WarmBrown.copy(alpha = 0.3f))
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}
