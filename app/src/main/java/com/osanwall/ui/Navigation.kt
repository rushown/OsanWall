package com.osanwall.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.osanwall.ui.components.OsanWallBottomBar
import com.osanwall.ui.chat.ChatDetailScreen
import com.osanwall.ui.chat.ChatListScreen
import com.osanwall.ui.discover.DiscoverScreen
import com.osanwall.ui.home.HomeScreen
import com.osanwall.ui.notifications.NotificationsScreen
import com.osanwall.ui.profile.ProfileScreen

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsState()

    var showAuthSheet by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            OsanWallBottomBar(
                currentRoute = currentRoute,
                isLoggedIn = authState.isLoggedIn,
                onNavigate = { route ->
                    when (route) {
                        "chat" -> {
                            if (authState.isLoggedIn) {
                                navController.navigate("chat") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                showAuthSheet = true
                            }
                        }
                        "profile" -> {
                            if (authState.isLoggedIn) {
                                navController.navigate("profile/me") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                showAuthSheet = true
                            }
                        }
                        else -> {
                            navController.navigate(route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    isLoggedIn = authState.isLoggedIn,
                    onRequestAuth = { showAuthSheet = true },
                    onOpenExplore = {
                        navController.navigate("discover") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenProfile = { userId ->
                        if (authState.isLoggedIn) {
                            navController.navigate("profile/${Uri.encode(userId)}")
                        } else {
                            showAuthSheet = true
                        }
                    },
                    onOpenMyProfile = {
                        if (authState.isLoggedIn) {
                            navController.navigate("profile/me") {
                                launchSingleTop = true
                            }
                        } else {
                            showAuthSheet = true
                        }
                    }
                )
            }
            composable("discover") {
                DiscoverScreen(
                    onNavigateToProfile = { userId ->
                        if (authState.isLoggedIn) {
                            navController.navigate("profile/${Uri.encode(userId)}")
                        } else {
                            showAuthSheet = true
                        }
                    }
                )
            }
            composable(
                route = "chat/{chatId}/{username}/{avatarUrl}",
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("username") { type = NavType.StringType },
                    navArgument("avatarUrl") { type = NavType.StringType }
                )
            ) { entry ->
                val chatId = Uri.decode(entry.arguments?.getString("chatId").orEmpty())
                val username = Uri.decode(entry.arguments?.getString("username").orEmpty())
                val avatarUrl = Uri.decode(entry.arguments?.getString("avatarUrl").orEmpty())
                ChatDetailScreen(
                    chatId = chatId,
                    otherUsername = username,
                    otherAvatarUrl = avatarUrl,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("chat") {
                ChatListScreen(
                    onOpenChat = { chatId, username, avatarUrl ->
                        navController.navigate(
                            "chat/${Uri.encode(chatId)}/${Uri.encode(username)}/${Uri.encode(avatarUrl)}"
                        )
                    }
                )
            }
            composable("notifications") {
                NotificationsScreen()
            }
            composable(
                route = "profile/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                val uid = entry.arguments?.getString("userId").orEmpty()
                if (uid == "me" && !authState.isLoggedIn) {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                        showAuthSheet = true
                    }
                    Box(Modifier.fillMaxSize())
                } else {
                    ProfileScreen(
                        userId = uid,
                        onBack = { navController.popBackStack() },
                        onOpenChat = { },
                        onSettings = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    when {
        showAuthSheet -> AuthBottomSheet(
            onDismiss = { showAuthSheet = false },
            onAuthenticated = {
                showAuthSheet = false
                navController.navigate("profile/me") {
                    launchSingleTop = true
                }
            }
        )
    }
}

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")

    object Home : Screen("home")
    object Discover : Screen("discover")
    object Create : Screen("create")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile/{userId}") {
        fun withId(userId: String) = "profile/$userId"
    }

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
