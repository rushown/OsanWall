package com.merowall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.*
import androidx.navigation.compose.*
import com.merowall.ui.*
import com.merowall.ui.chat.*
import com.merowall.ui.components.MeroWallBottomBar
import com.merowall.ui.discover.DiscoverScreen
import com.merowall.ui.profile.ProfileScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MeroWallApp()
        }
    }
}

@Composable
fun MeroWallApp() {
    val authViewModel: AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val authState by authViewModel.uiState.collectAsState()

    MeroWallTheme {
        if (authState.isLoggedIn) {
            MainGraph(onSignOut = { authViewModel.signOut() })
        } else {
            AuthGraph(onLoggedIn = { /* state change triggers recomposition */ })
        }
    }
}

@Composable
fun AuthGraph(onLoggedIn: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login", enterTransition = { fadeIn(tween(300)) }, exitTransition = { fadeOut(tween(300)) }) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate("register") },
                onLoggedIn = onLoggedIn
            )
        }
        composable("register", enterTransition = { slideInHorizontally { it } + fadeIn(tween(300)) }, exitTransition = { slideOutHorizontally { it } + fadeOut(tween(300)) }) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onLoggedIn = onLoggedIn
            )
        }
    }
}

@Composable
fun MainGraph(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = setOf("home", "discover", "notifications", "profile/{userId}")
    val showBottomBar = currentRoute in bottomBarRoutes || currentRoute?.startsWith("profile") == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MeroWallBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        when (route) {
                            "home" -> navController.navigate("home") { popUpTo("home") { inclusive = true } }
                            "discover" -> navController.navigate("discover") { launchSingleTop = true }
                            "create" -> navController.navigate("create")
                            "notifications" -> navController.navigate("notifications") { launchSingleTop = true }
                            "profile" -> navController.navigate("profile/me") { launchSingleTop = true }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 10 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 10 } }
        ) {
            composable("home") {
                com.merowall.ui.home.HomeScreen(
                    onNavigateToProfile = { navController.navigate("profile/$it") },
                    onNavigateToPostDetail = { navController.navigate("post/$it") },
                    onNavigateToCreate = { navController.navigate("create") }
                )
            }
            composable("discover") {
                DiscoverScreen(
                    onNavigateToProfile = { navController.navigate("profile/$it") }
                )
            }
            composable("create") {
                CreatePostSheet(onDismiss = { navController.popBackStack() })
            }
            composable("notifications") {
                NotificationsScreen()
            }
            composable(
                "profile/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: "me"
                ProfileScreen(
                    userId = userId,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { otherId ->
                        navController.navigate("chat")
                    },
                    onSettings = { navController.navigate("settings") }
                )
            }
            composable("chat") {
                ChatListScreen(
                    onOpenChat = { chatId, username, avatarUrl ->
                        navController.navigate("chat_detail/$chatId/$username/${android.net.Uri.encode(avatarUrl)}")
                    }
                )
            }
            composable(
                "chat_detail/{chatId}/{username}/{avatarUrl}",
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("username") { type = NavType.StringType },
                    navArgument("avatarUrl") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                ChatDetailScreen(
                    chatId = backStackEntry.arguments?.getString("chatId") ?: "",
                    otherUsername = backStackEntry.arguments?.getString("username") ?: "",
                    otherAvatarUrl = android.net.Uri.decode(backStackEntry.arguments?.getString("avatarUrl") ?: ""),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() }, onSignOut = onSignOut)
            }
        }
    }
}

// ── Placeholder Screens ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(0.9f))
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Notifications coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostSheet(onDismiss: () -> Unit) {
    var content by remember { mutableStateOf("") }
    val viewModel: com.merowall.ui.home.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Post", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(androidx.compose.material.icons.Icons.Default.Close, null) } },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.createPost(com.merowall.data.model.PostType.THOUGHT, content)
                            onDismiss()
                        },
                        enabled = content.isNotBlank()
                    ) { Text("Post", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("What's on your mind?") },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Sign Out")
            }
        }
    }
}
