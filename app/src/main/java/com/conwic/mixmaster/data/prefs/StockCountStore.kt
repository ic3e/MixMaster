package com.conwic.mixmaster.data.prefs

import android.content.Context
import com.conwic.mixmaster.domain.CountInterval
import com.conwic.mixmaster.domain.DefaultCountReminderMinute
import com.conwic.mixmaster.domain.SameDayAsLastCount
import com.conwic.mixmaster.domain.countDueAt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId
import kotlin.math.abs

/** One product's shelf as a pair of figures: unopened packs, and what is left in the open one. */
data class ShelfFigure(val packs: Int, val open: Double)

/** A product whose count came out different from what the app had. */
data class CountChange(val productId: Long, val before: ShelfFigure, val after: ShelfFigure)

/** What the last finished count found, kept on the warehouse page until somebody has read it. */
data class CountSummary(
    val finishedAt: Long,
    val counted: Int,
    val changes: List<CountChange>,
    /** Products nobody got to. Their figures were left as they were. */
    val skipped: List<Long>,
)

data class StockCountState(
    val interval: CountInterval = CountInterval.Default,
    /** When the last count was finished. Zero when there has never been one. */
    val lastCountAt: Long = 0L,
    /** What the first reminder is counted from while there has never been a count. */
    val anchorAt: Long = 0L,
    /** When the count under way was started. Zero when there is none. */
    val startedAt: Long = 0L,
    /** The shelf as the app had it when the count was started, for the summary at the end. */
    val before: Map<Long, ShelfFigure> = emptyMap(),
    /** Products already gone through in the count under way. */
    val counted: Set<Long> = emptySet(),
    /** When the phone last said a count was due. */
    val notifiedAt: Long = 0L,
    val summary: CountSummary? = null,
    /** When in the day the reminder comes, as minutes after midnight. */
    val reminderMinute: Int = DefaultCountReminderMinute,
    /** The weekday a count is kept to (ISO, Monday = 1), or [SameDayAsLastCount]. */
    val reminderWeekday: Int = SameDayAsLastCount,
) {
    val inProgress: Boolean get() = startedAt > 0L

    fun dueAt(zone: ZoneId = ZoneId.systemDefault()): Long? = countDueAt(interval, lastCountAt, anchorAt, zone, reminderMinute, reminderWeekday)

    fun isDue(now: Long = System.currentTimeMillis()): Boolean = dueAt()?.let { it <= now } ?: false
}

/**
 * The stock count: how often it is wanted, when it was last done, and one under way.
 *
 * SharedPreferences rather than the database: the reminder is worked out inside a broadcast
 * receiver, which has nowhere to suspend, and none of this is stock — the figures themselves go
 * straight into the stock table as they are counted, so a count left half done loses nothing.
 */
object StockCountStore {

    private const val FILE = "mixmaster_stock_count"
    private const val K_INTERVAL = "interval"
    private const val K_LAST = "lastCountAt"
    private const val K_ANCHOR = "anchorAt"
    private const val K_STARTED = "startedAt"
    private const val K_BEFORE = "before"
    private const val K_COUNTED = "counted"
    private const val K_NOTIFIED = "notifiedAt"
    private const val K_SUM_AT = "summaryAt"
    private const val K_SUM_COUNTED = "summaryCounted"
    private const val K_SUM_CHANGES = "summaryChanges"
    private const val K_SUM_SKIPPED = "summarySkipped"
    private const val K_TIME = "reminderMinute"
    private const val K_WEEKDAY = "reminderWeekday"

    private val flow = MutableStateFlow(StockCountState())

