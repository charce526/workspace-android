package cn.xzbim.workspace

import android.app.Activity
import android.app.Application
import android.os.Bundle
import cn.xzbim.workspace.data.local.AppDatabase
import cn.xzbim.workspace.data.preferences.SettingsDataStore
import cn.xzbim.workspace.network.NocoBaseAuthService
import cn.xzbim.workspace.network.NocoBaseConnectionService
import cn.xzbim.workspace.network.NocoBaseNotificationService
import cn.xzbim.workspace.repository.WorkspaceRepository
import cn.xzbim.workspace.security.SecureCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 应用 Application，负责初始化全局依赖（Database, Repository, Network Services, Keystore及前台生命周期同步）
 */
class WorkspaceApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val credentialStore by lazy { SecureCredentialStore(this) }
    val settingsDataStore by lazy { SettingsDataStore(this) }
    val connectionService by lazy { NocoBaseConnectionService() }
    val authService by lazy { NocoBaseAuthService() }
    val notificationService by lazy { NocoBaseNotificationService() }

    val workspaceRepository by lazy {
        WorkspaceRepository(
            workspaceDao = database.workspaceDao(),
            workspaceNotificationStateDao = database.workspaceNotificationStateDao(),
            credentialStore = credentialStore,
            settingsDataStore = settingsDataStore,
            connectionService = connectionService,
            authService = authService,
            notificationService = notificationService
        )
    }

    private var startedActivitiesCount = 0
    private var lastForegroundSyncTime = 0L
    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var foregroundTimerJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivitiesCount++
                if (startedActivitiesCount == 1) {
                    onAppEnteredForeground()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivitiesCount--
                if (startedActivitiesCount == 0) {
                    onAppEnteredBackground()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun onAppEnteredForeground() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncTime > 120_000L) {
            lastForegroundSyncTime = now
            applicationScope.launch {
                try {
                    workspaceRepository.syncNotificationCounts(isManualRefresh = false)
                } catch (_: Exception) {}
            }
        }

        foregroundTimerJob?.cancel()
        foregroundTimerJob = applicationScope.launch {
            while (isActive) {
                delay(300_000L)
                try {
                    workspaceRepository.syncNotificationCounts(isManualRefresh = false)
                } catch (_: Exception) {}
            }
        }
    }

    private fun onAppEnteredBackground() {
        foregroundTimerJob?.cancel()
        foregroundTimerJob = null
    }
}
