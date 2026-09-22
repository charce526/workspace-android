package cn.xzbim.workspace

import android.app.Application
import cn.xzbim.workspace.data.local.AppDatabase
import cn.xzbim.workspace.data.preferences.SettingsDataStore
import cn.xzbim.workspace.network.NocoBaseAuthService
import cn.xzbim.workspace.network.NocoBaseConnectionService
import cn.xzbim.workspace.repository.WorkspaceRepository
import cn.xzbim.workspace.security.SecureCredentialStore

/**
 * 应用 Application，负责初始化全局依赖（Database, Repository, Network Services, Keystore）
 */
class WorkspaceApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val credentialStore by lazy { SecureCredentialStore(this) }
    val settingsDataStore by lazy { SettingsDataStore(this) }
    val connectionService by lazy { NocoBaseConnectionService() }
    val authService by lazy { NocoBaseAuthService() }

    val workspaceRepository by lazy {
        WorkspaceRepository(
            workspaceDao = database.workspaceDao(),
            credentialStore = credentialStore,
            settingsDataStore = settingsDataStore,
            connectionService = connectionService,
            authService = authService
        )
    }
}
