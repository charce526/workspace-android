package cn.xzbim.workspace.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cn.xzbim.workspace.data.local.entity.WorkspaceNotificationStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * 站内消息未读数与退避状态 Room DAO 访问接口
 */
@Dao
interface WorkspaceNotificationStateDao {

    @Query("SELECT * FROM workspace_notification_states WHERE workspaceId = :workspaceId LIMIT 1")
    suspend fun getStateByWorkspaceId(workspaceId: String): WorkspaceNotificationStateEntity?

    @Query("SELECT * FROM workspace_notification_states")
    fun getAllNotificationStatesFlow(): Flow<List<WorkspaceNotificationStateEntity>>

    @Query("SELECT * FROM workspace_notification_states")
    suspend fun getAllNotificationStates(): List<WorkspaceNotificationStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateState(state: WorkspaceNotificationStateEntity)

    @Update
    suspend fun updateState(state: WorkspaceNotificationStateEntity)

    @Query("DELETE FROM workspace_notification_states WHERE workspaceId = :workspaceId")
    suspend fun deleteStateByWorkspaceId(workspaceId: String)
}
