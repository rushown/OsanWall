package com.osanwall.data.repository

/** Single source of truth for all Firestore/RTDB collection and field names. */
object Collections {
    const val USERS = "users"
    const val POSTS = "posts"
    const val CHATS = "chats"
    const val FOLLOWS = "follows"
    const val NOTIFICATIONS = "notifications"
    const val REPORTS = "reports"
    const val BANNED = "banned"
    const val COMMENTS = "comments"
}

object RtdbPaths {
    const val MESSAGES = "messages"
    const val CHATS = "chats"
    const val TYPING = "typing"
    const val PRESENCE = "presence"
}

object Fields {
    const val USER_ID = "userId"
    const val TIMESTAMP = "timestamp"
    const val LIKES_COUNT = "likesCount"
    const val COMMENTS_COUNT = "commentsCount"
    const val LIKES = "likes"
    const val FOLLOWERS_COUNT = "followersCount"
    const val FOLLOWING_COUNT = "followingCount"
    const val POSTS_COUNT = "postsCount"
    const val TOP_SONGS = "topSongs"
    const val TOP_MOVIES = "topMovies"
    const val TOP_BOOKS = "topBooks"
    const val AVATAR_URL = "avatarUrl"
    const val COVER_URL = "coverUrl"
    const val BIO = "bio"
    const val FCM_TOKEN = "fcmToken"
    const val LAST_MESSAGE = "lastMessage"
    const val LAST_MESSAGE_TIME = "lastMessageTime"
    const val LAST_MESSAGE_SENDER_ID = "lastMessageSenderId"
    const val FOLLOWER_ID = "followerId"
    const val FOLLOWING_ID = "followingId"
    const val PARTICIPANTS = "participants"
}
