package cn.xzbim.workspace.data.local.mapper

import cn.xzbim.workspace.data.local.entity.WorkspaceEntity
import cn.xzbim.workspace.data.model.Workspace

fun WorkspaceEntity.toDomainModel(): Workspace {
    return Workspace(
        id = id,
        name = name,
        serverUrl = serverUrl,
        username = username,
        lastUsedTime = lastUsedAt,
        isLastUsed = isLastUsed,
        isDefault = isDefault,
        notificationCountEnabled = notificationCountEnabled
    )
}

fun Workspace.toEntity(): WorkspaceEntity {
    return WorkspaceEntity(
        id = id,
        name = name,
        serverUrl = serverUrl,
        username = username,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        lastUsedAt = lastUsedTime,
        isLastUsed = isLastUsed,
        isDefault = isDefault,
        notificationCountEnabled = notificationCountEnabled
    )
}
