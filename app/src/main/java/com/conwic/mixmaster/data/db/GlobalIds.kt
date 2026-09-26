package com.conwic.mixmaster.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * New rows get ids no other phone will hand out.
 *
 * SQLite numbers rows 1, 2, 3… on every phone, which is fine for one phone and a collision the
 * moment two of them share a company: the employer's "product 24" and a worker's "note 24" are
 * different things with the same number, and every link between rows (a room's floor, a coat's
 * recipe) is made of those numbers.
 *
 * So a new row's id is the time it was made, in milliseconds, with twenty random bits beside it:
 * ids still count upwards the way the app sorts by them (a recipe's coats, the projects list), and
 * two phones would have to make a row in the same millisecond and draw the same one-in-a-million
 * number to clash. Never below the table's last id either, so two rows made in the same
 * millisecond on one phone still come out in the order they were made.
 *
 * Done where Room hands SQLite its insert: every entity insert Room generates writes the id as
 * `nullif(?, 0)` — "no id given, number it yourself" — and that is swapped for the expression
 * below. Rows that already exist keep their ids; an insert that brings its own id keeps it.
 */
class GlobalIdOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        GlobalIdOpenHelper(delegate.create(configuration))
}

private class GlobalIdOpenHelper(
    private val delegate: SupportSQLiteOpenHelper,
) : SupportSQLiteOpenHelper by delegate {
    override val writableDatabase: SupportSQLiteDatabase
        get() = GlobalIdDatabase(delegate.writableDatabase)

    override val readableDatabase: SupportSQLiteDatabase
        get() = GlobalIdDatabase(delegate.readableDatabase)
}

private class GlobalIdDatabase(
    private val delegate: SupportSQLiteDatabase,
) : SupportSQLiteDatabase by delegate {
    override fun compileStatement(sql: String): SupportSQLiteStatement =
        delegate.compileStatement(withGlobalIds(sql))
}

private val InsertInto = Regex("""^\s*INSERT\s+(?:OR\s+\w+\s+)?INTO\s+`?(\w+)`?""", RegexOption.IGNORE_CASE)
private const val RoomNoId = "nullif(?, 0)"

/** Room's insert with its "number it yourself" id swapped for a global one; anything else as it came. */
internal fun withGlobalIds(sql: String): String {
    if (!sql.contains(RoomNoId)) return sql
    val table = InsertInto.find(sql)?.groupValues?.get(1) ?: return sql
    return sql.replaceFirst(RoomNoId, "coalesce($RoomNoId, ${newIdExpression(table)})")
}

/**
 * Milliseconds since 1970, shifted up twenty bits, with twenty random bits in the gap — or one
 * past the table's highest id so far, whichever is larger. Good until the year 2248.
 */
internal fun newIdExpression(table: String): String =
    "max(" +
        "(CAST((julianday('now') - 2440587.5) * 86400000.0 AS INTEGER) << 20) | (random() & 1048575), " +
        "coalesce((SELECT seq FROM sqlite_sequence WHERE name = '$table'), 0) + 1" +
        ")"
