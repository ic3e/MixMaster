package com.conwic.mixmaster.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.conwic.mixmaster.data.db.dao.FloorDao
import com.conwic.mixmaster.data.db.dao.NoteDao
import com.conwic.mixmaster.data.db.dao.PhotoDao
import com.conwic.mixmaster.data.db.dao.ProductDao
import com.conwic.mixmaster.data.db.dao.ProjectDao
import com.conwic.mixmaster.data.db.dao.RoomAreaDao
import com.conwic.mixmaster.data.db.dao.TaskDao
import com.conwic.mixmaster.data.db.dao.TeamMemberDao
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
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
    ],
    version = 1,
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

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
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
