package com.conwic.mixmaster.data.seed

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import com.conwic.mixmaster.MainActivity
import com.conwic.mixmaster.data.db.AppDatabase
import com.conwic.mixmaster.data.db.entity.BlueprintEntity
import com.conwic.mixmaster.data.db.entity.DeliveryEntity
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.MaterialUseEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.RoomLayerEntity
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineRole
import com.conwic.mixmaster.data.db.entity.StockEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.db.entity.UsageLogEntity
import com.conwic.mixmaster.data.db.entity.UsedAmount
import com.conwic.mixmaster.data.db.entity.usedAmountsJson
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.model.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ceil

/**
 * The demo: the firm's own systems, five jobs in Finland, a fortnight of the calendar and a
 * stocked shelf — what the app looks like in use, to show a client or a new hand.
 *
 * The catalogue is real, off Ideal Work's own sheets: Architop, Lixio and Lixio Plus with the
 * gravel, the cement and the colours that go into them, Microtopping, the Epoxy Coat primer with
 * the quartz mixed in and the quartz broadcast over it, the mesh, and the sealers — PU WB-Easy
 * with its primers, Booster and matting agent, PU78 Matt and FX-Eco. Mapei's Primer SN and
 * Quarzo 0.5 are there too; their sheet isn't in the app yet, and their cards say so. The sheets
 * themselves travel with the demo, so a product's technical sheet opens with no signal.
 *
 * The jobs are made up — names, addresses, people — but every one is built out of those systems,
 * so every screen has something true to show: a coat to mix, a colour to dose, a shortage to
 * order. Loading it empties products, recipes, projects, the calendar and the warehouse first.
 * Dates are laid around the day it is loaded, so the week on the home screen is never empty.
 */
object DemoData {

    // ---- The catalogue: what is bought ----------------------------------------------------------

    /**
     * A thing bought and kept on the shelf. [key] is how the recipes and jobs below refer to it;
     * [pdf] is its technical sheet as it travels with the demo, [url] where it is on the web.
     */
    private class Item(
        val key: String,
        val brand: String,
        val name: String,
        val category: String,
        val packSize: Double,
        val type: String,
        val unit: String = "kg",
        val density: Double = 0.0,
        /** A colour: dosed off one part of a coat's mix, in kg per kg of that part. */
        val colourPerKg: Double = 0.0,
        val onSite: Boolean = false,
        val pdf: String = "",
        val url: String = "",
    )

    private const val IW = "Ideal Work"
    private const val MAPEI = "Mapei"
    private const val IW_DOCS = "https://www.idealwork.com/download/technical-documentation/"

    // the sheets in app/src/main/assets/demo/sheets
    private const val ARCHITOP = "ARCHITOP_TEC_EN.pdf"
    private const val LIXIO = "tds_LIXIO.pdf"
    private const val LIXIO_PLUS = "LIXIO-PLUS_TEC_ENG.pdf"
    private const val PACK_C = "PACK_C_TEC_ENG.pdf"
    private const val WB_EASY = "IDEALPU-WB_EASY_TEC_ENG.pdf"

    /** Colour Pack-C: 28 g per kg of the polymer, the fluid or the catalyst for the chart's full tone. */
    private const val FULL_TONE = 0.028

    private fun colour(key: String, name: String) =
        Item(key, IW, "Colour Pack-C $name", "Colour", 0.5, "tub", colourPerKg = FULL_TONE, pdf = PACK_C)

