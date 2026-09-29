package com.conwic.mixmaster.data.company

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteDatabase
import com.conwic.mixmaster.MixMasterApp
import com.conwic.mixmaster.data.db.AppDatabase
import com.conwic.mixmaster.data.db.newIdExpression
import com.conwic.mixmaster.data.model.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** How the phone stands with the company, for the Company screen. */
data class SyncStatus(
    /** Changes made on this phone that the server has not taken yet. */
    val waiting: Int = 0,
    /** A request is on its way. */
    val working: Boolean = false,
    /** The last try got no answer at all: no signal, or the server is down. */
    val offline: Boolean = false,
    /** The server answered, but with something other than the data: its word for what. */
    val problem: String? = null,
)

/**
 * Keeps this phone's database and the company's server the same.
 *
 * Out: SQLite triggers note every insert, change and delete in `sync_outbox`, whatever part of the
 * app made it, so none of the dozens of places that save something had to learn about sharing.
 * The outbox is sent in batches and emptied once the server has taken it; until then it waits,
 * across restarts, for a signal.
 *
 * In: the server numbers every change it takes, and the phone asks for everything after the last
 * number it saw — so a morning's start is one short request, not the whole catalogue. What
 * arrives is written with the triggers held off, so it does not bounce straight back out.
 *
 * Last write wins, row by row. A row this phone has changed and not sent yet is left alone when
 * somebody else's version of it arrives: this phone's goes up next, and that one stays.
 *
 * Every answer from the server says who this phone is and what it may do, and that is written
 * down each time — a permission taken away takes effect at the next contact. An answer saying the
 * phone has no place in the company any more empties it (see [CompanyWipe]).
 */
object SyncEngine {

    /**
     * Parents before children: the order a fresh phone has to fill its tables in, since a room
     * cannot be written before its floor. Photos and plans ([FileSync.Tables]) come after these,
     * once the company's server can keep their files.
     */
    val Tables = listOf(
        "products", "solutions", "solution_lines", "usage_logs",
        "projects", "floors", "room_areas", "room_layers", "tasks", "notes",
        "material_uses", "stock", "deliveries", "team_members",
    )

    private const val OUTBOX = "sync_outbox"
    /** Rows that arrived before the row they belong to — a room before its floor — kept to try again. */
    private const val INBOX = "sync_inbox"
    private const val PUSH_BATCH = 200
    /** How often an open app asks for news. */
    private const val PULL_EVERY = 30_000L
    private const val MARKS = "mixmaster_sync"
    private const val REQUEUE = "requeue"
    /** The server keeps photos' and plans' files, and this phone shares them. */
    private const val FILES = "files"
    /** How often a server that doesn't keep files yet is asked again: the employer may update it any day. */
    private const val FILES_CHECK_EVERY = 10 * 60_000L
    /** Below this, an id was numbered 1, 2, 3 on this phone before ids were made unique to it. */
    private const val SMALL_ID = 1_000_000_000_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    /** One conversation with the server at a time, whoever started it. */
    private val talking = Mutex()
    private var loop: Job? = null
    /** Files go up and come down beside the rows, so a big plan doesn't hold up everything else. */
    private var files: Job? = null
    @Volatile private var filesCheckedAt = 0L
    private var observer: InvalidationTracker.Observer? = null
    @Volatile private var foreground = false
    @Volatile private var pullWanted = true
    @Volatile private var lastPullAt = 0L
    private val columns = mutableMapOf<String, Set<String>>()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _filesShared = MutableStateFlow(false)
    /** Photos and plans travel with the rest: the server keeps their files. */
    val filesShared: StateFlow<Boolean> = _filesShared.asStateFlow()

    private lateinit var app: Context

    private fun database(): AppDatabase = AppDatabase.getInstance(app)
    private fun sql(): SupportSQLiteDatabase = database().openHelper.writableDatabase

    /** For [FileSync], which may be asked for a plan before anything here has run. */
    internal fun sqlFor(context: Context): SupportSQLiteDatabase {
        app = context.applicationContext
        return sql()
    }

    private fun filesOn(): Boolean = marks().getBoolean(FILES, false)

