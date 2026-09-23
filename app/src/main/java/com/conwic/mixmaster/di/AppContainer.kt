package com.conwic.mixmaster.di

import android.content.Context
import com.conwic.mixmaster.data.db.AppDatabase
import com.conwic.mixmaster.data.prefs.UserPrefs
import com.conwic.mixmaster.data.repository.DeliveryRepository
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.data.repository.StockRepository
import com.conwic.mixmaster.data.repository.TeamRepository

/**
 * Minimal hand-rolled dependency container (no Hilt/Dagger) so the whole app builds with only
 * AndroidX + Room + DataStore on the classpath. One instance lives on [com.conwic.mixmaster.MixMasterApp].
 */
class AppContainer(context: Context) {

    val database: AppDatabase = AppDatabase.getInstance(context)
    val userPrefs: UserPrefs = UserPrefs(context)

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
        )
    }

    val stockRepository: StockRepository by lazy { StockRepository(database.stockDao()) }

    val deliveryRepository: DeliveryRepository by lazy {
        DeliveryRepository(database.deliveryDao(), stockRepository)
    }

    val solutionRepository: SolutionRepository by lazy { SolutionRepository(database.solutionDao(), database.usageLogDao()) }

    val teamRepository: TeamRepository by lazy { TeamRepository(database.teamMemberDao()) }
}
