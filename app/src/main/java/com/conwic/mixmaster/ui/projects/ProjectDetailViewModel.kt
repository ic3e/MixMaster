package com.conwic.mixmaster.ui.projects

import com.conwic.mixmaster.domain.canonicalName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.RoomLayerEntity
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.coatLabel
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.photos.PhotoStore
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.data.repository.StockRepository
import com.conwic.mixmaster.domain.CoatMix
import com.conwic.mixmaster.domain.MixCalculator
import com.conwic.mixmaster.data.db.entity.usedAmounts
import com.conwic.mixmaster.domain.addOnNeeds
import com.conwic.mixmaster.domain.colourAddOn
import com.conwic.mixmaster.domain.MixPart
import com.conwic.mixmaster.domain.ProductStock
import com.conwic.mixmaster.domain.RecordedMix
import com.conwic.mixmaster.domain.SolutionMix
import com.conwic.mixmaster.domain.bookingsByProduct
import com.conwic.mixmaster.domain.needsByProduct
import com.conwic.mixmaster.domain.productStock
import com.conwic.mixmaster.domain.solutionMix
import com.conwic.mixmaster.ui.tasks.TaskDraft
import com.conwic.mixmaster.ui.tasks.toEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

data class ProjectDetailData(
    val project: ProjectEntity? = null,
    val floors: List<FloorEntity> = emptyList(),
    val rooms: List<RoomAreaEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val solutions: List<SolutionEntity> = emptyList(),
) {
    val totalAreaM2: Double get() = rooms.sumOf { it.areaM2 }
    val progressPercent: Int get() = if (tasks.isEmpty()) 0 else (tasks.count { it.isDone } * 100) / tasks.size
}


/** One bought item this job needs, set against what the warehouse can spare. */
data class ProjectMaterial(
    val productId: Long,
    val name: String,
    /** In the product's own pack unit. */
    val need: Double,
    val stock: ProductStock,
    /** Found on site — water from the tap. Needed, but not the shed's to supply. */
    val onSite: Boolean = false,
) {
    /** Free on the shelf once other jobs' bookings are honoured. */
    val available: Double get() = stock.free
    val shortfall: Double get() = (need - available).coerceAtLeast(0.0)
    val packsToOrder: Int?
        get() = when {
            shortfall <= 0.0 -> 0
            stock.packSize > 0.0 -> ceil(shortfall / stock.packSize).toInt()
            else -> null
        }

    /**
     * Whole packs to load for the site.
     *
     * Rounded up, because nobody carries two thirds of a bag out of the shed: 43 kg of a 25 kg
     * bag is two bags, and the rest goes back on the shelf.
     */
    val packsToTake: Int?
        get() = if (stock.packSize > 0.0 && need > 0.0) ceil(need / stock.packSize).toInt() else null

    /** Whole packs the shelf can actually spare — rounded down, for the same reason. */
    val packsAvailable: Int?
        get() = if (stock.packSize > 0.0) floor(available.coerceAtLeast(0.0) / stock.packSize).toInt() else null
}

