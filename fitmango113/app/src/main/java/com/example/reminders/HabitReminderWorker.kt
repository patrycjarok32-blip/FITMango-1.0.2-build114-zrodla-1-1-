package com.example.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

object HabitNotifications {
    const val CHANNEL = "fitmango_habits"
    fun allowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun show(context: Context, kind: String): Boolean {
        if (!allowed(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Nawyki FITMango", NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val id = when (kind) { "water" -> 4901; "creatine" -> 4902; "test" -> 4904; "weight" -> 4905; "activity" -> 4906; "weekly" -> 4907; else -> 4903 }
        val text = when (kind) {
            "test" -> "Przypomnienia FITMango są gotowe. To powiadomienie testowe."
            "water" -> "Czas na napój? Zapisz wypitą ilość w FITMango."
            "weight" -> "Jeśli planujesz pomiar, zważ się w podobnych warunkach i sprawdź zapis w Profilu."
            "activity" -> "Masz dziś ochotę na ruch? Wybierz aktywność dopasowaną do samopoczucia."
            "weekly" -> "Możesz podsumować ostatnie 7 zakończonych dni w Aktywność i regeneracja → Raport tygodniowy."
            "creatine" -> "Sprawdź dzisiejszy wpis kreatyny, jeśli ją przyjmujesz."
            else -> "Sprawdź zapisane posiłki i napoje, a potem podsumuj dzień."
        }
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_habit_notification)
            .setContentTitle("FITMango • " + when (kind) { "test" -> "Test przypomnień"; "water" -> "Nawodnienie"; "creatine" -> "Kreatyna"; "weight" -> "Pomiary"; "activity" -> "Aktywność"; "weekly" -> "Raport tygodniowy"; else -> "Zakończenie dnia" })
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(false).build())
        return true
    }
}
class HabitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            if (!HabitNotifications.allowed(applicationContext)) return Result.success()
            val settings = ReminderSupport.read(applicationContext)
            val now = LocalDateTime.now()
            val prefs = ReminderSupport.prefs(applicationContext)
            val dao = AppDatabase.getDatabase(applicationContext).dietaDao()
            for ((kind, slot) in ReminderSupport.due(settings, now)) {
                if (prefs.getString("sent_$kind", "") == slot) continue
                val today = now.toLocalDate().toString()
                if (kind == "creatine" && dao.getCreatineLogs().first().any { it.date == today }) continue
                if (kind == "water") {
                    val goal = applicationContext.getSharedPreferences("FITMangoHabits", Context.MODE_PRIVATE).getInt("waterGoal", 2500)
                    if (dao.getBeverageLogs().first().filter { it.date == today }.sumOf { it.amountMl } >= goal) continue
                }
                if (ReminderSupport.read(applicationContext) != settings || ReminderSupport.quiet(LocalDateTime.now().toLocalTime(), settings)) break
                if (HabitNotifications.show(applicationContext, kind)) prefs.edit().putString("sent_$kind", slot).commit()
            }
            return Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { return Result.retry() }
    }
}
