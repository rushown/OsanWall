package com.merowall.data.repository

import androidx.paging.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.merowall.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val postDao: PostDao,
    private val json: Json
) {
    companion object {
        private const val PAGE_SIZE = 20
        private const val CACHE_TTL = 7 * 24 * 60 * 60 * 1000L // 7 days
    }

    fun getFeedPosts(userId: String): Flow<PagingData<Post>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { FirestorePagingSource(firestore, userId) }
        ).flow
    }

    fun getCachedPostsFlow(): Flow<List<Post>> {
        return postDao.getPostsFlow().map { entities ->
            entities.map { it.toPost(json) }
        }
    }

    suspend fun likePost(postId: String, userId: String, isLiked: Boolean) {
        try {
            val ref = firestore.collection("posts").document(postId)
            if (isLiked) {
                ref.update("likes", com.google.firebase.firestore.FieldValue.arrayUnion(userId)).await()
            } else {
                ref.update("likes", com.google.firebase.firestore.FieldValue.arrayRemove(userId)).await()
            }
        } catch (e: Exception) {
            Timber.e(e, "Like post failed")
        }
    }

    suspend fun createPost(post: Post) {
        try {
            val ref = firestore.collection("posts").document()
            val newPost = post.copy(id = ref.id)
            ref.set(newPost).await()
            postDao.insertPost(newPost.toEntity(json))
        } catch (e: Exception) {
            Timber.e(e, "Create post failed")
            throw e
        }
    }

    suspend fun deletePost(postId: String) {
        firestore.collection("posts").document(postId).delete().await()
    }

    suspend fun getComments(postId: String): List<Comment> {
        return try {
            firestore.collection("posts").document(postId)
                .collection("comments")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get().await()
                .toObjects(Comment::class.java)
        } catch (e: Exception) {
            Timber.e(e)
            emptyList()
        }
    }

    suspend fun addComment(postId: String, comment: Comment) {
        val ref = firestore.collection("posts").document(postId)
            .collection("comments").document()
        ref.set(comment.copy(id = ref.id)).await()
        firestore.collection("posts").document(postId)
            .update("commentsCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
    }

    suspend fun cleanOldCache() {
        postDao.deleteOldPosts(System.currentTimeMillis() - CACHE_TTL)
    }
}

class FirestorePagingSource(
    private val firestore: FirebaseFirestore,
    private val userId: String
) : PagingSource<Any, Post>() {

    override suspend fun load(params: LoadParams<Any>): LoadResult<Any, Post> {
        return try {
            var query = firestore.collection("posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(params.loadSize.toLong())

            if (params.key != null) {
                query = query.startAfter(params.key)
            }

            val snapshot = query.get().await()
            val posts = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Post::class.java)?.copy(id = doc.id)
            }

            LoadResult.Page(
                data = posts,
                prevKey = null,
                nextKey = if (posts.size < params.loadSize) null else snapshot.documents.lastOrNull()
            )
        } catch (e: Exception) {
            Timber.e(e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Any, Post>) = null
}

private fun CachedPost.toPost(json: Json) = Post(
    id = id,
    userId = userId,
    userUsername = userUsername,
    userAvatarUrl = userAvatarUrl,
    type = PostType.valueOf(type),
    content = content,
    mediaData = mediaDataJson?.let { json.decodeFromString(it) },
    likesCount = likesCount,
    commentsCount = commentsCount,
    isLiked = isLiked,
    timestamp = timestamp
)

private fun Post.toEntity(json: Json) = CachedPost(
    id = id,
    userId = userId,
    userUsername = userUsername,
    userAvatarUrl = userAvatarUrl,
    type = type.name,
    content = content,
    mediaDataJson = mediaData?.let { json.encodeToString(MediaData.serializer(), it) },
    likesCount = likesCount,
    commentsCount = commentsCount,
    isLiked = isLiked,
    timestamp = timestamp
)
