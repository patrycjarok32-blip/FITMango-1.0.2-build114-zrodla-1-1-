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
    var showPlayer by remember(url) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (videoId == null) {
            Text("Nie można osadzić tego adresu. Wklej poprawny link do filmu YouTube.")
        } else {
            if (!showPlayer) Button(onClick = { showPlayer = true }) {
                Text("▶ Odtwórz wewnątrz FITMango")
            }
            if (showPlayer) {
                key(videoId) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = true
                                webChromeClient = WebChromeClient()
                                webViewClient = WebViewClient()
                                loadUrl("https://www.youtube-nocookie.com/embed/$videoId?playsinline=1")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(230.dp),
                        onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() }
                    )
                }
                TextButton(onClick = { showPlayer = false }) { Text("Zamknij odtwarzacz") }
            }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }) { Text("Otwórz w YouTube, jeśli osadzanie jest niedostępne") }
        }
    }
}
