package com.merowall.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_posts", indices = [Index("timestamp")])
data class CachedPost(
    @PrimaryKey val id: String,
    val userId: String,
    val userUsername: String,
    val userAvatarUrl: String,
    val type: String,
    val content: String,
    val mediaDataJson: String?,
    val likesCount: Int,
    val commentsCount: Int,
    val isLiked: Boolean,
    val timestamp: Long,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_messages", indices = [Index("chatId"), Index("timestamp")])
data class CachedMessage(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val type: String,
    val mediaUrl: String,
    val isRead: Boolean,
    val timestamp: Long,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_users")
data class CachedUser(
    @PrimaryKey val id: String,
    val username: String,
    val bio: String,
    val avatarUrl: String,
    val coverUrl: String,
    val followersCount: Int,
    val followingCount: Int,
    val isVerified: Boolean,
    val topSongsJson: String,
    val topMoviesJson: String,
    val topBooksJson: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_chats", indices = [Index("lastMessageTime")])
data class CachedChat(
    @PrimaryKey val id: String,
    val participantsJson: String,
    val participantInfoJson: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val cachedAt: Long = System.currentTimeMillis()
)
