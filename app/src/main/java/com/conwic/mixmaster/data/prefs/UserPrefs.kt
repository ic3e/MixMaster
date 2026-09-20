package com.conwic.mixmaster.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.domain.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mixmaster_prefs")

/** Persists the lightweight app-wide settings that aren't real business data: who's signed
 * in, whether onboarding has been seen, and display preferences. */
class UserPrefs(private val context: Context) {

    private object Keys {
        val ROLE = stringPreferencesKey("role")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val THEME = stringPreferencesKey("theme") // "Light" | "Dark" | "Auto"
        // Absent means nobody has chosen yet, which is what makes the phone's own language the
        // starting point without it later overriding a choice that was made.
        val LANGUAGE = stringPreferencesKey("language")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        // What the calculator is working on. Stored rather than held in the view model so
        // "Use in calculator" can say which product before the calculator exists.
        val LAST_PRODUCT_ID = longPreferencesKey("last_product_id")
        // Was labelled "contextual tips"; it now drives the mixing reminders in the calculator.
        // The key is left alone so anyone who already turned it off stays turned off.
        val MIXING_REMINDERS = booleanPreferencesKey("tips_enabled")
    }

    val role: Flow<Role> = context.dataStore.data.map { prefs ->
        prefs[Keys.ROLE]?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.EMPLOYER
    }

    suspend fun setRole(role: Role) {
        context.dataStore.edit { it[Keys.ROLE] = role.name }
    }

    val onboardingSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_SEEN] ?: false }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = seen }
    }

    /** "Light", "Dark" or "Auto" — read by the activity to pick the colour scheme. */
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "Auto" }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    /** The chosen language, or the phone's if it's one of ours, or English. */
    val language: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromTag(prefs[Keys.LANGUAGE]) ?: LanguageStore.deviceLanguage()
    }

    suspend fun setLanguage(language: AppLanguage) {
        // Mirrored first, and synchronously: the activity is about to be rebuilt and will read
        // the language from there before there is anywhere to suspend.
        LanguageStore.write(context, language)
        context.dataStore.edit { it[Keys.LANGUAGE] = language.tag }
    }

    /** The product the calculator should be on. 0 when nothing has been chosen yet. */
    val lastProductId: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_PRODUCT_ID] ?: 0L }

    suspend fun setLastProductId(id: Long) {
        context.dataStore.edit { it[Keys.LAST_PRODUCT_ID] = id }
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK_ENABLED] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    val mixingRemindersEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MIXING_REMINDERS] ?: true }

    suspend fun setMixingRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MIXING_REMINDERS] = enabled }
    }
}
