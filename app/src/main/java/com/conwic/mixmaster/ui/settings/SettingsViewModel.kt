package com.conwic.mixmaster.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.prefs.UserPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.conwic.mixmaster.domain.AppLanguage
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: String = "Auto",
    val language: AppLanguage = AppLanguage.ENGLISH,
    val role: Role = Role.EMPLOYER,
    val appLockEnabled: Boolean = false,
    val mixingRemindersEnabled: Boolean = true,
)

class SettingsViewModel(
    private val userPrefs: UserPrefs,
) : ViewModel() {

    // combine() only has typed overloads up to five flows, and there are exactly five.
    val uiState: StateFlow<SettingsUiState> = combine(
        userPrefs.theme,
        userPrefs.language,
        userPrefs.role,
        userPrefs.appLockEnabled,
        userPrefs.mixingRemindersEnabled,
    ) { theme, language, role, appLock, reminders ->
        SettingsUiState(
            theme = theme,
            language = language,
            role = role,
            appLockEnabled = appLock,
            mixingRemindersEnabled = reminders,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(value: String) = viewModelScope.launch { userPrefs.setTheme(value) }
    fun setLanguage(value: AppLanguage) = viewModelScope.launch { userPrefs.setLanguage(value) }
    fun setRole(value: Role) = viewModelScope.launch { userPrefs.setRole(value) }
    fun setAppLockEnabled(value: Boolean) = viewModelScope.launch { userPrefs.setAppLockEnabled(value) }
    fun setMixingRemindersEnabled(value: Boolean) = viewModelScope.launch { userPrefs.setMixingRemindersEnabled(value) }
}