    private val items = listOf(
        Item("water", "", "Water", "Water", 0.0, "canister", unit = "L", density = 1.0, onSite = true),
        // Microtopping
        Item("mtBc", IW, "Microtopping® BC powder", "Microtopping", 25.0, "bucket", url = IW_DOCS),
        Item("mtFc", IW, "Microtopping® FC powder", "Microtopping", 25.0, "bucket", url = IW_DOCS),
        Item("mtPoly", IW, "Microtopping® Polymer", "Microtopping", 25.0, "canister", url = IW_DOCS),
        // Architop: the Colour Hardener, the catalyst and Archi-Go
        Item("hardener", IW, "Colour Hardener", "Architop", 25.0, "bucket", pdf = ARCHITOP),
        Item("catalyst", IW, "Architop® Catalyst", "Architop", 25.0, "canister", pdf = "ARCHITOP_CATALYST_TEC_ENG.pdf"),
        Item("archiGo", IW, "Archi-Go", "Architop", 10.0, "canister", pdf = ARCHITOP),
        // Lixio: the powder, the fluid, and the 2/3 mm marble
        Item("lixioBlend", IW, "Lixio® Marble Blend Carrara 2/3 mm", "Gravel", 25.0, "bag", pdf = LIXIO),
        Item("lixioPowder", IW, "Lixio® Powder", "Lixio", 18.75, "bag", pdf = LIXIO),
        Item("lixioFluid", IW, "Lixio® Fluid", "Lixio", 6.5, "canister", pdf = LIXIO),
        // Lixio Plus: Colour-Mix, the 5/8 and 8/12 mm marble, cement 42.5 N from the builders'
        // merchant, and the admixture
        Item("mixNeutro", IW, "Colour-Mix Neutro", "Lixio", 25.0, "bag", pdf = "COLOUR_MIX_TEC_ENG.pdf"),
        Item("mixAntracite", IW, "Colour-Mix Antracite", "Lixio", 25.0, "bag", pdf = "COLOUR_MIX_TEC_ENG.pdf"),
        Item("botticino58", IW, "Marble aggregate Botticino 5/8 mm (SI3)", "Gravel", 25.0, "bag", pdf = LIXIO_PLUS),
        Item("botticino812", IW, "Marble aggregate Botticino 8/12 mm (SI4)", "Gravel", 25.0, "bag", pdf = LIXIO_PLUS),
        Item("ebano58", IW, "Marble aggregate Nero Ebano 5/8 mm (SI3)", "Gravel", 25.0, "bag", pdf = LIXIO_PLUS),
        Item("ebano812", IW, "Marble aggregate Nero Ebano 8/12 mm (SI4)", "Gravel", 25.0, "bag", pdf = LIXIO_PLUS),
        Item("liquidPlus", IW, "Lixio-Liquid Plus", "Lixio", 1.0, "bottle", unit = "L", density = 1.0, pdf = "LIXIO-LIQUID_PLUS_TEC_ENG.pdf"),
        Item("cement", "Finnsementti", "Yleissementti 42,5 N", "Cement", 25.0, "bag"),
        // the key under all of it: the epoxy, the quartz in it and the quartz over it
        Item("epoxyCoat", IW, "IW-Epoxy Coat A+B", "Primer", 23.0, "bucket", pdf = LIXIO_PLUS),
        Item("quartz0105", "", "Quartz sand 0.1–0.5 mm", "Sand", 25.0, "bag"),
        Item("quartz0712", "", "Quartz sand 0.7–1.2 mm", "Sand", 25.0, "bag"),
        Item("quartz1020", "", "Quartz sand 1.0–2.0 mm", "Sand", 25.0, "bag"),
        Item("snA", MAPEI, "Primer SN Part A", "Primer", 16.0, "bucket"),
        Item("snB", MAPEI, "Primer SN Part B", "Primer", 4.0, "canister"),
        Item("quarzo05", MAPEI, "Quarzo 0.5", "Sand", 25.0, "bag"),
        // over tiles: 80–100 g/m² fibreglass mesh, a roll of 50 m²
        Item("mesh", "", "Fibermesh fibreglass mesh 90 g/m²", "Reinforcement", 4.5, "roll"),
        // the sealers and what goes in them
        Item("wbPrimer", IW, "IdealPU-WB Primer", "Sealer", 4.0, "bottle"),
        Item("wbPrimerMax", IW, "Primer WB Max", "Sealer", 5.0, "canister", unit = "L", density = 1.0, pdf = "IDEALPU-WB_PRIMER_MAX_TEC_ENG.pdf"),
        Item("wbEasyA", IW, "IdealPU-WB-Easy Part A", "Sealer", 5.0, "canister", pdf = WB_EASY),
        Item("wbEasyB", IW, "IdealPU-WB-Easy Part B", "Sealer", 1.0, "bottle", pdf = WB_EASY),
        Item("booster", IW, "Booster WB", "Sealer", 0.5, "tub", pdf = "IDEALPU-WB-BOOSTER_TEC_ENG.pdf"),
        Item("opacizzante", IW, "IdealPU-WB-Opacizzante", "Sealer", 0.5, "bottle", pdf = "IDEALPU-WB-OPACIZZANTE_TEC_ENG.pdf"),
        Item("pu78A", IW, "IdealPU78 Matt Part A", "Sealer", 5.0, "canister", pdf = "IDEALPU78-MATT_TEC_ENG.pdf"),
        Item("pu78B", IW, "IdealPU78 Matt Part B", "Sealer", 2.5, "canister", pdf = "IDEALPU78-MATT_TEC_ENG.pdf"),
        Item("fxEco", IW, "Ideal FX-Eco", "Sealer", 10.0, "bucket", unit = "L", density = 1.0, pdf = "IDEAL_FX_ECO_TEC_ENG.pdf"),
        // Colour Pack-C: the chart's names
        colour("cAsh", "Ash White"),
        colour("cSilver", "Silver Grey"),
        colour("cBeige", "Beige Grey"),
        colour("cTortora", "Tortora"),
        colour("cSmoke", "Smoke"),
        colour("cOlive", "Olive Grey"),
        colour("cBlack", "Black"),
    )

    // ---- The catalogue: what is made of it ------------------------------------------------------

    /** One part of a recipe. [percentOfRest] above 0 makes it that share of everything else. */
    private class Line(val item: String, val label: String, val parts: Double, val percentOfRest: Double = 0.0)

    /** A part dosed off another one, in kg per kg of it: a hardening agent in component A. */
    private class AddOn(val item: String, val label: String, val perKg: Double, val against: String)

    private class Recipe(
        val key: String,
        val brand: String,
        val name: String,
        val category: String,
        val mode: DosingMode,
        val minDose: Double,
        val maxDose: Double,
        val unitLabel: String,
        val rangeNote: String,
        val source: String,
        val lines: List<Line>,
        val url: String = IW_DOCS,
        val addOns: List<AddOn> = emptyList(),
        /** The first coat's key when this is a later coat of the same mix. */
        val parent: String? = null,
        val coatName: String = "",
        val mixSeconds: Int = 0,
        val potLife: Int = 0,
    )

    private const val NOT_ON_SHEET = "Not on the sheets in the app yet — check against the manufacturer's data sheet"

