package com.merowall.data.repository

import com.google.firebase.firestore.FieldValue
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
    private val users get() = firestore.collection(Collections.USERS)
    private val follows get() = firestore.collection(Collections.FOLLOWS)

    fun getUserFlow(userId: String): Flow<User> = flow {
        try {
            val doc = users.document(userId).get().await()
            emit(doc.toObject(User::class.java) ?: User(id = userId))
        } catch (e: Exception) {
            Timber.e(e, "getUserFlow failed for $userId")
            emit(User(id = userId))
        }
    }

    suspend fun updateProfile(userId: String, updates: Map<String, Any>) {
        try {
            users.document(userId).update(updates).await()
        } catch (e: Exception) {
            Timber.e(e, "updateProfile failed")
            throw e
        }
    }

    suspend fun updateTopSongs(userId: String, songs: List<Song>) {
        try {
            users.document(userId).update(Fields.TOP_SONGS, songs).await()
        } catch (e: Exception) {
            Timber.e(e, "updateTopSongs failed")
            throw e
        }
    }

    suspend fun updateTopMovies(userId: String, movies: List<Movie>) {
        try {
            users.document(userId).update(Fields.TOP_MOVIES, movies).await()
        } catch (e: Exception) {
            Timber.e(e, "updateTopMovies failed")
            throw e
        }
    }

    suspend fun updateTopBooks(userId: String, books: List<Book>) {
        try {
            users.document(userId).update(Fields.TOP_BOOKS, books).await()
        } catch (e: Exception) {
            Timber.e(e, "updateTopBooks failed")
            throw e
        }
    }

    suspend fun uploadAvatar(userId: String, imageBytes: ByteArray): String {
        return try {
            val ref = storage.reference.child("avatars/$userId.webp")
            ref.putBytes(imageBytes).await()
            val url = ref.downloadUrl.await().toString()
            users.document(userId).update(Fields.AVATAR_URL, url).await()
            url
        } catch (e: Exception) {
            Timber.e(e, "uploadAvatar failed")
            throw e
        }
    }

    suspend fun uploadCover(userId: String, imageBytes: ByteArray): String {
        return try {
            val ref = storage.reference.child("covers/$userId.webp")
            ref.putBytes(imageBytes).await()
            val url = ref.downloadUrl.await().toString()
            users.document(userId).update(Fields.COVER_URL, url).await()
            url
        } catch (e: Exception) {
            Timber.e(e, "uploadCover failed")
            throw e
        }
    }

    suspend fun followUser(myId: String, targetId: String) {
        if (myId == targetId) return
        try {
            val batch = firestore.batch()
            val followRef = follows.document("${myId}_${targetId}")
            batch.set(followRef, mapOf(
                Fields.FOLLOWER_ID to myId,
                Fields.FOLLOWING_ID to targetId,
                Fields.TIMESTAMP to System.currentTimeMillis()
            ))
            batch.update(users.document(myId), Fields.FOLLOWING_COUNT, FieldValue.increment(1))
            batch.update(users.document(targetId), Fields.FOLLOWERS_COUNT, FieldValue.increment(1))
            batch.commit().await()
        } catch (e: Exception) {
            Timber.e(e, "followUser failed")
            throw e
        }
    }

    suspend fun unfollowUser(myId: String, targetId: String) {
        try {
            val batch = firestore.batch()
            batch.delete(follows.document("${myId}_${targetId}"))
            batch.update(users.document(myId), Fields.FOLLOWING_COUNT, FieldValue.increment(-1))
            batch.update(users.document(targetId), Fields.FOLLOWERS_COUNT, FieldValue.increment(-1))
            batch.commit().await()
        } catch (e: Exception) {
            Timber.e(e, "unfollowUser failed")
            throw e
        }
    }

    suspend fun isFollowing(myId: String, targetId: String): Boolean {
        return try {
            follows.document("${myId}_${targetId}").get().await().exists()
        } catch (e: Exception) {
            Timber.e(e, "isFollowing check failed")
            false
        }
    }

    suspend fun searchUsers(query: String): List<User> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return try {
            users
                .whereGreaterThanOrEqualTo(Fields.USER_ID, q)
                .whereLessThanOrEqualTo(Fields.USER_ID, "$q\uf8ff")
                .limit(20)
                .get().await()
                .toObjects(User::class.java)
        } catch (e: Exception) {
            Timber.e(e, "searchUsers failed")
            emptyList()
        }
    }

    suspend fun getSuggestedUsers(myId: String): List<User> {
        return try {
            users.limit(10).get().await()
                .toObjects(User::class.java)
                .filter { it.id != myId }
                .take(8)
        } catch (e: Exception) {
            Timber.e(e, "getSuggestedUsers failed")
            emptyList()
        }
    }

    suspend fun updateFcmToken(userId: String, token: String) {
        try {
            users.document(userId).update(Fields.FCM_TOKEN, token).await()
        } catch (e: Exception) {
            Timber.e(e, "updateFcmToken failed")
        }
    }
}
