package cn.xzbim.workspace.repository

import android.util.Log
import cn.xzbim.workspace.data.local.WorkspaceDao
import cn.xzbim.workspace.data.local.dao.WorkspaceNotificationStateDao
import cn.xzbim.workspace.data.local.entity.WorkspaceNotificationStateEntity
import cn.xzbim.workspace.data.local.mapper.toDomainModel
import cn.xzbim.workspace.data.local.mapper.toEntity
import cn.xzbim.workspace.data.model.Workspace
import cn.xzbim.workspace.data.preferences.SettingsDataStore
import cn.xzbim.workspace.data.preferences.ThemeMode
import cn.xzbim.workspace.network.NocoBaseApiClient
import cn.xzbim.workspace.network.NocoBaseAuthService
import cn.xzbim.workspace.network.NocoBaseConnectionService
import cn.xzbim.workspace.network.NocoBaseNotificationService
import cn.xzbim.workspace.network.result.ConnectionResult
import cn.xzbim.workspace.network.result.LoginResult
import cn.xzbim.workspace.network.result.NotificationCountResult
import cn.xzbim.workspace.network.result.SessionCheckResult
import cn.xzbim.workspace.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ManualRefreshResult {
    ALL_SUCCESS,
    PARTIAL_FAILED,
    ALL_FAILED,
    GLOBAL_DISABLED,
    NO_ELIGIBLE_WORKSPACES,
    COOLDOWN_ACTIVE
}

/**
 * 工作空间数据仓库（整合数据库、Keystore 安全凭据、DataStore、NocoBase 认证与站内消息同步）
 */