    /**
     * Changes to photos and plans are noted from the start, and wait in the outbox until the
     * server keeps files: an older one would lose the files, and refuses a worker's photo in a
     * way the phone takes as the company deleting it.
     */
    private fun heldBack(): String =
        if (filesOn()) "" else "tbl NOT IN (${FileSync.Tables.joinToString { "'$it'" }})"

    /** A new name for this phone in the company, made each time it joins. */
    fun newDevice(): String = UUID.randomUUID().toString().replace("-", "")

    // ---- Starting and stopping ---------------------------------------------------------------

    /**
     * At app start, and after joining: picks up where this phone left off. The database work
     * goes off the main thread — at app start it may be the first thing to open the database,
     * migrations and all.
     */
    fun start(context: Context) {
        app = context.applicationContext
        if (CompanyStore.current(app) == null) return
        scope.launch { begin() }
    }

    @Synchronized
    private fun begin() {
        loop?.cancel()
        installTriggers()
        if (marks().getBoolean(REQUEUE, false)) queueEverything(app)
        observer?.let { runCatching { database().invalidationTracker.removeObserver(it) } }
        _filesShared.value = filesOn()
        // Woken by any change to the shared tables, so a change goes up within a second or two.
        val watch = object : InvalidationTracker.Observer((Tables + FileSync.Tables).toTypedArray()) {
            override fun onInvalidated(tables: Set<String>) {
                wake.trySend(Unit)
            }
        }
        observer = watch
        runCatching { database().invalidationTracker.addObserver(watch) }
        pullWanted = true
        loop = scope.launch { keepInStep() }
    }

    @Synchronized
    fun stop() {
        loop?.cancel()
        loop = null
        files?.cancel()
        files = null
        observer?.let { runCatching { database().invalidationTracker.removeObserver(it) } }
        observer = null
        _status.value = SyncStatus()
    }

    /** The app came to the front, or went away. In front, it asks for news every half minute. */
    fun setForeground(context: Context, inFront: Boolean) {
        app = context.applicationContext
        foreground = inFront
        if (inFront) syncNow()
    }

    fun syncNow() {
        pullWanted = true
        wake.trySend(Unit)
    }

    private suspend fun keepInStep() {
        while (currentCoroutineContext().isActive) {
            if (CompanyStore.current(app) == null) return
            val pullDue = pullWanted || (foreground && System.currentTimeMillis() - lastPullAt >= PULL_EVERY)
            try {
                talking.withLock {
                    _status.update { it.copy(working = true) }
                    try {
                        pushAll()
                        if (pullDue) {
                            pullWanted = false
                            pullAll()
                        }
                    } catch (p: CompanyProblem) {
                        // The company has gone to another server: this phone goes after it, and
                        // the next turn of the loop carries on there.
                        if (p.code != "moved" || p.to == null) throw p
                        follow(p.to)
                    }
                    keepPeopleCopy()
                    checkFiles()
                }
                _status.update { it.copy(working = false, offline = false, problem = null) }
                if (filesOn() && files?.isActive != true) files = scope.launch { FileSync.transfer(app, sql()) }
            } catch (p: CompanyProblem) {
                _status.update { it.copy(working = false) }
                when (p.code) {
                    "revoked" -> {
                        CompanyWipe.run(app, CompanyWipe.Reason.Revoked)
                        return
                    }
                    "offline" -> _status.update { it.copy(offline = true) }
                    else -> _status.update { it.copy(offline = false, problem = p.code) }
                }
            } catch (e: CancellationException) {
                // Stopped from outside (leaving the company, joining again): not a failure to show.
                throw e
            } catch (e: Exception) {
                _status.update { it.copy(working = false, problem = "server") }
            }
            refreshWaiting()
            withTimeoutOrNull(if (foreground) PULL_EVERY else 5 * 60_000L) { wake.receive() }
            // A burst of saves — a whole room of coats — goes as one batch rather than ten.
            delay(700)
        }
    }

    // ---- Joining, setting up and leaving -----------------------------------------------------