    private val recipes = listOf(
        Recipe(
            "mtBc", IW, "Microtopping®", "Microtopping", DosingMode.COATS, 1150.0, 1550.0, "per coat",
            "1.15–1.55 kg/m² a coat. Colour Pack-C 28 g per kg of polymer.",
            "Ideal Work Microtopping technical data sheet — not in the app yet",
            listOf(Line("mtBc", "BC powder", 100.0), Line("mtPoly", "Polymer", 35.0)),
            coatName = "Base coat", mixSeconds = 180,
        ),
        Recipe(
            "mtFc", IW, "Microtopping®", "Microtopping", DosingMode.COATS, 190.0, 260.0, "per coat",
            "0.19–0.26 kg/m² a coat. Colour Pack-C 28 g per kg of polymer.",
            "Ideal Work Microtopping technical data sheet — not in the app yet",
            listOf(Line("mtFc", "FC powder", 100.0), Line("mtPoly", "Polymer", 50.0)),
            parent = "mtBc", coatName = "Finish coat", mixSeconds = 180,
        ),
        // 2.00 kg/m² of hardener, 0.48 of catalyst and 0.04 of Archi-Go: 25 kg + 6 kg covers 12.5 m²
        Recipe(
            "arch1", IW, "Architop®", "Architop", DosingMode.COATS, 2520.0, 2520.0, "per coat",
            "2.00 kg/m² Colour Hardener + 0.48 catalyst + 0.04 Archi-Go: 25 kg + 6 kg does about 12.5 m². " +
                "Catalyst, Colour Pack-C and Archi-Go together first, 2 min, then the hardener.",
            "Ideal Work Architop system guidelines, Rev. 02 (2026)",
            listOf(Line("hardener", "Colour Hardener", 25.0), Line("catalyst", "Catalyst", 6.0), Line("archiGo", "Archi-Go", 0.5)),
            coatName = "1st coat", mixSeconds = 120,
        ),
        // 1.50 + 0.24 + 0.02, and 2 L of water to the set: about 15 m², wet on wet
        Recipe(
            "arch2", IW, "Architop®", "Architop", DosingMode.COATS, 1880.0, 2090.0, "per coat",
            "1.50 kg/m² Colour Hardener + 0.24 catalyst + 0.02 Archi-Go + water: about 15 m² a set. " +
                "On the first coat while it's still wet but no longer tacky.",
            "Ideal Work Architop system guidelines, Rev. 02 (2026)",
            listOf(
                Line("hardener", "Colour Hardener", 25.0), Line("catalyst", "Catalyst", 4.0),
                Line("water", "Water", 2.0), Line("archiGo", "Archi-Go", 0.33),
            ),
            parent = "arch1", coatName = "2nd coat", mixSeconds = 120,
        ),
        Recipe(
            "lixio", IW, "Lixio®", "Lixio", DosingMode.MM, 2000.0, 2200.0, "per mm",
            "6–10 mm, of which about 2 mm is ground off. Fluid 6.25–6.50 kg to the set (±5%). " +
                "Colour Pack-C 28 g per kg of fluid. The sheet gives no figure per mm: about 2.1 kg/m².",
            "Ideal Work Lixio specification, Rev. 1 (2022)",
            listOf(Line("lixioBlend", "Marble Blend 2/3 mm", 25.0), Line("lixioPowder", "Powder", 18.75), Line("lixioFluid", "Fluid", 6.25)),
            mixSeconds = 180,
        ),
        // after the first grinds: 1 part pigmented fluid to 2 of cement
        Recipe(
            "lixioGrout", IW, "Lixio® grout", "Lixio", DosingMode.COATS, 200.0, 400.0, "per coat",
            "Into the pores after the first two or three grinds: 1 part Lixio Fluid (pigmented) to 2 parts cement. " +
                "Coverage: $NOT_ON_SHEET.",
            "Ideal Work Lixio specification, Rev. 1 (2022)",
            listOf(Line("lixioFluid", "Fluid", 1.0), Line("cement", "Cement", 2.0)),
        ),
        Recipe(
            "plusBotticino", IW, "Lixio® Plus Neutro / Botticino", "Lixio", DosingMode.MM, 2209.0, 2209.0, "per mm",
            "20–40 mm. Per cm and m²: Colour-Mix 2.2 kg, marble 6.6 + 6.6, cement 4.4, water 2.2, Lixio-Liquid Plus 0.09 L. " +
                "Water/cement no more than 0.48.",
            "Ideal Work Lixio Plus system guidelines, Rev. 04 (2025)",
            listOf(
                Line("mixNeutro", "Colour-Mix", 25.0), Line("botticino58", "Marble 5/8 mm", 75.0), Line("botticino812", "Marble 8/12 mm", 75.0),
                Line("cement", "Cement 42.5 N", 50.0), Line("water", "Water", 25.0), Line("liquidPlus", "Lixio-Liquid Plus", 1.0),
            ),
            mixSeconds = 330,
        ),
        Recipe(
            "plusEbano", IW, "Lixio® Plus Antracite / Nero Ebano", "Lixio", DosingMode.MM, 2209.0, 2209.0, "per mm",
            "20–40 mm. Per cm and m²: Colour-Mix 2.2 kg, marble 6.6 + 6.6, cement 4.4, water 2.2, Lixio-Liquid Plus 0.09 L. " +
                "Water/cement no more than 0.48.",
            "Ideal Work Lixio Plus system guidelines, Rev. 04 (2025)",
            listOf(
                Line("mixAntracite", "Colour-Mix", 25.0), Line("ebano58", "Marble 5/8 mm", 75.0), Line("ebano812", "Marble 8/12 mm", 75.0),
                Line("cement", "Cement 42.5 N", 50.0), Line("water", "Water", 25.0), Line("liquidPlus", "Lixio-Liquid Plus", 1.0),
            ),
            mixSeconds = 330,
        ),
        Recipe(
            "epoxy", IW, "IW-Epoxy Coat primer", "Primer", DosingMode.COATS, 300.0, 500.0, "per coat",
            "Spread at zero with 20% quartz 0.1–0.5 mm, rolled, then broadcast to saturation while wet. Coverage: $NOT_ON_SHEET.",
            "Ideal Work Architop, Lixio and Lixio Plus system guidelines",
            listOf(Line("epoxyCoat", "Epoxy Coat A+B", 100.0), Line("quartz0105", "Quartz 0.1–0.5 mm", 20.0, percentOfRest = 20.0)),
        ),
        Recipe(
            "primerSn", MAPEI, "Primer SN", "Primer", DosingMode.COATS, 300.0, 500.0, "per coat",
            "2K epoxy primer, Quarzo 0.5 broadcast into it wet. Ratio and coverage: $NOT_ON_SHEET.",
            "Mapei — sheet not in the app yet",
            listOf(Line("snA", "Part A", 80.0), Line("snB", "Part B", 20.0)),
            url = "", potLife = 40,
        ),
        Recipe(
            "wbPrimerMax", IW, "Primer WB Max", "Sealer", DosingMode.COATS, 52.5, 52.5, "per coat",
            "50 g/m² of primer, with 5% water (up to 20% in the heat). IdealPU-WB-Easy over it after 3 h, before 4. For Architop.",
            "Ideal Work Primer WB Max technical data sheet",
            listOf(Line("wbPrimerMax", "Primer WB Max", 100.0), Line("water", "Water", 5.0, percentOfRest = 5.0)),
        ),
        Recipe(
            "wbEasy1", IW, "IdealPU-WB-Easy", "Sealer", DosingMode.COATS, 50.0, 50.0, "per coat",
            "About 50 g/m² a coat, kept thin. Two coats, 3–4 h apart; sand if the second is more than 48 h after.",
            "Ideal Work IdealPU-WB-Easy technical data sheet",
            listOf(Line("wbEasyA", "Part A", 10.0), Line("wbEasyB", "Part B", 2.0), Line("water", "Water", 10.0, percentOfRest = 10.0)),
            coatName = "1st coat", potLife = 120,
        ),
        // Booster WB in the second coat only, 10 g to 100 g of component A
        Recipe(
            "wbEasy2", IW, "IdealPU-WB-Easy", "Sealer", DosingMode.COATS, 50.0, 50.0, "per coat",
            "About 50 g/m². Booster WB stirred into component A first, 10 g to 100 g: doubles the scratch resistance.",
            "Ideal Work IdealPU-WB-Easy and Booster WB technical data sheets",
            listOf(Line("wbEasyA", "Part A", 10.0), Line("wbEasyB", "Part B", 2.0), Line("water", "Water", 10.0, percentOfRest = 10.0)),
            addOns = listOf(AddOn("booster", "Booster WB", 0.1, against = "wbEasyA")),
            parent = "wbEasy1", coatName = "2nd coat + Booster", potLife = 120,
        ),
        Recipe(
            "pu78", IW, "IdealPU78 Matt", "Sealer", DosingMode.COATS, 50.0, 75.0, "per coat",
            "100–150 g/m² in two coats, 6–8 h apart. Solvent-based: ventilate. 5–10% X-100 thinner if needed.",
            "Ideal Work IdealPU78 Matt technical data sheet",
            listOf(Line("pu78A", "Part A", 100.0), Line("pu78B", "Part B", 50.0)),
            potLife = 40,
        ),
        Recipe(
            "fxEco", IW, "Ideal FX-Eco", "Sealer", DosingMode.COATS, 66.0, 88.0, "per coat",
            "60–80 g/m² of product a coat on dense surfaces like Microtopping, 100–150 on porous ones; diluted 10–50% with water.",
            "Ideal Work Ideal FX-Eco technical data sheet",
            listOf(Line("fxEco", "FX-Eco", 100.0), Line("water", "Water", 10.0, percentOfRest = 10.0)),
        ),
    )

