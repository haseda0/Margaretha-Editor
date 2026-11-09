package id.haseda.margaretha

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import id.haseda.margaretha.editor.EditorScreen
import id.haseda.margaretha.editor.EditorViewModel
import id.haseda.margaretha.home.HomeScreen
import id.haseda.margaretha.home.HomeViewModel
import id.haseda.margaretha.ui.MargarethaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MargarethaTheme { MargarethaRoot() }
        }
    }
}

@Composable
private fun MargarethaRoot() {
    val homeVm: HomeViewModel = viewModel()
    val editorVm: EditorViewModel = viewModel()
    // Saved across rotation and process death; the editor reloads the autosaved project.
    var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }

    val id = openProjectId
    if (id == null) {
        HomeScreen(
            vm = homeVm,
            onOpen = { projectId: String, uris: List<Uri> ->
                editorVm.open(projectId, uris)
                openProjectId = projectId
            },
        )
    } else {
        LaunchedEffect(id) { editorVm.open(id) }
        val close = {
            editorVm.close()
            openProjectId = null
            homeVm.refresh()
        }
        EditorScreen(vm = editorVm, onClose = close)
    }
}
