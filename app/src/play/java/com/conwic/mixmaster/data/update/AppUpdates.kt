package com.conwic.mixmaster.data.update

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The copy from Google Play is updated by Play, and Play allows no other way: this one never looks
 * for a new version, never fetches one and never hands one to the installer. It stands in for the
 * updater the copy passed round as a file carries (src/direct), so the screens that show one build
 * alike; Settings leaves the section out ([offered]) and Home never has a new version to announce.
 */
@Suppress("UNUSED_PARAMETER")
object AppUpdates {

    val offered = false

    val state: StateFlow<UpdateState> = MutableStateFlow(UpdateState.Idle)

    fun checkOnStart(context: Context) = Unit

    fun check(context: Context) = Unit

    fun download(context: Context, info: UpdateInfo) = Unit

    fun cancel() = Unit

    fun canInstall(context: Context): Boolean = false

    fun installPermissionIntent(context: Context): Intent = Intent()

    fun install(context: Context, file: File) = Unit
}
