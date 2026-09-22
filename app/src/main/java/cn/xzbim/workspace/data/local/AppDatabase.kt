package cn.xzbim.workspace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.xzbim.workspace.data.local.entity.WorkspaceEntity

/**
 * 应用 Room 数据库，管理表结构与版本（数据库名称：`workspace_database`，升级为版本 2 支持默认工作空间字段）
 */
@Database(
    entities = [WorkspaceEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        // Version 1 predates the default-workspace flag. Never silently erase saved workspaces.
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "workspace_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
