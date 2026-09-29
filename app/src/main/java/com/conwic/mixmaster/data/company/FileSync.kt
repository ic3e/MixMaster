package com.conwic.mixmaster.data.company

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/**
 * The files behind the photos and plans on a job, carried through the company's server.
 *
 * The rows travel with everything else ([SyncEngine]); each names its file by `fileKey`. This
 * sends a file up from the phone that has it and fetches it down to the ones that don't, a part
 * at a time: a PDF of a whole building can run to tens of megabytes, and neither a web host nor
 * Google takes that in one request.
 *
 * A photo comes down as soon as its row does — it is what the crew opens a job to look at. A plan
 * comes down the same way, so it is there on a site with no signal; one tapped before it has
 * arrived is fetched there and then ([fetchNow]).
 *
 * Nothing here runs until the server says it keeps files. Until then photos and plans stay on
 * the phone they were added on, as they always did.
 */
object FileSync {

    /** Where the two kinds live in the database: rows first, parents before them in [SyncEngine.Tables]. */
    val Tables = listOf("photos", "blueprints")

    /** The servers' part size and limit (MM_FILE_PART, MM_FILE_MAX): they refuse anything else. */
    private const val PART = 1536 * 1024
    private const val MAX = 40L * 1024 * 1024

    /** What this phone knows about the server's copy of each file, by key. */
    internal const val STATE = "sync_files"
    private const val HAVE = 1
    /** Could not be sent, and never will be — gone from the phone, or too big. Not tried again. */
    private const val GAVE_UP = 2

    /** Plans that came from the company; photos go in the photos folder, beside the ones taken here. */
    const val PLANS_DIR = "blueprints"
    private const val PHOTOS_DIR = "photos"

    /** Answers that end a round rather than one file's turn: nothing else will get through either. */
    private val Stoppers = setOf("offline", "moved", "revoked", "https", "not_server")

    /** A file asked for before anybody had sent it: when to ask again, and how long the wait was. */
    private val notThere = mutableMapOf<String, Pair<Long, Long>>()
    private var sweptAt = 0L

    private val _waiting = MutableStateFlow(0)
    /** Photos and plans still to go up from this phone or come down to it. */
    val waiting: StateFlow<Int> = _waiting.asStateFlow()

    /** A key for a new photo or plan: 32 hex digits, which is what the servers accept. */
    fun newKey(): String = UUID.randomUUID().toString().replace("-", "")

    internal fun installState(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS $STATE (k TEXT PRIMARY KEY NOT NULL, state INTEGER NOT NULL)")
    }

    /** A different server: none of its files are known yet. What this phone holds is offered again. */
    internal fun forgetServer(db: SupportSQLiteDatabase) {
        runCatching { db.execSQL("DELETE FROM $STATE") }
        synchronized(notThere) { notThere.clear() }
    }

    /**
     * One round: what this phone has that the server hasn't goes up, what it lacks comes down,
     * and files nothing points at any more are cleared away. Stops at the first sign of no signal;
     * the next round starts again from what is still missing.
     */
    internal suspend fun transfer(app: Context, db: SupportSQLiteDatabase) = withContext(Dispatchers.IO) {
        val link = CompanyStore.current(app) ?: return@withContext
        try {
            sendUp(app, link, db)
            fetchDown(app, link, db)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No signal, the company moving, this phone taken off — the rows' side deals with all
            // of that — or a full phone. The next round tries again.
        } finally {
            countWaiting(db)
        }
        if (System.currentTimeMillis() - sweptAt > 6 * 60 * 60 * 1000L) {
            sweep(app, db)
            sweptAt = System.currentTimeMillis()
        }
    }

    // ---- Up ----------------------------------------------------------------------------------

    private class Held(val key: String, val uri: String, val mime: String)

    private fun held(db: SupportSQLiteDatabase): List<Held> = buildList {
        Tables.forEach { table ->
            val mime = if (table == "photos") "'image/jpeg'" else "mimeType"
            db.query(
                "SELECT fileKey, uri, $mime FROM `$table` WHERE fileKey != '' AND uri != '' " +
                    "AND fileKey NOT IN (SELECT k FROM $STATE)",
            ).use { c ->
                while (c.moveToNext()) add(Held(c.getString(0), c.getString(1), c.getString(2).orEmpty()))
            }
        }
    }.distinctBy { it.key }

