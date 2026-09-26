package com.conwic.mixmaster.ui.company

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.company.AccessCode
import com.conwic.mixmaster.data.company.CompanyApi
import com.conwic.mixmaster.data.company.CompanyLink
import com.conwic.mixmaster.data.company.CompanyProblem
import com.conwic.mixmaster.data.company.CompanyStore
import com.conwic.mixmaster.data.company.CompanyWipe
import com.conwic.mixmaster.data.company.Hello
import com.conwic.mixmaster.data.company.Joined
import com.conwic.mixmaster.data.company.Perms
import com.conwic.mixmaster.data.company.Person
import com.conwic.mixmaster.data.company.ServerAddress
import com.conwic.mixmaster.data.company.ServerKind
import com.conwic.mixmaster.data.company.SyncEngine
import com.conwic.mixmaster.data.company.SyncStatus
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.prefs.UserPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A server that answered, and what it said about itself. */
data class Found(val server: String, val hello: Hello)

data class CompanyUi(
    /** What is being done right now, for the line at the top; null when nothing is. */
    @StringRes val busy: Int? = null,
    /** Rows received so far while a phone that has just joined fills up. */
    val progress: Int = 0,
    /** The last thing that went wrong, as the server's or the app's word for it. */
    val problem: String? = null,
    val found: Found? = null,
    val people: List<Person> = emptyList(),
    val peopleLoaded: Boolean = false,
    /** A code just made, waiting to be handed to its person. */
    val fresh: Person? = null,
    /** Something that went well and is worth saying, until it is read. */
    @StringRes val notice: Int? = null,
)

