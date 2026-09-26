package com.conwic.mixmaster.data.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Why a step of connecting did not go through, in words the screen can show. */
class ShareProblem(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { Cancelled, NoGoogleAccount, SignInFailed, Offline, NotInvited, NoAccess, Unknown }
}

/**
 * The Firebase project the company's link points at, started from the settings in [SyncStore]
 * rather than from a file built into the app.
 *
 * It is the default Firebase app, so everything else simply asks Firebase for its instance. Moving
 * to another project swaps it: the old app is deleted first, because the default one can only be
 * started once.
 */
object FirebaseBoot {

    fun start(context: Context, settings: FirebaseSettings): FirebaseApp {
        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        if (existing != null) {
            val same = existing.options.projectId == settings.projectId &&
                existing.options.applicationId == settings.appId
            if (same) return existing
            existing.delete()
        }
        val options = FirebaseOptions.Builder()
            .setApiKey(settings.apiKey)
            .setApplicationId(settings.appId)
            .setProjectId(settings.projectId)
            .apply {
                if (settings.senderId.isNotBlank()) setGcmSenderId(settings.senderId)
                if (settings.storageBucket.isNotBlank()) setStorageBucket(settings.storageBucket)
            }
            .build()
        return FirebaseApp.initializeApp(context.applicationContext, options)
    }

    fun isStarted(context: Context): Boolean =
        FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }
}

/**
 * Signing in with Google, through Credential Manager: the phone's own account picker, a Google ID
 * token, and that token swapped for a Firebase account. The app sees a name and an email, nothing
 * else of the Google account.
 */
object GoogleSignIn {

    suspend fun signIn(activity: Activity, webClientId: String): FirebaseUser {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            throw ShareProblem(ShareProblem.Kind.Cancelled, e)
        } catch (e: NoCredentialException) {
            throw ShareProblem(ShareProblem.Kind.NoGoogleAccount, e)
        } catch (e: Exception) {
            throw ShareProblem(ShareProblem.Kind.SignInFailed, e)
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw ShareProblem(ShareProblem.Kind.SignInFailed)
        }
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return try {
            online {
                FirebaseAuth.getInstance()
                    .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                    .await()
                    .user
            } ?: throw ShareProblem(ShareProblem.Kind.SignInFailed)
        } catch (e: ShareProblem) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ShareProblem(ShareProblem.Kind.SignInFailed, e)
        }
    }

    fun signOut() {
        runCatching { FirebaseAuth.getInstance().signOut() }
    }
}

/**
 * Anything that has to reach the server — making a company, joining one — rather than being
 * queued for later. Firestore would otherwise wait for a signal for as long as it takes, and a
 * button that does nothing for ten minutes in a basement reads as a broken app.
 */
private suspend fun <T> online(block: suspend () -> T): T = try {
    withTimeout(25_000) { block() }
} catch (e: TimeoutCancellationException) {
    throw ShareProblem(ShareProblem.Kind.Offline, e)
}

/** A member of the company, as its crew list in Firebase has them. */
data class Member(val uid: String, val name: String, val email: String, val role: String)

/** Someone asked to join who has not signed in yet. */
data class Invite(val email: String, val name: String, val role: String)

/**
 * The company itself: making it, asking people into it, and taking them out again.
 *
 * Laid out as the setup guide's rules expect — `companies/{id}` with its owner, `members/{uid}`
 * with each person's role, `invites/{email}` for the people asked but not yet in.
 */
object CompanyService {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val user: FirebaseUser get() = FirebaseAuth.getInstance().currentUser
        ?: throw ShareProblem(ShareProblem.Kind.SignInFailed)

    fun company(companyId: String) = db.collection("companies").document(companyId)

    /**
     * Makes a new company with the signed-in person as its owner and first employer. Two writes,
     * one after the other: the rules check the company's owner before letting the first member
     * in, and that check only sees what is already saved.
     */
    suspend fun create(name: String): String = guarded {
        val me = user
        val ref = db.collection("companies").document()
        online {
            ref.set(
                mapOf("name" to name, "ownerUid" to me.uid, "createdAt" to FieldValue.serverTimestamp()),
            ).await()
            ref.collection("members").document(me.uid).set(memberFields(me, RoleEmployer)).await()
        }
        ref.id
    }

    /** Joins with an invite made out to the signed-in email. Returns the role it gave. */
    suspend fun join(companyId: String): String = guarded {
        val me = user
        val email = me.email?.lowercase() ?: throw ShareProblem(ShareProblem.Kind.NotInvited)
        val company = company(companyId)
        online {
            val invite = try {
                company.collection("invites").document(email).get(Source.SERVER).await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    throw ShareProblem(ShareProblem.Kind.NotInvited, e)
                }
                throw e
            }
            if (!invite.exists()) throw ShareProblem(ShareProblem.Kind.NotInvited)
            val role = invite.getString("role") ?: RoleWorker
            company.collection("members").document(me.uid).set(memberFields(me, role)).await()
            role
        }
    }

    suspend fun invite(companyId: String, email: String, name: String, role: String) = guarded {
        online {
            company(companyId).collection("invites").document(email.trim().lowercase()).set(
                mapOf("name" to name.trim(), "role" to role, "invitedAt" to FieldValue.serverTimestamp()),
            ).await()
        }
        Unit
    }

    /** Takes someone out: their membership, and the invite that would let them straight back in. */
    suspend fun remove(companyId: String, member: Member) = guarded {
        online {
            company(companyId).collection("members").document(member.uid).delete().await()
            if (member.email.isNotBlank()) {
                company(companyId).collection("invites").document(member.email.lowercase()).delete().await()
            }
        }
        Unit
    }

    suspend fun cancelInvite(companyId: String, email: String) = guarded {
        online { company(companyId).collection("invites").document(email.lowercase()).delete().await() }
        Unit
    }

    private fun memberFields(me: FirebaseUser, role: String) = mapOf(
        "role" to role,
        "name" to (me.displayName ?: me.email.orEmpty()),
        "email" to me.email.orEmpty().lowercase(),
        "joinedAt" to FieldValue.serverTimestamp(),
    )

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: ShareProblem) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        throw ShareProblem(
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) ShareProblem.Kind.NoAccess
            else ShareProblem.Kind.Unknown,
            e,
        )
    } catch (e: Exception) {
        throw ShareProblem(ShareProblem.Kind.Unknown, e)
    }
}
