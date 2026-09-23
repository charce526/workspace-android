package cn.xzbim.workspace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.xzbim.workspace.data.model.Workspace
import cn.xzbim.workspace.data.preferences.ThemeMode
import cn.xzbim.workspace.network.result.LoginResult
import cn.xzbim.workspace.network.result.SessionCheckResult
import cn.xzbim.workspace.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 工作空间 ViewModel（处理 NocoBase 2.0+ 连接、真实登录认证、会话状态及防竞态工作空间打开逻辑）
 */
class WorkspaceViewModel(
    private val repository: WorkspaceRepository
) : ViewModel() {

    val isInitialized: StateFlow<Boolean> = repository.isInitializedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val workspaces: StateFlow<List<Workspace>> = repository.workspacesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val floatingBallPosition: StateFlow<Pair<Boolean, Float>> = repository.floatingBallPositionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Pair(true, 0.68f)
    )

    val floatingBallIdleAlpha: StateFlow<Float> = repository.floatingBallIdleAlphaFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.65f
    )

    val autoEnterLastWorkspace: StateFlow<Boolean> = repository.autoEnterLastWorkspaceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val rememberLoginState: StateFlow<Boolean> = repository.rememberLoginStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val themeMode: StateFlow<ThemeMode> = repository.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM
    )

    val webViewZoomEnabled: StateFlow<Boolean> = repository.webViewZoomEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val keepScreenOn: StateFlow<Boolean> = repository.keepScreenOnFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val openLinksInExternalBrowser: StateFlow<Boolean> = repository.openLinksInExternalBrowserFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val globalNotificationCountEnabled: StateFlow<Boolean> = repository.globalNotificationCountEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    private val _isOpeningWorkspace = MutableStateFlow(false)
    val isOpeningWorkspace: StateFlow<Boolean> = _isOpeningWorkspace.asStateFlow()

    private val _openingWorkspaceId = MutableStateFlow<String?>(null)
    val openingWorkspaceId: StateFlow<String?> = _openingWorkspaceId.asStateFlow()

    private var openWorkspaceJob: Job? = null

    fun setAutoEnterLastWorkspace(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setAutoEnterLastWorkspace(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setRememberLoginState(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setRememberLoginState(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveFloatingBallPosition(isRightSide: Boolean, verticalRatio: Float) {
        viewModelScope.launch {
            try {
                repository.saveFloatingBallPosition(isRightSide, verticalRatio)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveFloatingBallIdleAlpha(alpha: Float) {
        viewModelScope.launch {
            try {
                repository.saveFloatingBallIdleAlpha(alpha)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetFloatingBallPosition() {
        viewModelScope.launch {
            try {
                repository.resetFloatingBallPosition()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            try {
                repository.setThemeMode(mode)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setWebViewZoomEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setWebViewZoomEnabled(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setKeepScreenOn(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setOpenLinksInExternalBrowser(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setOpenLinksInExternalBrowser(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setGlobalNotificationCountEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setGlobalNotificationCountEnabled(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getWorkspaceById(id: String): Workspace? {
        return repository.getWorkspace(id)
    }

    fun checkSessionAndOpen(
        workspaceId: String,
        onValid: () -> Unit,
        onExpired: () -> Unit,
        onUnavailable: () -> Unit = {}
    ) {
        if (_isOpeningWorkspace.value) return

        _isOpeningWorkspace.value = true
        _openingWorkspaceId.value = workspaceId

        openWorkspaceJob?.cancel()
        openWorkspaceJob = viewModelScope.launch {
            try {
                val result = repository.checkSession(workspaceId)
                if (result is SessionCheckResult.Valid) {
                    onValid()
                } else if (result is SessionCheckResult.ExpiredOrUnauthorized) {
                    onExpired()
                } else {
                    onUnavailable()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                onUnavailable()
            } finally {
                _isOpeningWorkspace.value = false
                _openingWorkspaceId.value = null
            }
        }
    }

    fun signInAndSaveWorkspace(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        notificationCountEnabled: Boolean = true,
        onResult: (LoginResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.signInAndSaveWorkspace(
                name = name,
                serverUrl = serverUrl,
                username = username,
                password = password,
                notificationCountEnabled = notificationCountEnabled
            )
            onResult(result)
        }
    }

    fun reLoginAndUpdateWorkspace(
        workspaceId: String,
        password: String,
        onResult: (LoginResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.reLoginAndUpdateWorkspace(
                workspaceId = workspaceId,
                password = password
            )
            onResult(result)
        }
    }

    fun checkSession(
        workspaceId: String,
        onResult: (SessionCheckResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.checkSession(workspaceId)
            onResult(result)
        }
    }

    fun updateWorkspaceWithAuth(
        id: String,
        name: String,
        serverUrl: String,
        username: String,
        password: String? = null,
        notificationCountEnabled: Boolean = true,
        onResult: (LoginResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updateWorkspaceWithAuth(
                id = id,
                name = name,
                serverUrl = serverUrl,
                username = username,
                password = password,
                notificationCountEnabled = notificationCountEnabled
            )
            onResult(result)
        }
    }

    fun updateWorkspaceNotificationCountEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateWorkspaceNotificationCountEnabled(id, enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteWorkspace(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteWorkspace(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setLastUsedWorkspace(id: String) {
        viewModelScope.launch {
            try {
                repository.setLastUsedWorkspace(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setDefaultWorkspace(id: String) {
        viewModelScope.launch {
            try {
                repository.setDefaultWorkspace(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun unsetDefaultWorkspace(id: String) {
        viewModelScope.launch {
            try {
                repository.unsetDefaultWorkspace(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class WorkspaceViewModelFactory(
    private val repository: WorkspaceRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            return WorkspaceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