class CompanyViewModel(
    private val app: Context,
    private val userPrefs: UserPrefs,
) : ViewModel() {

    val link: StateFlow<CompanyLink?> = CompanyStore.link(app)
    val status: StateFlow<SyncStatus> = SyncEngine.status

    private val _ui = MutableStateFlow(CompanyUi())
    val ui: StateFlow<CompanyUi> = _ui.asStateFlow()

    init {
        loadPeople()
    }

    /** One thing at a time, with its name on screen while it runs and its failure after. */
    private fun work(@StringRes what: Int, block: suspend () -> Unit) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = what, problem = null, progress = 0) }
        viewModelScope.launch {
            try {
                block()
            } catch (p: CompanyProblem) {
                if (p.code == "revoked" && CompanyStore.current(app) != null) {
                    withContext(Dispatchers.IO) { CompanyWipe.run(app, CompanyWipe.Reason.Revoked) }
                }
                _ui.update { it.copy(problem = p.code) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(problem = "server") }
            } finally {
                _ui.update { it.copy(busy = null) }
            }
        }
    }

    fun dismissProblem() = _ui.update { it.copy(problem = null) }

    fun dismissNotice() = _ui.update { it.copy(notice = null) }

    // ---- Setting up (the employer) -----------------------------------------------------------

    /** Tries what was typed, and the likely places a server sits under it, until one answers. */
    fun check(address: String) = work(R.string.co_checking) {
        _ui.update { it.copy(found = null) }
        val candidates = ServerAddress.candidates(address)
        if (candidates.isEmpty()) throw CompanyProblem("not_server")
        var problem: CompanyProblem? = null
        for (server in candidates) {
            try {
                val hello = CompanyApi.hello(server)
                _ui.update { it.copy(found = Found(server, hello)) }
                return@work
            } catch (p: CompanyProblem) {
                // "Not a server here" is the least useful thing to report when another try said more.
                if (problem == null || problem.code == "not_server") problem = p
            }
        }
        throw problem ?: CompanyProblem("not_server")
    }

    fun forgetFound() = _ui.update { it.copy(found = null) }

    fun setUp(companyName: String, yourName: String, startEmpty: Boolean) {
        val found = _ui.value.found ?: return
        work(R.string.co_setting_up) {
            val device = SyncEngine.newDevice()
            val joined = CompanyApi.setup(found.server, companyName.trim(), yourName.trim(), device)
            saveLink(found.server, found.hello.kind, joined, device)
            userPrefs.setRole(Role.EMPLOYER)
            withContext(Dispatchers.IO) {
                if (startEmpty) SyncEngine.clearShared(app) else SyncEngine.queueEverything(app)
            }
            SyncEngine.start(app)
            _ui.update { it.copy(busy = R.string.co_uploading, found = null) }
            SyncEngine.catchUp(app)
            loadPeopleNow()
        }
    }

    // ---- Joining (everybody else) ------------------------------------------------------------

    fun join(text: String) {
        val code = AccessCode.parse(text)
        if (code == null) {
            _ui.update { it.copy(problem = "bad_format") }
            return
        }
        work(R.string.co_joining) {
            var server = code.server
            var hello = CompanyApi.hello(server)
            // A code made before the company moved still works: it went with the company.
            val movedTo = hello.movedTo
            if (movedTo != null) {
                server = movedTo
                hello = CompanyApi.hello(server)
            }
            if (!hello.claimed) throw CompanyProblem("not_claimed")
            val device = SyncEngine.newDevice()
            val joined = CompanyApi.join(server, code.code, device)
            // The key first, then the emptying: a phone that dies in between still knows its
            // company, and fills up from it on the next start.
            SyncEngine.stop()
            saveLink(server, hello.kind, joined, device)
            userPrefs.setRole(if (joined.me.owner) Role.EMPLOYER else Role.WORKER)
            withContext(Dispatchers.IO) { SyncEngine.clearShared(app) }
            SyncEngine.start(app)
            _ui.update { it.copy(busy = R.string.co_receiving) }
            SyncEngine.catchUp(app) { received -> _ui.update { it.copy(progress = received) } }
            loadPeopleNow()
        }
    }

    private fun saveLink(server: String, kind: ServerKind, joined: Joined, device: String) {
        CompanyStore.save(
            app,
            CompanyLink(
                server = server,
                kind = kind,
                companyId = joined.companyId,
                companyName = joined.companyName,
                token = joined.token,
                device = device,
                personId = joined.me.id,
                name = joined.me.name,
                owner = joined.me.owner,
                perms = if (joined.me.owner) Perms.All else joined.me.perms,
                lastContactAt = System.currentTimeMillis(),
            ),
        )
    }

    // ---- In the company ----------------------------------------------------------------------

    fun syncNow() {
        SyncEngine.syncNow()
        loadPeople()
    }

    fun loadPeople() {
        viewModelScope.launch { runCatching { loadPeopleNow() } }
    }

    private suspend fun loadPeopleNow() {
        val current = CompanyStore.current(app) ?: return
        if (!current.owner) return
        val people = CompanyApi.people(current)
        _ui.update { it.copy(people = people, peopleLoaded = true) }
        // The copy a move needs if this server is ever lost, kept fresh while it is here.
        CompanyStore.savePeopleExport(app, CompanyApi.exportPeople(current))
    }

    // ---- Moving servers ------------------------------------------------------------------------

    /** Moves the company to the empty server just checked. Everybody else's phone follows. */
    fun moveHere() {
        val found = _ui.value.found ?: return
        if (found.hello.claimed) return
        work(R.string.co_move_old) {
            SyncEngine.move(app, found.server, found.hello.kind) { step ->
                val what = when (step) {
                    SyncEngine.MoveStep.OldServer -> R.string.co_move_old
                    SyncEngine.MoveStep.NewServer -> R.string.co_move_new
                    SyncEngine.MoveStep.Upload -> R.string.co_move_upload
                }
                _ui.update { it.copy(busy = what) }
            }
            _ui.update { it.copy(found = null, notice = R.string.co_moved_ok) }
            loadPeopleNow()
        }
    }

    /** The company's new address, typed in because the old server is gone and cannot say. */
    fun followMove(address: String) = work(R.string.co_checking) {
        SyncEngine.followTo(app, address)
        _ui.update { it.copy(notice = R.string.co_followed_ok) }
        loadPeopleNow()
    }

    fun savePerson(id: Long, name: String, owner: Boolean, perms: Perms) {
        val current = link.value ?: return
        work(R.string.co_saving) {
            val (person, people) = CompanyApi.savePerson(current, id, name.trim(), owner, perms)
            _ui.update { it.copy(people = people, peopleLoaded = true, fresh = if (id == 0L) person else it.fresh) }
        }
    }

    fun newCode(id: Long) {
        val current = link.value ?: return
        work(R.string.co_saving) {
            val (person, people) = CompanyApi.newCode(current, id)
            _ui.update { it.copy(people = people, fresh = person) }
        }
    }

    fun removePerson(id: Long) {
        val current = link.value ?: return
        work(R.string.co_saving) {
            val people = CompanyApi.removePerson(current, id)
            _ui.update { ui -> ui.copy(people = people, fresh = ui.fresh?.takeIf { it.id != id }) }
        }
    }

    fun showCode(person: Person) = _ui.update { it.copy(fresh = person) }

    fun dismissFresh() = _ui.update { it.copy(fresh = null) }

    /** A worker going: the server is told if it can be, and the company's data leaves the phone either way. */
    fun leave() {
        val current = link.value ?: return
        work(R.string.co_leaving) {
            runCatching { CompanyApi.leave(current) }
            withContext(Dispatchers.IO) { CompanyWipe.run(app, CompanyWipe.Reason.Left) }
        }
    }

    /**
     * An owner stopping sharing on this phone. What is waiting goes up first; the data stays here
     * and on the server.
     */
    fun disconnect() {
        val current = link.value ?: return
        work(R.string.co_leaving) {
            runCatching { SyncEngine.catchUp(app) }
            runCatching { CompanyApi.leave(current) }
            withContext(Dispatchers.IO) { SyncEngine.disconnect(app) }
            CompanyStore.clear(app)
            _ui.value = CompanyUi()
        }
    }
}
