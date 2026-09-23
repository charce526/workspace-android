package cn.xzbim.workspace.repository

import android.util.Log
import cn.xzbim.workspace.data.local.WorkspaceDao
import cn.xzbim.workspace.data.local.mapper.toDomainModel
import cn.xzbim.workspace.data.local.mapper.toEntity
import cn.xzbim.workspace.data.model.Workspace
import cn.xzbim.workspace.data.preferences.SettingsDataStore
import cn.xzbim.workspace.data.preferences.ThemeMode
import cn.xzbim.workspace.network.NocoBaseApiClient
import cn.xzbim.workspace.network.NocoBaseAuthService
import cn.xzbim.workspace.network.NocoBaseConnectionService
import cn.xzbim.workspace.network.result.ConnectionResult
import cn.xzbim.workspace.network.result.LoginResult
import cn.xzbim.workspace.network.result.SessionCheckResult
import cn.xzbim.workspace.security.SecureCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 工作空间数据仓库（整合数据库、Keystore 安全凭据、DataStore 及 NocoBase 2.0+ 网络认证）
 */
class WorkspaceRepository(
    private val workspaceDao: WorkspaceDao,
    private val credentialStore: SecureCredentialStore,
    private val settingsDataStore: SettingsDataStore,
    private val connectionService: NocoBaseConnectionService,
    private val authService: NocoBaseAuthService
) {

    companion object {
        private const val TAG = "WorkspaceRepository"
    }

    val workspacesFlow: Flow<List<Workspace>> = workspaceDao.getAllWorkspacesFlow().map { list ->
        list.map { it.toDomainModel() }
    }

    val floatingBallPositionFlow: Flow<Pair<Boolean, Float>> = settingsDataStore.floatingBallPositionFlow
    val floatingBallIdleAlphaFlow: Flow<Float> = settingsDataStore.floatingBallIdleAlphaFlow
    val autoEnterLastWorkspaceFlow: Flow<Boolean> = settingsDataStore.autoEnterLastWorkspaceFlow
    val rememberLoginStateFlow: Flow<Boolean> = settingsDataStore.rememberLoginStateFlow
    val themeModeFlow: Flow<ThemeMode> = settingsDataStore.themeModeFlow
    val webViewZoomEnabledFlow: Flow<Boolean> = settingsDataStore.webViewZoomEnabledFlow
    val keepScreenOnFlow: Flow<Boolean> = settingsDataStore.keepScreenOnFlow
    val openLinksInExternalBrowserFlow: Flow<Boolean> = settingsDataStore.openLinksInExternalBrowserFlow
    val globalNotificationCountEnabledFlow: Flow<Boolean> = settingsDataStore.globalNotificationCountEnabledFlow

    val isInitializedFlow: Flow<Boolean> = combine(
        workspacesFlow,
        autoEnterLastWorkspaceFlow
    ) { _, _ -> true }

    suspend fun saveFloatingBallPosition(isRightSide: Boolean, verticalRatio: Float) {
        settingsDataStore.saveFloatingBallPosition(isRightSide, verticalRatio)
    }

    suspend fun saveFloatingBallIdleAlpha(alpha: Float) {
        settingsDataStore.saveFloatingBallIdleAlpha(alpha)
    }

    suspend fun resetFloatingBallPosition() {
        settingsDataStore.resetFloatingBallPosition()
    }

    suspend fun setAutoEnterLastWorkspace(enabled: Boolean) {
        settingsDataStore.setAutoEnterLastWorkspace(enabled)
    }

    suspend fun setRememberLoginState(enabled: Boolean) {
        settingsDataStore.setRememberLoginState(enabled)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        settingsDataStore.setThemeMode(mode)
    }

    suspend fun setWebViewZoomEnabled(enabled: Boolean) {
        settingsDataStore.setWebViewZoomEnabled(enabled)
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        settingsDataStore.setKeepScreenOn(enabled)
    }

    suspend fun setOpenLinksInExternalBrowser(enabled: Boolean) {
        settingsDataStore.setOpenLinksInExternalBrowser(enabled)
    }

    suspend fun setGlobalNotificationCountEnabled(enabled: Boolean) {
        settingsDataStore.setGlobalNotificationCountEnabled(enabled)
    }

    suspend fun getWorkspace(id: String): Workspace? {
        return workspaceDao.getWorkspaceById(id)?.toDomainModel()
    }

    fun getPassword(workspaceId: String): String? {
        return credentialStore.getPassword(workspaceId)
    }

    fun getToken(workspaceId: String): String? {
        return credentialStore.getToken(workspaceId)
    }

    /**
     * 测试服务器基础 HTTP/HTTPS 连通性
     */
    suspend fun testConnection(serverUrl: String): ConnectionResult {
        return connectionService.testConnection(serverUrl)
    }

    /**
     * 连接服务器、真实登录并保存工作空间与凭据
     * 直接请求 NocoBase 2.0+ 官方 POST /api/auth:signIn 端点完成判断与登录
     */
    suspend fun signInAndSaveWorkspace(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        notificationCountEnabled: Boolean = true
    ): LoginResult {
        val formattedUrl = NocoBaseApiClient.normalizeServerUrl(serverUrl)

        val loginResult = authService.signIn(formattedUrl, username, password)

        if (loginResult is LoginResult.Success) {
            val id = UUID.randomUUID().toString()

            val newWorkspace = Workspace(
                id = id,
                name = name.ifBlank { "NocoBase 工作空间" },
                serverUrl = formattedUrl,
                username = username,
                lastUsedTime = System.currentTimeMillis(),
                isLastUsed = true,
                notificationCountEnabled = notificationCountEnabled
            )

            workspaceDao.insertAndMarkLastUsed(newWorkspace.toEntity())
            credentialStore.saveToken(id, loginResult.token)

            val rememberLogin = settingsDataStore.rememberLoginStateFlow.first()
            if (rememberLogin && password.isNotEmpty()) {
                credentialStore.savePassword(id, password)
            }
            Log.d(TAG, "Workspace and secure credential saved successfully for ID: $id")

            return loginResult.copy(workspaceId = id)
        }

        return loginResult
    }

    /**
     * 重新验证/重新登录现有工作空间（原地更新 Token 与凭据，绝不生成重复记录）
     */
    suspend fun reLoginAndUpdateWorkspace(
        workspaceId: String,
        password: String
    ): LoginResult {
        val existing = workspaceDao.getWorkspaceById(workspaceId)
            ?: return LoginResult.NetworkError("未找到对应工作空间记录")

        Log.d(TAG, "reLoginAndUpdateWorkspace -> Re-authenticating workspaceId: $workspaceId")
        val loginResult = authService.signIn(existing.serverUrl, existing.username, password)

        if (loginResult is LoginResult.Success) {
            val updatedEntity = existing.copy(
                lastUsedAt = System.currentTimeMillis(),
                isLastUsed = true,
                updatedAt = System.currentTimeMillis()
            )

            workspaceDao.updateWorkspace(updatedEntity)
            credentialStore.saveToken(workspaceId, loginResult.token)

            val rememberLogin = settingsDataStore.rememberLoginStateFlow.first()
            if (rememberLogin && password.isNotEmpty()) {
                credentialStore.savePassword(workspaceId, password)
            } else {
                credentialStore.savePassword(workspaceId, "")
            }

            Log.d(TAG, "reLoginAndUpdateWorkspace -> Successfully updated credentials in-place for $workspaceId")
            return loginResult.copy(workspaceId = workspaceId)
        }

        return loginResult
    }

    /**
     * 校验会话状态 (校验 Token 有效性，必要时自动尝试刷新/重新登录)
     */
    suspend fun checkSession(workspaceId: String): SessionCheckResult {
        val workspace = getWorkspace(workspaceId) ?: return SessionCheckResult.ExpiredOrUnauthorized
        val token = getToken(workspaceId)

        if (!token.isNullOrBlank()) {
            val result = authService.checkSession(workspace.serverUrl, token)
            if (result is SessionCheckResult.Valid) {
                return result
            }
        }

        val savedPassword = getPassword(workspaceId)
        if (!savedPassword.isNullOrBlank()) {
            val reLoginResult = authService.signIn(
                workspace.serverUrl,
                workspace.username,
                savedPassword
            )
            if (reLoginResult is LoginResult.Success) {
                credentialStore.saveToken(workspaceId, reLoginResult.token)
                return SessionCheckResult.Valid(reLoginResult.userId, reLoginResult.username)
            }
            if (reLoginResult !is LoginResult.InvalidCredentials) {
                return SessionCheckResult.Unavailable()
            }
        }

        return SessionCheckResult.ExpiredOrUnauthorized
    }

    suspend fun updateWorkspaceWithAuth(
        id: String,
        name: String,
        serverUrl: String,
        username: String,
        password: String? = null,
        notificationCountEnabled: Boolean = true
    ): LoginResult {
        val existing = workspaceDao.getWorkspaceById(id) ?: return LoginResult.NetworkError("工作空间不存在")
        val formattedUrl = NocoBaseApiClient.normalizeServerUrl(serverUrl)

        // A saved password must never be sent to a newly edited server/account.
        val identityChanged = formattedUrl != existing.serverUrl || username != existing.username
        val verifyPassword = if (!password.isNullOrBlank()) password else if (!identityChanged) getPassword(id) ?: "" else ""

        if (verifyPassword.isBlank()) {
            return LoginResult.InvalidCredentials("密码为空，请输入密码进行验证")
        }

        val loginResult = authService.signIn(formattedUrl, username, verifyPassword)

        if (loginResult is LoginResult.Success) {
            val updatedEntity = existing.copy(
                name = name.ifBlank { existing.name },
                serverUrl = formattedUrl,
                username = username,
                notificationCountEnabled = notificationCountEnabled,
                updatedAt = System.currentTimeMillis()
            )

            workspaceDao.updateWorkspace(updatedEntity)
            credentialStore.saveToken(id, loginResult.token)

            val rememberLogin = settingsDataStore.rememberLoginStateFlow.first()
            if (rememberLogin && verifyPassword.isNotEmpty()) {
                credentialStore.savePassword(id, verifyPassword)
            } else {
                credentialStore.savePassword(id, "")
            }

            Log.d(TAG, "updateWorkspaceWithAuth -> Verification success, saved updated credentials for $id")
            return loginResult
        }

        return loginResult
    }

    suspend fun deleteWorkspace(id: String) {
        workspaceDao.deleteWorkspaceById(id)
        credentialStore.clearCredential(id)
    }

    suspend fun setLastUsedWorkspace(id: String) {
        workspaceDao.markLastUsed(id)
    }

    suspend fun getLastUsedWorkspace(): Workspace? {
        return workspaceDao.getLastUsedWorkspace()?.toDomainModel()
    }

    suspend fun setDefaultWorkspace(id: String) {
        workspaceDao.markDefault(id)
    }

    suspend fun unsetDefaultWorkspace(id: String) {
        workspaceDao.unsetDefaultWorkspace(id)
    }

    suspend fun getDefaultWorkspace(): Workspace? {
        return workspaceDao.getDefaultWorkspace()?.toDomainModel()
    }

    suspend fun updateWorkspaceNotificationCountEnabled(id: String, enabled: Boolean) {
        val existing = workspaceDao.getWorkspaceById(id) ?: return
        workspaceDao.updateWorkspace(existing.copy(notificationCountEnabled = enabled, updatedAt = System.currentTimeMillis()))
    }
}