    @Volatile
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Read from disk the first time anything asks; the screens and the alarm share it after. */
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                flow.value = load(context)
                loaded = true
            }
        }
    }

    fun state(context: Context): StateFlow<StockCountState> {
        ensureLoaded(context)
        return flow.asStateFlow()
    }

    fun read(context: Context): StockCountState {
        ensureLoaded(context)
        return flow.value
    }

    private fun load(context: Context): StockCountState = runCatching {
        val p = prefs(context)
        // The first reminder is counted from the first time anybody could have seen the setting,
        // not from 1970 — which would make a count "due" on the day this build is installed.
        val anchor = p.getLong(K_ANCHOR, 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis().also { p.edit().putLong(K_ANCHOR, it).apply() }
        val summaryAt = p.getLong(K_SUM_AT, 0L)
        StockCountState(
            interval = CountInterval.of(p.getString(K_INTERVAL, null)),
            lastCountAt = p.getLong(K_LAST, 0L),
            anchorAt = anchor,
            startedAt = p.getLong(K_STARTED, 0L),
            before = decodeFigures(p.getString(K_BEFORE, null)),
            counted = decodeIds(p.getString(K_COUNTED, null)).toSet(),
            notifiedAt = p.getLong(K_NOTIFIED, 0L),
            reminderMinute = p.getInt(K_TIME, DefaultCountReminderMinute),
            reminderWeekday = p.getInt(K_WEEKDAY, SameDayAsLastCount),
            summary = if (summaryAt > 0L) {
                CountSummary(
                    finishedAt = summaryAt,
                    counted = p.getInt(K_SUM_COUNTED, 0),
                    changes = decodeChanges(p.getString(K_SUM_CHANGES, null)),
                    skipped = decodeIds(p.getString(K_SUM_SKIPPED, null)),
                )
            } else {
                null
            },
        )
    }.getOrDefault(StockCountState(anchorAt = System.currentTimeMillis()))

    private fun write(context: Context, next: StockCountState) {
        flow.value = next
        runCatching {
            prefs(context).edit()
                .putString(K_INTERVAL, next.interval.key)
                .putLong(K_LAST, next.lastCountAt)
                .putLong(K_ANCHOR, next.anchorAt)
                .putLong(K_STARTED, next.startedAt)
                .putString(K_BEFORE, encodeFigures(next.before))
                .putString(K_COUNTED, next.counted.joinToString(","))
                .putLong(K_NOTIFIED, next.notifiedAt)
                .putInt(K_TIME, next.reminderMinute)
                .putInt(K_WEEKDAY, next.reminderWeekday)
                .putLong(K_SUM_AT, next.summary?.finishedAt ?: 0L)
                .putInt(K_SUM_COUNTED, next.summary?.counted ?: 0)
                .putString(K_SUM_CHANGES, encodeChanges(next.summary?.changes.orEmpty()))
                .putString(K_SUM_SKIPPED, next.summary?.skipped.orEmpty().joinToString(","))
                .commit()
        }
    }

    private fun update(context: Context, change: (StockCountState) -> StockCountState) {
        write(context, change(read(context)))
    }

    fun setInterval(context: Context, interval: CountInterval) = update(context) {
        // Switched on after a spell off with no count ever done: the fortnight starts now, not
        // from whenever the app was first installed.
        val anchor = if (it.interval == CountInterval.OFF && it.lastCountAt == 0L) System.currentTimeMillis() else it.anchorAt
        it.copy(interval = interval, anchorAt = anchor, notifiedAt = 0L)
    }

    fun setReminderMinute(context: Context, minuteOfDay: Int) = update(context) {
        it.copy(reminderMinute = minuteOfDay.coerceIn(0, 24 * 60 - 1))
    }

    fun setReminderWeekday(context: Context, weekday: Int) = update(context) {
        // A new day is a new due date: whatever was said about the old one no longer applies.
        it.copy(reminderWeekday = if (weekday in 1..7) weekday else SameDayAsLastCount, notifiedAt = 0L)
    }

    /** Starts a count, remembering the shelf as it stands; the last one's summary goes. Does nothing if one is under way. */
    fun start(context: Context, shelf: Map<Long, ShelfFigure>) = update(context) {
        if (it.inProgress) it else it.copy(startedAt = System.currentTimeMillis(), before = shelf, counted = emptySet(), summary = null)
    }

    /**
     * Drops the count under way without recording it. For one where nothing was counted: saving
     * that as a count would move the reminder on a fortnight for a shelf nobody looked at.
     */
    fun cancel(context: Context) = update(context) {
        it.copy(startedAt = 0L, before = emptyMap(), counted = emptySet())
    }

    fun markCounted(context: Context, productId: Long) = update(context) {
        if (!it.inProgress || productId in it.counted) it else it.copy(counted = it.counted + productId)
    }

    /** A tick taken back — tapped by mistake, or the rack wants a second look. */
    fun unmarkCounted(context: Context, productId: Long) = update(context) {
        if (productId !in it.counted) it else it.copy(counted = it.counted - productId)
    }

    /**
     * Closes the count and keeps what it found. [shelf] is every product as it now stands.
     *
     * Only what was gone through can have changed, so only that is compared — a delivery that
     * arrived during the count is not the count's doing.
     */
    fun finish(context: Context, shelf: Map<Long, ShelfFigure>) = update(context) { state ->
        if (!state.inProgress) return@update state
        val now = System.currentTimeMillis()
        val changes = state.counted.mapNotNull { id ->
            val before = state.before[id] ?: ShelfFigure(0, 0.0)
            val after = shelf[id] ?: ShelfFigure(0, 0.0)
            if (before.packs == after.packs && abs(before.open - after.open) < 0.0005) null
            else CountChange(id, before, after)
        }
        state.copy(
            lastCountAt = now,
            startedAt = 0L,
            before = emptyMap(),
            counted = emptySet(),
            notifiedAt = 0L,
            summary = CountSummary(
                finishedAt = now,
                counted = state.counted.count { it in shelf },
                changes = changes,
                skipped = shelf.keys.filter { it !in state.counted },
            ),
        )
    }

    fun dismissSummary(context: Context) = update(context) { it.copy(summary = null) }

    fun markNotified(context: Context, at: Long) = update(context) { it.copy(notifiedAt = at) }

    // Plain text, not JSON: a few dozen numbers, and nothing here is worth a parser failing on.

    private fun encodeFigures(figures: Map<Long, ShelfFigure>): String =
        figures.entries.joinToString(";") { (id, f) -> "$id:${f.packs}:${f.open}" }

    private fun decodeFigures(raw: String?): Map<Long, ShelfFigure> =
        raw.orEmpty().split(';').mapNotNull { part ->
            val bits = part.split(':')
            if (bits.size != 3) return@mapNotNull null
            val id = bits[0].toLongOrNull() ?: return@mapNotNull null
            id to ShelfFigure(bits[1].toIntOrNull() ?: 0, bits[2].toDoubleOrNull() ?: 0.0)
        }.toMap()

    private fun encodeChanges(changes: List<CountChange>): String =
        changes.joinToString(";") { c ->
            "${c.productId}:${c.before.packs}:${c.before.open}:${c.after.packs}:${c.after.open}"
        }

    private fun decodeChanges(raw: String?): List<CountChange> =
        raw.orEmpty().split(';').mapNotNull { part ->
            val bits = part.split(':')
            if (bits.size != 5) return@mapNotNull null
            val id = bits[0].toLongOrNull() ?: return@mapNotNull null
            CountChange(
                productId = id,
                before = ShelfFigure(bits[1].toIntOrNull() ?: 0, bits[2].toDoubleOrNull() ?: 0.0),
                after = ShelfFigure(bits[3].toIntOrNull() ?: 0, bits[4].toDoubleOrNull() ?: 0.0),
            )
        }

    private fun decodeIds(raw: String?): List<Long> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
}
