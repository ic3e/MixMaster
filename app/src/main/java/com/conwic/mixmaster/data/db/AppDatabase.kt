package com.conwic.mixmaster.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.conwic.mixmaster.data.db.dao.FloorDao
import com.conwic.mixmaster.data.db.dao.NoteDao
import com.conwic.mixmaster.data.db.dao.PhotoDao
import com.conwic.mixmaster.data.db.dao.ProductDao
import com.conwic.mixmaster.data.db.dao.ProjectDao
import com.conwic.mixmaster.data.db.dao.RoomAreaDao
import com.conwic.mixmaster.data.db.dao.TaskDao
import com.conwic.mixmaster.data.db.dao.TeamMemberDao
import com.conwic.mixmaster.data.db.dao.UsageLogDao
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import com.conwic.mixmaster.data.db.entity.UsageLogEntity
import com.conwic.mixmaster.data.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val DATABASE_NAME = "mixmaster.db"

@Database(
    entities = [
        ProductEntity::class,
        ProductComponentEntity::class,
        ProjectEntity::class,
        FloorEntity::class,
        RoomAreaEntity::class,
        TaskEntity::class,
        NoteEntity::class,
        PhotoEntity::class,
        TeamMemberEntity::class,
        UsageLogEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun projectDao(): ProjectDao
    abstract fun floorDao(): FloorDao
    abstract fun roomAreaDao(): RoomAreaDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun photoDao(): PhotoDao
    abstract fun teamMemberDao(): TeamMemberDao
    abstract fun usageLogDao(): UsageLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        /** Closes the live connection and drops the cached singleton so the next [getInstance]
         * call reopens the file from disk — used right before a backup file is restored over it. */
        fun closeAndReset() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        /** Adds pack size / unit / type and a numeric density to each mix component.
         * Written as a real migration rather than a destructive one because there are now
         * projects, tasks and logged usage on devices that must survive the update. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE product_components ADD COLUMN packageSize REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE product_components ADD COLUMN packageUnit TEXT NOT NULL DEFAULT 'kg'")
                db.execSQL("ALTER TABLE product_components ADD COLUMN packageType TEXT NOT NULL DEFAULT 'bag'")
                db.execSQL("ALTER TABLE product_components ADD COLUMN densityKgPerL REAL NOT NULL DEFAULT 0")
            }
        }

        /** Lets a task exist without a project, so the home screen can hold a plain job list.
         * SQLite can't relax a NOT NULL column in place, so the table is rebuilt: the new table
         * is created with the exact shape Room expects for version 4, the rows are copied over,
         * and the two indices are put back. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`projectId` INTEGER, " +
                        "`title` TEXT NOT NULL, " +
                        "`dueDate` INTEGER, " +
                        "`priority` TEXT NOT NULL, " +
                        "`isDone` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO `tasks_new` (`id`, `projectId`, `title`, `dueDate`, `priority`, `isDone`) " +
                        "SELECT `id`, `projectId`, `title`, `dueDate`, `priority`, `isDone` FROM `tasks`",
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueDate` ON `tasks` (`dueDate`)")
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                // Last resort only: with a migration in place this shouldn't fire, but it keeps
                // the app openable rather than stuck if a future version misses a path.
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // First launch: seed the real manufacturer product catalog and a
                        // starter team so the app isn't empty on first open.
                        CoroutineScope(Dispatchers.IO).launch {
                            getInstance(context).let { database ->
                                SeedData.seed(database)
                            }
                        }
                    }
                })
                .build()
    }
}
