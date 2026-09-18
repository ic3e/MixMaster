package com.conwic.mixmaster.data.seed

import com.conwic.mixmaster.data.db.AppDatabase
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.model.TaskPriority
import java.time.Instant
import java.time.LocalDate

/**
 * First-run seed: the real manufacturer product catalog (ratios researched from public
 * Ideal Work / Mapei / Sika / Ardex technical datasheets) plus one demo project so the app
 * isn't empty on first open. Runs once, from [AppDatabase]'s onCreate callback.
 */
object SeedData {

    private data class SeedProduct(
        val brand: String,
        val name: String,
        val category: String,
        val dosingMode: DosingMode,
        val doseGramsPerM2: Double,
        val doseUnitLabel: String,
        val rangeNote: String,
        val sourceNote: String,
        val parts: List<Pair<String, Double>>,
    )

    private val products = listOf(
        SeedProduct(
            brand = "Ideal Work",
            name = "Microtopping® Base Coat",
            category = "Microtopping",
            dosingMode = DosingMode.COATS,
            doseGramsPerM2 = 1350.0,
            doseUnitLabel = "per coat",
            rangeNote = "~1.35 kg/m² per coat",
            sourceNote = "Ideal Work technical datasheet",
            parts = listOf("Powder" to 100.0, "Polymer" to 35.0),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Microtopping® HP",
            category = "Microtopping",
            dosingMode = DosingMode.COATS,
            doseGramsPerM2 = 840.0,
            doseUnitLabel = "per coat",
            rangeNote = "~0.84 kg/m² per coat",
            sourceNote = "Ideal Work technical datasheet",
            parts = listOf("Powder" to 100.0, "Polymer" to 40.0),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Microtopping® Finish Coat",
            category = "Microtopping",
            dosingMode = DosingMode.COATS,
            doseGramsPerM2 = 225.0,
            doseUnitLabel = "per coat",
            rangeNote = "~0.225 kg/m² per coat",
            sourceNote = "Ideal Work technical datasheet",
            parts = listOf("Powder" to 100.0, "Polymer" to 50.0),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Nuvolato Architop® Coat 1",
            category = "Architop",
            dosingMode = DosingMode.POUR,
            doseGramsPerM2 = 2480.0,
            doseUnitLabel = "per pour",
            rangeNote = "~2.48 kg/m² per pour",
            sourceNote = "Ideal Work technical datasheet",
            parts = listOf("Powder" to 25.0, "Polymer" to 6.0),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Nuvolato Architop® Coat 2",
            category = "Architop",
            dosingMode = DosingMode.POUR,
            doseGramsPerM2 = 2070.0,
            doseUnitLabel = "per pour + water",
            rangeNote = "~2.07 kg/m² + water",
            sourceNote = "Ideal Work technical datasheet",
            parts = listOf("Powder" to 25.0, "Polymer" to 4.0, "Water" to 2.0),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "IdealPU-WB Primer",
            category = "Primer",
            dosingMode = DosingMode.COATS,
            doseGramsPerM2 = 50.0,
            doseUnitLabel = "per coat · 2K epoxy",
            rangeNote = "~50 g/m² · 2K epoxy — exact A:B split not published, check datasheet",
            sourceNote = "Ideal Work technical datasheet (mix ratio not publicly listed)",
            parts = listOf("Mixed product (see datasheet for A:B)" to 1.0),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Ideal Sealer",
            category = "Sealer",
            dosingMode = DosingMode.COATS,
            doseGramsPerM2 = 180.0,
            doseUnitLabel = "per coat · 1 component",
            rangeNote = "~180 g/m² · single-component",
            sourceNote = "Ideal Work technical datasheet",
            parts = listOf("Sealer (1K, ready to use)" to 1.0),
        ),
        SeedProduct(
            brand = "Mapei",
            name = "Ultraplan Eco",
            category = "Self-Levelling",
            dosingMode = DosingMode.MM,
            doseGramsPerM2 = 1600.0,
            doseUnitLabel = "per mm",
            rangeNote = "~1.6 kg/m² per mm",
            sourceNote = "Mapei technical datasheet",
            parts = listOf("Powder" to 100.0, "Water" to 25.0),
        ),
        SeedProduct(
            brand = "Mapei",
            name = "Primer G",
            category = "Primer",
            dosingMode = DosingMode.COATS,
            doseGramsPerM2 = 125.0,
            doseUnitLabel = "per coat",
            rangeNote = "~0.10–0.15 kg/m² · diluted 1:1–1:3 with water (range, not fixed)",
            sourceNote = "Mapei technical datasheet — dilution shown is an illustrative midpoint of the published 1:1–1:3 range",
            parts = listOf("Primer G" to 1.0, "Water" to 2.0),
        ),
        SeedProduct(
            brand = "Sika",
            name = "Sikafloor®-263 SL",
            category = "Self-Levelling",
            dosingMode = DosingMode.MM,
            doseGramsPerM2 = 1050.0,
            doseUnitLabel = "per mm",
            rangeNote = "~0.9–1.2 kg/m² per mm",
            sourceNote = "Sika technical datasheet",
            parts = listOf("Part A" to 79.0, "Part B" to 21.0),
        ),
        SeedProduct(
            brand = "Ardex",
            name = "ARDEX K 301",
            category = "Self-Levelling",
            dosingMode = DosingMode.MM,
            doseGramsPerM2 = 1600.0,
            doseUnitLabel = "per mm",
            rangeNote = "~1.6 kg/m² per mm",
            sourceNote = "Ardex technical datasheet",
            parts = listOf("Powder" to 100.0, "Water" to 21.0),
        ),
    )

