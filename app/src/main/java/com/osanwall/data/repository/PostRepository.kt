package com.merowall.data.repository

import androidx.paging.*
import com.google.firebase.firestore.FieldValue
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
        private const val CACHE_TTL = 7 * 24 * 60 * 60 * 1000L
    }

    private val posts get() = firestore.collection(Collections.POSTS)

    fun getFeedPosts(userId: String): Flow<PagingData<Post>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { FirestorePagingSource(firestore) }
        ).flow
    }

    fun getCachedPostsFlow(): Flow<List<Post>> =
        postDao.getPostsFlow().map { it.map { e -> e.toPost(json) } }

    suspend fun likePost(postId: String, userId: String, isLiked: Boolean) {
        try {
            val update = if (isLiked)
                mapOf(
                    Fields.LIKES to FieldValue.arrayUnion(userId),
                    Fields.LIKES_COUNT to FieldValue.increment(1)
                )
            else
                mapOf(
                    Fields.LIKES to FieldValue.arrayRemove(userId),
                    Fields.LIKES_COUNT to FieldValue.increment(-1)
                )
            posts.document(postId).update(update).await()
        } catch (e: Exception) {
            Timber.e(e, "likePost failed for $postId")
        }
    }

    suspend fun createPost(post: Post) {
        try {
            val ref = posts.document()
            val newPost = post.copy(id = ref.id, timestamp = System.currentTimeMillis())
            ref.set(newPost).await()
            // Update user post count
            firestore.collection(Collections.USERS).document(post.userId)
                .update(Fields.POSTS_COUNT, FieldValue.increment(1)).await()
            postDao.insertPost(newPost.toEntity(json))
        } catch (e: Exception) {
            Timber.e(e, "createPost failed")
            throw e
        }
    }

    suspend fun deletePost(postId: String, userId: String) {
        try {
            val batch = firestore.batch()
            batch.delete(posts.document(postId))
            batch.update(
                firestore.collection(Collections.USERS).document(userId),
                Fields.POSTS_COUNT, FieldValue.increment(-1)
            )
            batch.commit().await()
        } catch (e: Exception) {
            Timber.e(e, "deletePost failed for $postId")
            throw e
        }
    }

    suspend fun getComments(postId: String): List<Comment> {
        return try {
            posts.document(postId)
                .collection(Collections.COMMENTS)
                .orderBy(Fields.TIMESTAMP, Query.Direction.ASCENDING)
                .get().await()
                .toObjects(Comment::class.java)
        } catch (e: Exception) {
            Timber.e(e, "getComments failed for $postId")
            emptyList()
        }
    }

    suspend fun addComment(postId: String, comment: Comment) {
        try {
            val batch = firestore.batch()
            val commentRef = posts.document(postId).collection(Collections.COMMENTS).document()
            batch.set(commentRef, comment.copy(id = commentRef.id))
            batch.update(posts.document(postId), Fields.COMMENTS_COUNT, FieldValue.increment(1))
            batch.commit().await()
        } catch (e: Exception) {
            Timber.e(e, "addComment failed for $postId")
            throw e
        }
    }

    suspend fun cleanOldCache() {
        try {
            postDao.deleteOldPosts(System.currentTimeMillis() - CACHE_TTL)
        } catch (e: Exception) {
            Timber.e(e, "cleanOldCache failed")
        }
    }
}

class FirestorePagingSource(
    private val firestore: FirebaseFirestore
) : PagingSource<Any, Post>() {

    override suspend fun load(params: LoadParams<Any>): LoadResult<Any, Post> {
        return try {
            var query = firestore.collection(Collections.POSTS)
                .orderBy(Fields.TIMESTAMP, Query.Direction.DESCENDING)
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
            Timber.e(e, "FirestorePagingSource load failed")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Any, Post>) = null
}

private fun CachedPost.toPost(json: Json) = Post(
    id = id, userId = userId, userUsername = userUsername, userAvatarUrl = userAvatarUrl,
    type = PostType.valueOf(type), content = content,
    mediaData = mediaDataJson?.let { runCatching { json.decodeFromString<MediaData>(it) }.getOrNull() },
    likesCount = likesCount, commentsCount = commentsCount, isLiked = isLiked, timestamp = timestamp
)

private fun Post.toEntity(json: Json) = CachedPost(
    id = id, userId = userId, userUsername = userUsername, userAvatarUrl = userAvatarUrl,
    type = type.name, content = content,
    mediaDataJson = mediaData?.let { runCatching { json.encodeToString(MediaData.serializer(), it) }.getOrNull() },
    likesCount = likesCount, commentsCount = commentsCount, isLiked = isLiked, timestamp = timestamp
)
