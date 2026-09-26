package com.conwic.mixmaster.ui.sharing

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.prefs.UserPrefs
import com.conwic.mixmaster.data.sync.CompanyLink
import com.conwic.mixmaster.data.sync.CompanyService
import com.conwic.mixmaster.data.sync.FirebaseBoot
import com.conwic.mixmaster.data.sync.FirebaseSettings
import com.conwic.mixmaster.data.sync.GoogleServicesFile
import com.conwic.mixmaster.data.sync.GoogleSignIn
import com.conwic.mixmaster.data.sync.Invite
import com.conwic.mixmaster.data.sync.JoinCode
import com.conwic.mixmaster.data.sync.Member
import com.conwic.mixmaster.data.sync.RoleEmployer
import com.conwic.mixmaster.data.sync.SettingsFile
import com.conwic.mixmaster.data.sync.ShareProblem
import com.conwic.mixmaster.data.sync.SyncEngine
import com.conwic.mixmaster.data.sync.SyncStatus
import com.conwic.mixmaster.data.sync.SyncStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SharingUi(
    val busy: Boolean = false,
    /** The employer's settings file, read and waiting for a company name. */
    val settings: FirebaseSettings? = null,
    @StringRes val problem: Int? = null,
    /** An invite made just now, for the screen to hand to the share sheet. */
    val shareText: String? = null,
)