    private class Catalogue(val products: Map<String, Long>, val solutions: Map<String, Long>) {
        fun p(key: String) = products.getValue(key)
        fun s(key: String) = solutions.getValue(key)
    }

    /** Where the colour is dosed from in each tinted mix: the polymer, the catalyst, the fluid. */
    private val tintedOff = mapOf("mtBc" to 1, "mtFc" to 1, "arch1" to 1, "arch2" to 1, "lixio" to 2)

    private fun resolved(recipe: Recipe): List<Double> {
        val base = recipe.lines.filter { it.percentOfRest <= 0.0 }.sumOf { it.parts }
        return recipe.lines.map { if (it.percentOfRest > 0.0) base * it.percentOfRest / 100.0 else it.parts }
    }

    private fun ratioLabel(recipe: Recipe): String = resolved(recipe).joinToString(":") { parts ->
        if (parts == parts.toLong().toDouble()) parts.toLong().toString() else "%.2f".format(Locale.ROOT, parts).trimEnd('0').trimEnd('.')
    }

    /** The catalogue alone, with the manufacturers' links for sheets: a fresh install starts with this. */
    suspend fun insertCatalogue(db: AppDatabase) {
        insertCatalogueKeyed(db) { item -> item.url.ifBlank { if (item.pdf.isNotBlank()) IW_DOCS else "" } }
    }

    private suspend fun insertCatalogueKeyed(db: AppDatabase, sheetOf: (Item) -> String): Catalogue {
        val productDao = db.productDao()
        val solutionDao = db.solutionDao()
        val products = items.associate { item ->
            item.key to productDao.insertProduct(
                ProductEntity(
                    brand = item.brand,
                    name = item.name,
                    category = item.category,
                    dosingMode = DosingMode.COATS,
                    minDoseGramsPerM2 = 0.0,
                    maxDoseGramsPerM2 = 0.0,
                    typicalDoseGramsPerM2 = 0.0,
                    doseUnitLabel = "",
                    rangeNote = "",
                    isAddOn = item.colourPerKg > 0.0,
                    addOnAmountPerKg = item.colourPerKg,
                    addOnUnit = "kg",
                    packageSize = item.packSize,
                    packageUnit = item.unit,
                    packageType = item.type,
                    densityKgPerL = item.density,
                    technicalSheetUrl = sheetOf(item),
                    suppliedOnSite = item.onSite,
                ),
            )
        }
        val solutions = mutableMapOf<String, Long>()
        recipes.forEach { recipe ->
            val parts = resolved(recipe)
            val id = solutionDao.insert(
                SolutionEntity(
                    brand = recipe.brand,
                    name = recipe.name,
                    category = recipe.category,
                    dosingMode = recipe.mode,
                    minDoseGramsPerM2 = recipe.minDose,
                    maxDoseGramsPerM2 = recipe.maxDose,
                    typicalDoseGramsPerM2 = (recipe.minDose + recipe.maxDose) / 2.0,
                    doseUnitLabel = recipe.unitLabel,
                    rangeNote = recipe.rangeNote,
                    sourceNote = recipe.source,
                    datasheetUrl = recipe.url,
                    ratioLabel = ratioLabel(recipe),
                    parentId = recipe.parent?.let { solutions.getValue(it) } ?: 0L,
                    coatName = recipe.coatName,
                    mixSeconds = recipe.mixSeconds,
                    potLifeMinutes = recipe.potLife,
                ),
            )
            solutions[recipe.key] = id
            val lineIds = mutableMapOf<String, Long>()
            recipe.lines.forEachIndexed { index, line ->
                lineIds[line.item] = solutionDao.insertLine(
                    SolutionLineEntity(
                        solutionId = id,
                        productId = products.getValue(line.item),
                        label = line.label,
                        role = SolutionLineRole.BASE,
                        ratioParts = parts[index],
                        percentOfRest = line.percentOfRest,
                        sortOrder = index,
                    ),
                )
            }
            recipe.addOns.forEachIndexed { index, addOn ->
                solutionDao.insertLine(
                    SolutionLineEntity(
                        solutionId = id,
                        productId = products.getValue(addOn.item),
                        label = addOn.label,
                        role = SolutionLineRole.ADD_ON,
                        amountPerKg = addOn.perKg,
                        amountUnit = "kg",
                        againstLineId = lineIds[addOn.against] ?: 0L,
                        sortOrder = recipe.lines.size + index,
                    ),
                )
            }
        }
        return Catalogue(products, solutions)
    }

    // ---- The jobs -------------------------------------------------------------------------------

    /**
     * One coat on a room: a recipe or a product laid as it comes, how much of it, how many, and
     * the colour it is tinted with.
     */
    private class Coat(
        val recipe: String? = null,
        val item: String? = null,
        val dose: Double = 0.0,
        val quantity: Double = 1.0,
        val colour: String? = null,
        val colourPerKg: Double = FULL_TONE,
    )

    private class Room(val name: String, val area: Double, val coats: List<Coat>)
    private class Floor(val name: String, val rooms: List<Room>)
    private class Task(val title: String, val inDays: Long, val priority: TaskPriority = TaskPriority.MEDIUM, val done: Boolean = false)
    private class Note(val author: String, val role: Role, val daysAgo: Long, val text: String)
    private class Photo(val asset: String, val room: String?, val daysAgo: Long, val caption: String)