class WorkspaceRepository(
    private val workspaceDao: WorkspaceDao,
    private val workspaceNotificationStateDao: WorkspaceNotificationStateDao,
    private val credentialStore: SecureCredentialStore,
    private val settingsDataStore: SettingsDataStore,
    private val connectionService: NocoBaseConnectionService,
    private val authService: NocoBaseAuthService,
    private val notificationService: NocoBaseNotificationService = NocoBaseNotificationService()
) {

    companion object {
        private const val TAG = "WorkspaceRepository"
    }

    val workspacesFlow: Flow<List<Workspace>> = workspaceDao.getAllWorkspacesFlow().map { list ->
        list.map { it.toDomainModel() }
    }

    val notificationStatesFlow: Flow<Map<String, WorkspaceNotificationStateEntity>> =
        workspaceNotificationStateDao.getAllNotificationStatesFlow().map { list ->
            list.associateBy { it.workspaceId }
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

    suspend fun syncNotificationCounts(isManualRefresh: Boolean = false): ManualRefreshResult = withContext(Dispatchers.IO) {
        val globalEnabled = globalNotificationCountEnabledFlow.first()
        if (!globalEnabled) {
            return@withContext ManualRefreshResult.GLOBAL_DISABLED
        }

        val allWorkspaces = workspaceDao.getAllWorkspaces().map { it.toDomainModel() }
        val eligibleWorkspaces = allWorkspaces.filter { it.notificationCountEnabled }

        if (eligibleWorkspaces.isEmpty()) {
            return@withContext ManualRefreshResult.NO_ELIGIBLE_WORKSPACES
        }

        val now = System.currentTimeMillis()
        var successCount = 0
        var failureCount = 0

        coroutineScope {
            val semaphore = Semaphore(3)
            val jobs = eligibleWorkspaces.map { workspace ->
                async {
                    semaphore.withPermit {
                        val existingState = workspaceNotificationStateDao.getStateByWorkspaceId(workspace.id)
                            ?: WorkspaceNotificationStateEntity(workspaceId = workspace.id)

                        if (!isManualRefresh) {
                            if (existingState.status == "UNSUPPORTED" || existingState.status == "AUTH_REQUIRED") {
                                return@async
                            }
                            if (now < existingState.nextRetryAt) {
                                return@async
                            }
                        }

                        val token = getToken(workspace.id) ?: ""
                        val result = notificationService.fetchUnreadCount(workspace.serverUrl, token)

                        val newState = calculateNextNotificationState(existingState, result, now)
                        workspaceNotificationStateDao.insertOrUpdateState(newState)

                        if (result is NotificationCountResult.Success) {
                            successCount++
                        } else {
                            failureCount++
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        when {
            successCount > 0 && failureCount == 0 -> ManualRefreshResult.ALL_SUCCESS
            successCount > 0 && failureCount > 0 -> ManualRefreshResult.PARTIAL_FAILED
            else -> ManualRefreshResult.ALL_FAILED
        }
    }

    private fun calculateNextNotificationState(
        existing: WorkspaceNotificationStateEntity,
        result: NotificationCountResult,
        now: Long
    ): WorkspaceNotificationStateEntity {
        return when (result) {
            is NotificationCountResult.Success -> {
                existing.copy(
                    unreadCount = result.count,
                    status = "AVAILABLE",
                    failureCount = 0,
                    lastAttemptAt = now,
                    lastSuccessAt = now,
                    nextRetryAt = 0L,
                    lastErrorType = null
                )
            }
            is NotificationCountResult.AuthRequired -> {
                existing.copy(
                    status = "AUTH_REQUIRED",
                    lastAttemptAt = now,
                    lastErrorType = "AUTH_REQUIRED"
                )
            }
            is NotificationCountResult.Unsupported -> {
                existing.copy(
                    status = "UNSUPPORTED",
                    lastAttemptAt = now,
                    lastErrorType = "UNSUPPORTED"
                )
            }
            is NotificationCountResult.RateLimited -> {
                val backoffMs = (result.retryAfterSeconds ?: 600L) * 1000L
                val nextCount = existing.failureCount + 1
                existing.copy(
                    status = if (nextCount >= 5) "SUSPENDED" else "RATE_LIMITED",
                    failureCount = nextCount,
                    lastAttemptAt = now,
                    nextRetryAt = now + backoffMs,
                    lastErrorType = "RATE_LIMITED"
                )
            }
            is NotificationCountResult.ServerError -> {
                val nextCount = existing.failureCount + 1
                val backoffMs = calculateBackoffMs(nextCount)
                existing.copy(
                    status = if (nextCount >= 5) "SUSPENDED" else "SERVER_ERROR",
                    failureCount = nextCount,
                    lastAttemptAt = now,
                    nextRetryAt = now + backoffMs,
                    lastErrorType = "SERVER_ERROR_${result.statusCode}"
                )
            }
            is NotificationCountResult.NetworkError, NotificationCountResult.InvalidResponse -> {
                val nextCount = existing.failureCount + 1
                val backoffMs = calculateBackoffMs(nextCount)
                existing.copy(
                    status = if (nextCount >= 5) "SUSPENDED" else "NETWORK_ERROR",
                    failureCount = nextCount,
                    lastAttemptAt = now,
                    nextRetryAt = now + backoffMs,
                    lastErrorType = "NETWORK_ERROR"
                )
            }
        }
    }

    private fun calculateBackoffMs(failureCount: Int): Long {
        return when (failureCount) {
            1 -> 5 * 60 * 1000L
            2 -> 15 * 60 * 1000L
            3 -> 60 * 60 * 1000L
            4 -> 6 * 60 * 60 * 1000L
            else -> 24 * 60 * 60 * 1000L
        }
    }

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

    suspend fun testConnection(serverUrl: String): ConnectionResult {
        return connectionService.testConnection(serverUrl)
    }

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

            // 登录成功后复位通知状态为 AVAILABLE 并清空失败计数
            val existingState = workspaceNotificationStateDao.getStateByWorkspaceId(workspaceId)
                ?: WorkspaceNotificationStateEntity(workspaceId = workspaceId)
            workspaceNotificationStateDao.insertOrUpdateState(
                existingState.copy(status = "AVAILABLE", failureCount = 0, nextRetryAt = 0L)
            )

            Log.d(TAG, "reLoginAndUpdateWorkspace -> Successfully updated credentials in-place for $workspaceId")
            return loginResult.copy(workspaceId = workspaceId)
        }

        return loginResult
    }

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
        password: String? = null
    ): LoginResult {
        val existing = workspaceDao.getWorkspaceById(id) ?: return LoginResult.NetworkError("工作空间不存在")
        val formattedUrl = NocoBaseApiClient.normalizeServerUrl(serverUrl)

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
                notificationCountEnabled = existing.notificationCountEnabled, // 保持已有的 notificationCountEnabled 原值！
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

            // 修改服务器/账号成功后重置退避状态
            val existingState = workspaceNotificationStateDao.getStateByWorkspaceId(id)
                ?: WorkspaceNotificationStateEntity(workspaceId = id)
            workspaceNotificationStateDao.insertOrUpdateState(
                existingState.copy(status = "AVAILABLE", failureCount = 0, nextRetryAt = 0L)
            )

            Log.d(TAG, "updateWorkspaceWithAuth -> Verification success, saved updated credentials for $id")
            return loginResult
        }

        return loginResult
    }

    suspend fun updateWorkspaceNotificationCountEnabled(id: String, enabled: Boolean) {
        val existing = workspaceDao.getWorkspaceById(id) ?: return
        workspaceDao.updateWorkspace(existing.copy(notificationCountEnabled = enabled, updatedAt = System.currentTimeMillis()))
        if (enabled) {
            // 重新开启开关后复位失败重试，允许立即获取
            val existingState = workspaceNotificationStateDao.getStateByWorkspaceId(id)
                ?: WorkspaceNotificationStateEntity(workspaceId = id)
            workspaceNotificationStateDao.insertOrUpdateState(
                existingState.copy(status = "AVAILABLE", failureCount = 0, nextRetryAt = 0L)
            )
        }
    }

    suspend fun deleteWorkspace(id: String) {
        workspaceDao.deleteWorkspaceById(id)
        workspaceNotificationStateDao.deleteStateByWorkspaceId(id)
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

    suspend fun reorderWorkspaces(orderedWorkspaceIds: List<String>) {
        workspaceDao.updateWorkspacesOrder(orderedWorkspaceIds)
    }
}
