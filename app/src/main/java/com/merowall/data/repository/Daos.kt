package com.merowall.data.repository

import androidx.paging.PagingSource
import androidx.room.*
import com.merowall.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM cached_posts ORDER BY timestamp DESC")
    fun getAllPosts(): PagingSource<Int, CachedPost>

    @Query("SELECT * FROM cached_posts ORDER BY timestamp DESC LIMIT 50")
    fun getPostsFlow(): Flow<List<CachedPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<CachedPost>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: CachedPost)

    @Query("DELETE FROM cached_posts WHERE cachedAt < :before")
    suspend fun deleteOldPosts(before: Long)

    @Query("UPDATE cached_posts SET likesCount = :count, isLiked = :liked WHERE id = :postId")
    suspend fun updateLike(postId: String, count: Int, liked: Boolean)

    @Query("DELETE FROM cached_posts")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM cached_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<CachedMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: CachedMessage)

    @Query("DELETE FROM cached_messages WHERE cachedAt < :before")
    suspend fun deleteOldMessages(before: Long)

    @Query("UPDATE cached_messages SET isRead = 1 WHERE chatId = :chatId AND senderId != :myId")
    suspend fun markMessagesRead(chatId: String, myId: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM cached_users WHERE id = :userId")
    suspend fun getUserById(userId: String): CachedUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: CachedUser)

    @Query("DELETE FROM cached_users WHERE cachedAt < :before")
    suspend fun deleteOldUsers(before: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM cached_chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<CachedChat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<CachedChat>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: CachedChat)

    @Query("DELETE FROM cached_chats")
    suspend fun clearAll()
}
