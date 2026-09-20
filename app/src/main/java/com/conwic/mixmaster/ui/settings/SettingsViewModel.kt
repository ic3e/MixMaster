package com.conwic.mixmaster.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.prefs.UserPrefs
import com.conwic.mixmaster.data.repository.TeamRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.conwic.mixmaster.domain.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: String = "Auto",
    val language: AppLanguage = AppLanguage.ENGLISH,
    val role: Role = Role.EMPLOYER,
    val appLockEnabled: Boolean = false,
    val mixingRemindersEnabled: Boolean = true,
    val team: List<TeamMemberEntity> = emptyList(),
)

class SettingsViewModel(
    private val userPrefs: UserPrefs,
    private val teamRepository: TeamRepository,
) : ViewModel() {

    private data class Prefs(
        val theme: String,
        val language: AppLanguage,
        val role: Role,
        val appLock: Boolean,
        val reminders: Boolean,
    )

    // combine() only has typed overloads up to five flows, so the preferences are gathered
    // into one first and joined with the team after.
    private val prefs: Flow<Prefs> = combine(
        userPrefs.theme,
        userPrefs.language,
        userPrefs.role,
        userPrefs.appLockEnabled,
        userPrefs.mixingRemindersEnabled,
    ) { theme, language, role, appLock, reminders ->
        Prefs(theme, language, role, appLock, reminders)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs,
        teamRepository.observeAll(),
    ) { p, team ->
        SettingsUiState(
            theme = p.theme,
            language = p.language,
            role = p.role,
            appLockEnabled = p.appLock,
            mixingRemindersEnabled = p.reminders,
            team = team,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(value: String) = viewModelScope.launch { userPrefs.setTheme(value) }
    fun setLanguage(value: AppLanguage) = viewModelScope.launch { userPrefs.setLanguage(value) }
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
