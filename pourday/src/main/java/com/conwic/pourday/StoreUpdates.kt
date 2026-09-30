package com.conwic.pourday

import com.conwic.pourday.game.GameUpdates

/**
 * How this copy of Pour Day hears of a newer one, which depends on where it came from: the copy
 * passed round as a file looks for itself (src/direct), and the one from Google Play leaves it to
 * Play (src/play). Each of the two builds has its own [storeUpdates].
 */
internal interface StoreUpdates : GameUpdates {
    /** The quiet look on start. */
    fun look()

    /** Back in the app, perhaps from the phone's "install unknown apps" screen. */
    fun resumed()
}
