package com.conwic.mixmaster.data.company

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What one person may change. Everybody in the company can see everything. */
data class Perms(
    /** Products and recipes. */
    val catalogue: Boolean = false,
    /** Projects, floors, rooms and the coats on them. */
    val projects: Boolean = false,
    /** Stock counts, deliveries and orders. */
    val warehouse: Boolean = true,
    /** Mixes, notes and ticking off tasks. */
    val site: Boolean = true,
) {
    companion object {
        val All = Perms(catalogue = true, projects = true, warehouse = true, site = true)
    }
}

/** The kind of server a company keeps its data on. The app talks to both the same way. */
enum class ServerKind(val key: String) {
    WEBSITE("website"),
    GOOGLE("google"),
    ;

    companion object {
        fun of(key: String?): ServerKind = entries.firstOrNull { it.key == key } ?: WEBSITE
    }
}

/** This phone's place in a company: where the data is kept, who it is there, and what it may do. */
data class CompanyLink(
    val server: String,
    val kind: ServerKind,
    val companyId: String,
    val companyName: String,
    /** The phone's key to the server. Only this phone has it; a new access code makes a new one. */
    val token: String,
    /** Made fresh each time the phone joins, so a phone can tell its own changes coming back. */
    val device: String,
    val personId: Long,
    val name: String,
    val owner: Boolean,
    val perms: Perms,
    /** The last time the server answered. */
    val lastContactAt: Long,
) {
    /** What is left of the address for the eye, without the https:// in front. */
    val serverShown: String get() = server.removePrefix("https://")
}

/**
 * The company link, kept beside the database rather than in it: a restored backup or a wiped
 * database must not take the phone's key with it, or bring back somebody else's.
 */
object CompanyStore {

    private const val FILE = "mixmaster_company"
    private const val K_SERVER = "server"
    private const val K_KIND = "kind"
    private const val K_COMPANY_ID = "companyId"
    private const val K_COMPANY_NAME = "companyName"
    private const val K_TOKEN = "token"
    private const val K_DEVICE = "device"
    private const val K_PERSON = "personId"
    private const val K_NAME = "name"
    private const val K_OWNER = "owner"
    private const val K_P_CATALOGUE = "pCatalogue"
    private const val K_P_PROJECTS = "pProjects"
    private const val K_P_WAREHOUSE = "pWarehouse"
    private const val K_P_SITE = "pSite"
    private const val K_CONTACT = "lastContactAt"
    private const val K_ENDED = "endedCompany"

    private val flow = MutableStateFlow<CompanyLink?>(null)
    private val ended = MutableStateFlow<String?>(null)
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            flow.value = load(context)
            ended.value = prefs(context).getString(K_ENDED, null)
            loaded = true
        }
    }

    fun link(context: Context): StateFlow<CompanyLink?> {
        ensureLoaded(context)
        return flow.asStateFlow()
    }

    fun current(context: Context): CompanyLink? {
        ensureLoaded(context)
        return flow.value
    }

    private fun load(context: Context): CompanyLink? {
        val p = prefs(context)
        val token = p.getString(K_TOKEN, null) ?: return null
        val server = p.getString(K_SERVER, null) ?: return null
        return CompanyLink(
            server = server,
            kind = ServerKind.of(p.getString(K_KIND, null)),
            companyId = p.getString(K_COMPANY_ID, "").orEmpty(),
            companyName = p.getString(K_COMPANY_NAME, "").orEmpty(),
            token = token,
            device = p.getString(K_DEVICE, "").orEmpty(),
            personId = p.getLong(K_PERSON, 0L),
            name = p.getString(K_NAME, "").orEmpty(),
            owner = p.getBoolean(K_OWNER, false),
            perms = Perms(
                catalogue = p.getBoolean(K_P_CATALOGUE, false),
                projects = p.getBoolean(K_P_PROJECTS, false),
                warehouse = p.getBoolean(K_P_WAREHOUSE, true),
                site = p.getBoolean(K_P_SITE, true),
            ),
            lastContactAt = p.getLong(K_CONTACT, 0L),
        )
    }

    /**
     * Written with commit(), not apply(): a join is followed by wiping and refilling the database,
     * and a phone that dies in the middle must come back still knowing which company it was in.
     */
    fun save(context: Context, link: CompanyLink) {
        ensureLoaded(context)
        prefs(context).edit()
            .putString(K_SERVER, link.server)
            .putString(K_KIND, link.kind.key)
            .putString(K_COMPANY_ID, link.companyId)
            .putString(K_COMPANY_NAME, link.companyName)
            .putString(K_TOKEN, link.token)
            .putString(K_DEVICE, link.device)
            .putLong(K_PERSON, link.personId)
            .putString(K_NAME, link.name)
            .putBoolean(K_OWNER, link.owner)
            .putBoolean(K_P_CATALOGUE, link.perms.catalogue)
            .putBoolean(K_P_PROJECTS, link.perms.projects)
            .putBoolean(K_P_WAREHOUSE, link.perms.warehouse)
            .putBoolean(K_P_SITE, link.perms.site)
            .putLong(K_CONTACT, link.lastContactAt)
            .commit()
        flow.value = link
    }

    fun update(context: Context, change: (CompanyLink) -> CompanyLink) {
        val now = current(context) ?: return
        save(context, change(now))
    }

    fun clear(context: Context) {
        ensureLoaded(context)
        val keepEnded = prefs(context).getString(K_ENDED, null)
        prefs(context).edit().clear().apply { if (keepEnded != null) putString(K_ENDED, keepEnded) }.commit()
        flow.value = null
    }

    // ---- The note left when a company cut this phone off ---------------------------------------

    /** The company whose data was taken off this phone, until somebody has read that it was. */
    fun endedNotice(context: Context): StateFlow<String?> {
        ensureLoaded(context)
        return ended.asStateFlow()
    }

    fun setEnded(context: Context, companyName: String) {
        ensureLoaded(context)
        prefs(context).edit().putString(K_ENDED, companyName).commit()
        ended.value = companyName
    }

    fun dismissEnded(context: Context) {
        ensureLoaded(context)
        prefs(context).edit().remove(K_ENDED).apply()
        ended.value = null
    }
}
