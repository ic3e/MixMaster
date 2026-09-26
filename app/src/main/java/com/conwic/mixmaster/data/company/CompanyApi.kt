package com.conwic.mixmaster.data.company

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException

/**
 * Something the server said no to, or could not be reached to say anything.
 *
 * [code] is the server's own word for it ("revoked", "bad_code"…) or one of the app's:
 * "offline" (no answer at all), "not_server" (an answer, but not from a MixMaster server),
 * "https" (the address is not a secure one).
 *
 * [to] is set when the company has moved: the server's word for where it went. [detail] is what
 * answered instead of a server — a page's title — for the person trying to find out why.
 */
class CompanyProblem(val code: String, val to: String? = null, val detail: String? = null) : Exception(code)

data class Me(val id: Long, val name: String, val owner: Boolean, val perms: Perms)

data class Hello(
    val kind: ServerKind,
    val claimed: Boolean,
    val companyId: String?,
    val companyName: String?,
    /** Where the company went, when this server has handed it on. */
    val movedTo: String?,
)

data class Joined(val token: String, val companyId: String, val companyName: String, val me: Me)

data class Person(
    val id: Long,
    val name: String,
    val owner: Boolean,
    val perms: Perms,
    /** "pending" (a code is out and not used yet), "active" or "left". */
    val status: String,
    val code: String?,
    val seenAt: Long?,
)

/** One row as it travels: its columns as the JSON the phone that wrote it made of them. */
data class RemoteRow(val table: String, val id: Long, val data: String?, val deleted: Boolean, val device: String? = null) {
    val key: String get() = "$table/$id"
}

data class Pulled(
    val me: Me,
    val companyName: String,
    val rows: List<RemoteRow>,
    val next: Long,
    val more: Boolean,
    val movedTo: String?,
)

data class Pushed(val me: Me, val companyName: String, val refused: List<RemoteRow>)

/**
 * The code a worker is given: eight letters and digits, and where the company's server is.
 *
 * Written as `K7MQ-3XPD@conwic.fi/mixmaster/api.php`, so the one thing to copy carries both, and
 * nothing about the server is built into the app — the company can move, and the next codes
 * simply point somewhere else.
 */
data class AccessCode(val code: String, val server: String) {
    val shown: String get() = "${code.take(4)}-${code.drop(4)}@${server.removePrefix("https://")}"

    companion object {
        /** What a sentence can leave stuck to the end of an address pasted out of a message. */
        private const val TrailingPunctuation = ".,;)\"'"

        // The form the app writes, found anywhere in a pasted message.
        private val Written = Regex("""\b([2-9A-HJ-NP-Z]{4})-([2-9A-HJ-NP-Z]{4})@(\S+)""")
        // Typed in by hand: any case, dash or none — but then it has to be all there is.
        private val Typed = Regex("""^\s*([A-Za-z0-9]{4})[\s-]*([A-Za-z0-9]{4})\s*@\s*(\S+)\s*$""")

        fun parse(text: String): AccessCode? {
            val match = Written.find(text) ?: Typed.find(text) ?: return null
            val server = match.groupValues[3].trimEnd { it in TrailingPunctuation }
                .removePrefix("https://").removePrefix("http://")
            if (server.isBlank() || !server.contains('.')) return null
            return AccessCode(code = (match.groupValues[1] + match.groupValues[2]).uppercase(), server = "https://$server")
        }
    }
}

object ServerAddress {
    /**
     * What was typed, as the addresses worth trying, most likely first: "conwic.fi" finds the
     * server in conwic.fi/mixmaster/api.php, and a Google web app address is taken as it is.
     */
    fun candidates(typed: String): List<String> {
        var text = typed.trim().replace(" ", "")
        if (text.isEmpty()) return emptyList()
        text = text.removePrefix("http://").removePrefix("https://")
        text = "https://$text"
        val url = runCatching { URL(text) }.getOrNull() ?: return emptyList()
        if (url.host.isNullOrBlank() || !url.host.contains('.')) return emptyList()
        val path = url.path.orEmpty()
        val base = text.substringBefore('?').trimEnd('/')
        return when {
            url.host == "script.google.com" -> listOf(text)
            path.endsWith(".php") -> listOf(text)
            path.isEmpty() || path == "/" -> listOf("$base/mixmaster/api.php", "$base/api.php")
            else -> listOf("$base/api.php", text)
        }
    }
}

/**
 * The company's server, whichever kind it is: one address, JSON both ways.
 *
 * Plain HttpURLConnection, so the app carries no networking library for the sake of a handful
 * of small requests. Redirects are followed by hand: a Google web app answers a POST by pointing
 * at the result, which has to be fetched with a GET, and leaving that to the platform is leaving
 * it to whichever HTTP stack a given phone happens to ship.
 */
object CompanyApi {

    private const val PROTOCOL = 1

