package com.merowall.data.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.merowall.data.model.Result
import com.merowall.data.model.User
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
    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    fun signInWithEmail(email: String, password: String): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = fetchUser(result.user!!.uid)
            emit(Result.Success(user))
        } catch (e: Exception) {
            Timber.e(e, "Email sign in failed")
            emit(Result.Error(e.message ?: "Sign in failed", e))
        }
    }

    fun signUpWithEmail(email: String, password: String, username: String): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user!!.uid
            val user = User(
                id = uid,
                email = email,
                username = username
            )
            firestore.collection("users").document(uid).set(user).await()
            emit(Result.Success(user))
        } catch (e: Exception) {
            Timber.e(e, "Email sign up failed")
            emit(Result.Error(e.message ?: "Sign up failed", e))
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
                    username = account.displayName?.replace(" ", "").lowercase() ?: "user$uid",
                    avatarUrl = account.photoUrl?.toString() ?: ""
                )
                firestore.collection("users").document(uid).set(newUser).await()
                newUser
            } else {
                fetchUser(uid)
            }
            emit(Result.Success(user))
        } catch (e: Exception) {
            Timber.e(e, "Google sign in failed")
            emit(Result.Error(e.message ?: "Google sign in failed", e))
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun fetchUser(userId: String): User {
        val doc = firestore.collection("users").document(userId).get().await()
        return doc.toObject(User::class.java) ?: User(id = userId)
    }

    fun sendPasswordReset(email: String): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            auth.sendPasswordResetEmail(email).await()
            emit(Result.Success(Unit))
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Failed to send reset email", e))
        }
    }
}
