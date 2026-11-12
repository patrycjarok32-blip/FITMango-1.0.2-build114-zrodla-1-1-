package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** YouTube-only in-app playback. Other URLs are not loaded into a WebView. */
object MangoVideoUrl {
    fun embed(url: String): String? {
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase(java.util.Locale.ROOT) ?: return null
        val id = when (host) {
            "youtu.be", "www.youtu.be" -> uri.pathSegments.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com" ->
                if (uri.path == "/watch") uri.getQueryParameter("v")
                else if (uri.pathSegments.firstOrNull() in listOf("shorts", "embed", "live"))
                    uri.pathSegments.getOrNull(1) else null
            else -> null
        } ?: return null
        if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return null
        return "https://www.youtube.com/embed/$id?playsinline=1&rel=0&origin=https%3A%2F%2Fwww.youtube.com"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MangoInAppVideoPlayer(url: String, modifier: Modifier = Modifier) {
    val embed = remember(url) { MangoVideoUrl.embed(url) }
    if (embed == null) {
        Text("Ten materiał nie obsługuje bezpiecznego odtwarzania w aplikacji.",
            style = MaterialTheme.typography.bodySmall)
        return
    }
    key(embed) {
        AndroidView(
            modifier = modifier.fillMaxWidth().height(220.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: android.webkit.WebResourceRequest
                        ): Boolean = request.url.host !in setOf(
                            "www.youtube-nocookie.com", "youtube-nocookie.com",
                            "www.youtube.com", "youtube.com")
                    }
                    // Error 153 often indicates a missing referrer for embedded playback.
                    loadUrl(embed, mapOf("Referer" to "https://www.youtube.com/"))
                }
            },
            update = { },
            onRelease = { web -> web.stopLoading(); web.loadUrl("about:blank"); web.destroy() }
        )
    }
}