    private suspend fun call(server: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        body.put("v", PROTOCOL)
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        var url = runCatching { URL(server) }.getOrNull() ?: throw CompanyProblem("not_server")
        val asked = url
        if (url.protocol != "https") throw CompanyProblem("https")
        var post = true
        var hops = 0
        while (true) {
            val conn = try {
                url.openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw CompanyProblem("offline")
            }
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("Accept", "application/json")
                if (post) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setFixedLengthStreamingMode(bytes.size)
                    conn.outputStream.use { it.write(bytes) }
                }
                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw CompanyProblem("not_server")
                    url = URL(url, location)
                    if (url.protocol != "https") throw CompanyProblem("https")
                    if (status != 307 && status != 308) post = false
                    if (++hops > 5) throw CompanyProblem("not_server")
                    continue
                }
                val stream = if (status >= 400) conn.errorStream else conn.inputStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                    ?: throw CompanyProblem(diagnose(asked, url, status, text), detail = pageTitle(text))
                if (!json.optBoolean("ok", false)) {
                    throw CompanyProblem(json.optString("error", "server").ifBlank { "server" }, json.text("to"))
                }
                return@withContext json
            } catch (e: CompanyProblem) {
                throw e
            } catch (e: SSLException) {
                throw CompanyProblem("https")
            } catch (e: IOException) {
                throw CompanyProblem("offline")
            } finally {
                conn.disconnect()
            }
        }
        // Not reached: the loop above only ever returns or throws.
        throw CompanyProblem("offline")
    }

    /**
     * Something answered, but not a MixMaster server: what it most likely was, so whoever is
     * setting a server up is told what to change rather than only that something is wrong. The
     * Google cases are the ones a first setup runs into, and each has its own fix.
     */
    private fun diagnose(asked: URL, answered: URL, status: Int, text: String): String {
        val google = asked.host == "script.google.com"
        return when {
            google && asked.path.endsWith("/dev") -> "google_dev"
            google && !asked.path.endsWith("/exec") -> "google_editor"
            asked.host == "docs.google.com" || asked.host == "drive.google.com" -> "google_editor"
            answered.host == "accounts.google.com" || text.contains("accounts.google.com/v3/signin") ||
                text.contains("ServiceLogin") -> "google_access"
            text.contains("Script function not found") -> "google_old_version"
            text.contains("Authorization is required", ignoreCase = true) -> "google_authorize"
            status == 404 -> "not_found"
            else -> "not_server"
        }
    }

    /** A page's title, or the start of its text: enough to tell a sign-in page from an error. */
    private fun pageTitle(text: String): String? {
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(text)?.groupValues?.get(1)
        val plain = (title ?: text.take(400).replace(Regex("<[^>]*>"), " "))
            .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ").trim()
        return plain.take(100).ifBlank { null }
    }

    private fun signed(link: CompanyLink, action: String): JSONObject = JSONObject()
        .put("a", action)
        .put("token", link.token)
        .put("company", link.companyId)
        .put("device", link.device)

    // ---- Before there is a company on this phone ---------------------------------------------

    suspend fun hello(server: String): Hello {
        val json = call(server, JSONObject().put("a", "hello"))
        if (json.optString("app") != "mixmaster") throw CompanyProblem("not_server")
        return Hello(
            kind = ServerKind.of(json.optString("kind")),
            claimed = json.optBoolean("claimed", false),
            companyId = json.optJSONObject("company")?.text("id"),
            companyName = json.optJSONObject("company")?.text("name"),
            movedTo = json.text("moved_to"),
        )
    }

    suspend fun setup(server: String, companyName: String, name: String, device: String): Joined =
        joined(
            call(
                server,
                JSONObject().put("a", "setup").put("company_name", companyName).put("name", name).put("device", device),
            ),
        )

    suspend fun join(server: String, code: String, device: String): Joined =
        joined(call(server, JSONObject().put("a", "join").put("code", code).put("device", device)))

    private fun joined(json: JSONObject): Joined {
        val company = json.getJSONObject("company")
        return Joined(
            token = json.getString("token"),
            companyId = company.getString("id"),
            companyName = company.optString("name"),
            me = readMe(json.getJSONObject("me")),
        )
    }

    // ---- Keeping in step ---------------------------------------------------------------------

    suspend fun pull(link: CompanyLink, since: Long): Pulled {
        val json = call(link.server, signed(link, "pull").put("since", since))
        val rows = json.optJSONArray("rows") ?: JSONArray()
        return Pulled(
            me = readMe(json.getJSONObject("me")),
            companyName = json.optJSONObject("company")?.optString("name") ?: link.companyName,
            rows = (0 until rows.length()).mapNotNull { readRow(rows.getJSONObject(it)) },
            next = json.optLong("next", since),
            more = json.optBoolean("more", false),
            movedTo = json.text("moved_to"),
        )
    }

    suspend fun push(link: CompanyLink, rows: List<RemoteRow>): Pushed {
        val out = JSONArray()
        rows.forEach { row ->
            val item = JSONObject().put("t", row.table).put("id", row.id.toString())
            if (row.deleted || row.data == null) item.put("x", true) else item.put("d", row.data)
            out.put(item)
        }
        val json = call(link.server, signed(link, "push").put("rows", out))
        val refused = json.optJSONArray("refused") ?: JSONArray()
        return Pushed(
            me = readMe(json.getJSONObject("me")),
            companyName = json.optJSONObject("company")?.optString("name") ?: link.companyName,
            refused = (0 until refused.length()).mapNotNull { readRow(refused.getJSONObject(it)) },
        )
    }

    suspend fun leave(link: CompanyLink) {
        call(link.server, signed(link, "leave"))
    }

    // ---- Moving the company to another server -----------------------------------------------

    /**
     * The company's list of people as the server keeps it, keys and all (as hashes, which let
     * nobody in). Kept on the owner's phone, so a company whose server is gone can still move.
     */
    suspend fun exportPeople(link: CompanyLink): String =
        (call(link.server, signed(link, "export")).optJSONArray("people") ?: JSONArray()).toString()

    /** An empty server takes the company in: same company, same people, same keys. */
    suspend fun adopt(server: String, link: CompanyLink, people: String) {
        val company = JSONObject().put("id", link.companyId).put("name", link.companyName)
        call(
            server,
            JSONObject().put("a", "adopt").put("token", link.token).put("company", company).put("people", JSONArray(people)),
        )
    }

    /** Closes the old server for writing and points everybody at [to]; an empty [to] opens it again. */
    suspend fun moveOut(link: CompanyLink, to: String) {
        call(link.server, signed(link, "move_out").put("to", to))
    }

    /** Everything has arrived: the rest of the crew can write to the new server. */
    suspend fun moveDone(link: CompanyLink) {
        call(link.server, signed(link, "move_done"))
    }

    // ---- The owner's list of people ----------------------------------------------------------

    suspend fun people(link: CompanyLink): List<Person> = readPeople(call(link.server, signed(link, "people")))

    suspend fun savePerson(link: CompanyLink, id: Long, name: String, owner: Boolean, perms: Perms): Pair<Person, List<Person>> {
        val person = JSONObject().put("name", name).put("owner", owner).put("perms", permsJson(perms))
        if (id > 0L) person.put("id", id)
        val json = call(link.server, signed(link, "person_save").put("person", person))
        return readPerson(json.getJSONObject("person")) to readPeople(json)
    }

    suspend fun newCode(link: CompanyLink, id: Long): Pair<Person, List<Person>> {
        val json = call(link.server, signed(link, "person_code").put("id", id))
        return readPerson(json.getJSONObject("person")) to readPeople(json)
    }

    suspend fun removePerson(link: CompanyLink, id: Long): List<Person> =
        readPeople(call(link.server, signed(link, "person_remove").put("id", id)))

    // ---- Reading the answers -----------------------------------------------------------------

    private fun readPeople(json: JSONObject): List<Person> {
        val list = json.optJSONArray("people") ?: JSONArray()
        return (0 until list.length()).map { readPerson(list.getJSONObject(it)) }
    }

    private fun readPerson(json: JSONObject) = Person(
        id = json.optLong("id"),
        name = json.optString("name"),
        owner = json.optBoolean("owner", false),
        perms = readPerms(json.optJSONObject("perms")),
        status = json.optString("status"),
        code = json.optString("code").takeIf { !json.isNull("code") && it.isNotBlank() },
        seenAt = json.optLong("seen").takeIf { !json.isNull("seen") && it > 0L },
    )

    private fun readMe(json: JSONObject) = Me(
        id = json.optLong("id"),
        name = json.optString("name"),
        owner = json.optBoolean("owner", false),
        perms = readPerms(json.optJSONObject("perms")),
    )

    private fun readPerms(json: JSONObject?): Perms = if (json == null) Perms() else Perms(
        catalogue = json.optBoolean("catalogue", false),
        projects = json.optBoolean("projects", false),
        warehouse = json.optBoolean("warehouse", false),
        site = json.optBoolean("site", false),
    )

    private fun permsJson(perms: Perms): JSONObject = JSONObject()
        .put("catalogue", perms.catalogue)
        .put("projects", perms.projects)
        .put("warehouse", perms.warehouse)
        .put("site", perms.site)

    /** A text field, or null when it is missing, null or empty. */
    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun readRow(json: JSONObject): RemoteRow? {
        val id = json.optString("id").toLongOrNull() ?: return null
        val deleted = json.optBoolean("x", false)
        return RemoteRow(
            table = json.optString("t"),
            id = id,
            data = if (deleted || json.isNull("d")) null else json.optString("d"),
            deleted = deleted,
            device = if (json.isNull("dev")) null else json.optString("dev"),
        )
    }
}
