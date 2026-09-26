package com.conwic.mixmaster.data.sync

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteDatabase
import com.conwic.mixmaster.data.db.AppDatabase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors

/** How the phone stands with the company, for the line on the Company sharing screen. */
data class SyncStatus(
    val running: Boolean = false,
    /** Changes made on this phone that have not been handed over yet. */
    val waiting: Int = 0,
    /** The last news came from the server rather than from the phone's own copy. */
    val online: Boolean = false,
    val lastReceivedAt: Long = 0L,
    /** Taken off the company's crew list, or never let in. */
    val noAccess: Boolean = false,
    val signedOut: Boolean = false,
)

/**
 * Keeps this phone's database and the company's shared copy the same.
 *
 * Every table is mirrored as a collection under `companies/{id}`: one document per row, named by
 * the row's id (ids are the same on every phone — see GlobalIds), holding the row's columns as
 * they are, plus `_at` (when the server took it), `_dev` (which phone wrote it) and `_deleted`.
 *
 * Out: SQLite triggers note every insert, change and delete in `sync_outbox`, whatever part of the
 * app made it, so none of the dozens of places that save something had to learn about sharing.
 * The outbox is sent in batches and emptied; Firestore keeps what it could not send yet and sends
 * it when there is a signal, across restarts.
 *
 * In: one listener per table, asking only for what changed since this phone last heard, so a
 * morning's start costs a handful of reads rather than the whole catalogue. What arrives is written
 * with the triggers held off, so it does not bounce straight back out.
 *
 * Last write wins, row by row. Two people changing the same row at once is rare here, and the row
 * that lands second is the one that stays.
 */
object SyncEngine {

    /**
     * Parents before children: the order a fresh phone has to fill its tables in, since a room
     * cannot be written before its floor. Photos stay out until their files travel with them.
     */
    val Tables = listOf(
        "products", "product_components", "solutions", "solution_lines", "usage_logs",
        "projects", "floors", "room_areas", "room_layers", "tasks", "notes",
        "material_uses", "stock", "deliveries", "team_members",
    )

    /**
     * What a worker may write — the same list as `workerMayWrite` in the setup guide's rules. A
     * worker's change to anything else would be refused by the server, and a refused write takes
     * the rest of its batch down with it, so those are put back from the server instead of sent.
     */
    val WorkerTables = setOf("stock", "deliveries", "material_uses", "usage_logs", "notes", "tasks")

    private const val OUTBOX = "sync_outbox"
    private const val BATCH = 400
    private const val MARKS = "mixmaster_sync_marks"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val applier = Executors.newSingleThreadExecutor { Thread(it, "mixmaster-sync") }
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val listeners = mutableListOf<ListenerRegistration>()
    private var pusher: Job? = null
    private var observer: InvalidationTracker.Observer? = null
    private val retry = mutableMapOf<String, RemoteRow>()
    private val columns = mutableMapOf<String, Set<String>>()
    private val fromServer = mutableMapOf<String, Boolean>()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private lateinit var app: Context

