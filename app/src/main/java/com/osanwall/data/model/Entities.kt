package com.osanwall.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_posts")
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

@Entity(tableName = "cached_messages")
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
    val username: String = "",
    val avatarUrl: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_chats")
data class CachedChat(
    @PrimaryKey val id: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSenderId: String = "",
    val unreadCount: Int = 0,
    val cachedAt: Long = System.currentTimeMillis()
)
