package com.conwic.mixmaster.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.prefs.UserPrefs
import com.conwic.mixmaster.data.repository.TeamRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: String = "Light",
    val units: String = "Metric",
    val role: Role = Role.EMPLOYER,
    val appLockEnabled: Boolean = false,
    val tipsEnabled: Boolean = true,
    val team: List<TeamMemberEntity> = emptyList(),
)

class SettingsViewModel(
    private val userPrefs: UserPrefs,
    private val teamRepository: TeamRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        userPrefs.theme,
        userPrefs.units,
        userPrefs.role,
        userPrefs.appLockEnabled,
        userPrefs.tipsEnabled,
    ) { theme, units, role, appLock, tips ->
        SettingsUiState(theme = theme, units = units, role = role, appLockEnabled = appLock, tipsEnabled = tips)
    }.combine(teamRepository.observeAll()) { partial, team ->
        partial.copy(team = team)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(value: String) = viewModelScope.launch { userPrefs.setTheme(value) }
    fun setUnits(value: String) = viewModelScope.launch { userPrefs.setUnits(value) }
    fun setRole(value: Role) = viewModelScope.launch { userPrefs.setRole(value) }
    fun setAppLockEnabled(value: Boolean) = viewModelScope.launch { userPrefs.setAppLockEnabled(value) }
    fun setTipsEnabled(value: Boolean) = viewModelScope.launch { userPrefs.setTipsEnabled(value) }
}