    /** This install, told apart from every other phone writing to the company. */
    private val deviceId: String by lazy {
        val prefs = app.getSharedPreferences(MARKS, Context.MODE_PRIVATE)
        prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device", it).apply()
        }
    }

    private fun database(): AppDatabase = AppDatabase.getInstance(app)
    private fun sql(): SupportSQLiteDatabase = database().openHelper.writableDatabase

    // ---- Starting and stopping ---------------------------------------------------------------

    /**
     * At app start, and after connecting: picks up where this phone left off, if it shares a
     * company. The database work goes off the main thread — at app start it may be the first
     * thing to open the database, migrations and all.
     */
    fun start(context: Context) {
        app = context.applicationContext
        val link = SyncStore.current(app) ?: return
        if (runCatching { FirebaseBoot.start(app, link.settings) }.isFailure) return
        if (FirebaseAuth.getInstance().currentUser == null) {
            _status.value = SyncStatus(signedOut = true)
            return
        }
        scope.launch { begin(link) }
    }

    @Synchronized
    private fun begin(link: CompanyLink) {
        stopListening()
        installTriggers()
        _status.value = SyncStatus(running = true)
        startPusher(link)
        startListening(link)
    }

    @Synchronized
    fun stop() {
        stopListening()
        pusher?.cancel()
        pusher = null
        observer?.let { runCatching { database().invalidationTracker.removeObserver(it) } }
        observer = null
        _status.value = SyncStatus()
    }

    /**
     * The employer's phone, just after making the company: everything on it goes up. Queued
     * through the outbox like any other change, so it survives the app closing half way.
     */
    fun uploadEverything(context: Context) {
        app = context.applicationContext
        installTriggers()
        clearMarks()
        val db = sql()
        db.execSQL("DELETE FROM $OUTBOX")
        Tables.forEach { table ->
            db.execSQL("INSERT INTO $OUTBOX (tbl, rid, op) SELECT '$table', id, 'u' FROM `$table`")
        }
    }

    /**
     * A phone joining: what it had is set aside for the company's copy. Kept rows would carry
     * numbers the company's own rows already use (every phone starts counting at 1), so there is
     * no merging them in — the Company sharing screen offers a backup before this runs.
     */
    fun clearForJoin(context: Context) {
        app = context.applicationContext
        installTriggers()
        clearMarks()
        applying {
            val db = sql()
            Tables.asReversed().forEach { table -> db.execSQL("DELETE FROM `$table`") }
            db.execSQL("DELETE FROM $OUTBOX")
        }
        synchronized(retry) { retry.clear() }
    }

    /** Leaves the company on this phone: stops, forgets the outbox, keeps the data where it is. */
    fun disconnect(context: Context) {
        app = context.applicationContext
        stop()
        runCatching {
            val db = sql()
            Tables.forEach { table ->
                listOf("i", "u", "d").forEach { op -> db.execSQL("DROP TRIGGER IF EXISTS sync_${table}_$op") }
            }
            db.execSQL("DROP TABLE IF EXISTS $OUTBOX")
        }
        clearMarks()
        SyncStore.clear(app)
        GoogleSignIn.signOut()
    }

    // ---- Out: triggers and the outbox --------------------------------------------------------

    /**
     * Idempotent, and run on every start: a migration that rebuilds a table drops its triggers
     * with it, and a restored backup may come from before any of this existed.
     */
    private fun installTriggers() {
        val db = sql()
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $OUTBOX (seq INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "tbl TEXT NOT NULL, rid INTEGER NOT NULL, op TEXT NOT NULL)",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (k TEXT PRIMARY KEY NOT NULL, v INTEGER NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO sync_state (k, v) VALUES ('applying', 0)")
        // A crash half way through applying would otherwise leave every later change unrecorded.
        db.execSQL("UPDATE sync_state SET v = 0 WHERE k = 'applying'")
        val quiet = "WHEN (SELECT v FROM sync_state WHERE k = 'applying') = 0"
        Tables.forEach { t ->
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_i AFTER INSERT ON `$t` $quiet " +
                    "BEGIN INSERT INTO $OUTBOX (tbl, rid, op) VALUES ('$t', NEW.id, 'u'); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_u AFTER UPDATE ON `$t` $quiet " +
                    "BEGIN INSERT INTO $OUTBOX (tbl, rid, op) VALUES ('$t', NEW.id, 'u'); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_d AFTER DELETE ON `$t` $quiet " +
                    "BEGIN INSERT INTO $OUTBOX (tbl, rid, op) VALUES ('$t', OLD.id, 'd'); END",
            )
        }
    }

    private fun startPusher(link: CompanyLink) {
        pusher?.cancel()
        observer?.let { runCatching { database().invalidationTracker.removeObserver(it) } }
        // Woken by any change to the app's tables, and every few seconds regardless.
        val watch = object : InvalidationTracker.Observer(Tables.toTypedArray()) {
            override fun onInvalidated(tables: Set<String>) {
                wake.trySend(Unit)
            }
        }
        observer = watch
        runCatching { database().invalidationTracker.addObserver(watch) }
        pusher = scope.launch {
            while (isActive) {
                val sent = runCatching { pushOnce(link) }.getOrDefault(0)
                if (sent == 0) {
                    withTimeoutOrNull(5_000) { wake.receive() }
                    delay(300) // let a burst of saves settle into one batch
                }
            }
        }
    }

    private data class Pending(val seq: Long, val table: String, val id: Long, val op: String)

    /** Hands one batch of the outbox to Firestore. Returns how many rows went. */
    private fun pushOnce(link: CompanyLink): Int {
        val db = sql()
        val rows = mutableListOf<Pending>()
        db.query("SELECT seq, tbl, rid, op FROM $OUTBOX ORDER BY seq LIMIT $BATCH").use { c ->
            while (c.moveToNext()) rows += Pending(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3))
        }
        refreshWaiting()
        if (rows.isEmpty()) return 0
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return 0
        val company = CompanyService.company(link.companyId)
        val batch = FirebaseFirestore.getInstance().batch()
        val refused = mutableListOf<Pair<String, Long>>()
        // The last word on each row is the one that counts; earlier ones in the same batch are spent.
        rows.groupBy { it.table to it.id }.forEach { (key, changes) ->
            val (table, id) = key
            if (table !in Tables) return@forEach
            if (!link.isEmployer && table !in WorkerTables) {
                refused += key
                return@forEach
            }
            val doc = company.collection(table).document(id.toString())
            val row = if (changes.last().op == "d") null else readRow(db, table, id)
            val stamp = mapOf("_at" to FieldValue.serverTimestamp(), "_by" to uid, "_dev" to deviceId)
            if (row == null) {
                batch.set(doc, mapOf("_deleted" to true) + stamp, SetOptions.merge())
            } else {
                batch.set(doc, row + mapOf("_deleted" to false) + stamp)
            }
        }
        batch.commit().addOnFailureListener { e ->
            val denied = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            if (denied) {
                _status.update { it.copy(noAccess = true) }
            }
        }
        db.execSQL("DELETE FROM $OUTBOX WHERE seq <= ?", arrayOf<Any>(rows.last().seq))
        if (refused.isNotEmpty()) scope.launch { restoreFromServer(link, refused) }
        refreshWaiting()
        return rows.size
    }

    private fun refreshWaiting() {
        val waiting = runCatching {
            sql().query("SELECT count(*) FROM $OUTBOX").use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }.getOrDefault(0)
        _status.update { it.copy(waiting = waiting) }
    }

    /** A row as a map of its columns, ready to be a document. Local file paths stay behind. */
    private fun readRow(db: SupportSQLiteDatabase, table: String, id: Long): Map<String, Any?>? =
        db.query("SELECT * FROM `$table` WHERE id = ?", arrayOf<Any>(id)).use { c ->
            if (!c.moveToFirst()) return null
            (0 until c.columnCount).mapNotNull { i ->
                val name = c.getColumnName(i)
                val value: Any? = when (c.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    Cursor.FIELD_TYPE_STRING -> c.getString(i)
                    Cursor.FIELD_TYPE_NULL -> null
                    else -> return@mapNotNull null
                }
                // A photo or a sheet saved on this phone means nothing on another one.
                if (value is String && (value.startsWith("file:") || value.startsWith("content:"))) {
                    return@mapNotNull null
                }
                name to value
            }.toMap()
        }

    /** A worker's change to something only the employer may change: the company's version goes back. */
    private suspend fun restoreFromServer(link: CompanyLink, rows: List<Pair<String, Long>>) {
        val company = CompanyService.company(link.companyId)
        val restored = rows.mapNotNull { (table, id) ->
            val doc = runCatching {
                company.collection(table).document(id.toString()).get(Source.SERVER).await()
            }.getOrNull() ?: return@mapNotNull null
            if (doc.exists()) RemoteRow.from(table, doc) else RemoteRow(table, id, emptyMap(), deleted = true)
        }
        if (restored.isNotEmpty()) applier.execute { apply(restored) }
    }

    // ---- In: listeners -----------------------------------------------------------------------

    private fun startListening(link: CompanyLink) {
        val company = CompanyService.company(link.companyId)
        Tables.forEach { table ->
            val since = mark(table)
            // A minute's overlap: a change the server stamped a moment before the last one seen
            // is read twice rather than missed, and writing a row twice changes nothing.
            val query = if (since > 0L) {
                company.collection(table).whereGreaterThan("_at", Timestamp(Date(since - 60_000)))
            } else {
                company.collection(table)
            }
            listeners += query.addSnapshotListener(applier, MetadataChanges.EXCLUDE) { snap, error ->
                if (error != null) {
                    if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        _status.update { it.copy(noAccess = true) }
                    }
                    return@addSnapshotListener
                }
                if (snap != null) receive(table, snap)
            }
        }
    }

    private fun stopListening() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
    }

    private fun receive(table: String, snap: QuerySnapshot) {
        val rows = snap.documentChanges.mapNotNull { change ->
            val doc = change.document
            // Our own writes, before the server has taken them, and after: this phone has them.
            if (doc.metadata.hasPendingWrites()) return@mapNotNull null
            if (doc.getString("_dev") == deviceId) return@mapNotNull null
            if (change.type == DocumentChange.Type.REMOVED) return@mapNotNull null
            RemoteRow.from(table, doc)
        }
        if (rows.isNotEmpty()) apply(rows)
        val newest = snap.documents.mapNotNull { it.getTimestamp("_at")?.toDate()?.time }.maxOrNull()
        if (newest != null && newest > mark(table)) setMark(table, newest)
        synchronized(fromServer) { fromServer[table] = !snap.metadata.isFromCache }
        val online = synchronized(fromServer) { fromServer.values.any { it } }
        _status.update {
            it.copy(online = online, lastReceivedAt = if (rows.isNotEmpty()) System.currentTimeMillis() else it.lastReceivedAt)
        }
    }

    // ---- Applying what arrives ---------------------------------------------------------------

    private data class RemoteRow(val table: String, val id: Long, val values: Map<String, Any?>, val deleted: Boolean) {
        val key get() = "$table/$id"

        companion object {
            fun from(table: String, doc: DocumentSnapshot) = RemoteRow(
                table = table,
                id = doc.id.toLongOrNull() ?: 0L,
                values = doc.data.orEmpty().filterKeys { !it.startsWith("_") },
                deleted = doc.getBoolean("_deleted") == true,
            )
        }
    }

    /**
     * Writes what arrived, parents first, with the triggers held off. A row whose parent has not
     * arrived yet (a room before its floor) waits and is tried again with the next delivery.
     */
    private fun apply(incoming: List<RemoteRow>) {
        val order = Tables.withIndex().associate { it.value to it.index }
        val work = synchronized(retry) {
            (retry.values + incoming).associateBy { it.key }.values
                .filter { it.id != 0L }
                .sortedBy { order[it.table] ?: Int.MAX_VALUE }
                .also { retry.clear() }
        }
        val waiting = unsentKeys()
        val failed = mutableListOf<RemoteRow>()
        applying {
            var todo: List<RemoteRow> = work.filter { it.key !in waiting }
            while (todo.isNotEmpty()) {
                val stuck = todo.filterNot(::applyOne)
                if (stuck.size == todo.size) {
                    failed += stuck
                    break
                }
                todo = stuck
            }
        }
        synchronized(retry) { failed.forEach { retry[it.key] = it } }
    }

    /** Rows changed here that have not gone out yet: this phone's version is the newer one. */
    private fun unsentKeys(): Set<String> = runCatching {
        sql().query("SELECT tbl, rid FROM $OUTBOX").use { c ->
            buildSet { while (c.moveToNext()) add("${c.getString(0)}/${c.getLong(1)}") }
        }
    }.getOrDefault(emptySet())

    /** Update if the row is here, insert if not — never replace, which would take its children with it. */
    private fun applyOne(row: RemoteRow): Boolean = try {
        val db = sql()
        if (row.deleted) {
            db.delete("`${row.table}`", "id = ?", arrayOf<Any>(row.id))
        } else {
            val known = columnsOf(db, row.table)
            val values = ContentValues()
            row.values.forEach { (name, value) ->
                if (name !in known || name == "id") return@forEach
                when (value) {
                    null -> values.putNull(name)
                    is Long -> values.put(name, value)
                    is Int -> values.put(name, value.toLong())
                    is Double -> values.put(name, value)
                    is Boolean -> values.put(name, if (value) 1L else 0L)
                    is String -> values.put(name, value)
                    else -> values.put(name, value.toString())
                }
            }
            val updated = db.update("`${row.table}`", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf<Any>(row.id))
            if (updated == 0) {
                values.put("id", row.id)
                db.insert("`${row.table}`", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
        true
    } catch (e: SQLiteConstraintException) {
        false
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
        columns.getOrPut(table) {
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val nameAt = c.getColumnIndex("name")
                buildSet { while (c.moveToNext()) add(c.getString(nameAt)) }
            }
        }

    /**
     * Runs [block] in one transaction with the triggers told to look away, so rows written for
     * the company's sake are not recorded as this phone's own changes.
     */
    private fun applying(block: () -> Unit) {
        database().runInTransaction(
            Runnable {
                val db = sql()
                db.execSQL("UPDATE sync_state SET v = 1 WHERE k = 'applying'")
                try {
                    block()
                } finally {
                    db.execSQL("UPDATE sync_state SET v = 0 WHERE k = 'applying'")
                }
            },
        )
    }

    // ---- Where each table was left -----------------------------------------------------------

    private fun marks() = app.getSharedPreferences(MARKS, Context.MODE_PRIVATE)
    private fun mark(table: String): Long = marks().getLong("at_$table", 0L)
    private fun setMark(table: String, at: Long) = marks().edit().putLong("at_$table", at).apply()

    private fun clearMarks() {
        val keep = marks().getString("device", null)
        marks().edit().clear().apply {
            if (keep != null) putString("device", keep)
        }.apply()
    }
}
