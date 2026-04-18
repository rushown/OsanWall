package com.merowall.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.merowall.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val userDao: UserDao
) {
    fun getUserFlow(userId: String): Flow<User> = flow {
        try {
            val doc = firestore.collection("users").document(userId).get().await()
            val user = doc.toObject(User::class.java) ?: User(id = userId)
            emit(user)
        } catch (e: Exception) {
            Timber.e(e)
            emit(User(id = userId))
        }
    }

    suspend fun updateProfile(userId: String, updates: Map<String, Any>) {
        firestore.collection("users").document(userId).update(updates).await()
    }

    suspend fun updateTopSongs(userId: String, songs: List<Song>) {
        firestore.collection("users").document(userId)
            .update("topSongs", songs).await()
    }

    suspend fun updateTopMovies(userId: String, movies: List<Movie>) {
        firestore.collection("users").document(userId)
            .update("topMovies", movies).await()
    }

    suspend fun updateTopBooks(userId: String, books: List<Book>) {
        firestore.collection("users").document(userId)
            .update("topBooks", books).await()
    }

    suspend fun uploadAvatar(userId: String, imageBytes: ByteArray): String {
        val ref = storage.reference.child("avatars/$userId.webp")
        ref.putBytes(imageBytes).await()
        val url = ref.downloadUrl.await().toString()
        firestore.collection("users").document(userId)
            .update("avatarUrl", url).await()
        return url
    }

    suspend fun uploadCover(userId: String, imageBytes: ByteArray): String {
        val ref = storage.reference.child("covers/$userId.webp")
        ref.putBytes(imageBytes).await()
        val url = ref.downloadUrl.await().toString()
        firestore.collection("users").document(userId)
            .update("coverUrl", url).await()
        return url
    }

    suspend fun followUser(myId: String, targetId: String) {
        firestore.collection("follows").document("${myId}_${targetId}")
            .set(mapOf("followerId" to myId, "followingId" to targetId, "timestamp" to System.currentTimeMillis()))
            .await()
        firestore.collection("users").document(myId)
            .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
        firestore.collection("users").document(targetId)
            .update("followersCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
    }

    suspend fun unfollowUser(myId: String, targetId: String) {
        firestore.collection("follows").document("${myId}_${targetId}").delete().await()
        firestore.collection("users").document(myId)
            .update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
        firestore.collection("users").document(targetId)
            .update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
    }

    suspend fun isFollowing(myId: String, targetId: String): Boolean {
        val doc = firestore.collection("follows").document("${myId}_${targetId}").get().await()
        return doc.exists()
    }

    suspend fun searchUsers(query: String): List<User> {
        return try {
            firestore.collection("users")
                .whereGreaterThanOrEqualTo("username", query)
                .whereLessThanOrEqualTo("username", "$query\uf8ff")
                .limit(20)
                .get().await()
                .toObjects(User::class.java)
        } catch (e: Exception) {
            Timber.e(e)
            emptyList()
        }
    }

    suspend fun getSuggestedUsers(myId: String): List<User> {
        return try {
            firestore.collection("users")
                .whereNotEqualTo("id", myId)
                .limit(10)
                .get().await()
                .toObjects(User::class.java)
        } catch (e: Exception) {
            Timber.e(e)
            emptyList()
        }
    }
}
