package com.example

import android.content.Context
import android.os.Build

/** Local crash checkpoint. Never records exception messages, keys, recipe data or network bodies. */
internal object StartupDiagnostics {
    private const val PREFS = "mangofit_startup_diagnostics"
    private const val ERROR = "pending_error"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { save(app, error) } catch (_: Exception) { /* Retain Android's normal crash handling. */ }
            previous?.uncaughtException(thread, error)
        }
    }

    fun format(error: Throwable): String = buildString {
        append("Android API ${Build.VERSION.SDK_INT}\n")
        // Types and code locations are enough to identify the failure without private messages.
        val seen = java.util.IdentityHashMap<Throwable, Boolean>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth++ < 8 && seen.put(current, true) == null) {
            append(current.javaClass.name).append('\n')
            current.stackTrace.take(12).forEach { append("  ").append(it.toString()).append('\n') }
            current = current.cause
        }
    }.take(12000)

    fun save(context: Context, error: Throwable) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ERROR, format(error)).commit()
    }

    fun pending(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ERROR, null)

    fun databaseVersion(context: Context): String = try {
        val file = context.getDatabasePath("moja_dieta_ai.db")
        if (!file.exists()) "brak bazy" else {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { it.version.toString() }
        }
    } catch (_: Exception) { "nie udało się odczytać" }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ERROR).commit()
    }
}
