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
import com.osanwall.ui.components.OsanWallActionSheet
import com.osanwall.ui.components.OsanWallBottomBar
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
    var showCreateSheet by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            OsanWallBottomBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    when (route) {
                        "create" -> showCreateSheet = true
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
        showCreateSheet -> OsanWallActionSheet(
            title = "Create on OsanWall",
            subtitle = "Choose a format to post: thought, song, movie, or book.",
            primaryLabel = "Start Creating",
            onDismiss = { showCreateSheet = false },
            onPrimary = {
                showCreateSheet = false
                navController.navigate("discover") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
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