    /**
     * The employer's phone, just after setting up the company: everything on it is queued to go
     * up, through the outbox like any other change, so it survives the app closing half way.
     */
    fun queueEverything(context: Context) {
        app = context.applicationContext
        installTriggers()
        clearMarks()
        val db = sql()
        db.execSQL("DELETE FROM $OUTBOX")
        db.execSQL("DELETE FROM $INBOX")
        // Photos and plans wait there until the server is seen to keep their files (checkFiles).
        (Tables + FileSync.Tables).forEach { table ->
            db.execSQL("INSERT INTO $OUTBOX (tbl, rid, op) SELECT '$table', id, 'u' FROM `$table`")
        }
    }

    /** A backup was restored over this phone's data: all of it goes up again at the next start. */
    fun requeueOnStart(context: Context) {
        context.applicationContext.getSharedPreferences(MARKS, Context.MODE_PRIVATE)
            .edit().putBoolean(REQUEUE, true).commit()
    }

    /**
     * Empties the shared tables, for a phone joining (the company's copy comes in instead) or an
     * employer starting the company from nothing. Nothing is queued: the server is not told.
     */
    fun clearShared(context: Context) {
        app = context.applicationContext
        installTriggers()
        clearMarks()
        applying {
            val db = sql()
            // Photos and blueprints belong to projects; with the projects gone they have nothing
            // to hang on.
            db.execSQL("DELETE FROM photos")
            db.execSQL("DELETE FROM blueprints")
            Tables.asReversed().forEach { table -> db.execSQL("DELETE FROM `$table`") }
            db.execSQL("DELETE FROM $OUTBOX")
            db.execSQL("DELETE FROM $INBOX")
        }
        FileSync.clearFiles(app)
    }

    /**
     * Stops sharing on this phone and keeps what is on it. The triggers go, so the app is exactly
     * as it was before there was a company.
     */
    fun disconnect(context: Context) {
        app = context.applicationContext
        stop()
        runCatching {
            val db = sql()
            (Tables + FileSync.Tables).forEach { table ->
                listOf("i", "u", "d").forEach { op -> db.execSQL("DROP TRIGGER IF EXISTS sync_${table}_$op") }
            }
            db.execSQL("DROP TABLE IF EXISTS $OUTBOX")
            db.execSQL("DROP TABLE IF EXISTS $INBOX")
            db.execSQL("DROP TABLE IF EXISTS ${FileSync.STATE}")
        }
        clearMarks()
        FileSync.reset()
    }

    /**
     * Brings the phone level with the server now, while the Company screen waits: used straight
     * after joining, so the crew member sees the company's data before they see the app.
     */
    suspend fun catchUp(context: Context, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        app = context.applicationContext
        talking.withLock {
            pushAll()
            pullAll(onProgress)
        }
        refreshWaiting()
    }

    // ---- Moving servers --------------------------------------------------------------------

    /**
     * Goes after a company that has moved to [to]. Only once the new server has been seen to hold
     * this company, and to know this phone's key, is the address changed: a phone must never
     * empty itself because a server it was only trying out does not know it.
     */
    private suspend fun follow(to: String) {
        val link = CompanyStore.current(app) ?: return
        val hello = CompanyApi.hello(to)
        if (!hello.claimed || hello.companyId != link.companyId) throw CompanyProblem("moved_unready")
        val there = link.copy(server = to, kind = hello.kind)
        try {
            // Asks for nothing, only whether the key works.
            CompanyApi.pull(there, Long.MAX_VALUE / 4)
        } catch (p: CompanyProblem) {
            throw if (p.code == "revoked") CompanyProblem("moved_unknown") else p
        }
        CompanyStore.save(app, there)
        // The new server numbers its changes from the start: everything is asked for again.
        // What this phone has not sent yet stays in the outbox and goes there instead.
        marks().edit().remove("since").commit()
        // Nor has it got any files yet but the ones the owner's phone brought: this one offers its own.
        FileSync.forgetServer(sql())
        lastPullAt = 0L
        pullWanted = true
    }

