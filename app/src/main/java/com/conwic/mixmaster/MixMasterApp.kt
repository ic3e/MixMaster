package com.conwic.mixmaster

import android.app.Application
import com.conwic.mixmaster.di.AppContainer

class MixMasterApp : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