    private class Job(
        val key: String,
        val name: String,
        val client: String,
        val address: String,
        val status: ProjectStatus,
        val startsIn: Long,
        val finishesIn: Long,
        val scope: String,
        val floors: List<Floor>,
        val plans: List<Pair<String, String>>,
        val photos: List<Photo>,
        val notes: List<Note>,
        val tasks: List<Task>,
        val issued: Boolean = false,
    )

    // The build-ups, coat by coat, as the sheets lay them out.

    /** Epoxy Coat with its quartz, and the coarser quartz broadcast into it to saturation. */
    private fun key(broadcast: String?, grams: Double = 2500.0) = listOfNotNull(
        Coat(recipe = "epoxy"),
        broadcast?.let { Coat(item = it, dose = grams) },
    )

    /** The sealer a floor gets: PU WB-Easy over its primer, PU78 Matt, or FX-Eco. */
    private fun sealing(kind: String, architop: Boolean = false) = when (kind) {
        "pu78" -> listOf(Coat(recipe = "pu78", quantity = 2.0))
        "fx" -> listOf(Coat(recipe = "fxEco", quantity = 2.0))
        else -> listOf(
            if (architop) Coat(recipe = "wbPrimerMax") else Coat(item = "wbPrimer", dose = 50.0),
            Coat(recipe = "wbEasy1"),
            Coat(recipe = "wbEasy2"),
        )
    }

    private fun microtopping(colour: String, sealer: String = "wb", overTiles: Boolean = false, wall: Boolean = false) =
        key(if (wall) null else "quartz0712") +
            listOfNotNull(if (overTiles) Coat(item = "mesh", dose = 100.0) else null) +
            listOf(
                Coat(recipe = "mtBc", quantity = 2.0, colour = colour),
                Coat(recipe = "mtFc", quantity = 2.0, colour = colour),
            ) + sealing(sealer)

    // Colour Pack-C is 28 g per kg of catalyst in the first coat and per kg of catalyst and water
    // in the second — 6 kg of liquid either way, so the second coat's 4 kg of catalyst takes 42.
    private fun architop(colour: String) = key("quartz0712") + listOf(
        Coat(recipe = "arch1", colour = colour),
        Coat(recipe = "arch2", colour = colour, colourPerKg = 0.042),
    ) + sealing("wb", architop = true)

    private fun lixio(colour: String, mm: Double, mapei: Boolean = false) =
        (if (mapei) listOf(Coat(recipe = "primerSn"), Coat(item = "quarzo05", dose = 1500.0)) else key("quartz0712")) +
            listOf(
                Coat(recipe = "lixio", quantity = mm, colour = colour),
                // the same fluid, sprayed over the fresh pour so it doesn't dry out
                Coat(item = "lixioFluid", dose = 100.0),
                Coat(recipe = "lixioGrout"),
            ) + sealing("wb")

    // 1.0–2.0 mm quartz broadcast at about 4 kg/m², 1.5 of it swept up and used again
    private fun lixioPlus(recipe: String, mm: Double) =
        key("quartz1020", grams = 2500.0) + listOf(Coat(recipe = recipe, quantity = mm)) + sealing("wb")

