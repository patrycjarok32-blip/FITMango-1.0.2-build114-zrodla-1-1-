package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.HealthConnectSyncEntity
import com.example.ui.theme.*
import com.example.viewmodel.HealthConnectSupport

/**
 * Read-only Health Connect insights. Never modifies nutrition targets, exports health
 * records, or guesses missing data. Permissions remain owned by the existing coordinator.
 */
@Composable
fun HealthConnectInsights(
    sync: HealthConnectSyncEntity?,
    eatenCalories: Int,
    calorieGoal: Int,
    sleepMinutes: Long?,
    onSync: () -> Unit,
    syncing: Boolean
) {
    val connected = sync?.isConnected == true
    val validSync = connected && (sync.lastSyncTimeMillis > 0L)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        HorizontalDivider(color = MangoMint.copy(alpha = 0.30f))
        Text("Twoje zdrowie • Health Connect", fontWeight = FontWeight.Bold, color = Cream)
        Text(
            if (validSync) "Ostatnia synchronizacja: " + HealthConnectSupport.formatLastSyncTime(sync!!.lastSyncTimeMillis)
            else "Brak aktualnej synchronizacji. Sprawdź połączenie i uprawnienia w ustawieniach Health Connect.",
            style = MaterialTheme.typography.bodySmall, color = GoldAccent
        )
        val remaining = (calorieGoal - eatenCalories).coerceAtLeast(0)
        Surface(shape = RoundedCornerShape(18.dp), color = MutedSurface2) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Bilans żywieniowy", color = MangoYellow, fontWeight = FontWeight.SemiBold)
                Text("Zjedzone: $eatenCalories kcal  •  Cel: $calorieGoal kcal", color = Cream)
                Text("Do celu: $remaining kcal", color = Cream)
                Text("Spalone kalorie pokazujemy osobno — nie zwiększają automatycznie celu diety.",
                    color = GoldAccent, style = MaterialTheme.typography.bodySmall)
                if (validSync && sync!!.activeCaloriesBurned > 0.0) {
                    Text("Aktywność (Health Connect): ${sync.activeCaloriesBurned.toInt()} kcal",
                        color = MangoMint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val advice = when {
            !validSync -> "Po synchronizacji zobaczysz wskazówki na podstawie dostępnych danych."
            sleepMinutes != null && sleepMinutes in 1..359 ->
                "Ostatni odczyt snu jest krótki. Rozważ odpoczynek i regularne posiłki."
            sleepMinutes != null && sleepMinutes >= 420 ->
                "Ostatni odczyt snu: " + HealthConnectSupport.formatSleepDurationDisplay(sleepMinutes) + ". Obserwuj regularność regeneracji."
            else -> "Zadbaj o regularne posiłki, nawodnienie i regenerację."
        }
        Text("Wskazówka: $advice", color = Cream, style = MaterialTheme.typography.bodySmall)
        Text("Wskazówki są informacyjne, nie są diagnozą. Dane zdrowotne nie są wysyłane do AI przez ten panel.",
            color = GoldAccent, style = MaterialTheme.typography.labelSmall)
        OutlinedButton(onClick = onSync, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
            Text(if (syncing) "Synchronizowanie…" else "Synchronizuj Health Connect")
        }
    }
}
