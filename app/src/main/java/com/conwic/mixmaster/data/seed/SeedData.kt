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
 * First-run seed: the real manufacturer product catalog (ratios and coverage ranges researched
 * from public Ideal Work / Mapei / Sika / Ardex technical datasheets) plus one demo project so
 * the app isn't empty on first open. Runs once, from [AppDatabase]'s onCreate callback.
 */
object SeedData {

    private data class SeedComponent(
        val label: String,
        val ratio: Double,
        val basis: String = "Weight",
        val density: String = "",
        val potLife: String = "",
        val notes: String = "",
    )

    private data class SeedProduct(
        val brand: String,
        val name: String,
        val category: String,
        val dosingMode: DosingMode,
        /** Real published min/max coverage where known; equal to each other (and to typical)
         * where no range was found, rather than inventing one. */
        val minDoseGramsPerM2: Double,
        val maxDoseGramsPerM2: Double,
        val doseUnitLabel: String,
        val rangeNote: String,
        val sourceNote: String,
        val datasheetUrl: String = "",
        val parts: List<SeedComponent>,
        /** Override for the denormalized list-card badge, e.g. "1K" for single-component
         * products where joining a single ratio number wouldn't read sensibly. */
        val ratioLabelOverride: String? = null,
    ) {
        val typicalDoseGramsPerM2: Double get() = (minDoseGramsPerM2 + maxDoseGramsPerM2) / 2.0
        val ratioLabel: String get() = ratioLabelOverride ?: parts.joinToString(":") { it.ratio.toInt().toString() }
    }

