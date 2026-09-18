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
    val theme: String = "Auto",
    val role: Role = Role.EMPLOYER,
    val appLockEnabled: Boolean = false,
    val mixingRemindersEnabled: Boolean = true,
    val team: List<TeamMemberEntity> = emptyList(),
)

class SettingsViewModel(
    private val userPrefs: UserPrefs,
    private val teamRepository: TeamRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        userPrefs.theme,
        userPrefs.role,
        userPrefs.appLockEnabled,
        userPrefs.mixingRemindersEnabled,
        teamRepository.observeAll(),
    ) { theme, role, appLock, reminders, team ->
        SettingsUiState(
            theme = theme,
            role = role,
            appLockEnabled = appLock,
            mixingRemindersEnabled = reminders,
            team = team,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(value: String) = viewModelScope.launch { userPrefs.setTheme(value) }
    fun setRole(value: Role) = viewModelScope.launch { userPrefs.setRole(value) }
    fun setAppLockEnabled(value: Boolean) = viewModelScope.launch { userPrefs.setAppLockEnabled(value) }
    fun setMixingRemindersEnabled(value: Boolean) = viewModelScope.launch { userPrefs.setMixingRemindersEnabled(value) }

    /** Adds a member, or replaces one when the draft already has an id. */
    fun saveTeamMember(member: TeamMemberEntity) {
        if (member.name.isBlank()) return
        viewModelScope.launch { teamRepository.add(member) }
    }

    fun removeTeamMember(member: TeamMemberEntity) {
        viewModelScope.launch { teamRepository.remove(member) }
    }
}