    private val jobs = listOf(
        Job(
            key = "punavuori",
            name = "Punavuori loft",
            client = "Laura and Antti Virtanen",
            address = "Tehtaankatu 12 B 7, 00140 Helsinki",
            status = ProjectStatus.ACTIVE,
            startsIn = -8, finishesIn = 10,
            scope = "Microtopping through the flat over the old screed, and in the bathroom over the tiles, walls and floor: " +
                "Epoxy Coat with quartz as the key, the mesh over the tiles, two base and two finish coats. " +
                "Ash White in the living areas, sealed with PU WB-Easy; Smoke in the bathroom, sealed with PU78 Matt.",
            floors = listOf(
                Floor("Apartment", listOf(
                    Room("Living room & kitchen", 42.5, microtopping("cAsh")),
                    Room("Hall", 8.2, microtopping("cAsh")),
                    Room("Bathroom floor", 5.6, microtopping("cSmoke", sealer = "pu78", overTiles = true)),
                    Room("Bathroom walls", 18.4, microtopping("cSmoke", sealer = "pu78", overTiles = true, wall = true)),
                )),
            ),
            plans = listOf("punavuori_plan1.png" to "Punavuori – apartment plan.png"),
            photos = listOf(
                Photo("punavuori_1.jpg", "Living room & kitchen", 6, "Epoxy Coat down, quartz 0.7–1.2 broadcast into it. Sweep and hoover before the base coat."),
                Photo("punavuori_2.jpg", "Living room & kitchen", 3, "First base coat. Second one tomorrow."),
                Photo("punavuori_3.jpg", "Hall", 1, "Finish in Ash White, first coat of WB-Easy on it."),
            ),
            notes = listOf(
                Note("Tanel", Role.EMPLOYER, 7, "Client wants the same Ash White as the sample board, not the brochure. Board is in the van."),
                Note("Mart", Role.WORKER, 3, "Radiator pipes in the living room: masked, do the collars by hand. Lift works 7–16 only."),
                Note("Tanel", Role.EMPLOYER, 1, "Bathroom: PU78 Matt is solvent-based, fan in the window and nobody in the flat that day."),
            ),
            tasks = listOf(
                Task("Second base coat, living room & hall", 0, TaskPriority.HIGH),
                Task("Sand the base coat, 60 grit", 1),
                Task("First finish coat, Ash White", 2, TaskPriority.HIGH),
                Task("Bathroom: PU78 Matt ×2, 6–8 h apart", 6),
                Task("Handover with the Virtanens", 10, TaskPriority.HIGH),
                Task("Grind and prime the living room", -6, done = true),
                Task("Mesh over the bathroom tiles", -3, done = true),
            ),
        ),
        Job(
            key = "sointu",
            name = "Kahvila Sointu",
            client = "Sointu Coffee Oy",
            address = "Hämeenkatu 21, 33200 Tampere",
            status = ProjectStatus.ACTIVE,
            startsIn = -1, finishesIn = 17,
            scope = "Lixio micro-terrazzo in the café and behind the counter, 8 mm, Carrara 2/3 mm in Ash White, ground to 400 " +
                "and sealed with PU WB-Easy, on Mapei Primer SN with Quarzo 0.5 broadcast. Microtopping in the toilets, sealed " +
                "with FX-Eco. Café closed Mon–Wed of week two.",
            floors = listOf(
                Floor("Ground floor", listOf(
                    Room("Café", 64.0, lixio("cAsh", 8.0, mapei = true)),
                    Room("Counter", 12.5, lixio("cAsh", 8.0, mapei = true)),
                    Room("Toilets", 6.8, microtopping("cSilver", sealer = "fx")),
                )),
            ),
            plans = listOf("sointu_plan1.png" to "Sointu – ground floor plan.png"),
            photos = listOf(
                Photo("sointu_1.jpg", "Café", 0, "Primer SN with Quarzo 0.5, broadcast to refusal. Sweep off the loose tomorrow."),
                Photo("sointu_2.jpg", "Counter", 0, "Test panel: Lixio after the first grind, before the grout."),
                Photo("sointu_3.jpg", null, 0, "The look they signed off: Lixio ground to 400, Carrara in Ash White."),
            ),
            notes = listOf(
                Note("Tanel", Role.EMPLOYER, 2, "Owner is Emma. Keys from the bakery next door before 7."),
                Note("Janne", Role.WORKER, 0, "Moisture 2.8% in the café, under the 4% the sheet allows. Corner by the drain was 4.1 — fan on it overnight."),
            ),
            tasks = listOf(
                Task("Grind the café slab, 40 grit", -1, done = true),
                Task("Primer SN and Quarzo 0.5 broadcast", 0, TaskPriority.HIGH),
                Task("Sweep and hoover the quartz", 1),
                Task("Pour Lixio 8 mm, café and counter", 3, TaskPriority.HIGH),
                Task("Grind, grout, grind to 400", 7),
                Task("Microtopping in the toilets", 8),
                Task("Seal: WB Primer, WB-Easy ×2", 13),
            ),
        ),
        Job(
            key = "tapiola",
            name = "Nordic Kitchen showroom",
            client = "Keittiömaailma Oy",
            address = "Tapiontori 3, 02100 Espoo",
            status = ProjectStatus.PLANNING,
            startsIn = 20, finishesIn = 38,
            scope = "Architop over the showroom and the office, Beige Grey, on the existing screed after grinding: Epoxy Coat " +
                "and quartz, two coats wet on wet, power-trowelled to the cloudy finish, Primer WB Max and WB-Easy over it. " +
                "The shop stays open: the showroom is done in two halves, split with the blue tape.",
            floors = listOf(
                Floor("Showroom", listOf(
                    Room("Showroom – front half", 58.5, architop("cBeige")),
                    Room("Showroom – back half", 58.5, architop("cBeige")),
                    Room("Office", 22.0, architop("cBeige")),
                )),
            ),
            plans = listOf("tapiola_plan1.png" to "Tapiola – showroom plan.png"),
            photos = listOf(
                Photo("tapiola_1.jpg", "Showroom – front half", 5, "The old screed: patched, hollow by the entrance. Hammer test before the price goes out."),
                Photo("tapiola_2.jpg", null, 4, "Sample boards left with the client. They like Beige Grey."),
                Photo("tapiola_3.jpg", "Office", 2, "Test area in the office corner, both coats and seven blade passes."),
            ),
            notes = listOf(
                Note("Tanel", Role.EMPLOYER, 4, "Quote sent for 139 m². They want it before the Christmas sale. Underfloor heating off 5 days before."),
            ),
            tasks = listOf(
                Task("Follow up the quote with Keittiömaailma", 2, TaskPriority.HIGH),
                Task("Order Colour Hardener, catalyst and Archi-Go", 12),
                Task("Book the power trowels, 60 and 90 cm", 16),
            ),
        ),
        Job(
            key = "oulu",
            name = "Oulu library lobby",
            client = "City of Oulu, Premises Services",
            address = "Kaarlenväylä 3, 90100 Oulu",
            status = ProjectStatus.COMPLETED,
            startsIn = -44, finishesIn = -19,
            scope = "Lixio Plus terrazzo in the lobby, 25 mm, Colour-Mix Neutro with Botticino 5/8 and 8/12. Lixio in the corridor " +
                "and on the stair landing, 8 mm, Carrara in Beige Grey. Epoxy Coat and quartz under all of it; ground to 400 and " +
                "sealed with PU WB-Easy.",
            floors = listOf(
                Floor("Entrance level", listOf(
                    Room("Lobby", 96.0, lixioPlus("plusBotticino", 25.0)),
                    Room("Corridor", 34.0, lixio("cBeige", 8.0)),
                    Room("Stair landing", 9.0, lixio("cBeige", 8.0)),
                )),
            ),
            plans = listOf("oulu_plan1.png" to "Oulu – entrance level plan.png"),
            photos = listOf(
                Photo("oulu_1.jpg", "Lobby", 20, "Lobby after the final polish. Handed over."),
                Photo("oulu_2.jpg", "Corridor", 21, "Corridor in Lixio, before the sealer."),
                Photo("oulu_3.jpg", "Lobby", 22, "Close up: Botticino 5/8 and 8/12 after the grind."),
            ),
            notes = listOf(
                Note("Tanel", Role.EMPLOYER, 30, "Lobby poured in two days, joint under the stair beam."),
                Note("Mikko", Role.WORKER, 19, "Snag list closed. City's inspector happy with the corridor joint."),
            ),
            tasks = listOf(
                Task("Send the maintenance guide to the city", -12, done = true),
                Task("Invoice the retention", 14, TaskPriority.LOW),
            ),
            issued = true,
        ),
        Job(
            key = "saimaa",
            name = "Villa Saimaa",
            client = "Mikko and Heidi Laine",
            address = "Rantatie 41, 57230 Savonlinna",
            status = ProjectStatus.ACTIVE,
            startsIn = 7, finishesIn = 31,
            scope = "The lot: Architop in the living room and kitchen (Tortora), Lixio Plus in the entrance and out on the terrace " +
                "(Colour-Mix Antracite with Nero Ebano), Microtopping in the upstairs bathroom over the tiles, floor and walls. " +
                "The terrace goes first, before the frosts.",
            floors = listOf(
                Floor("Ground floor", listOf(
                    Room("Living room", 38.0, architop("cTortora")),
                    Room("Kitchen", 16.0, architop("cTortora")),
                    Room("Entrance", 7.5, lixioPlus("plusEbano", 25.0)),
                    Room("Terrace", 22.0, lixioPlus("plusEbano", 30.0)),
                )),
                Floor("Upstairs", listOf(
                    Room("Bathroom floor", 7.2, microtopping("cTortora", overTiles = true)),
                    Room("Bathroom walls", 21.0, microtopping("cTortora", overTiles = true, wall = true)),
                )),
            ),
            plans = listOf(
                "saimaa_plan1.png" to "Villa Saimaa – ground floor.png",
                "saimaa_plan2.png" to "Villa Saimaa – upstairs.png",
            ),
            photos = listOf(
                Photo("saimaa_1.jpg", "Terrace", 9, "Terrace slab jet-washed. Falls are fine, 1.5% to the lake."),
                Photo("saimaa_2.jpg", "Entrance", 9, "Lixio Plus sample: Antracite with Nero Ebano 5/8 and 8/12."),
                Photo("saimaa_3.jpg", "Bathroom walls", 9, "The bathroom they want: Tortora on the walls, the same on the floor."),
            ),
            notes = listOf(
                Note("Tanel", Role.EMPLOYER, 9, "Three hours from Helsinki: two nights at the guest house in Savonlinna booked for the terrace pour."),
                Note("Tanel", Role.EMPLOYER, 2, "Lixio Plus not below 10 °C. Check the forecast on the Friday before the terrace."),
            ),
            tasks = listOf(
                Task("Order the Nero Ebano aggregates and Colour-Mix", 1, TaskPriority.HIGH),
                Task("Check the Savonlinna forecast for the terrace", 4),
                Task("Terrace: Epoxy Coat and quartz, then Lixio Plus 30 mm", 8, TaskPriority.HIGH),
                Task("Architop, living room & kitchen", 15),
                Task("Bathroom microtopping upstairs", 22),
            ),
        ),
    )

