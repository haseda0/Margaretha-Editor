package id.haseda.margaretha.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.haseda.margaretha.MargarethaApp
import id.haseda.margaretha.data.LoadResult
import id.haseda.margaretha.data.ProjectSummary
import id.haseda.margaretha.timeline.AspectRatio
import id.haseda.margaretha.timeline.EditOutcome
import id.haseda.margaretha.timeline.RenameProject
import id.haseda.margaretha.timeline.TimelineEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val loading: Boolean = true,
    val projects: List<ProjectSummary> = emptyList(),
    val recoverable: ProjectSummary? = null,
    val message: String? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as MargarethaApp).container.repository
    val thumbnails = (app as MargarethaApp).container.thumbnails
    val settings = (app as MargarethaApp).container.settings

    /** Bytes held by re-creatable files (leftover renders, decoded caches). Never project data. */
    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) {
        getApplication<Application>().cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearCache() {
        thumbnails.trim()
        withContext(Dispatchers.IO) {
            getApplication<Application>().cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
        }
        _state.update { it.copy(message = "Cache dibersihkan. Proyek dan media tidak tersentuh.") }
    }

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val list = runCatching { repository.list() }.getOrElse {
                _state.update { s -> s.copy(loading = false, message = "Gagal membaca daftar proyek.") }
                return@launch
            }
            // A session marker that survived means the app was killed while editing.
            val openId = repository.sessionProjectId()
            _state.update { it.copy(loading = false, projects = list, recoverable = list.firstOrNull { p -> p.id == openId }) }
        }
    }

    fun dismissRecovery() {
        repository.clearSession()
        _state.update { it.copy(recoverable = null) }
    }

    suspend fun create(name: String, aspect: AspectRatio): String? = runCatching {
        repository.create(name.trim().ifEmpty { "Proyek baru" }.take(TimelineEngine.MAX_NAME_LENGTH), aspect, System.currentTimeMillis(), settings.defaultFrameRate).id
    }.getOrElse {
        _state.update { s -> s.copy(message = "Gagal membuat proyek: ${it.message}") }
        null
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            val loaded = repository.load(id) as? LoadResult.Loaded ?: return@launch
            when (val r = TimelineEngine.apply(loaded.project, RenameProject(name), System.currentTimeMillis())) {
                is EditOutcome.Applied -> runCatching { repository.save(r.project) }
                is EditOutcome.Rejected -> _state.update { it.copy(message = r.reason) }
            }
            refresh()
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            if (repository.duplicate(id, System.currentTimeMillis()) == null) _state.update { it.copy(message = "Gagal menduplikat proyek.") }
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refresh()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
