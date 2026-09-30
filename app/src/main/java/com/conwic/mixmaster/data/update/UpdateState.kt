package com.conwic.mixmaster.data.update

import androidx.annotation.StringRes
import java.io.File

/** What CI published, as read from dist/latest.json. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    /** Nothing asked for yet. */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(@StringRes val reasonRes: Int) : UpdateState
}