    suspend fun seed(db: AppDatabase) {
        val productDao = db.productDao()
        if (productDao.countAll() > 0) return

        val productIds = mutableMapOf<String, Long>()
        products.forEach { seedProduct ->
            val id = productDao.insertProductWithComponents(
                product = ProductEntity(
                    brand = seedProduct.brand,
                    name = seedProduct.name,
                    category = seedProduct.category,
                    dosingMode = seedProduct.dosingMode,
                    typicalDoseGramsPerM2 = seedProduct.doseGramsPerM2,
                    doseUnitLabel = seedProduct.doseUnitLabel,
                    rangeNote = seedProduct.rangeNote,
                    sourceNote = seedProduct.sourceNote,
                ),
                components = seedProduct.parts.mapIndexed { index, (label, ratio) ->
                    ProductComponentEntity(productId = 0, label = label, ratioParts = ratio, sortOrder = index)
                },
            )
            productIds[seedProduct.name] = id
        }

        seedDemoTeam(db)
        seedDemoProject(db, productIds)
    }

    private suspend fun seedDemoTeam(db: AppDatabase) {
        val teamDao = db.teamMemberDao()
        teamDao.insert(TeamMemberEntity(name = "Tanel", email = "tanel@conwic.fi", role = Role.EMPLOYER))
        teamDao.insert(TeamMemberEntity(name = "Marek Saar", email = "marek@conwic.fi", role = Role.WORKER))
        teamDao.insert(TeamMemberEntity(name = "Jaan Kask", email = "jaan@conwic.fi", role = Role.WORKER))
    }

    private suspend fun seedDemoProject(db: AppDatabase, productIds: Map<String, Long>) {
        val projectId = db.projectDao().insert(
            ProjectEntity(
                name = "Riverside Warehouse Floor",
                clientName = "Baltic Freight OÜ",
                address = "Paldiski mnt 12, Tallinn",
                status = ProjectStatus.ACTIVE,
                startDate = LocalDate.of(2026, 9, 2),
                targetFinishDate = LocalDate.of(2026, 9, 30),
                scopeNotes = "3,100 m² warehouse floor. Ideal Work Microtopping® system — Base Coat, " +
                    "then Finish Coat — over the existing slab. Client wants light grey (colour chart 03), " +
                    "matte sealer. Access via loading dock B only before 16:00.",
            ),
        )

        val groundFloorId = db.floorDao().insert(FloorEntity(projectId = projectId, name = "Ground Floor", sortOrder = 0))
        val mezzanineId = db.floorDao().insert(FloorEntity(projectId = projectId, name = "Mezzanine", sortOrder = 1))

        val baseCoatId = productIds["Microtopping® Base Coat"]
        val finishCoatId = productIds["Microtopping® Finish Coat"]

        val roomDao = db.roomAreaDao()
        roomDao.insert(RoomAreaEntity(floorId = groundFloorId, projectId = projectId, name = "Bay 1", areaM2 = 1550.0, assignedProductId = baseCoatId, sortOrder = 0))
        roomDao.insert(RoomAreaEntity(floorId = groundFloorId, projectId = projectId, name = "Bay 2", areaM2 = 1550.0, assignedProductId = baseCoatId, sortOrder = 1))
        roomDao.insert(RoomAreaEntity(floorId = groundFloorId, projectId = projectId, name = "Loading Dock", areaM2 = 210.0, assignedProductId = null, sortOrder = 2))
        roomDao.insert(RoomAreaEntity(floorId = mezzanineId, projectId = projectId, name = "Office", areaM2 = 85.0, assignedProductId = finishCoatId, sortOrder = 0))
        roomDao.insert(RoomAreaEntity(floorId = mezzanineId, projectId = projectId, name = "Corridor", areaM2 = 40.0, assignedProductId = null, sortOrder = 1))

        val taskDao = db.taskDao()
        taskDao.insert(TaskEntity(projectId = projectId, title = "Apply Finish Coat — bay 2", dueDate = LocalDate.of(2026, 9, 19), priority = TaskPriority.HIGH))
        taskDao.insert(TaskEntity(projectId = projectId, title = "Order more Microtopping® powder", dueDate = LocalDate.of(2026, 9, 18), priority = TaskPriority.MEDIUM))
        taskDao.insert(TaskEntity(projectId = projectId, title = "Prime bay 1 — IdealPU-WB", dueDate = LocalDate.of(2026, 9, 16), priority = TaskPriority.DONE, isDone = true))
        taskDao.insert(TaskEntity(projectId = projectId, title = "Client walkthrough", dueDate = LocalDate.of(2026, 9, 22), priority = TaskPriority.LOW))

        db.noteDao().insert(
            NoteEntity(
                projectId = projectId,
                authorName = "Marek (site)",
                authorRole = Role.WORKER,
                text = "Bay 1 base coat went down clean — humidity was fine this morning.",
                createdAt = Instant.now().minusSeconds(3600),
            ),
        )
        db.noteDao().insert(
            NoteEntity(
                projectId = projectId,
                authorName = "Tanel",
                authorRole = Role.EMPLOYER,
                text = "Client confirmed colour chart 03. Order two extra bags of Finish Coat for the office area.",
                createdAt = Instant.now().minusSeconds(86_400),
            ),
        )
    }
}
