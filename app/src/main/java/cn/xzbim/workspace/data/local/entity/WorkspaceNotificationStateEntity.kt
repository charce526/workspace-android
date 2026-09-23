package cn.xzbim.workspace.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 站内消息未读数与重试状态 Room Entity 实体表
 */
@Entity(tableName = "workspace_notification_states")
data class WorkspaceNotificationStateEntity(
    @PrimaryKey
    val workspaceId: String,

    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0,

    @ColumnInfo(name = "status")
    val status: String = "AVAILABLE",

    @ColumnInfo(name = "failure_count")
    val failureCount: Int = 0,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long = 0L,

    @ColumnInfo(name = "last_success_at")
    val lastSuccessAt: Long = 0L,

    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long = 0L,

    @ColumnInfo(name = "last_error_type")
    val lastErrorType: String? = null
)
