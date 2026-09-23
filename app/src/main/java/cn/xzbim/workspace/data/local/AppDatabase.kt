package cn.xzbim.workspace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.xzbim.workspace.data.local.dao.WorkspaceNotificationStateDao
import cn.xzbim.workspace.data.local.entity.WorkspaceEntity
import cn.xzbim.workspace.data.local.entity.WorkspaceNotificationStateEntity

/**
 * 应用 Room 数据库（数据库名称：`workspace_database`，升级为版本 4 支持工作空间拖拽排序字段 `order_index`）
 */
@Database(
    entities = [WorkspaceEntity::class, WorkspaceNotificationStateEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workspaceDao(): WorkspaceDao
    abstract fun workspaceNotificationStateDao(): WorkspaceNotificationStateDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = mutableSetOf<String>()
                db.query("PRAGMA table_info(workspaces)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
                }
                if ("is_default" !in columns) {
                    db.execSQL("ALTER TABLE workspaces ADD COLUMN is_default INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = mutableSetOf<String>()
                db.query("PRAGMA table_info(workspaces)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
                }
                if ("notification_count_enabled" !in columns) {
                    db.execSQL("ALTER TABLE workspaces ADD COLUMN notification_count_enabled INTEGER NOT NULL DEFAULT 1")
                }
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workspace_notification_states` (
                        `workspaceId` TEXT NOT NULL,
                        `unread_count` INTEGER NOT NULL DEFAULT 0,
                        `status` TEXT NOT NULL DEFAULT 'AVAILABLE',
                        `failure_count` INTEGER NOT NULL DEFAULT 0,
                        `last_attempt_at` INTEGER NOT NULL DEFAULT 0,
                        `last_success_at` INTEGER NOT NULL DEFAULT 0,
                        `next_retry_at` INTEGER NOT NULL DEFAULT 0,
                        `last_error_type` TEXT,
                        PRIMARY KEY(`workspaceId`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = mutableSetOf<String>()
                db.query("PRAGMA table_info(workspaces)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
                }
                if ("order_index" !in columns) {
                    db.execSQL("ALTER TABLE workspaces ADD COLUMN order_index INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "workspace_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