    /**
     * The new address typed in by hand — for a company whose old server is gone and so cannot
     * point the way. Tries what was typed and the likely places a server sits under it.
     */
    suspend fun followTo(context: Context, typed: String) = withContext(Dispatchers.IO) {
        app = context.applicationContext
        val candidates = ServerAddress.candidates(typed)
        if (candidates.isEmpty()) throw CompanyProblem("not_server")
        var problem: CompanyProblem? = null
        for (server in candidates) {
            try {
                talking.withLock { follow(server) }
                syncNow()
                return@withContext
            } catch (p: CompanyProblem) {
                if (problem == null || problem.code == "not_server") problem = p
            }
        }
        throw problem ?: CompanyProblem("not_server")
    }

    /** The steps of a move, for the Company screen to say which one it is on. */
    enum class MoveStep { OldServer, NewServer, Upload }

    /**
     * Moves the whole company to the empty server at [to]: the same company, the same people and
     * the same keys, so every phone follows on its own and nobody needs a new code.
     *
     * The old server is closed for writing first, then read to the end, so nothing anybody sent
     * gets left behind; if it cannot be reached at all, this phone's own copy and its last list of
     * people are what move. The crew's phones wait on the new server until everything is there.
     */
    suspend fun move(context: Context, to: String, kind: ServerKind, onStep: (MoveStep) -> Unit) = withContext(Dispatchers.IO) {
        app = context.applicationContext
        stop()
        try {
            talking.withLock {
                val old = CompanyStore.current(app) ?: throw CompanyProblem("revoked")
                onStep(MoveStep.OldServer)
                var closed = false
                val people = try {
                    // Refused if an earlier try already closed it — no matter: everything on this
                    // phone goes to the new server below anyway.
                    try {
                        pushAll()
                    } catch (p: CompanyProblem) {
                        if (p.code != "moved") throw p
                    }
                    CompanyApi.moveOut(old, to)
                    closed = true
                    pullAll(takingLeave = true)
                    CompanyApi.exportPeople(old).also { CompanyStore.savePeopleExport(app, it) }
                } catch (p: CompanyProblem) {
                    if (p.code != "offline" && p.code != "not_server" && p.code != "server") throw p
                    CompanyStore.peopleExport(app) ?: onlyMe(old)
                }
                onStep(MoveStep.NewServer)
                try {
                    CompanyApi.adopt(to, old, people)
                } catch (p: CompanyProblem) {
                    // Not moved after all: the old server is opened again, so nobody is left waiting.
                    if (closed) runCatching { CompanyApi.moveOut(old, "") }
                    throw p
                }
                CompanyStore.save(app, old.copy(server = to, kind = kind))
                onStep(MoveStep.Upload)
                queueEverything(app)
                pushAll()
                pullAll()
                CompanyStore.current(app)?.let { CompanyApi.moveDone(it) }
            }
        } finally {
            start(app)
        }
        refreshWaiting()
    }