class CompanySharingViewModel(
    private val app: Context,
    private val userPrefs: UserPrefs,
) : ViewModel() {

    val link: StateFlow<CompanyLink?> = SyncStore.link(app)
    val status: StateFlow<SyncStatus> = SyncEngine.status

    private val _ui = MutableStateFlow(SharingUi())
    val ui: StateFlow<SharingUi> = _ui.asStateFlow()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _invites = MutableStateFlow<List<Invite>>(emptyList())
    val invites: StateFlow<List<Invite>> = _invites.asStateFlow()

    private var watching: List<ListenerRegistration> = emptyList()

    init {
        viewModelScope.launch {
            link.collect { current -> watchPeople(current) }
        }
    }

    /** The Google account this phone is signed in with, once Firebase is running. */
    fun myEmail(): String? =
        if (FirebaseBoot.isStarted(app)) FirebaseAuth.getInstance().currentUser?.email else null

    fun dismissProblem() = _ui.update { it.copy(problem = null) }
    fun sharedInvite() = _ui.update { it.copy(shareText = null) }
    fun forgetSettings() = _ui.update { it.copy(settings = null) }

    /** The employer's google-services.json, picked from the phone's files. */
    fun readSettingsFile(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            when (val read = text?.let { GoogleServicesFile.read(it, app.packageName) }) {
                is SettingsFile.Read -> _ui.update { it.copy(settings = read.settings, problem = null) }
                is SettingsFile.Problem -> _ui.update { it.copy(settings = null, problem = read.reason) }
                null -> _ui.update { it.copy(settings = null, problem = R.string.share_file_unreadable) }
            }
        }
    }

    /** Signs the employer in, makes the company, and sends everything on this phone up to it. */
    fun createCompany(activity: Activity, name: String) {
        val settings = _ui.value.settings ?: return
        step {
            FirebaseBoot.start(app, settings)
            GoogleSignIn.signIn(activity, settings.webClientId)
            val companyId = CompanyService.create(name.trim())
            SyncStore.save(app, CompanyLink(settings, companyId, name.trim(), RoleEmployer))
            withContext(Dispatchers.IO) { SyncEngine.uploadEverything(app) }
            userPrefs.setRole(Role.EMPLOYER)
            SyncEngine.start(app)
            _ui.update { it.copy(settings = null) }
        }
    }

    /** Signs a crew member in with the invited account, and swaps this phone's data for the company's. */
    fun join(activity: Activity, pasted: String) {
        val code = JoinCode.find(pasted)
        if (code == null) {
            _ui.update { it.copy(problem = R.string.share_bad_code) }
            return
        }
        step {
            FirebaseBoot.start(app, code.settings)
            GoogleSignIn.signIn(activity, code.settings.webClientId)
            val role = try {
                CompanyService.join(code.companyId)
            } catch (e: ShareProblem) {
                // Signed in with an account nobody invited: let them try another one.
                GoogleSignIn.signOut()
                throw e
            }
            SyncStore.save(app, CompanyLink(code.settings, code.companyId, code.companyName, role))
            withContext(Dispatchers.IO) { SyncEngine.clearForJoin(app) }
            userPrefs.setRole(if (role == RoleEmployer) Role.EMPLOYER else Role.WORKER)
            SyncEngine.start(app)
        }
    }

    /** Back in after Firebase forgot who this was — a reinstall, or cleared app data. */
    fun signInAgain(activity: Activity) {
        val current = link.value ?: return
        step {
            FirebaseBoot.start(app, current.settings)
            GoogleSignIn.signIn(activity, current.settings.webClientId)
            SyncEngine.start(app)
            watchPeople(current)
        }
    }

    fun invite(name: String, email: String, role: String, message: (JoinCode) -> String) {
        val current = link.value ?: return
        step {
            CompanyService.invite(current.companyId, email, name, role)
            _ui.update { it.copy(shareText = message(codeFor(current))) }
        }
    }

    fun resend(message: (JoinCode) -> String) {
        val current = link.value ?: return
        _ui.update { it.copy(shareText = message(codeFor(current))) }
    }

    fun remove(member: Member) {
        val current = link.value ?: return
        step { CompanyService.remove(current.companyId, member) }
    }

    fun cancelInvite(invite: Invite) {
        val current = link.value ?: return
        step { CompanyService.cancelInvite(current.companyId, invite.email) }
    }

    fun disconnect() {
        viewModelScope.launch {
            watchPeople(null)
            withContext(Dispatchers.IO) { SyncEngine.disconnect(app) }
        }
    }

    private fun codeFor(current: CompanyLink) = JoinCode(current.settings, current.companyId, current.companyName)

    /** One step at a time, with the button greyed out while it runs and what went wrong kept for the screen. */
    private fun step(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: ShareProblem) {
                _ui.update { it.copy(problem = e.textRes(), busy = false) }
                return@launch
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update { it.copy(problem = R.string.share_problem_unknown, busy = false) }
                return@launch
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    /** The crew list, live, while the screen is open and there is a company to show. */
    private fun watchPeople(current: CompanyLink?) {
        watching.forEach { runCatching { it.remove() } }
        watching = emptyList()
        _members.value = emptyList()
        _invites.value = emptyList()
        if (current == null || !FirebaseBoot.isStarted(app)) return
        if (FirebaseAuth.getInstance().currentUser == null) return
        val company = CompanyService.company(current.companyId)
        val list = mutableListOf<ListenerRegistration>()
        list += company.collection("members").addSnapshotListener { snap, _ ->
            _members.value = snap?.documents.orEmpty().map { doc ->
                Member(
                    uid = doc.id,
                    name = doc.getString("name").orEmpty(),
                    email = doc.getString("email").orEmpty(),
                    role = doc.getString("role").orEmpty(),
                )
            }.sortedWith(compareBy({ it.role != RoleEmployer }, { it.name.lowercase() }))
        }
        if (current.isEmployer) {
            list += company.collection("invites").addSnapshotListener { snap, _ ->
                val joined = _members.value.map { it.email }.toSet()
                _invites.value = snap?.documents.orEmpty().map { doc ->
                    Invite(email = doc.id, name = doc.getString("name").orEmpty(), role = doc.getString("role").orEmpty())
                }.filter { it.email !in joined }
            }
        }
        watching = list
    }

    override fun onCleared() {
        watching.forEach { runCatching { it.remove() } }
    }
}

@StringRes
private fun ShareProblem.textRes(): Int = when (kind) {
    ShareProblem.Kind.Cancelled -> R.string.share_problem_cancelled
    ShareProblem.Kind.NoGoogleAccount -> R.string.share_problem_no_account
    ShareProblem.Kind.SignInFailed -> R.string.share_problem_sign_in
    ShareProblem.Kind.Offline -> R.string.share_problem_offline
    ShareProblem.Kind.NotInvited -> R.string.share_problem_not_invited
    ShareProblem.Kind.NoAccess -> R.string.share_problem_no_access
    ShareProblem.Kind.Unknown -> R.string.share_problem_unknown
}
