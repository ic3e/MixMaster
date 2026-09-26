package com.conwic.mixmaster.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.conwic.mixmaster.data.db.dao.FloorDao
import com.conwic.mixmaster.data.db.dao.MaterialUseDao
import com.conwic.mixmaster.data.db.dao.NoteDao
import com.conwic.mixmaster.data.db.dao.PhotoDao
import com.conwic.mixmaster.data.db.dao.ProductDao
import com.conwic.mixmaster.data.db.dao.DeliveryDao
import com.conwic.mixmaster.data.db.dao.StockDao
import com.conwic.mixmaster.data.db.dao.RoomLayerDao
import com.conwic.mixmaster.data.db.dao.SolutionDao
import com.conwic.mixmaster.data.db.dao.ProjectDao
import com.conwic.mixmaster.data.db.dao.RoomAreaDao
import com.conwic.mixmaster.data.db.dao.TaskDao
import com.conwic.mixmaster.data.db.dao.TeamMemberDao
import com.conwic.mixmaster.data.db.dao.UsageLogDao
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.MaterialUseEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import com.conwic.mixmaster.data.db.entity.UsageLogEntity
import com.conwic.mixmaster.data.db.entity.DeliveryEntity
import com.conwic.mixmaster.data.db.entity.StockEntity
import com.conwic.mixmaster.domain.isWaterLabel
import com.conwic.mixmaster.data.db.entity.RoomLayerEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineEntity
import com.conwic.mixmaster.data.db.entity.SolutionEntity
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
        StockEntity::class,
        SolutionEntity::class,
        SolutionLineEntity::class,
        RoomLayerEntity::class,
        DeliveryEntity::class,
        MaterialUseEntity::class,
    ],
    version = 15,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stockDao(): StockDao

    abstract fun deliveryDao(): DeliveryDao

    abstract fun solutionDao(): SolutionDao

    abstract fun roomLayerDao(): RoomLayerDao

    abstract fun productDao(): ProductDao
    abstract fun projectDao(): ProjectDao
    abstract fun floorDao(): FloorDao
    abstract fun roomAreaDao(): RoomAreaDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun photoDao(): PhotoDao
    abstract fun teamMemberDao(): TeamMemberDao
    abstract fun usageLogDao(): UsageLogDao

    abstract fun materialUseDao(): MaterialUseDao

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

        /** Adds the three columns that let a product be used as an add-on to another's mix. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN isAddOn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN addOnAmountPerKg REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN addOnUnit TEXT NOT NULL DEFAULT 'kg'")
            }
        }

        /**
         * Adds the warehouse: what is on the shelf for each part, and the stamp that says a
         * project has taken its material out (after which it stops booking any).
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`productId` INTEGER NOT NULL, " +
                        "`componentId` INTEGER NOT NULL, " +
                        "`fullPacks` INTEGER NOT NULL DEFAULT 0, " +
                        "`openAmount` REAL NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_componentId` ON `stock` (`componentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_productId` ON `stock` (`productId`)")
                db.execSQL("ALTER TABLE projects ADD COLUMN materialsIssuedAt INTEGER")
            }
        }

        /**
         * Splits products into what you buy and what you mix.
         *
         * A product carried its own mix ratio until now, so water sat in the catalogue as a
         * product with a recipe, and a room could hold exactly one of them — no primer, no
         * sealer. A product is now a bought item with a pack size of its own, a solution is a
         * recipe made of products, and a room holds an ordered list of coats.
         *
         * Everything already entered is carried across. Each product becomes a solution of the
         * same name, and each of its parts becomes a product named after its parent, so
         * "Powder" from two brands cannot be silently merged into one. Water-like parts are the
         * exception and share a single Water, which is what a shed actually holds. The old
         * product rows stay, archived, because logged jobs and rooms still point at them.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Packaging belongs to the thing that is ordered, carried and counted.
                db.execSQL("ALTER TABLE products ADD COLUMN packageSize REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN packageUnit TEXT NOT NULL DEFAULT 'kg'")
                db.execSQL("ALTER TABLE products ADD COLUMN packageType TEXT NOT NULL DEFAULT 'bag'")
                db.execSQL("ALTER TABLE products ADD COLUMN densityKgPerL REAL NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `solutions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`brand` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`dosingMode` TEXT NOT NULL, `minDoseGramsPerM2` REAL NOT NULL, " +
                        "`maxDoseGramsPerM2` REAL NOT NULL, `typicalDoseGramsPerM2` REAL NOT NULL, " +
                        "`doseUnitLabel` TEXT NOT NULL, `rangeNote` TEXT NOT NULL, " +
                        "`sourceNote` TEXT NOT NULL, `datasheetUrl` TEXT NOT NULL, " +
                        "`ratioLabel` TEXT NOT NULL, `isArchived` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `solution_lines` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`solutionId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL, `role` TEXT NOT NULL, `ratioParts` REAL NOT NULL, " +
                        "`amountPerKg` REAL NOT NULL DEFAULT 0, " +
                        "`amountUnit` TEXT NOT NULL DEFAULT 'kg', " +
                        "`againstLineId` INTEGER NOT NULL DEFAULT 0, `sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`solutionId`) REFERENCES `solutions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_solution_lines_solutionId` ON `solution_lines` (`solutionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_solution_lines_productId` ON `solution_lines` (`productId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `room_layers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`roomId` INTEGER NOT NULL, " +
                        "`solutionId` INTEGER NOT NULL DEFAULT 0, " +
                        "`productId` INTEGER NOT NULL DEFAULT 0, " +
                        "`doseGramsPerM2` REAL NOT NULL DEFAULT 0, " +
                        "`quantity` REAL NOT NULL DEFAULT 1, " +
                        "`colourProductId` INTEGER NOT NULL DEFAULT 0, " +
                        "`colourAmountPerKg` REAL NOT NULL DEFAULT 0, " +
                        "`colourUnit` TEXT NOT NULL DEFAULT 'kg', " +
                        "`colourAgainstIndex` INTEGER NOT NULL DEFAULT 0, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`roomId`) REFERENCES `room_areas`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_room_layers_roomId` ON `room_layers` (`roomId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_room_layers_solutionId` ON `room_layers` (`solutionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_room_layers_productId` ON `room_layers` (`productId`)")

                fun lastId(): Long =
                    db.query("SELECT last_insert_rowid()").use { c ->
                        if (c.moveToFirst()) c.getLong(0) else 0L
                    }

                /** A bought item, carrying the packaging the mix part was entered with. */
                fun newProduct(
                    name: String,
                    brand: String,
                    category: String,
                    packSize: Double,
                    packUnit: String,
                    packType: String,
                    density: Double,
                ): Long {
                    db.execSQL(
                        "INSERT INTO products (brand, name, category, dosingMode, minDoseGramsPerM2, " +
                            "maxDoseGramsPerM2, typicalDoseGramsPerM2, doseUnitLabel, rangeNote, " +
                            "sourceNote, datasheetUrl, ratioLabel, isAddOn, addOnAmountPerKg, addOnUnit, " +
                            "isArchived, packageSize, packageUnit, packageType, densityKgPerL) " +
                            "VALUES (?, ?, ?, 'COATS', 0, 0, 0, '', '', '', '', '', 0, 0, 'kg', 0, ?, ?, ?, ?)",
                        arrayOf<Any>(brand, name, category, packSize, packUnit, packType, density),
                    )
                    return lastId()
                }

                var waterId = 0L
                fun water(): Long {
                    if (waterId == 0L) waterId = newProduct("Water", "", "Water", 0.0, "L", "canister", 1.0)
                    return waterId
                }

                // Read the whole catalogue first. Inserting bought items while a cursor is open
                // on the same table would have it walk over rows this migration just wrote.
                val sources = mutableListOf<Array<Any?>>()
                db.query(
                    "SELECT id, brand, name, category, dosingMode, minDoseGramsPerM2, maxDoseGramsPerM2, " +
                        "typicalDoseGramsPerM2, doseUnitLabel, rangeNote, sourceNote, datasheetUrl, " +
                        "ratioLabel, isAddOn FROM products WHERE isArchived = 0",
                ).use { c ->
                    while (c.moveToNext()) {
                        sources.add(
                            arrayOf(
                                c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "",
                                c.getString(3) ?: "", c.getString(4) ?: "COATS", c.getDouble(5),
                                c.getDouble(6), c.getDouble(7), c.getString(8) ?: "",
                                c.getString(9) ?: "", c.getString(10) ?: "", c.getString(11) ?: "",
                                c.getString(12) ?: "", c.getInt(13),
                            ),
                        )
                    }
                }

                val componentToProduct = mutableMapOf<Long, Long>()
                val productToSolution = mutableMapOf<Long, Long>()

                for (row in sources) {
                    val oldId = row[0] as Long
                    val brand = row[1] as String
                    val name = row[2] as String
                    val category = row[3] as String

                    val parts = mutableListOf<Array<Any?>>()
                    db.query(
                        "SELECT id, label, ratioParts, packageSize, packageUnit, packageType, densityKgPerL " +
                            "FROM product_components WHERE productId = ? ORDER BY sortOrder",
                        arrayOf<Any>(oldId),
                    ).use { c ->
                        while (c.moveToNext()) {
                            parts.add(
                                arrayOf(
                                    c.getLong(0), c.getString(1) ?: "", c.getDouble(2), c.getDouble(3),
                                    c.getString(4) ?: "kg", c.getString(5) ?: "bag", c.getDouble(6),
                                ),
                            )
                        }
                    }

                    // A colour or admixture is already a bought item: it keeps its place in the
                    // catalogue and becomes a line on whichever mix uses it.
                    if ((row[13] as Int) == 1) {
                        val first = parts.firstOrNull()
                        if (first != null) {
                            db.execSQL(
                                "UPDATE products SET packageSize = ?, packageUnit = ?, packageType = ?, " +
                                    "densityKgPerL = ? WHERE id = ?",
                                arrayOf<Any>(first[3] as Double, first[4] as String, first[5] as String, first[6] as Double, oldId),
                            )
                        }
                        continue
                    }

                    db.execSQL(
                        "INSERT INTO solutions (brand, name, category, dosingMode, minDoseGramsPerM2, " +
                            "maxDoseGramsPerM2, typicalDoseGramsPerM2, doseUnitLabel, rangeNote, " +
                            "sourceNote, datasheetUrl, ratioLabel, isArchived) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                        arrayOf<Any>(
                            brand, name, category, row[4] as String, row[5] as Double, row[6] as Double,
                            row[7] as Double, row[8] as String, row[9] as String, row[10] as String,
                            row[11] as String, row[12] as String,
                        ),
                    )
                    val solutionId = lastId()
                    productToSolution[oldId] = solutionId

                    if (parts.isEmpty()) {
                        // Nothing was ever said about its parts, so it is the whole of its own mix.
                        db.execSQL(
                            "INSERT INTO solution_lines (solutionId, productId, label, role, ratioParts, " +
                                "amountPerKg, amountUnit, againstLineId, sortOrder) " +
                                "VALUES (?, ?, '', 'BASE', 100, 0, 'kg', 0, 0)",
                            arrayOf<Any>(solutionId, newProduct(name, brand, category, 0.0, "kg", "bag", 0.0)),
                        )
                    } else {
                        parts.forEachIndexed { index, part ->
                            val label = part[1] as String
                            val productId = if (isWaterLabel(label)) {
                                water()
                            } else {
                                newProduct(
                                    if (parts.size == 1) name else "$name $label",
                                    brand, category,
                                    part[3] as Double, part[4] as String, part[5] as String, part[6] as Double,
                                )
                            }
                            componentToProduct[part[0] as Long] = productId
                            db.execSQL(
                                "INSERT INTO solution_lines (solutionId, productId, label, role, ratioParts, " +
                                    "amountPerKg, amountUnit, againstLineId, sortOrder) " +
                                    "VALUES (?, ?, ?, 'BASE', ?, 0, 'kg', 0, ?)",
                                arrayOf<Any>(solutionId, productId, label, part[2] as Double, index),
                            )
                        }
                    }

                    // The old row was the mix, not a thing you buy. It stays for whatever points
                    // at it, out of the catalogue.
                    db.execSQL("UPDATE products SET isArchived = 1 WHERE id = ?", arrayOf<Any>(oldId))
                }

                // A room's single product becomes its first coat.
                val assignments = mutableListOf<Pair<Long, Long>>()
                db.query("SELECT id, assignedProductId FROM room_areas WHERE assignedProductId IS NOT NULL").use { c ->
                    while (c.moveToNext()) assignments.add(c.getLong(0) to c.getLong(1))
                }
                assignments.forEach { (roomId, oldProductId) ->
                    val solutionId = productToSolution[oldProductId]
                    if (solutionId != null) {
                        db.execSQL(
                            "INSERT INTO room_layers (roomId, solutionId, productId, doseGramsPerM2, quantity, sortOrder) " +
                                "VALUES (?, ?, 0, 0, 1, 0)",
                            arrayOf<Any>(roomId, solutionId),
                        )
                    }
                }

                // Logged readings belong to the mix they were taken on, so the table is rebuilt
                // around the solution. A reading against something that became no solution — a
                // pigment — has nothing to be an average of, and is left behind.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usage_logs_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`solutionId` INTEGER NOT NULL, " +
                        "`doseGramsPerM2` REAL NOT NULL, " +
                        "`loggedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`solutionId`) REFERENCES `solutions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                val readings = mutableListOf<Array<Any?>>()
                db.query("SELECT productId, doseGramsPerM2, loggedAt FROM usage_logs").use { c ->
                    while (c.moveToNext()) {
                        readings.add(arrayOf(c.getLong(0), c.getDouble(1), c.getLong(2)))
                    }
                }
                readings.forEach { reading ->
                    val solutionId = productToSolution[reading[0] as Long]
                    if (solutionId != null) {
                        db.execSQL(
                            "INSERT INTO usage_logs_new (solutionId, doseGramsPerM2, loggedAt) VALUES (?, ?, ?)",
                            arrayOf<Any>(solutionId, reading[1] as Double, reading[2] as Long),
                        )
                    }
                }
                db.execSQL("DROP TABLE `usage_logs`")
                db.execSQL("ALTER TABLE `usage_logs_new` RENAME TO `usage_logs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_logs_solutionId` ON `usage_logs` (`solutionId`)")

                // Stock counted against a mix part is stock of the product that part became.
                val counts = mutableListOf<Array<Any?>>()
                db.query("SELECT componentId, fullPacks, openAmount, updatedAt FROM stock").use { c ->
                    while (c.moveToNext()) {
                        counts.add(arrayOf(c.getLong(0), c.getInt(1), c.getDouble(2), c.getLong(3)))
                    }
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`productId` INTEGER NOT NULL, " +
                        "`fullPacks` INTEGER NOT NULL DEFAULT 0, " +
                        "`openAmount` REAL NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0)",
                )
                counts.forEach { count ->
                    val productId = componentToProduct[count[0] as Long]
                    if (productId != null) {
                        db.execSQL(
                            "INSERT OR REPLACE INTO stock_new (productId, fullPacks, openAmount, updatedAt) " +
                                "VALUES (?, ?, ?, ?)",
                            arrayOf<Any>(productId, count[1] as Int, count[2] as Double, count[3] as Long),
                        )
                    }
                }
                db.execSQL("DROP TABLE `stock`")
                db.execSQL("ALTER TABLE `stock_new` RENAME TO `stock`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_productId` ON `stock` (`productId`)")
            }
        }

        /**
         * Adds what has been ordered but has not turned up yet.
         *
         * Nothing existing changes: a delivery only becomes stock when someone says it arrived,
         * and until then it is there so the shelf can say "short, but two bags are due Friday"
         * rather than leaving that in someone's head.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `deliveries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`productId` INTEGER NOT NULL, " +
                        "`packs` INTEGER NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`expectedOn` INTEGER NOT NULL, " +
                        "`orderedOn` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`arrivedOn` INTEGER, " +
                        "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deliveries_productId` ON `deliveries` (`productId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deliveries_expectedOn` ON `deliveries` (`expectedOn`)")
            }
        }

        /**
         * Lets one mix hold more than one coat.
         *
         * A datasheet can give the same product two recipes — architop lays its first coat at
         * 2.0 kg/m² of hardener to 0.48 of catalyst and its second at 1.5 to 0.24 — so a coat
         * is a recipe of its own, tied to the others by the first one's id. Nothing already
         * entered changes: every existing mix is a family of one.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE solutions ADD COLUMN parentId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE solutions ADD COLUMN coatName TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds the mixing time a datasheet gives, so it can be counted down rather than guessed. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE solutions ADD COLUMN mixSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the receipts: what was actually mixed on a project, as against what the layout
         * plans and the warehouse carries.
         *
         * The parts of a mix ride along as JSON in one column — nothing queries inside them,
         * and they must not be recomputed from a recipe that has been edited since.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `material_uses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`projectId` INTEGER NOT NULL, " +
                        "`roomId` INTEGER NOT NULL, " +
                        "`solutionId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`jobLabel` TEXT NOT NULL, " +
                        "`batches` INTEGER NOT NULL, " +
                        "`totalGrams` REAL NOT NULL, " +
                        "`parts` TEXT NOT NULL, " +
                        "`mixedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_material_uses_projectId` " +
                        "ON `material_uses` (`projectId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_material_uses_roomId` " +
                        "ON `material_uses` (`roomId`)",
                )
            }
        }

        /**
         * Adds pot life to a recipe: how long the mixed material stays workable.
         *
         * It is on the datasheet of everything this firm lays and was nowhere in the app, so
         * the one figure that decides how big a batch should be was carried in somebody's head.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE solutions ADD COLUMN potLifeMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the two sheets every bought item has behind it: safety, and technical.
         *
         * Kept on the product rather than the recipe, because they are published per bought
         * item, and a client asking for documentation is asking about what went on their floor.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN safetySheetUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN technicalSheetUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        /** A part can now be a share of the others — see SolutionLineEntity.percentOfRest. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE solution_lines ADD COLUMN percentOfRest REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * Products found on site rather than bought. Water is the one there already is: the
         * shared Water every recipe points at, which the earlier migrations and the seed both
         * file under the Water category.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN suppliedOnSite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE products SET suppliedOnSite = 1 WHERE category = 'Water' OR lower(name) = 'water'")
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                )
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
