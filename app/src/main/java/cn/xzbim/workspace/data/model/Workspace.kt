package cn.xzbim.workspace.data.model

/**
 * 工作空间 (Workspace) 领域模型
 */
data class Workspace(
    val id: String,
    val name: String,
    val serverUrl: String,
    val username: String,
    val lastUsedTime: Long = System.currentTimeMillis(),
    val isLastUsed: Boolean = false,
    val isDefault: Boolean = false,
    val notificationCountEnabled: Boolean = true
)