    private suspend fun sendUp(app: Context, link: CompanyLink, db: SupportSQLiteDatabase) {
        val unknown = held(db)
        if (unknown.isEmpty()) return
        // Asked first: after a move, every phone holds files the new server may already have
        // from another one, and a list is one request where sending them is dozens.
        val missing = mutableSetOf<String>()
        unknown.chunked(500).forEach { chunk -> missing += CompanyApi.fileHas(link, chunk.map { it.key }) }
        unknown.filter { it.key !in missing }.forEach { mark(db, it.key, HAVE) }
        unknown.filter { it.key in missing }.forEach { file ->
            try {
                mark(db, file.key, if (upload(app, link, file)) HAVE else GAVE_UP)
            } catch (p: CompanyProblem) {
                when (p.code) {
                    // Not this phone's to send, or not a file the server will take: asking again won't change it.
                    "not_allowed", "bad_request" -> mark(db, file.key, GAVE_UP)
                    else -> throw p
                }
            }
        }
    }

    /**
     * Sends one file, a part at a time. False when there is nothing to send: the file has gone
     * from the phone, or it is bigger than the server takes.
     */
    private suspend fun upload(app: Context, link: CompanyLink, held: Held): Boolean {
        val source = Uri.parse(held.uri)
        val copied = source.scheme != "file"
        // A plan is a picker's content:// link: copied out first, so its size is known and each
        // part can be read from where it starts.
        val file = if (copied) copyToCache(app, source, held.key) else source.path?.let { File(it) }
        try {
            if (file == null || !file.isFile || file.length() <= 0L || file.length() > MAX) return false
            val size = file.length()
            val parts = ((size + PART - 1) / PART).toInt()
            var done = false
            RandomAccessFile(file, "r").use { raf ->
                for (part in 0 until parts) {
                    val bytes = ByteArray(minOf(PART.toLong(), size - part.toLong() * PART).toInt())
                    raf.seek(part.toLong() * PART)
                    raf.readFully(bytes)
                    done = CompanyApi.filePut(link, held.key, part, parts, size, held.mime, bytes)
                }
            }
            // Not whole after the last part means a part went missing on the way: sent again next round.
            if (!done) throw CompanyProblem("offline")
            return true
        } finally {
            if (copied) file?.delete()
        }
    }

    private fun copyToCache(app: Context, source: Uri, key: String): File? = runCatching {
        val dir = File(app.cacheDir, "upload").apply { mkdirs() }
        val file = File(dir, key)
        app.contentResolver.openInputStream(source)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        file
    }.getOrNull()

    // ---- Down --------------------------------------------------------------------------------

    private class Wanted(val table: String, val key: String, val name: String, val mime: String)

    private fun wanted(db: SupportSQLiteDatabase): List<Wanted> = buildList {
        Tables.forEach { table ->
            val columns = if (table == "photos") "'', 'image/jpeg'" else "name, mimeType"
            db.query("SELECT fileKey, $columns FROM `$table` WHERE uri = '' AND fileKey != ''").use { c ->
                while (c.moveToNext()) add(Wanted(table, c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty()))
            }
        }
    }.distinctBy { it.key }

    private suspend fun fetchDown(app: Context, link: CompanyLink, db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        wanted(db).forEach { item ->
            val until = synchronized(notThere) { notThere[item.key]?.first ?: 0L }
            if (until > now) return@forEach
            try {
                fetch(app, link, item)
            } catch (p: CompanyProblem) {
                // One file the server can't hand over mustn't hold up all the others behind it.
                if (p.code in Stoppers) throw p
                later(item.key)
            }
        }
    }