    private class StockLine(val item: String, val packs: Int, val open: Double = 0.0)
    private class Order(val item: String, val packs: Int, val inDays: Long, val orderedDaysAgo: Long, val note: String, val by: String)

    // Enough on the shelf for every booked job but one: Saimaa's Lixio Plus — the Nero Ebano in
    // both sizes and the Colour-Mix Antracite — which is on the list to order tomorrow. Three
    // lines on the home screen's warehouse card, not twenty.
    private val shelf = listOf(
        StockLine("mtBc", 11), StockLine("mtFc", 3, 8.0), StockLine("mtPoly", 5, 12.5),
        StockLine("hardener", 30), StockLine("catalyst", 7, 11.0), StockLine("archiGo", 2),
        StockLine("lixioBlend", 28), StockLine("lixioPowder", 28), StockLine("lixioFluid", 28, 2.5),
        StockLine("mixNeutro", 2), StockLine("mixAntracite", 3), StockLine("botticino58", 4), StockLine("botticino812", 5),
        StockLine("ebano58", 2), StockLine("ebano812", 2), StockLine("liquidPlus", 9), StockLine("cement", 18),
        StockLine("epoxyCoat", 6), StockLine("quartz0105", 2), StockLine("quartz0712", 28), StockLine("quartz1020", 4),
        StockLine("snA", 2), StockLine("snB", 2), StockLine("quarzo05", 6), StockLine("mesh", 2),
        StockLine("wbPrimer", 3), StockLine("wbPrimerMax", 3), StockLine("wbEasyA", 7), StockLine("wbEasyB", 7),
        StockLine("booster", 4), StockLine("opacizzante", 1), StockLine("pu78A", 1), StockLine("pu78B", 1), StockLine("fxEco", 1),
        StockLine("cAsh", 13), StockLine("cSilver", 2), StockLine("cBeige", 8), StockLine("cTortora", 5),
        StockLine("cSmoke", 2), StockLine("cOlive", 1), StockLine("cBlack", 1),
    )

    // Topping up what the next jobs will run down, not covering a shortage.
    private val orders = listOf(
        Order("lixioFluid", 6, 2, 3, "Ideal Work Finland, order IW-26-0412", "Tanel"),
        Order("quartz0712", 20, 4, 2, "Quartz sand, order 5518", "Tanel"),
        Order("cTortora", 4, 5, 1, "Ideal Work Finland, order IW-26-0421", "Tanel"),
    )

    // ---- Loading it -----------------------------------------------------------------------------

    /** Every shared table the demo replaces, children before parents. */
    private val Cleared = listOf(
        "photos", "blueprints", "material_uses", "notes", "tasks", "room_layers", "room_areas", "floors", "projects",
        "deliveries", "stock", "usage_logs", "solution_lines", "solutions", "products",
    )

    private const val DemoDir = "demo"

