package cn.xzbim.workspace.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 工作空间 Entity 实体表，映射 `workspaces` 数据表
 */
@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "server_url")
    val serverUrl: String,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_last_used")
    val isLastUsed: Boolean = false,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "notification_count_enabled")
    val notificationCountEnabled: Boolean = true,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0
)
