package com.osanwall.data.repository

import android.util.Patterns
import com.osanwall.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.osanwall.data.model.Result
import com.osanwall.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    companion object {
        const val DEMO_EMAIL = "demo@osanwall.app"
        const val DEMO_PASSWORD = "OsanWallDemo1"
    }

    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    fun signInWithEmail(email: String, password: String): Flow<Result<User>> = flow {
        emit(Result.Loading)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emit(Result.Error("Invalid email address"))
            return@flow
        }
        if (password.length < 6) {
            emit(Result.Error("Password must be at least 6 characters"))
            return@flow
        }
        try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = fetchUser(result.user!!.uid)
            emit(Result.Success(user))
        } catch (e: Exception) {
            Timber.e(e, "Email sign in failed")
            if (BuildConfig.DEBUG && isDemoCredentials(email, password)) {
                tryDemoSignIn()?.let { emit(Result.Success(it)) } ?: emit(Result.Error(mapAuthError(e.message), e))
            } else {
                emit(Result.Error(mapAuthError(e.message), e))
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, username: String): Flow<Result<User>> = flow {
        emit(Result.Loading)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emit(Result.Error("Invalid email address"))
            return@flow
        }
        if (password.length < 6) {
            emit(Result.Error("Password must be at least 6 characters"))
            return@flow
        }
        val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_.]"), "")
        if (cleanUsername.length < 3) {
            emit(Result.Error("Username must be at least 3 characters (letters, numbers, _ or .)"))
            return@flow
        }
        try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val uid = result.user!!.uid
            val user = User(id = uid, email = email.trim(), username = cleanUsername)
            firestore.collection(Collections.USERS).document(uid).set(user).await()
            emit(Result.Success(user))
        } catch (e: Exception) {
            Timber.e(e, "Email sign up failed")
            emit(Result.Error(mapAuthError(e.message), e))
        }
    }

    fun signInWithGoogle(account: GoogleSignInAccount): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val uid = result.user!!.uid
            val isNew = result.additionalUserInfo?.isNewUser == true
            val user = if (isNew) {
                val newUser = User(
                    id = uid,
                    email = account.email ?: "",
                    username = account.displayName
                        ?.lowercase()
                        ?.replace(Regex("[^a-z0-9_.]"), "")
                        ?.take(20)
                        ?: "user${uid.take(6)}",
                    avatarUrl = account.photoUrl?.toString() ?: ""
                )
                firestore.collection(Collections.USERS).document(uid).set(newUser).await()
                newUser
            } else {
                fetchUser(uid)
            }
            emit(Result.Success(user))
        } catch (e: Exception) {
            Timber.e(e, "Google sign in failed")
            emit(Result.Error(mapAuthError(e.message), e))
        }
    }

    fun signOut() = auth.signOut()

    suspend fun fetchUser(userId: String): User {
        return try {
            val doc = firestore.collection(Collections.USERS).document(userId).get().await()
            doc.toObject(User::class.java) ?: User(id = userId)
        } catch (e: Exception) {
            Timber.e(e, "fetchUser failed for $userId")
            User(id = userId)
        }
    }

    fun sendPasswordReset(email: String): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emit(Result.Error("Invalid email address"))
            return@flow
        }
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            emit(Result.Success(Unit))
        } catch (e: Exception) {
            emit(Result.Error(mapAuthError(e.message), e))
        }
    }

    private fun isDemoCredentials(email: String, password: String): Boolean =
        email.trim().equals(DEMO_EMAIL, ignoreCase = true) && password == DEMO_PASSWORD

    /**
     * Debug-only fallback when the demo email is not registered in Firebase yet.
     * Requires Anonymous sign-in enabled in the Firebase project.
     */
    private suspend fun tryDemoSignIn(): User? {
        if (!BuildConfig.DEBUG) return null
        return try {
            val anon = auth.signInAnonymously().await()
            val uid = anon.user!!.uid
            val demo = User(
                id = uid,
                email = DEMO_EMAIL,
                username = "demo_explorer",
                bio = "Demo account — explore OsanWall."
            )
            firestore.collection(Collections.USERS).document(uid).set(demo).await()
            demo
        } catch (e: Exception) {
            Timber.e(e, "Demo sign-in fallback failed")
            null
        }
    }

    private fun mapAuthError(message: String?): String = when {
        message == null -> "Authentication failed"
        "password" in message.lowercase() -> "Incorrect password"
        "user" in message.lowercase() && "found" in message.lowercase() -> "No account found with this email"
        "email" in message.lowercase() && "use" in message.lowercase() -> "Email already in use"
        "network" in message.lowercase() -> "Network error - check your connection"
        "too-many-requests" in message.lowercase() -> "Too many attempts - try again later"
        else -> message
    }
}
