package com.conwic.mixmaster.di

import android.content.Context
import com.conwic.mixmaster.data.company.Access
import com.conwic.mixmaster.data.company.CompanyStore
import com.conwic.mixmaster.data.db.AppDatabase
import com.conwic.mixmaster.data.prefs.UserPrefs
import com.conwic.mixmaster.data.repository.DeliveryRepository
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.data.repository.StockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Minimal hand-rolled dependency container (no Hilt/Dagger) so the whole app builds with only
 * AndroidX + Room + DataStore on the classpath. One instance lives on [com.conwic.mixmaster.MixMasterApp].
 */
class AppContainer(context: Context) {

    /** The application context — safe to hold, and what the file-backed stores need. */
    val appContext: Context = context.applicationContext

    val database: AppDatabase = AppDatabase.getInstance(context)

    /**
     * For work that has to finish even though whatever asked for it is going away — a mix being
     * written against its project while the mixing screen is being closed over it. A scope
     * belonging to a composition would be cancelled halfway through that.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val userPrefs: UserPrefs = UserPrefs(context)

    /** What this phone may change: set by the employer in a company, by the role switch otherwise. */
    val access: Flow<Access> = combine(userPrefs.role, CompanyStore.link(appContext)) { role, link ->
        Access.of(role, link)
    }

    val productRepository: ProductRepository by lazy { ProductRepository(database.productDao()) }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(
            projectDao = database.projectDao(),
            floorDao = database.floorDao(),
            roomAreaDao = database.roomAreaDao(),
            roomLayerDao = database.roomLayerDao(),
            taskDao = database.taskDao(),
            noteDao = database.noteDao(),
            photoDao = database.photoDao(),
            materialUseDao = database.materialUseDao(),
        )
    }

    val stockRepository: StockRepository by lazy { StockRepository(database.stockDao()) }

    val deliveryRepository: DeliveryRepository by lazy {
        DeliveryRepository(database.deliveryDao(), stockRepository, appContext)
    }

    val solutionRepository: SolutionRepository by lazy { SolutionRepository(database.solutionDao(), database.usageLogDao()) }

}