    /**
     * The owner alone, for a move with the old server gone and no copy of the list kept: the
     * company still moves, and the crew are given new codes on the new server.
     */
    private fun onlyMe(link: CompanyLink): String {
        val all = JSONObject().put("catalogue", true).put("projects", true).put("warehouse", true).put("site", true)
        val hash = MessageDigest.getInstance("SHA-256").digest(link.token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return JSONArray().put(
            JSONObject()
                .put("id", link.personId)
                .put("name", link.name)
                .put("owner", true)
                .put("perms", all)
                .put("status", "active")
                .put("token_hash", hash)
                .put("device", link.device),
        ).toString()
    }

    /**
     * The owner's phone keeps a fresh copy of the people list, keys included, so the company can
     * still be moved if the server it is on disappears. Twice a day is plenty: people come and go
     * far less often than that.
     */
    private suspend fun keepPeopleCopy() {
        val link = CompanyStore.current(app) ?: return
        if (!link.owner) return
        if (System.currentTimeMillis() - CompanyStore.peopleExportAt(app) < 12 * 60 * 60 * 1000L) return
        runCatching { CompanyStore.savePeopleExport(app, CompanyApi.exportPeople(link)) }
    }

    // ---- Photos and plans --------------------------------------------------------------------

    /**
     * Whether the company's server keeps the files behind photos and plans — asked of it, since a
     * server set up before it could only learns how when the employer updates it. From then on
     * the photos and plans on this phone are shared like everything else.
     */
    private suspend fun checkFiles() {
        if (filesOn()) return
        val now = System.currentTimeMillis()
        if (now - filesCheckedAt < FILES_CHECK_EVERY) return
        filesCheckedAt = now
        val link = CompanyStore.current(app) ?: return
        val hello = try {
            CompanyApi.hello(link.server)
        } catch (p: CompanyProblem) {
            return
        }
        if (!hello.files) return
        adoptFiles()
        marks().edit().putBoolean(FILES, true).commit()
        _filesShared.value = true
        // What waited in the outbox goes up now.
        wake.trySend(Unit)
    }

    /** The employer has just updated the server: asked again now rather than in ten minutes. */
    fun checkFilesNow() {
        filesCheckedAt = 0L
        syncNow()
    }

    /**
     * Photos and plans added before this version, which no trigger ever noted: they are the ones
     * without a key. An id another phone may have used as well gets a new one, each gets the key
     * its file will be kept under, and they are queued to go up.
     */
    private fun adoptFiles() {
        applying {
            val db = sql()
            FileSync.Tables.forEach { table ->
                val small = mutableListOf<Long>()
                db.query("SELECT id FROM `$table` WHERE id < $SMALL_ID AND fileKey = ''").use { c ->
                    while (c.moveToNext()) small += c.getLong(0)
                }
                small.forEach { id ->
                    // One statement a row, so each draws its own random bits; a clash is drawn again.
                    repeat(5) {
                        try {
                            db.execSQL("UPDATE `$table` SET id = ${newIdExpression(table)} WHERE id = ?", arrayOf<Any>(id))
                            return@forEach
                        } catch (e: SQLiteConstraintException) {
                            // drawn again
                        }
                    }
                }
                db.execSQL("INSERT INTO $OUTBOX (tbl, rid, op) SELECT '$table', id, 'u' FROM `$table` WHERE fileKey = ''")
                db.execSQL("UPDATE `$table` SET fileKey = lower(hex(randomblob(16))) WHERE fileKey = ''")
            }
        }
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
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $INBOX (tbl TEXT NOT NULL, rid INTEGER NOT NULL, data TEXT, " +
                "deleted INTEGER NOT NULL, PRIMARY KEY (tbl, rid))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (k TEXT PRIMARY KEY NOT NULL, v INTEGER NOT NULL)")
        FileSync.installState(db)
        db.execSQL("INSERT OR IGNORE INTO sync_state (k, v) VALUES ('applying', 0)")
        // A crash half way through applying would otherwise leave every later change unrecorded.
        db.execSQL("UPDATE sync_state SET v = 0 WHERE k = 'applying'")
        val quiet = "WHEN (SELECT v FROM sync_state WHERE k = 'applying') = 0"
        (Tables + FileSync.Tables).forEach { t ->
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

    private data class Pending(val seq: Long, val table: String, val id: Long, val op: String)

    private suspend fun pushAll() {
        while (pushOnce()) Unit
    }

    /** Hands one batch of the outbox to the server. False once there is nothing left to send. */
    private suspend fun pushOnce(): Boolean {
        val link = CompanyStore.current(app) ?: return false
        val db = sql()
        val pending = mutableListOf<Pending>()
        val held = heldBack()
        val only = if (held.isEmpty()) "" else "WHERE $held"
        db.query("SELECT seq, tbl, rid, op FROM $OUTBOX $only ORDER BY seq LIMIT $PUSH_BATCH").use { c ->
            while (c.moveToNext()) pending += Pending(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3))
        }
        if (pending.isEmpty()) return false
        // The last word on each row is the one that counts; the row is read as it stands now.
        val rows = pending.groupBy { it.table to it.id }.mapNotNull { (key, changes) ->
            val (table, id) = key
            if (table !in Tables && table !in FileSync.Tables) return@mapNotNull null
            val data = if (changes.last().op == "d") null else readRow(db, table, id)
            RemoteRow(table = table, id = id, data = data, deleted = data == null)
        }
        val answer = if (rows.isEmpty()) null else CompanyApi.push(link, rows)
        db.execSQL("DELETE FROM $OUTBOX WHERE seq <= ?" + if (held.isEmpty()) "" else " AND $held", arrayOf<Any>(pending.last().seq))
        if (answer != null) {
            noteAnswer(answer.me, answer.companyName)
            // Not this phone's to change: the company's version goes back where it was.
            if (answer.refused.isNotEmpty()) apply(answer.refused)
        }
        refreshWaiting()
        return true
    }

    private fun refreshWaiting() {
        val waiting = runCatching {
            val held = heldBack()
            sql().query("SELECT count(DISTINCT tbl || '/' || rid) FROM $OUTBOX" + if (held.isEmpty()) "" else " WHERE $held")
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }.getOrDefault(0)
        _status.update { it.copy(waiting = waiting) }
    }

    /**
     * A row as JSON of its columns. A file on this phone means nothing on another one, so it
     * stays; a photo's or plan's travels on its own, by its key (see [FileSync]).
     */
    private fun readRow(db: SupportSQLiteDatabase, table: String, id: Long): String? =
        db.query("SELECT * FROM `$table` WHERE id = ?", arrayOf<Any>(id)).use { c ->
            if (!c.moveToFirst()) return null
            val json = JSONObject()
            val withFile = table in FileSync.Tables
            for (i in 0 until c.columnCount) {
                val name = c.getColumnName(i)
                if (withFile && name == "uri") continue
                when (c.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> json.put(name, c.getLong(i))
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).takeIf { it.isFinite() }?.let { json.put(name, it) }
                    Cursor.FIELD_TYPE_STRING -> {
                        val text = c.getString(i)
                        if (!text.startsWith("file:") && !text.startsWith("content:")) json.put(name, text)
                    }
                    Cursor.FIELD_TYPE_NULL -> json.put(name, JSONObject.NULL)
                }
            }
            json.toString()
        }

    // ---- In ----------------------------------------------------------------------------------

    private suspend fun pullAll(onProgress: (Int) -> Unit = {}, takingLeave: Boolean = false) {
        var since = mark()
        var received = 0
        while (true) {
            val link = CompanyStore.current(app) ?: return
            val page = CompanyApi.pull(link, since)
            if (page.movedTo != null && !takingLeave) throw CompanyProblem("moved", page.movedTo)
            // Another phone is sharing photos: the server keeps files now, and this one should too.
            if (!filesOn() && page.rows.any { it.table in FileSync.Tables }) filesCheckedAt = 0L
            noteAnswer(page.me, page.companyName)
            // What this phone sent comes back numbered like everybody else's; it already has it.
            apply(page.rows.filter { it.device == null || it.device != link.device })
            received += page.rows.size
            onProgress(received)
            since = page.next
            setMark(since)
            if (!page.more) break
        }
        lastPullAt = System.currentTimeMillis()
    }

    /** Who the server says this phone is, and what it may do — written down every time. */
    private suspend fun noteAnswer(me: Me, companyName: String) {
        val before = CompanyStore.current(app) ?: return
        CompanyStore.update(app) {
            it.copy(
                name = me.name,
                owner = me.owner,
                perms = if (me.owner) Perms.All else me.perms,
                companyName = companyName.ifBlank { it.companyName },
                lastContactAt = System.currentTimeMillis(),
            )
        }
        if (before.owner != me.owner) {
            (app as? MixMasterApp)?.container?.userPrefs?.setRole(if (me.owner) Role.EMPLOYER else Role.WORKER)
        }
    }

    /**
     * Writes what arrived, parents first, with the triggers held off. A row whose parent has not
     * arrived yet (a room before its floor) waits in the inbox and is tried again next time.
     */
    private fun apply(incoming: List<RemoteRow>) {
        // Photos and plans are taken in even before this phone shares its own: a server that sent
        // them keeps their files, and a row passed over now would never be sent again.
        val order = (Tables + FileSync.Tables).withIndex().associate { it.value to it.index }
        val waiting = unsentKeys()
        applying {
            val db = sql()
            val held = mutableListOf<RemoteRow>()
            db.query("SELECT tbl, rid, data, deleted FROM $INBOX").use { c ->
                while (c.moveToNext()) {
                    held += RemoteRow(c.getString(0), c.getLong(1), if (c.isNull(2)) null else c.getString(2), c.getInt(3) != 0)
                }
            }
            var todo = (held + incoming).associateBy { it.key }.values
                .filter { it.table in order && it.key !in waiting }
                .sortedBy { order[it.table] ?: Int.MAX_VALUE }
            while (todo.isNotEmpty()) {
                val stuck = todo.filterNot { applyOne(db, it) }
                if (stuck.size == todo.size) break
                todo = stuck
            }
            db.execSQL("DELETE FROM $INBOX")
            todo.forEach { row ->
                db.execSQL(
                    "INSERT OR REPLACE INTO $INBOX (tbl, rid, data, deleted) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(row.table, row.id, row.data, if (row.deleted) 1 else 0),
                )
            }
        }
    }

    /** Rows changed here that have not gone out yet: this phone's version is the newer one. */
    private fun unsentKeys(): Set<String> = runCatching {
        sql().query("SELECT tbl, rid FROM $OUTBOX").use { c ->
            buildSet { while (c.moveToNext()) add("${c.getString(0)}/${c.getLong(1)}") }
        }
    }.getOrDefault(emptySet())

    /** Update if the row is here, insert if not — never replace, which would take its children with it. */
    private fun applyOne(db: SupportSQLiteDatabase, row: RemoteRow): Boolean = try {
        val withFile = row.table in FileSync.Tables
        if (row.deleted) {
            // A photo taken off the job on another phone takes this phone's copy of it too.
            if (withFile) {
                db.query("SELECT uri FROM `${row.table}` WHERE id = ?", arrayOf<Any>(row.id)).use { c ->
                    if (c.moveToFirst()) FileSync.forgetLocal(app, c.getString(0))
                }
            }
            db.delete("`${row.table}`", "id = ?", arrayOf<Any>(row.id))
        } else {
            val known = columnsOf(db, row.table)
            val json = JSONObject(row.data ?: "{}")
            val values = ContentValues()
            json.keys().forEach { name ->
                if (name !in known || name == "id") return@forEach
                // Where the file is on this phone is this phone's own business.
                if (withFile && name == "uri") return@forEach
                when (val value = json.opt(name)) {
                    null, JSONObject.NULL -> values.putNull(name)
                    is Int -> values.put(name, value.toLong())
                    is Long -> values.put(name, value)
                    is Double -> values.put(name, value)
                    is Boolean -> values.put(name, if (value) 1L else 0L)
                    is String -> values.put(name, value)
                    is Number -> values.put(name, value.toDouble())
                    else -> values.put(name, value.toString())
                }
            }
            // An update with nothing in it is refused outright rather than doing nothing.
            val updated = if (values.size() == 0) 0 else db.update("`${row.table}`", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf<Any>(row.id))
            if (updated == 0) {
                values.put("id", row.id)
                // Not here yet: FileSync fetches it and fills this in.
                if (withFile) values.put("uri", "")
                db.insert("`${row.table}`", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
        true
    } catch (e: SQLiteConstraintException) {
        false
    } catch (e: org.json.JSONException) {
        // Not something this app can read. Dropped rather than held: it will not get any better.
        true
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> = synchronized(columns) {
        columns.getOrPut(table) {
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val nameAt = c.getColumnIndex("name")
                buildSet { while (c.moveToNext()) add(c.getString(nameAt)) }
            }
        }
    }

    /**
     * Runs [block] in one transaction with the triggers told to look away, so rows written for
     * the company's sake are not recorded as this phone's own changes.
     */
    internal fun applying(block: () -> Unit) {
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

    // ---- Where the phone was left ------------------------------------------------------------

    private fun marks() = app.getSharedPreferences(MARKS, Context.MODE_PRIVATE)
    private fun mark(): Long = marks().getLong("since", 0L)
    private fun setMark(since: Long) {
        marks().edit().putLong("since", since).commit()
    }

    /** Also forgets whether the server keeps files: the next server is asked afresh. */
    private fun clearMarks() {
        marks().edit().clear().commit()
        lastPullAt = 0L
        filesCheckedAt = 0L
        _filesShared.value = false
        runCatching { FileSync.forgetServer(sql()) }
    }
}
