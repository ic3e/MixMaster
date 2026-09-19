package com.conwic.mixmaster

import android.app.Application
import com.conwic.mixmaster.data.crash.CrashLog
import com.conwic.mixmaster.di.AppContainer

class MixMasterApp : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        // First, so a crash while the container is being built is still recorded.
        CrashLog.install(this)
        container = AppContainer(this)
    }
}
