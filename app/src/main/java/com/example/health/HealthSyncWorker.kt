package com.example.health

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.DietaRepository
import com.example.viewmodel.HealthConnectCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.getSharedPreferences("fitmango_health_sync", Context.MODE_PRIVATE).getBoolean("automatic", true)) return Result.success()
        val manager = HealthConnectManager(applicationContext)
        if (!manager.canReadInBackground() || !manager.hasAnyPermission(manager.getGrantedPermissions())) return Result.success()
        val repository = DietaRepository(AppDatabase.getDatabase(applicationContext).dietaDao())
        if (repository.healthConnectSync.first()?.isConnected != true) return Result.success()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            val coordinator = HealthConnectCoordinator(repository, applicationContext as Application, scope,
                MutableStateFlow(repository.userProfile.first()))
            if (coordinator.syncDirect(automatic = true).first) Result.success() else Result.retry()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
        finally { scope.cancel() }
    }
}
