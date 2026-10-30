package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.viewmodel.isInstagramReaderUrl
import com.example.viewmodel.isInstagramRecipePage
import com.example.viewmodel.usableInstagramCaption
import org.json.JSONTokener

/** Reads only post descriptions after an explicit user action; never reads inputs or cookies. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramCaptionReader(url: String, onDismiss: () -> Unit, onCaption: (String) -> Unit) {
    var browser by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose { browser?.stopLoading(); browser?.destroy(); browser = null }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("Opis rolki z Instagrama", style = MaterialTheme.typography.titleLarge)
                Text("Otwórz opis („więcej”), a następnie pobierz go do przepisu. W razie potrzeby zaloguj się na stronie Instagrama.",
                    style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    WebView(context).apply {
                        browser = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                !isInstagramReaderUrl(request.url.toString())
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                            }
                            override fun onPageFinished(view: WebView, url: String?) { loading = false }
                        }
                        if (isInstagramReaderUrl(url)) loadUrl(url)
                    }
                })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Zamknij") }
                    Button(enabled = !reading && !loading, onClick = {
                        val page = browser
                        if (page == null || !isInstagramRecipePage(page.url.orEmpty())) {
                            message = "Najpierw otwórz rolkę lub post. Możesz wrócić do linku po zalogowaniu."
                            browser?.loadUrl(url)
                        } else {
                            reading = true
                            page.evaluateJavascript(CAPTION_SCRIPT) { result ->
                                reading = false
                                val text = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull().orEmpty()
                                val caption = usableInstagramCaption(text)
                                if (caption != null) onCaption(caption)
                                else message = "Nie znaleziono opisu przepisu. Rozwiń opis i spróbuj ponownie. Tekst wyłącznie na filmie nie jest odczytywany."
                            }
                        }
                    }) { Text(if (reading) "Pobieram…" else "Pobierz opis") }
                }
            }
        }
    }
}

// Only description fields and the visible post heading; deliberately excludes comments and login forms.
private val CAPTION_SCRIPT = """
    (function() {
        const descriptions = [];
        document.querySelectorAll('article h1, main h1').forEach(function(n) {
            if (n.getClientRects().length && n.innerText) descriptions.push(n.innerText);
        });
        document.querySelectorAll('script[type="application/ld+json"]').forEach(function(n) {
            try {
                const visit = function(v) {
                    if (!v || typeof v !== 'object') return;
                    if (Array.isArray(v)) { v.forEach(visit); return; }
                    if (typeof v.description === 'string') descriptions.push(v.description);
                    if (typeof v.articleBody === 'string') descriptions.push(v.articleBody);
                    if (v['@graph']) visit(v['@graph']);
                };
                visit(JSON.parse(n.textContent));
            } catch (_) {}
        });
        const meta = document.querySelector('meta[property="og:description"]');
        if (meta && meta.content) descriptions.push(meta.content);
        return descriptions.filter(function(t) { return t.trim().length >= 20; })
            .sort(function(a,b) { return b.length - a.length; })[0]?.slice(0,60000) || '';
    })();
""".trimIndent()
