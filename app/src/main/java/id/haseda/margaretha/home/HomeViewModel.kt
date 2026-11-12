package id.haseda.margaretha.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import id.haseda.margaretha.MargarethaApp
import id.haseda.margaretha.data.ExportDestination
import id.haseda.margaretha.data.ExportRecord
import id.haseda.margaretha.data.ProjectRepository
import id.haseda.margaretha.data.StorageStatus
import id.haseda.margaretha.data.UserStorage
import id.haseda.margaretha.timeline.ProjectCodec
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
    val exports: List<ExportRecord> = emptyList(),
    val storage: StorageStatus = StorageStatus.AppManaged,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as MargarethaApp).container.repository
    val thumbnails = (app as MargarethaApp).container.thumbnails
    val settings = (app as MargarethaApp).container.settings
    val account = (app as MargarethaApp).container.account
    private val storage = (app as MargarethaApp).container.storage
    private val exportHistory = (app as MargarethaApp).container.exportHistory

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
        // The project list is scoped to the account, so it follows sign-in and sign-out.
        viewModelScope.launch { account.state.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            val list = runCatching { repository.list(account.ownerId) }.getOrElse {
                _state.update { s -> s.copy(loading = false, message = "Gagal membaca daftar proyek.") }
                return@launch
            }
            // A session marker that survived means the app was killed while editing.
            val openId = repository.sessionProjectId()
            val exports = withContext(Dispatchers.IO) { exportHistory.list() }
            val folder = withContext(Dispatchers.IO) { storage.status() }
            _state.update { it.copy(loading = false, projects = list, recoverable = list.firstOrNull { p -> p.id == openId }, exports = exports, storage = folder) }
        }
    }

    fun dismissRecovery() {
        repository.clearSession()
        _state.update { it.copy(recoverable = null) }
    }

    suspend fun create(name: String, aspect: AspectRatio): String? = runCatching {
        repository.create(name.trim().ifEmpty { "Proyek baru" }.take(TimelineEngine.MAX_NAME_LENGTH), aspect, System.currentTimeMillis(), settings.defaultFrameRate, account.ownerId).id
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

    private fun say(text: String) = _state.update { it.copy(message = text) }

    // ---- User-selected storage ------------------------------------------------------------------

    /** Called with the folder the user picked in the system file picker. */
    fun adoptFolder(uri: Uri) {
        viewModelScope.launch {
            when (val status = withContext(Dispatchers.IO) { storage.adopt(uri) }) {
                is StorageStatus.Ready -> say("Folder \"${status.folderName}\" siap dipakai.")
                is StorageStatus.Unavailable -> say(status.reason)
                StorageStatus.AppManaged -> Unit
            }
            refresh()
        }
    }

    fun releaseFolder() {
        storage.release()
        settings.exportDestination = ExportDestination.GALLERY
        settings.autoBackup = false
        say("Folder dilepas. File yang sudah ada di sana tidak dihapus.")
        refresh()
    }

    /** Writes every project of this account into MargrethaEditor/Backups in the chosen folder. */
    fun backupAllToFolder() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val all = repository.exportAll(account.ownerId)
                    all.forEach { (name, text) ->
                        storage.write(listOf(UserStorage.BACKUPS), name, "application/json") { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    all.size
                }
            }
            say(result.fold({ "$it proyek dicadangkan ke ${UserStorage.ROOT}/${UserStorage.BACKUPS}." }, { "Cadangan gagal: ${it.message}" }))
            refresh()
        }
    }

    /** Saves one project file wherever the user chose in the system "save as" dialog. */
    fun backupProjectTo(projectId: String, target: Uri) {
        viewModelScope.launch {
            val loaded = repository.load(projectId) as? LoadResult.Loaded ?: return@launch say("Proyek tidak bisa dibaca.")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(target, "wt")?.use { it.write(ProjectCodec.encode(loaded.project).toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            say(if (ok) "Cadangan \"${loaded.project.name}\" tersimpan. Media tidak ikut disalin." else "Gagal menulis file cadangan.")
        }
    }

    fun backupFileName(projectId: String): String =
        _state.value.projects.firstOrNull { it.id == projectId }?.name.orEmpty().ifBlank { "Proyek" }.take(40) + ProjectRepository.BACKUP_EXTENSION

    /** Restores a project from a backup file. Existing projects are never overwritten. */
    fun restoreFrom(source: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(source)?.use { input ->
                        // Project files are small; refuse anything that clearly is not one.
                        val bytes = input.readBytes()
                        if (bytes.size > MAX_BACKUP_BYTES) null else bytes.toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            } ?: return@launch say("File cadangan tidak bisa dibaca atau terlalu besar.")
            when (val r = repository.importProject(text, account.ownerId, System.currentTimeMillis())) {
                is LoadResult.Failed -> say("Bukan cadangan yang valid: ${r.reason}")
                is LoadResult.Loaded -> say("\"${r.project.name}\" dipulihkan. Tautkan ulang media bila ada yang hilang.")
            }
            refresh()
        }
    }

    fun clearExportHistory() {
        exportHistory.clear()
        refresh()
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
    }
}
