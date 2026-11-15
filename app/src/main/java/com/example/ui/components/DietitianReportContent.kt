package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Renders the locally calculated facts and Gemini interpretation without raw Markdown symbols. */
@Composable
fun DietitianReportContent(report: String) {
    if (report.isBlank()) {
        Text("Wygeneruj analizę, aby zobaczyć podsumowanie tygodnia.")
        return
    }
    val lines = report.lines()
    var section = mutableListOf<String>()
    val blocks = mutableListOf<Pair<String?, List<String>>>()
    var heading: String? = null
    fun flush() {
        if (section.isNotEmpty() || heading != null) {
            blocks.add(heading to section.toList())
            section = mutableListOf()
        }
    }
    lines.forEach { raw ->
        val line = raw.trim()
        if (line.matches(Regex("^#{1,6}\\s+.*"))) {
            flush()
            heading = line.replace(Regex("^#{1,6}\\s+"), "").trim()
        } else if (line == "---") {
            flush()
            heading = null
        } else if (line.isNotBlank()) {
            section.add(line)
        }
    }
    flush()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { (title, paragraphs) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!title.isNullOrBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
                    paragraphs.forEach { line ->
                        val cleaned = line.replace("**", "").replace(Regex("^\\*\\s+"), "• ")
                            .replace(Regex("^[-]\\s+"), "• ")
                        Text(cleaned, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
