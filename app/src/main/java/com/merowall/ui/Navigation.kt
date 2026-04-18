package com.osanwall.ui

sealed class Screen(val route: String) {
    // Auth
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")

    // Main tabs
    object Home : Screen("home")
    object Discover : Screen("discover")
    object Create : Screen("create")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile/{userId}") {
        fun withId(userId: String) = "profile/$userId"
    }

    // Secondary
    object Chat : Screen("chat")
    object ChatDetail : Screen("chat/{chatId}/{username}/{avatarUrl}") {
        fun withArgs(chatId: String, username: String, avatarUrl: String) =
            "chat/$chatId/$username/${android.net.Uri.encode(avatarUrl)}"
    }
    object PostDetail : Screen("post/{postId}") {
        fun withId(postId: String) = "post/$postId"
    }
    object EditProfile : Screen("edit_profile")
    object Settings : Screen("settings")
    object SearchMedia : Screen("search_media/{type}") {
        fun withType(type: String) = "search_media/$type"
    }
}
