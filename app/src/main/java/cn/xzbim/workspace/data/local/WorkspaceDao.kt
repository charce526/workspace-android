package cn.xzbim.workspace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cn.xzbim.workspace.data.local.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 工作空间 Room DAO 数据访问接口
 */
@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspaces ORDER BY is_default DESC, last_used_at DESC")
    fun getAllWorkspacesFlow(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces")
    suspend fun getAllWorkspaces(): List<WorkspaceEntity>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: WorkspaceEntity)

    @Update
    suspend fun updateWorkspace(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspaceById(id: String)

    @Query("UPDATE workspaces SET is_last_used = 0")
    suspend fun clearLastUsed()

    @Query("UPDATE workspaces SET is_last_used = 1, last_used_at = :lastUsedAt WHERE id = :id")
    suspend fun setLastUsed(id: String, lastUsedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM workspaces WHERE is_last_used = 1 LIMIT 1")
    suspend fun getLastUsedWorkspace(): WorkspaceEntity?

    @Query("UPDATE workspaces SET is_default = 0")
    suspend fun clearDefaultWorkspace()

    @Query("UPDATE workspaces SET is_default = 1 WHERE id = :id")
    suspend fun setDefaultWorkspace(id: String)

    @Query("UPDATE workspaces SET is_default = 0 WHERE id = :id")
    suspend fun unsetDefaultWorkspace(id: String)

    @Query("SELECT * FROM workspaces WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultWorkspace(): WorkspaceEntity?
}
