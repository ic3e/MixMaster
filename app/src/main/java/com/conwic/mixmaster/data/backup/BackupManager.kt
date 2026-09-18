package com.conwic.mixmaster.data.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.conwic.mixmaster.MainActivity
import com.conwic.mixmaster.data.db.AppDatabase
import com.conwic.mixmaster.data.db.DATABASE_NAME
import java.io.File

/**
 * Exports/imports the whole local Room database as a single file, so a contractor replacing
 * their phone can carry every product, project, room and task over to the new device — the
 * "export the existing db" requirement from the original brief.
 */
object BackupManager {

    fun export(context: Context, destination: Uri): Boolean = try {
        checkpoint(context)
        context.contentResolver.openOutputStream(destination)?.use { out ->
            context.getDatabasePath(DATABASE_NAME).inputStream().use { it.copyTo(out) }
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Overwrites the live database with a previously exported file, then restarts the app so
     * every Room/DataStore connection reopens cleanly against the restored data. */
    fun importAndRestart(context: Context, source: Uri): Boolean {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val restored = try {
            AppDatabase.closeAndReset()
            context.contentResolver.openInputStream(source)?.use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
            }
            // The freshly-copied main file has no matching WAL/SHM history — drop the stale
            // side files so SQLite doesn't try to replay them against it on next open.
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            true
        } catch (e: Exception) {
            false
        }
        if (restored) restartApp(context)
        return restored
    }

    private fun checkpoint(context: Context) {
        val db = AppDatabase.getInstance(context)
        db.query("PRAGMA wal_checkpoint(FULL)", null).close()
    }

    private fun restartApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        if (context is Activity) context.finish()
        Runtime.getRuntime().exit(0)
    }
}
