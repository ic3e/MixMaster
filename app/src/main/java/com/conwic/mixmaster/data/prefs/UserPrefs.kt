package com.conwic.mixmaster.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.conwic.mixmaster.data.model.Role
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
        val UNITS = stringPreferencesKey("units") // "Metric" | "Imperial"
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val TIPS_ENABLED = booleanPreferencesKey("tips_enabled")
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

    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "Light" }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    val units: Flow<String> = context.dataStore.data.map { it[Keys.UNITS] ?: "Metric" }

    suspend fun setUnits(units: String) {
        context.dataStore.edit { it[Keys.UNITS] = units }
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK_ENABLED] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    val tipsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TIPS_ENABLED] ?: true }

    suspend fun setTipsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TIPS_ENABLED] = enabled }
    }
}
