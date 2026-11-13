package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.ClipRepository
import com.example.data.repository.ViralClipAiService
import com.example.data.repository.YouTubeMetadataService
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.studio.ClipStudioScreen
import com.example.ui.studio.ClipStudioViewModel
import com.example.ui.studio.ProjectClipsScreen
import com.example.ui.studio.ProjectClipsViewModel
import com.example.ui.theme.ClipPulseTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_HOME = "home"
private const val ROUTE_PROJECT = "project/{projectId}"
private const val ROUTE_STUDIO = "studio/{projectId}/{clipId}"

class MainActivity : ComponentActivity() {

    private var sharedYouTubeText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        extractSharedUrl(intent)?.let { sharedYouTubeText = it }

        enableEdgeToEdge()
        setContent {
            ClipPulseTheme {
                AppNavigation(
                    sharedUrl = sharedYouTubeText,
                    onConsumeSharedUrl = { sharedYouTubeText = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedUrl(intent)?.let { sharedYouTubeText = it }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        return null
    }
}

@Composable
fun AppNavigation(
    sharedUrl: String?,
    onConsumeSharedUrl: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedNavHost(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email ?: "Creator",
                userPhotoUrl = user.photoUrl?.toString(),
                sharedUrl = sharedUrl,
                onConsumeSharedUrl = onConsumeSharedUrl
            )
        }
    }
}

@Composable
private fun AuthenticatedNavHost(
    currentUserId: String,
    userDisplayName: String,
    userPhotoUrl: String?,
    sharedUrl: String?,
    onConsumeSharedUrl: () -> Unit
) {
    val navController = rememberNavController()
    val youtubeService = remember { YouTubeMetadataService() }
    val aiService = remember { ViralClipAiService() }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(ROUTE_HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                key = "home_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        HomeViewModel(
                            repository = ClipRepository(db),
                            youtubeService = youtubeService,
                            aiService = aiService,
                            currentUserId = currentUserId
                        )
                    }
                }
            )

            LaunchedEffect(sharedUrl) {
                if (!sharedUrl.isNullOrBlank()) {
                    homeViewModel.onUrlChanged(sharedUrl)
                    onConsumeSharedUrl()
                }
            }

            HomeScreen(
                viewModel = homeViewModel,
                userDisplayName = userDisplayName,
                userPhotoUrl = userPhotoUrl,
                onOpenProject = { projectId ->
                    navController.navigate("project/$projectId")
                }
            )
        }

        composable(
            route = ROUTE_PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val projectClipsViewModel: ProjectClipsViewModel = viewModel(
                key = "project_${currentUserId}_$projectId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        ProjectClipsViewModel(
                            repository = ClipRepository(db),
                            currentUserId = currentUserId,
                            projectId = projectId
                        )
                    }
                }
            )

            ProjectClipsScreen(
                viewModel = projectClipsViewModel,
                onBack = { navController.popBackStack() },
                onOpenClipStudio = { clipId ->
                    navController.navigate("studio/$projectId/$clipId")
                }
            )
        }

        composable(
            route = ROUTE_STUDIO,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("clipId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val clipId = backStackEntry.arguments?.getString("clipId").orEmpty()
            val studioViewModel: ClipStudioViewModel = viewModel(
                key = "studio_${currentUserId}_${projectId}_$clipId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        ClipStudioViewModel(
                            repository = ClipRepository(db),
                            aiService = aiService,
                            currentUserId = currentUserId,
                            projectId = projectId,
                            clipId = clipId
                        )
                    }
                }
            )

            ClipStudioScreen(
                viewModel = studioViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
