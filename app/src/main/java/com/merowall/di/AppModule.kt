package com.merowall.di

import android.content.Context
import androidx.room.Room
import com.merowall.BuildConfig
import com.merowall.data.api.*
import com.merowall.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson() = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    @Named("tmdb")
    fun provideTmdbRetrofit(json: Json, client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/")
            .client(
                client.newBuilder()
                    .addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer ${BuildConfig.TMDB_API_KEY}")
                            .build()
                        chain.proceed(req)
                    }
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("spotify")
    fun provideSpotifyRetrofit(json: Json, client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("spotifyAuth")
    fun provideSpotifyAuthRetrofit(json: Json, client: OkHttpClient): Retrofit {
        val credentials = android.util.Base64.encodeToString(
            "${BuildConfig.SPOTIFY_CLIENT_ID}:${BuildConfig.SPOTIFY_CLIENT_SECRET}".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        return Retrofit.Builder()
            .baseUrl("https://accounts.spotify.com/")
            .client(
                client.newBuilder()
                    .addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                            .addHeader("Authorization", "Basic $credentials")
                            .build()
                        chain.proceed(req)
                    }
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/x-www-form-urlencoded".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("googleBooks")
    fun provideGoogleBooksRetrofit(json: Json, client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(
                client.newBuilder()
                    .addInterceptor { chain ->
                        val url = chain.request().url.newBuilder()
                            .addQueryParameter("key", BuildConfig.GOOGLE_BOOKS_API_KEY)
                            .build()
                        chain.proceed(chain.request().newBuilder().url(url).build())
                    }
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("worker")
    fun provideWorkerRetrofit(json: Json, client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.CLOUDFLARE_WORKER_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(@Named("tmdb") retrofit: Retrofit): TmdbApiService =
        retrofit.create(TmdbApiService::class.java)

    @Provides
    @Singleton
    fun provideSpotifyApiService(@Named("spotify") retrofit: Retrofit): SpotifyApiService =
        retrofit.create(SpotifyApiService::class.java)

    @Provides
    @Singleton
    fun provideSpotifyAuthService(@Named("spotifyAuth") retrofit: Retrofit): SpotifyAuthService =
        retrofit.create(SpotifyAuthService::class.java)

    @Provides
    @Singleton
    fun provideGoogleBooksApiService(@Named("googleBooks") retrofit: Retrofit): GoogleBooksApiService =
        retrofit.create(GoogleBooksApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeroWallDatabase {
        return Room.databaseBuilder(context, MeroWallDatabase::class.java, "merowall.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun providePostDao(db: MeroWallDatabase) = db.postDao()
    @Provides fun provideMessageDao(db: MeroWallDatabase) = db.messageDao()
    @Provides fun provideUserDao(db: MeroWallDatabase) = db.userDao()
    @Provides fun provideChatDao(db: MeroWallDatabase) = db.chatDao()
}
