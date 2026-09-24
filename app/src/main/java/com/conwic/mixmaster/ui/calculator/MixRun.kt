package com.conwic.mixmaster.ui.calculator

/**
 * Whether a batch is in the mixer right now, asked from outside the mixing screen.
 *
 * It exists for the app lock. The lock asks again after a couple of minutes in the background,
 * and asking again drops everything composed behind it — which, three minutes into a batch with
 * the phone face down on a bag, is the run, the clock and the tally. The lock is about who is
 * holding the phone, and during a mix the answer is the person standing at the mixer with gloves
 * on, so it waits until the batch is out.
 *
 * Kept as the batch's own due time rather than a flag, so it cannot be left switched on: if the
 * screen goes away without ever saying the run ended, this goes quiet by itself.
 */
object MixRun {

    /** Long enough to pour and come back for the next batch; short enough to be a limit. */
    private const val GraceMillis = 10 * 60 * 1000L

    @Volatile
    private var dueAt = 0L

    /** A batch has gone in and is due at [atMillis]. */
    fun started(atMillis: Long) {
        dueAt = atMillis
    }

    /** The run is over, or the batch has been dealt with. */
    fun ended() {
        dueAt = 0L
    }

    /** A batch is in the mixer, or has just come up and is waiting to be acknowledged. */
    fun underWay(): Boolean = dueAt > 0L && System.currentTimeMillis() < dueAt + GraceMillis
}
