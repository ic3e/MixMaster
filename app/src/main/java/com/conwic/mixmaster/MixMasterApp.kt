package com.conwic.mixmaster

import android.app.Application
import com.conwic.mixmaster.data.crash.CrashLog
import com.conwic.mixmaster.di.AppContainer
import com.conwic.mixmaster.data.sync.SyncEngine

class MixMasterApp : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        // First, so a crash while the container is being built is still recorded.
        CrashLog.install(this)
        container = AppContainer(this)
        // A phone that shares a company picks up where it left off: sends what it changed while
        // the app was closed, and hears what everyone else did.
        SyncEngine.start(this)
    }
}
