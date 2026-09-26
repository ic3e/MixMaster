package com.conwic.mixmaster.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A phone's place in a company: which project, which company in it, and as whom. */
data class CompanyLink(
    val settings: FirebaseSettings,
    val companyId: String,
    val companyName: String,
    /** "employer" or "worker", as the company's crew list has it. */
    val role: String,
) {
    val isEmployer: Boolean get() = role == RoleEmployer
}

const val RoleEmployer = "employer"
const val RoleWorker = "worker"

/**
 * Whether this phone shares a company, and with which.
 *
 * SharedPreferences, read synchronously, because it is needed before anything else starts: the
 * Firebase connection is made from it at app start, before the first screen or the database.
 */
object SyncStore {

    private const val FILE = "mixmaster_sync"
    private val flow = MutableStateFlow<CompanyLink?>(null)

    @Volatile
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun link(context: Context): StateFlow<CompanyLink?> {
        ensureLoaded(context)
        return flow.asStateFlow()
    }

    fun current(context: Context): CompanyLink? {
        ensureLoaded(context)
        return flow.value
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                flow.value = runCatching { load(context) }.getOrNull()
                loaded = true
            }
        }
    }

    private fun load(context: Context): CompanyLink? {
        val p = prefs(context)
        val companyId = p.getString("companyId", null)?.takeIf { it.isNotBlank() } ?: return null
        return CompanyLink(
            settings = FirebaseSettings(
                apiKey = p.getString("apiKey", "").orEmpty(),
                appId = p.getString("appId", "").orEmpty(),
                projectId = p.getString("projectId", "").orEmpty(),
                senderId = p.getString("senderId", "").orEmpty(),
                storageBucket = p.getString("storageBucket", "").orEmpty(),
                webClientId = p.getString("webClientId", "").orEmpty(),
            ),
            companyId = companyId,
            companyName = p.getString("companyName", "").orEmpty(),
            role = p.getString("role", RoleWorker).orEmpty(),
        )
    }

    fun save(context: Context, link: CompanyLink) {
        ensureLoaded(context)
        prefs(context).edit()
            .putString("apiKey", link.settings.apiKey)
            .putString("appId", link.settings.appId)
            .putString("projectId", link.settings.projectId)
            .putString("senderId", link.settings.senderId)
            .putString("storageBucket", link.settings.storageBucket)
            .putString("webClientId", link.settings.webClientId)
            .putString("companyId", link.companyId)
            .putString("companyName", link.companyName)
            .putString("role", link.role)
            .commit()
        flow.value = link
    }

    fun clear(context: Context) {
        ensureLoaded(context)
        prefs(context).edit().clear().commit()
        flow.value = null
    }
}
