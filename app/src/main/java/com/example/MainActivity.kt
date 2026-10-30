package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import com.example.ai.SecureKeyStorage
import com.example.ui.navigation.MainNavigation
import com.example.ui.theme.MojaDietaAiTheme
import com.example.viewmodel.DietaViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: DietaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartupDiagnostics.install(applicationContext)
        val previousCrash = StartupDiagnostics.pending(this)
        if (previousCrash != null) {
            showStartupError(previousCrash)
            return
        }
        SecureKeyStorage.init(applicationContext)
        runCatching { com.example.reminders.ReminderSupport.schedule(applicationContext) }
        handleShareIntent(intent)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0xFF0B1017.toInt()), navigationBarStyle = SystemBarStyle.dark(0xFF0B1017.toInt()))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    viewModel.healthConnect.syncAutomatically()
                    delay(com.example.viewmodel.HealthConnectCoordinator.AUTO_SYNC_INTERVAL_MS)
                }
            }
        }
        setContent {
            MojaDietaAiTheme {
                MainNavigation(viewModel = viewModel)
            }
        }
    }

    private fun showStartupError(report: String) {
        // Native views keep this recovery screen independent of Compose, Room and the ViewModel.
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
            setBackgroundColor(0xFFFFF8E7.toInt())
        }
        val title = android.widget.TextView(this).apply {
            text = "MangoFit — raport uruchomienia\n\nDane aplikacji zostały zachowane. Zrób zrzut tego ekranu i prześlij go w rozmowie. Raport jest zapisany wyłącznie na telefonie."
            textSize = 18f
            setTextColor(0xFF203C30.toInt())
        }
        container.addView(title)
        val scroll = android.widget.ScrollView(this)
        scroll.addView(android.widget.TextView(this).apply {
            text = "Wersja bazy: ${StartupDiagnostics.databaseVersion(this@MainActivity)}\n\n$report"
            textSize = 13f
            setTextColor(0xFF203C30.toInt())
            setTextIsSelectable(true)
        })
        container.addView(scroll, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        container.addView(android.widget.Button(this).apply {
            text = "Spróbuj uruchomić ponownie"
            setOnClickListener {
                StartupDiagnostics.clear(this@MainActivity)
                recreate()
            }
        })
        setContentView(container)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (StartupDiagnostics.pending(this) == null) handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.handleIncomingSharedRecipeText(sharedText)
                // Do not replay the same share after activity recreation.
                intent.removeExtra(Intent.EXTRA_TEXT)
            }
        }
    }
}