    /**
     * A plan tapped before it has come down: fetched now, while the person waits. Its file:// link,
     * or null when it can't be had yet — nobody has sent it, or there is no signal.
     */
    suspend fun fetchNow(context: Context, table: String, key: String, name: String, mime: String): String? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val link = CompanyStore.current(app) ?: return@withContext null
            runCatching { fetch(app, link, Wanted(table, key, name, mime)) }.getOrNull()
        }

    /** Fetches one file and points its rows at it. Its link, or null when the server hasn't got it yet. */
    private suspend fun fetch(app: Context, link: CompanyLink, item: Wanted): String? {
        val first = try {
            CompanyApi.fileGet(link, item.key, 0)
        } catch (p: CompanyProblem) {
            if (p.code != "not_found") throw p
            // Its row came before its file: whoever sent it may still be sending.
            later(item.key)
            return null
        }
        val dir = File(app.filesDir, if (item.table == "photos") PHOTOS_DIR else PLANS_DIR).apply { mkdirs() }
        val out = File(dir, "${item.key}.${extension(item, first.mime)}")
        // Written beside it under a name of its own, so a tap and a round fetching the same plan
        // at once can't write into one file.
        val part = File(dir, "${item.key}.${System.nanoTime()}.part")
        try {
            part.outputStream().use { output ->
                output.write(first.bytes)
                for (index in 1 until first.parts) output.write(CompanyApi.fileGet(link, item.key, index).bytes)
            }
            if (part.length() != first.size || !part.renameTo(out)) {
                later(item.key)
                return null
            }
        } finally {
            part.delete()
        }
        val stored = Uri.fromFile(out).toString()
        val db = SyncEngine.sqlFor(app)
        SyncEngine.applying {
            db.execSQL("UPDATE `${item.table}` SET uri = ? WHERE fileKey = ? AND uri = ''", arrayOf<Any>(stored, item.key))
        }
        mark(db, item.key, HAVE)
        synchronized(notThere) { notThere.remove(item.key) }
        return stored
    }

    /** Not to be had just now: asked for again in a minute, then less and less often, up to hourly. */
    private fun later(key: String) {
        synchronized(notThere) {
            val wait = ((notThere[key]?.second ?: 30_000L) * 2).coerceAtMost(60 * 60_000L)
            notThere[key] = (System.currentTimeMillis() + wait) to wait
        }
    }

    private fun extension(item: Wanted, served: String): String {
        if (item.table == "photos") return "jpg"
        val mime = item.mime.ifBlank { served }
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: item.name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
            ?: "bin"
    }

    // ---- Keeping count and keeping tidy ------------------------------------------------------

    private fun mark(db: SupportSQLiteDatabase, key: String, state: Int) {
        db.execSQL("INSERT OR REPLACE INTO $STATE (k, state) VALUES (?, ?)", arrayOf<Any>(key, state))
    }

    private fun countWaiting(db: SupportSQLiteDatabase) {
        _waiting.value = runCatching {
            Tables.sumOf { table ->
                db.query(
                    "SELECT count(*) FROM `$table` WHERE fileKey != '' AND " +
                        "(uri = '' OR fileKey NOT IN (SELECT k FROM $STATE))",
                ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            }
        }.getOrDefault(0)
    }

    /** Nothing waiting: the company has gone from this phone. */
    internal fun reset() {
        _waiting.value = 0
        synchronized(notThere) { notThere.clear() }
    }

    /**
     * A photo or plan taken off a job on another phone, or with the job, leaves its file here with
     * nothing pointing at it. Only files an hour old: a photo is copied in a moment before its row
     * is written, and must not be swept away in between.
     */
    private fun sweep(app: Context, db: SupportSQLiteDatabase) = runCatching {
        val used = buildSet {
            Tables.forEach { table ->
                db.query("SELECT uri FROM `$table` WHERE uri LIKE 'file:%'").use { c ->
                    while (c.moveToNext()) Uri.parse(c.getString(0)).lastPathSegment?.let { add(it) }
                }
            }
        }
        val old = System.currentTimeMillis() - 60 * 60_000L
        listOf(PHOTOS_DIR, PLANS_DIR).forEach { name ->
            File(app.filesDir, name).listFiles()?.forEach { file ->
                if (file.isFile && file.name !in used && file.lastModified() < old) file.delete()
            }
        }
    }

    /**
     * The file behind a photo or plan that is going, when it is one of the app's own copies. A
     * picker's link is the phone's file, not the app's, and is left alone.
     */
    internal fun forgetLocal(app: Context, stored: String) {
        runCatching {
            val uri = Uri.parse(stored)
            if (uri.scheme != "file") return
            val file = File(uri.path ?: return)
            val parent = file.parentFile?.canonicalPath ?: return
            val ours = listOf(PHOTOS_DIR, PLANS_DIR).map { File(app.filesDir, it).canonicalPath }
            if (parent in ours) file.delete()
        }
    }

    /** Joining or starting afresh: the files went with the rows. */
    internal fun clearFiles(app: Context) {
        listOf(PHOTOS_DIR, PLANS_DIR).forEach { File(app.filesDir, it).deleteRecursively() }
    }
}
