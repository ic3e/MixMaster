package com.conwic.pourday

import android.app.Activity

/** Passed round as a file, nobody else tells it of a new version: it looks on GitHub itself. */
internal fun storeUpdates(activity: Activity): StoreUpdates? = PourDayUpdates(activity)
