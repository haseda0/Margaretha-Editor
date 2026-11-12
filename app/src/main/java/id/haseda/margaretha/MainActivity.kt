package id.haseda.margaretha

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.haseda.margaretha.account.AccountScreen
import id.haseda.margaretha.account.AccountState
import id.haseda.margaretha.account.AuthScreen
import id.haseda.margaretha.editor.EditorScreen
import id.haseda.margaretha.editor.EditorViewModel
import id.haseda.margaretha.home.HomeScreen
import id.haseda.margaretha.home.HomeViewModel
import id.haseda.margaretha.home.OnboardingScreen
import id.haseda.margaretha.home.SettingsScreen
import id.haseda.margaretha.ui.MargarethaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A cold start from a verification or recovery email carries the link in the launch intent.
        if (savedInstanceState == null) handleAuthLink(intent)
        setContent {
            MargarethaTheme { MargarethaRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthLink(intent)
    }

    private fun handleAuthLink(intent: Intent?) {
        val data = intent?.data ?: return
        (application as MargarethaApp).container.account.handleDeepLink(data)
    }
}

/** Screens outside the editor. The editor itself is tracked by the open project id. */
private enum class Screen { HOME, SETTINGS, AUTH, ACCOUNT }

@Composable
private fun MargarethaRoot() {
    val homeVm: HomeViewModel = viewModel()
    val editorVm: EditorViewModel = viewModel()
    val account = homeVm.account
    val accountState by account.state.collectAsStateWithLifecycle()
    val notice by account.notice.collectAsStateWithLifecycle()
    // Saved across rotation and process death; the editor reloads the autosaved project.
    var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var onboarding by rememberSaveable { mutableStateOf(!homeVm.settings.onboardingDone) }
    val snackbar = remember { SnackbarHostState() }

    // Account notices (verified, session expired, recovery) can arrive on any screen.
    LaunchedEffect(notice) {
        notice?.let {
            account.consumeNotice()
            snackbar.showSnackbar(it)
        }
    }
    // A recovery link signs the user in and must lead straight to "set a new password".
    LaunchedEffect(accountState) {
        if ((accountState as? AccountState.SignedIn)?.needsNewPassword == true && openProjectId == null) screen = Screen.ACCOUNT
    }

    Box(Modifier.fillMaxSize()) {
        val id = openProjectId
        when {
            onboarding -> OnboardingScreen {
                homeVm.settings.onboardingDone = true
                onboarding = false
            }
            id != null -> {
                LaunchedEffect(id) { editorVm.open(id) }
                EditorScreen(vm = editorVm, onClose = {
                    editorVm.close()
                    openProjectId = null
                    homeVm.refresh()
                })
            }
            screen == Screen.SETTINGS -> SettingsScreen(
                vm = homeVm,
                onBack = { screen = Screen.HOME },
                onAccount = { screen = if (accountState is AccountState.SignedIn) Screen.ACCOUNT else Screen.AUTH },
            )
            screen == Screen.AUTH -> AuthScreen(account, onBack = { screen = Screen.HOME })
            screen == Screen.ACCOUNT -> AccountScreen(account, onBack = { screen = Screen.HOME })
            else -> HomeScreen(
                vm = homeVm,
                onOpen = { projectId: String, uris: List<Uri> ->
                    editorVm.open(projectId, uris)
                    openProjectId = projectId
                },
                onSettings = { screen = Screen.SETTINGS },
                onAccount = { screen = if (accountState is AccountState.SignedIn) Screen.ACCOUNT else Screen.AUTH },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).navigationBarsPadding().padding(top = 48.dp, start = 16.dp, end = 16.dp))
    }
}
