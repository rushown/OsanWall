package com.merowall.data.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import com.merowall.data.model.*

@Database(
    entities = [CachedPost::class, CachedMessage::class, CachedUser::class, CachedChat::class],
    version = 1,
    exportSchema = false
)
abstract class MeroWallDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
}
