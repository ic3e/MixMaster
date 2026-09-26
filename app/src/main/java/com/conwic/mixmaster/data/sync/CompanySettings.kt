package com.conwic.mixmaster.data.sync

import android.util.Base64
import androidx.annotation.StringRes
import com.conwic.mixmaster.R
import org.json.JSONObject

/**
 * Where the company's shared data lives: the Firebase project the employer set up, as far as the
 * app needs to know it. None of it is a secret — it says which project to talk to, and the
 * project's own rules decide who may read or write anything in it.
 */
data class FirebaseSettings(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val senderId: String,
    val storageBucket: String,
    /** The "web" OAuth client Google sign-in hands its token to. Made when sign-in is switched on. */
    val webClientId: String,
)

/** What reading a google-services.json came to. */
sealed interface SettingsFile {
    data class Read(val settings: FirebaseSettings) : SettingsFile
    data class Problem(@StringRes val reason: Int) : SettingsFile
}

/**
 * Reads the google-services.json the employer downloads from Firebase (setup guide, step 6).
 *
 * The file can list several apps; this one is found by its package name. The sign-in client is
 * only in the file once Google sign-in has been switched on (step 3), so a file downloaded too
 * early is told apart from one that is simply wrong.
 */
object GoogleServicesFile {

    fun read(text: String, packageName: String): SettingsFile = runCatching {
        val root = JSONObject(text)
        val project = root.getJSONObject("project_info")
        val clients = root.getJSONArray("client")
        val client = (0 until clients.length()).map { clients.getJSONObject(it) }.firstOrNull {
            it.optJSONObject("client_info")
                ?.optJSONObject("android_client_info")
                ?.optString("package_name") == packageName
        } ?: return SettingsFile.Problem(R.string.share_file_wrong_app)

        val apiKey = client.optJSONArray("api_key")?.optJSONObject(0)?.optString("current_key").orEmpty()
        val appId = client.getJSONObject("client_info").optString("mobilesdk_app_id")
        val webClient = webClientId(client)
            ?: return SettingsFile.Problem(R.string.share_file_no_sign_in)
        if (apiKey.isBlank() || appId.isBlank()) return SettingsFile.Problem(R.string.share_file_unreadable)

        SettingsFile.Read(
            FirebaseSettings(
                apiKey = apiKey,
                appId = appId,
                projectId = project.getString("project_id"),
                senderId = project.optString("project_number"),
                storageBucket = project.optString("storage_bucket"),
                webClientId = webClient,
            ),
        )
    }.getOrElse { SettingsFile.Problem(R.string.share_file_unreadable) }

    /** Client type 3 is the web client; it sits in one of two places depending on the file's age. */
    private fun webClientId(client: JSONObject): String? {
        val direct = client.optJSONArray("oauth_client")
        val other = client.optJSONObject("services")
            ?.optJSONObject("appinvite_service")
            ?.optJSONArray("other_platform_oauth_client")
        return listOfNotNull(direct, other).firstNotNullOfOrNull { list ->
            (0 until list.length()).map { list.getJSONObject(it) }
                .firstOrNull { it.optInt("client_type") == 3 }
                ?.optString("client_id")
                ?.takeIf { it.isNotBlank() }
        }
    }
}

/**
 * Everything a worker's phone needs to join: the project, and which company in it.
 *
 * Sent as one line of letters and digits inside the invite, so it survives WhatsApp, SMS and a
 * copy-paste from an email alike; the link form (`mixmaster://join?c=…`) opens the app straight
 * away where the messenger lets it be tapped.
 */
data class JoinCode(
    val settings: FirebaseSettings,
    val companyId: String,
    val companyName: String,
) {
    fun encode(): String {
        val json = JSONObject()
            .put("v", 1)
            .put("k", settings.apiKey)
            .put("a", settings.appId)
            .put("p", settings.projectId)
            .put("s", settings.senderId)
            .put("b", settings.storageBucket)
            .put("w", settings.webClientId)
            .put("c", companyId)
            .put("n", companyName)
        return Base64.encodeToString(
            json.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    fun link(): String = "$LinkPrefix${encode()}"

    companion object {
        const val LinkPrefix = "mixmaster://join?c="

        /**
         * A code found in whatever was pasted: the bare code, the link, or the whole invite
         * message with both in it. Null when there is nothing in it that reads as one.
         */
        fun find(text: String): JoinCode? {
            val fromLink = Regex("""[?&]c=([A-Za-z0-9_\-]+)""").find(text)?.groupValues?.get(1)
            val candidates = listOfNotNull(fromLink) +
                Regex("""[A-Za-z0-9_\-]{60,}""").findAll(text).map { it.value }.toList()
            return candidates.firstNotNullOfOrNull(::decode)
        }

        private fun decode(code: String): JoinCode? = runCatching {
            val json = JSONObject(
                String(Base64.decode(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8),
            )
            JoinCode(
                settings = FirebaseSettings(
                    apiKey = json.getString("k"),
                    appId = json.getString("a"),
                    projectId = json.getString("p"),
                    senderId = json.optString("s"),
                    storageBucket = json.optString("b"),
                    webClientId = json.getString("w"),
                ),
                companyId = json.getString("c"),
                companyName = json.optString("n"),
            ).takeIf { it.settings.apiKey.isNotBlank() && it.companyId.isNotBlank() }
        }.getOrNull()
    }
}
