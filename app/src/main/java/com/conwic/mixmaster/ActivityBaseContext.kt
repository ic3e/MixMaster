package com.conwic.mixmaster

import android.content.Context

/**
 * The context the framework tied to the activity, kept from before the language wrapper.
 *
 * [MainActivity.attachBaseContext] swaps the activity's base context for one built with
 * createConfigurationContext, which is what puts every popup and sheet in the chosen language.
 * That new context belongs to nothing — the framework's "outer context" for it is itself — and
 * the print service takes a job only from a context whose outer context is an activity. Every
 * context reachable from a screen leads back to the wrapped one, which is why printing was
 * refused however it was asked for, and why unwrapping to the activity did not help: the
 * activity's own getSystemService goes through that same base.
 *
 * This is the one context the framework still points at the activity.
 */
object ActivityBaseContext {

    @Volatile
    private var context: Context? = null

    fun set(base: Context) {
        context = base
    }

    /** Cleared by identity, so an activity being replaced cannot take its successor with it. */
    fun clear(base: Context) {
        if (context === base) context = null
    }

    fun current(): Context? = context
}