class ProjectDetailViewModel(
    private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val productRepository: ProductRepository,
    private val solutionRepository: SolutionRepository,
    private val stockRepository: StockRepository,
    private val projectId: Long,
) : ViewModel() {

    /** Every job, archived ones too — for the clients and names already in use. */
    val everyProject: StateFlow<List<ProjectEntity>> =
        combine(projectRepository.observeAll(), projectRepository.observeArchived()) { live, archived -> live + archived }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val data: StateFlow<ProjectDetailData> = combine(
        projectRepository.observeById(projectId),
        projectRepository.observeFloors(projectId),
        projectRepository.observeRooms(projectId),
        projectRepository.observeTasks(projectId),
        projectRepository.observeNotes(projectId),
    ) { project, floors, rooms, tasks, notes ->
        ProjectDetailData(project, floors, rooms, tasks, notes, emptyList())
    }.combine(projectRepository.observePhotos(projectId)) { partial, photos ->
        partial.copy(photos = photos)
    }.combine(productRepository.observeAll()) { partial, products ->
        partial.copy(products = products)
    }.combine(solutionRepository.observeAll()) { partial, solutions ->
        partial.copy(solutions = solutions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectDetailData())

    /**
     * What has actually been mixed on this job, newest first.
     *
     * The JSON each receipt carries is read out here rather than on screen: it is the same list
     * every time the tab redraws, and it is the one thing on this screen that is a record of
     * what happened rather than a plan for what is to.
     */
    val recordedMixes: StateFlow<List<RecordedMix>> =
        projectRepository.observeMaterialUses(projectId)
            .map { uses ->
                uses.map { use ->
                    RecordedMix(
                        id = use.id,
                        title = use.title,
                        jobLabel = use.jobLabel,
                        batches = use.batches,
                        totalGrams = use.totalGrams,
                        parts = use.usedAmounts(),
                        mixedAt = use.mixedAt,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Every solution with its lines resolved, keyed by id — the recipes this job can lay. */
    private val mixes: StateFlow<Map<Long, SolutionMix>> = combine(
        solutionRepository.observeAllWithLines(),
        productRepository.observeAll(),
    ) { solutions, products ->
        val productsById = products.associateBy { it.id }
        solutions.associate { it.solution.id to solutionMix(it.solution, it.lines, productsById) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val layers: StateFlow<List<RoomLayerEntity>> = projectRepository.observeLayers(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The coats on each room, in the order they go down. */
    val roomCoats: StateFlow<Map<Long, List<CoatMix>>> = combine(
        data,
        layers,
        mixes,
    ) { current, rows, byId ->
        val productsById = current.products.associateBy { it.id }
        current.rooms.associate { room ->
            room.id to rows.filter { it.roomId == room.id }.sortedBy { it.sortOrder }.mapNotNull { layer ->
                coatMix(layer, room.areaM2, byId, productsById)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * What this job needs of each product, set against what is genuinely spare.
     *
     * Spare, not on hand: material another job has already booked is not this job's to take.
     */
    /** Every other job, so this one can be measured against what they have not claimed. */
    private val elsewhere = combine(
        projectRepository.observeAll(),
        projectRepository.observeAllRooms(),
        projectRepository.observeAllLayers(),
    ) { projects, rooms, allLayers -> Triple(projects, rooms, allLayers) }

    private val allMaterials: StateFlow<List<ProjectMaterial>> = combine(
        data,
        layers,
        mixes,
        stockRepository.observeAll(),
        elsewhere,
    ) { current, rows, byId, stock, others ->
        val productsById = current.products.associateBy { it.id }
        val needs = needsByProduct(current.rooms, rows.groupBy { it.roomId }, byId, productsById)
        val stockByProduct = stock.associateBy { it.productId }
        // Every other job's booking, so this one is measured against what is genuinely spare.
        val claimed = bookingsByProduct(
            projects = others.first.filter { it.id != projectId },
            rooms = others.second,
            layersByRoom = others.third.groupBy { it.roomId },
            mixes = byId,
            products = productsById,
        )
        needs.mapNotNull { (productId, need) ->
            val product = productsById[productId] ?: return@mapNotNull null
            ProjectMaterial(
                productId = productId,
                name = product.name,
                need = need,
                stock = productStock(product, stockByProduct[productId], claimed[productId].orEmpty()),
                onSite = product.suppliedOnSite,
            )
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** What comes out of the shed for this job — the list the van is loaded from. */
    val materials: StateFlow<List<ProjectMaterial>> = allMaterials
        .map { list -> list.filterNot { it.onSite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** What the job needs that is found on site, so it can be asked for before the day. */
    val siteMaterials: StateFlow<List<ProjectMaterial>> = allMaterials
        .map { list -> list.filter { it.onSite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun coatMix(
        layer: RoomLayerEntity,
        areaM2: Double,
        mixesById: Map<Long, SolutionMix>,
        productsById: Map<Long, ProductEntity>,
    ): CoatMix? {
        val mix = mixesById[layer.solutionId]
        if (mix != null) {
            val dose = layer.doseGramsPerM2.takeIf { it > 0.0 } ?: mix.solution.typicalDoseGramsPerM2
            val result = MixCalculator.compute(mix.parts, areaM2, layer.quantity, dose)
            return CoatMix(
                layer = layer,
                // Named with its coat, so a room reads "architop · 2nd coat" rather than the
                // same line twice.
                title = mix.solution.coatLabel,
                brand = mix.solution.brand,
                doseGramsPerM2 = dose,
                parts = mix.parts,
                result = result,
                colour = colourAddOn(layer, mix.parts, productsById)
                    ?.let { addOnNeeds(result, listOf(it)).firstOrNull() },
            )
        }
        val product = productsById[layer.productId] ?: return null
        val parts = listOf(
            MixPart(
                productId = product.id,
                label = product.name,
                ratioParts = 100.0,
                packageSize = product.packageSize,
                packageUnit = product.packageUnit,
                packageType = product.packageType,
                densityKgPerL = product.densityKgPerL,
            ),
        )
        return CoatMix(
            layer = layer,
            title = product.name,
            brand = product.brand,
            doseGramsPerM2 = layer.doseGramsPerM2,
            parts = parts,
            result = MixCalculator.compute(parts, areaM2, layer.quantity, layer.doseGramsPerM2),
        )
    }

    /** Takes a recorded mix back off: one recorded twice, or against the wrong bay. */
    fun removeRecordedMix(id: Long) {
        viewModelScope.launch { projectRepository.removeMaterialUse(id) }
    }

    /** Puts another coat on a room — a primer, a mix, a sealer. */
    fun addCoat(roomId: Long, solutionId: Long, productId: Long, doseGramsPerM2: Double, quantity: Double) {
        viewModelScope.launch {
            projectRepository.addLayer(roomId, solutionId, productId, doseGramsPerM2, quantity)
        }
    }

    /** Tints a coat, or clears the tint when [colourProductId] is 0. */
    fun setCoatColour(layer: RoomLayerEntity, colourProductId: Long, amountPerKg: Double, unit: String, againstIndex: Int) {
        viewModelScope.launch {
            projectRepository.updateLayer(
                layer.copy(
                    colourProductId = colourProductId,
                    colourAmountPerKg = amountPerKg,
                    colourUnit = unit,
                    colourAgainstIndex = againstIndex,
                ),
            )
        }
    }

    fun removeCoat(layer: RoomLayerEntity) {
        viewModelScope.launch { projectRepository.removeLayer(layer) }
    }


    /** Takes this job's material off the shelf, once, and stops it booking any more. */
    fun takeMaterialsOutOfStock() {
        val project = data.value.project ?: return
        if (project.materialsIssuedAt != null) return
        val needed = materials.value
        viewModelScope.launch {
            needed.forEach { material ->
                stockRepository.take(material.productId, material.need, material.stock.packSize)
            }
            projectRepository.setMaterialsIssued(project, System.currentTimeMillis())
        }
    }

    fun addFloor(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val sortOrder = data.value.floors.size
            projectRepository.addFloor(FloorEntity(projectId = projectId, name = name.trim(), sortOrder = sortOrder))
        }
    }

    fun addRoom(floorId: Long, name: String, areaM2: Double) {
        if (name.isBlank() || areaM2 <= 0.0) return
        viewModelScope.launch {
            val sortOrder = data.value.rooms.count { it.floorId == floorId }
            projectRepository.addRoom(
                RoomAreaEntity(floorId = floorId, projectId = projectId, name = name.trim(), areaM2 = areaM2, sortOrder = sortOrder),
            )
        }
    }

    /** Renames a floor. A floor was add-only, and a typo stood for the life of the job. */
    fun renameFloor(floor: FloorEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { projectRepository.updateFloor(floor.copy(name = name.trim())) }
    }

    /** Takes a floor off. Its rooms and their coats go with it — the dialog says so. */
    fun removeFloor(floor: FloorEntity) {
        viewModelScope.launch { projectRepository.removeFloor(floor) }
    }

    /** A bay gets renamed and re-measured once somebody has been round it with a tape. */
    fun updateRoom(room: RoomAreaEntity, name: String, areaM2: Double) {
        if (name.isBlank() || areaM2 <= 0.0) return
        viewModelScope.launch {
            projectRepository.updateRoom(room.copy(name = name.trim(), areaM2 = areaM2))
        }
    }

    fun removeRoom(room: RoomAreaEntity) {
        viewModelScope.launch { projectRepository.removeRoom(room) }
    }

    /** Changes what a coat is laid at — the rate, and how many passes of it. */
    fun updateCoat(layer: RoomLayerEntity, doseGramsPerM2: Double, quantity: Double) {
        viewModelScope.launch {
            projectRepository.updateLayer(
                layer.copy(
                    doseGramsPerM2 = doseGramsPerM2.coerceAtLeast(0.0),
                    quantity = quantity.coerceAtLeast(0.0),
                ),
            )
        }
    }

    fun removeNote(note: NoteEntity) {
        viewModelScope.launch { projectRepository.removeNote(note) }
    }

    /** Takes a photo off the job, and the copy the app made of it off the phone. */
    fun removePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            projectRepository.removePhoto(photo)
            PhotoStore.forget(appContext, photo.uri)
        }
    }

    /** Saves a task from the shared editor. New tasks are pinned to this project. */
    fun saveTask(draft: TaskDraft) {
        if (draft.title.isBlank()) return
        val entity = draft.copy(projectId = projectId).toEntity()
        viewModelScope.launch {
            if (draft.id == null) projectRepository.addTask(entity) else projectRepository.updateTask(entity)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { projectRepository.deleteTask(taskId) }
    }

    fun setTaskDone(taskId: Long, done: Boolean) {
        viewModelScope.launch { projectRepository.setTaskDone(taskId, done) }
    }

    fun addNote(text: String, authorName: String, role: Role) {
        if (text.isBlank()) return
        viewModelScope.launch {
            projectRepository.addNote(
                NoteEntity(projectId = projectId, authorName = authorName, authorRole = role, text = text.trim(), createdAt = Instant.now()),
            )
        }
    }

    fun addPhoto(uri: String, roomId: Long?, caption: String) {
        viewModelScope.launch {
            projectRepository.addPhoto(PhotoEntity(projectId = projectId, roomId = roomId, uri = uri, caption = caption, takenAt = Instant.now()))
        }
    }

    fun setBlueprintUri(uri: String) {
        viewModelScope.launch {
            data.value.project?.let { projectRepository.save(it.copy(blueprintUri = uri)) }
        }
    }

    fun updateDetails(
        name: String,
        clientName: String,
        address: String,
        scopeNotes: String,
        startDate: LocalDate?,
        targetFinishDate: LocalDate?,
        status: ProjectStatus,
    ) {
        viewModelScope.launch {
            data.value.project?.let {
                projectRepository.save(
                    it.copy(
                        name = name.trim(),
                        // The spelling already on another job, if this is the same client.
                        clientName = canonicalName(
                            clientName,
                            everyProject.value.filter { p -> p.id != it.id }.map { p -> p.clientName },
                        ),
                        address = address.trim(),
                        scopeNotes = scopeNotes.trim(),
                        startDate = startDate,
                        targetFinishDate = targetFinishDate,
                        status = status,
                    ),
                )
            }
        }
    }

    fun archive(onArchived: () -> Unit) {
        viewModelScope.launch {
            data.value.project?.let { projectRepository.archive(it) }
            onArchived()
        }
    }
}
