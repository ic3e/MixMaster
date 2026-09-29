package com.conwic.mixmaster.data.seed

import com.conwic.mixmaster.data.db.AppDatabase

/**
 * First-run seed: the firm's own catalogue — the Ideal Work and Mapei systems it lays, and the
 * gravel, cement, quartz, mesh and colours that go into them (see [DemoData]). Runs once, from
 * [AppDatabase]'s onCreate callback.
 *
 * Nothing else is seeded. A made-up job and a made-up crew are not the firm's: a demo project
 * counted as an active job on the home screen, booked material it was never going to use, and
 * drove the "check the warehouse" warning. The demo jobs are there to load on purpose, from
 * Settings.
 */
object SeedData {

    suspend fun seed(db: AppDatabase) {
        if (db.productDao().countAll() > 0) return
        DemoData.insertCatalogue(db)
    }
}