    /**
     * Empties the products, recipes, projects, calendar and warehouse, loads the demo, and starts
     * the app again on it. On a phone in a company every row that goes and every row that comes
     * is queued for the server like any other change, so the whole company gets the demo.
     */
    suspend fun load(context: Context) {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(app)
            // The files behind the photos, the plans and the sheets go with their rows.
            listOf("photos", DemoDir, "sheets").forEach { File(app.filesDir, it).deleteRecursively() }
            val sheets = mutableMapOf<String, String>()
            db.withTransaction {
                val sql = db.openHelper.writableDatabase
                Cleared.forEach { table -> sql.execSQL("DELETE FROM `$table`") }
                val catalogue = insertCatalogueKeyed(db) { item ->
                    if (item.pdf.isBlank()) item.url else sheets.getOrPut(item.pdf) { keep(app, "sheets/${item.pdf}", "sheets") }
                }
                insertJobs(app, db, catalogue)
                insertWarehouse(db, catalogue)
            }
            // A count or a mix half done belongs to the catalogue that has just gone.
            listOf("mixmaster_stock_count", "mixmaster_mix_run").forEach { file ->
                app.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
        restart(app)
    }

    /** Copies one of the demo's files out of the app into a file of its own; its file:// address. */
    private fun keep(app: Context, asset: String, folder: String): String {
        val dir = File(app.filesDir, folder).apply { mkdirs() }
        val file = File(dir, "demo-" + asset.substringAfterLast('/'))
        app.assets.open("demo/$asset").use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return Uri.fromFile(file).toString()
    }

    private suspend fun insertJobs(app: Context, db: AppDatabase, catalogue: Catalogue) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun daysAgo(days: Long, hour: Int) = today.minusDays(days).atTime(hour, 15).atZone(zone).toInstant()
        jobs.forEach { job ->
            val projectId = db.projectDao().insert(
                ProjectEntity(
                    name = job.name,
                    clientName = job.client,
                    address = job.address,
                    status = job.status,
                    startDate = today.plusDays(job.startsIn),
                    targetFinishDate = today.plusDays(job.finishesIn),
                    scopeNotes = job.scope,
                    materialsIssuedAt = if (job.issued) daysAgo(-job.startsIn, 7).toEpochMilli() else null,
                ),
            )
            val roomIds = mutableMapOf<String, Long>()
            job.floors.forEachIndexed { floorIndex, floor ->
                val floorId = db.floorDao().insert(FloorEntity(projectId = projectId, name = floor.name, sortOrder = floorIndex))
                floor.rooms.forEachIndexed { roomIndex, room ->
                    val roomId = db.roomAreaDao().insert(
                        RoomAreaEntity(floorId = floorId, projectId = projectId, name = room.name, areaM2 = room.area, sortOrder = roomIndex),
                    )
                    roomIds[room.name] = roomId
                    room.coats.forEachIndexed { coatIndex, coat ->
                        val colour = coat.colour?.let { catalogue.p(it) } ?: 0L
                        db.roomLayerDao().insert(
                            RoomLayerEntity(
                                roomId = roomId,
                                solutionId = coat.recipe?.let { catalogue.s(it) } ?: 0L,
                                productId = coat.item?.let { catalogue.p(it) } ?: 0L,
                                doseGramsPerM2 = coat.dose,
                                quantity = coat.quantity,
                                colourProductId = colour,
                                colourAmountPerKg = if (colour > 0L) coat.colourPerKg else 0.0,
                                colourUnit = "kg",
                                colourAgainstIndex = coat.recipe?.let { tintedOff[it] } ?: 0,
                                sortOrder = coatIndex,
                            ),
                        )
                    }
                }
            }
            job.plans.forEach { (asset, name) ->
                db.blueprintDao().insert(
                    BlueprintEntity(projectId = projectId, uri = keep(app, asset, DemoDir), name = name, mimeType = "image/png", addedAt = daysAgo(10, 9)),
                )
            }
            job.photos.forEach { photo ->
                db.photoDao().insert(
                    PhotoEntity(
                        projectId = projectId,
                        roomId = photo.room?.let { roomIds[it] },
                        uri = keep(app, photo.asset, "photos"),
                        caption = photo.caption,
                        takenAt = daysAgo(photo.daysAgo, 14),
                    ),
                )
            }
            job.notes.forEach { note ->
                db.noteDao().insert(
                    NoteEntity(projectId = projectId, authorName = note.author, authorRole = note.role, text = note.text, createdAt = daysAgo(note.daysAgo, 17)),
                )
            }
            job.tasks.forEach { task ->
                db.taskDao().insert(
                    TaskEntity(
                        projectId = projectId,
                        title = task.title,
                        dueDate = today.plusDays(task.inDays),
                        priority = task.priority,
                        isDone = task.done,
                    ),
                )
            }
            if (job.key == "oulu") receipts(db, catalogue, projectId, roomIds, today)
        }
        // the jobs that aren't a project's
        listOf(
            Task("Collect the Colour Pack-C order from the warehouse", 1),
            Task("Service the grinder: new diamonds, 40 and 80", 3, TaskPriority.LOW),
            Task("Stock count before the Saimaa job", 5),
        ).forEach { task ->
            db.taskDao().insert(TaskEntity(projectId = null, title = task.title, dueDate = today.plusDays(task.inDays), priority = task.priority))
        }
        // the site's own coverage readings, next to the datasheets'
        listOf("mtBc" to listOf(1320.0, 1410.0, 1380.0), "mtFc" to listOf(230.0, 215.0), "arch1" to listOf(2560.0, 2490.0))
            .forEach { (recipe, readings) ->
                readings.forEachIndexed { k, dose ->
                    db.usageLogDao().insert(UsageLogEntity(solutionId = catalogue.s(recipe), doseGramsPerM2 = dose, loggedAt = daysAgo(30L - k * 9, 16)))
                }
            }
    }

    /**
     * What went down at Oulu, as the mixing screen would have written it: whole sets, as many as
     * the rooms took, off the recipes above.
     */
    private suspend fun receipts(db: AppDatabase, catalogue: Catalogue, projectId: Long, rooms: Map<String, Long>, today: LocalDate) {
        val zone = ZoneId.systemDefault()
        suspend fun mix(room: String, area: Double, mm: Double, recipeKey: String, daysAgo: Long) {
            val recipe = recipes.first { it.key == recipeKey }
            val parts = resolved(recipe)
            val set = parts.sum()
            val batches = ceil(area * mm * recipe.minDose / 1000.0 / set).toInt()
            val used = recipe.lines.mapIndexed { index, line ->
                UsedAmount(catalogue.p(line.item), line.label, parts[index] * batches * 1000.0)
            }
            db.materialUseDao().insert(
                MaterialUseEntity(
                    projectId = projectId,
                    roomId = rooms[room] ?: 0L,
                    solutionId = catalogue.s(recipeKey),
                    title = recipe.name,
                    jobLabel = "Oulu library lobby · $room",
                    batches = batches,
                    totalGrams = used.sumOf { it.grams },
                    parts = usedAmountsJson(used),
                    mixedAt = today.minusDays(daysAgo).atTime(10, 30).atZone(zone).toInstant().toEpochMilli(),
                ),
            )
        }
        mix("Lobby", 96.0, 25.0, "plusBotticino", 30)
        mix("Corridor", 34.0, 8.0, "lixio", 28)
        mix("Stair landing", 9.0, 8.0, "lixio", 28)
    }

    private suspend fun insertWarehouse(db: AppDatabase, catalogue: Catalogue) {
        val today = LocalDate.now()
        val counted = System.currentTimeMillis() - 2 * 86_400_000L
        shelf.forEach { line ->
            val id = catalogue.p(line.item)
            // a stock row carries its product's own id, as everywhere else in the app
            db.stockDao().insert(StockEntity(id = id, productId = id, fullPacks = line.packs, openAmount = line.open, updatedAt = counted))
        }
        orders.forEach { order ->
            db.deliveryDao().insert(
                DeliveryEntity(
                    productId = catalogue.p(order.item),
                    packs = order.packs,
                    expectedOn = today.plusDays(order.inDays),
                    orderedOn = today.minusDays(order.orderedDaysAgo),
                    note = order.note,
                    orderedBy = order.by,
                ),
            )
        }
    }

    /** Every screen holds the old catalogue in memory: the app starts again on the new one. */
    private fun restart(app: Context) {
        runCatching {
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        Runtime.getRuntime().exit(0)
    }
}
