package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/** Only YouTube video IDs are embedded; no arbitrary HTML or third-party page URLs. */
object YoutubeEmbed {
    private val id = Regex("^[A-Za-z0-9_-]{11}$")
    fun videoId(raw: String): String? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val candidate = when (host) {
            "youtu.be" -> uri.pathSegments.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com" ->
                if (uri.path == "/watch") uri.getQueryParameter("v")
                else if (uri.pathSegments.firstOrNull() in listOf("shorts", "embed", "live"))
                    uri.pathSegments.getOrNull(1) else null
            else -> null
        }
        return candidate?.takeIf { id.matches(it) }
    }
}

@Composable
fun EmbeddedYoutubeWorkout(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val videoId = remember(url) { YoutubeEmbed.videoId(url) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (videoId == null) {
            Text("Nie można odtworzyć tego linku. Sprawdź adres filmu YouTube.")
        } else {
            // The technique button already opens the dialog: do not require a second play action.
            MangoInAppVideoPlayer(url, Modifier.fillMaxWidth())
            Text("Jeśli YouTube zablokuje osadzanie, użyj przycisku poniżej.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                val target = Uri.parse("https://www.youtube.com/watch?v=$videoId")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
            }) { Text("▶ Otwórz film w YouTube") }
        }
    }
}