    private val products = listOf(
        SeedProduct(
            brand = "Ideal Work",
            name = "Microtopping® Base Coat",
            category = "Microtopping",
            dosingMode = DosingMode.COATS,
            minDoseGramsPerM2 = 1150.0,
            maxDoseGramsPerM2 = 1550.0,
            doseUnitLabel = "per coat",
            rangeNote = "1.15–1.55 kg/m² per coat (typical 1.35)",
            sourceNote = "Ideal Work technical datasheet",
            datasheetUrl = "https://www.idealwork.it/wp-content/REPOSITORYFILE/TEC/IT/MICROTOPPING_TEC_ITA.pdf",
            parts = listOf(
                SeedComponent("Powder", 100.0, notes = "25 kg bag (grey or white)"),
                SeedComponent("Polymer", 35.0, potLife = "Mix 3 min before adding powder", notes = "Keep cool before and during use"),
            ),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Microtopping® HP",
            category = "Microtopping",
            dosingMode = DosingMode.COATS,
            minDoseGramsPerM2 = 715.0,
            maxDoseGramsPerM2 = 965.0,
            doseUnitLabel = "per coat",
            rangeNote = "0.72–0.97 kg/m² per coat (typical 0.84)",
            sourceNote = "Ideal Work technical datasheet",
            datasheetUrl = "https://www.idealwork.it/wp-content/REPOSITORYFILE/TEC/IT/MICROTOPPING-HP_TEC_ITA.pdf",
            parts = listOf(SeedComponent("Powder", 100.0), SeedComponent("Polymer", 40.0)),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Microtopping® Finish Coat",
            category = "Microtopping",
            dosingMode = DosingMode.COATS,
            minDoseGramsPerM2 = 190.0,
            maxDoseGramsPerM2 = 260.0,
            doseUnitLabel = "per coat",
            rangeNote = "0.19–0.26 kg/m² per coat (typical 0.225)",
            sourceNote = "Ideal Work technical datasheet",
            datasheetUrl = "https://www.idealwork.it/wp-content/REPOSITORYFILE/TEC/IT/MICROTOPPING_TEC_ITA.pdf",
            parts = listOf(SeedComponent("Powder", 100.0), SeedComponent("Polymer", 50.0)),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Nuvolato Architop® Coat 1",
            category = "Architop",
            dosingMode = DosingMode.POUR,
            // No published range found for Coat 1 specifically — showing the datasheet
            // typical only rather than inventing a spread.
            minDoseGramsPerM2 = 2480.0,
            maxDoseGramsPerM2 = 2480.0,
            doseUnitLabel = "per pour",
            rangeNote = "~2.48 kg/m² per pour (no published range found)",
            sourceNote = "Ideal Work technical datasheet",
            datasheetUrl = "https://www.idealwork.it/wp-content/REPOSITORYFILE/TEC/IT/ARCHITOP_TEC_ITA.pdf",
            parts = listOf(SeedComponent("Powder", 25.0), SeedComponent("Polymer", 6.0)),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Nuvolato Architop® Coat 2",
            category = "Architop",
            dosingMode = DosingMode.POUR,
            minDoseGramsPerM2 = 1850.0,
            maxDoseGramsPerM2 = 2300.0,
            doseUnitLabel = "per pour + water",
            rangeNote = "1.85–2.30 kg/m² + water (typical 2.07)",
            sourceNote = "Ideal Work technical datasheet",
            datasheetUrl = "https://www.idealwork.it/wp-content/REPOSITORYFILE/TEC/IT/ARCHITOP_TEC_ITA.pdf",
            parts = listOf(SeedComponent("Powder", 25.0), SeedComponent("Polymer", 4.0), SeedComponent("Water", 2.0)),
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "IdealPU-WB Primer",
            category = "Primer",
            dosingMode = DosingMode.COATS,
            minDoseGramsPerM2 = 50.0,
            maxDoseGramsPerM2 = 50.0,
            doseUnitLabel = "per coat · 2K epoxy",
            rangeNote = "~50 g/m² · 2K epoxy — exact A:B split not published, check datasheet",
            sourceNote = "Ideal Work technical datasheet (mix ratio not publicly listed)",
            parts = listOf(SeedComponent("Mixed product (see datasheet for A:B)", 1.0)),
            ratioLabelOverride = "2K",
        ),
        SeedProduct(
            brand = "Ideal Work",
            name = "Ideal Sealer",
            category = "Sealer",
            dosingMode = DosingMode.COATS,
            minDoseGramsPerM2 = 180.0,
            maxDoseGramsPerM2 = 180.0,
            doseUnitLabel = "per coat · 1 component",
            rangeNote = "~180 g/m² · single-component",
            sourceNote = "Ideal Work technical datasheet",
            parts = listOf(SeedComponent("Sealer (1K, ready to use)", 1.0)),
            ratioLabelOverride = "1K",
        ),
        SeedProduct(
            brand = "Mapei",
            name = "Ultraplan Eco",
            category = "Self-Levelling",
            dosingMode = DosingMode.MM,
            minDoseGramsPerM2 = 1440.0,
            maxDoseGramsPerM2 = 1760.0,
            doseUnitLabel = "per mm",
            rangeNote = "1.44–1.76 kg/m² per mm (typical 1.6)",
            sourceNote = "Mapei technical datasheet",
            datasheetUrl = "https://cdnmedia.mapei.com/docs/librariesprovider2/products-documents/1_00513_ultraplan-eco_it-it_533e6ce7be3c4081b8189c2bc5820f3f.pdf",
            parts = listOf(SeedComponent("Powder", 100.0), SeedComponent("Water", 25.0)),
        ),
        SeedProduct(
            brand = "Mapei",
            name = "Primer G",
            category = "Primer",
            dosingMode = DosingMode.COATS,
            minDoseGramsPerM2 = 100.0,
            maxDoseGramsPerM2 = 150.0,
            doseUnitLabel = "per coat",
            rangeNote = "0.10–0.15 kg/m² · diluted 1:1–1:3 with water",
            sourceNote = "Mapei technical datasheet — dilution shown (1:2) is an illustrative midpoint of the published 1:1–1:3 range",
            datasheetUrl = "https://www.mapei.com/it/it/prodotti-e-soluzioni/prodotti/dettaglio/primer-g",
            parts = listOf(SeedComponent("Primer G (concentrate)", 1.0), SeedComponent("Water", 2.0)),
        ),
        SeedProduct(
            brand = "Sika",
            name = "Sikafloor®-263 SL",
            category = "Self-Levelling",
            dosingMode = DosingMode.MM,
            minDoseGramsPerM2 = 900.0,
            maxDoseGramsPerM2 = 1200.0,
            doseUnitLabel = "per mm",
            rangeNote = "0.9–1.2 kg/m² per mm (typical 1.05)",
            sourceNote = "Sika technical datasheet",
            datasheetUrl = "https://gcc.sika.com/content/dam/dms/gcc/x/sikafloor_-263_sl.pdf",
            parts = listOf(SeedComponent("Part A · Resin", 79.0), SeedComponent("Part B · Hardener", 21.0)),
        ),
        SeedProduct(
            brand = "Ardex",
            name = "ARDEX K 301",
            category = "Self-Levelling",
            dosingMode = DosingMode.MM,
            minDoseGramsPerM2 = 1440.0,
            maxDoseGramsPerM2 = 1760.0,
            doseUnitLabel = "per mm",
            rangeNote = "1.44–1.76 kg/m² per mm (typical 1.6)",
            sourceNote = "Ardex technical datasheet",
            datasheetUrl = "https://ardex.co.uk/wp-content/uploads/2023/02/ARDEX-K-301.pdf",
            parts = listOf(SeedComponent("Powder", 100.0), SeedComponent("Water", 21.0)),
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
                    minDoseGramsPerM2 = seedProduct.minDoseGramsPerM2,
                    maxDoseGramsPerM2 = seedProduct.maxDoseGramsPerM2,
                    typicalDoseGramsPerM2 = seedProduct.typicalDoseGramsPerM2,
                    doseUnitLabel = seedProduct.doseUnitLabel,
                    rangeNote = seedProduct.rangeNote,
                    sourceNote = seedProduct.sourceNote,
                    datasheetUrl = seedProduct.datasheetUrl,
                    ratioLabel = seedProduct.ratioLabel,
                ),
                components = seedProduct.parts.mapIndexed { index, part ->
                    ProductComponentEntity(
                        productId = 0,
                        label = part.label,
                        ratioParts = part.ratio,
                        basis = part.basis,
                        density = part.density,
                        potLife = part.potLife,
                        notes = part.notes,
                        sortOrder = index,
                        // Water is the one density that needs no datasheet. Pack sizes are left
                        // unset rather than guessed — they vary by supplier and market, so the
                        // office fills in the ones they actually buy.
                        densityKgPerL = if (part.label.equals("Water", ignoreCase = true)) 1.0 else 0.0,
                    )
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
