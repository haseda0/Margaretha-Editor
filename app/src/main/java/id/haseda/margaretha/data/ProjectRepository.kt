package id.haseda.margaretha.data

import android.content.Context
import android.util.Log
import id.haseda.margaretha.timeline.AspectRatio
import id.haseda.margaretha.timeline.Canvas
import id.haseda.margaretha.timeline.Project
import id.haseda.margaretha.timeline.ProjectCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAtMs: Long,
    val durationUs: Long,
    val clipCount: Int,
    val aspect: AspectRatio,
    val coverUri: String?,
    val coverTimeUs: Long,
)

sealed interface LoadResult {
    data class Loaded(val project: Project, val recoveredFromBackup: Boolean) : LoadResult
    data class Failed(val reason: String) : LoadResult
}

/**
 * Crash-safe project storage.
 *
 * Layout: files/projects/<id>/project.json (+ project.json.bak = previous good version).
 * Writes go to a temp file that is fsync'ed, then the old file becomes the backup and the temp
 * file is renamed into place. A crash at any point leaves either the new file or the backup.
 */
class ProjectRepository(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private fun dir(id: String) = File(root, id)
    private fun main(id: String) = File(dir(id), FILE)
    private fun backup(id: String) = File(dir(id), "$FILE.bak")
    private fun temp(id: String) = File(dir(id), "$FILE.tmp")

    suspend fun list(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { d ->
            when (val r = loadBlocking(d.name)) {
                is LoadResult.Loaded -> r.project.toSummary()
                is LoadResult.Failed -> null
            }
        }.sortedByDescending { it.updatedAtMs }
    }

    suspend fun load(id: String): LoadResult = withContext(Dispatchers.IO) { mutex.withLock { loadBlocking(id) } }

    private fun loadBlocking(id: String): LoadResult {
        val primary = readDecoded(main(id))
        if (primary is ProjectCodec.DecodeResult.Success) return LoadResult.Loaded(primary.project, false)
        val fallback = readDecoded(backup(id))
        if (fallback is ProjectCodec.DecodeResult.Success) {
            Log.w(TAG, "Project $id restored from backup: ${(primary as? ProjectCodec.DecodeResult.Failure)?.reason}")
            return LoadResult.Loaded(fallback.project, true)
        }
        return LoadResult.Failed((primary as? ProjectCodec.DecodeResult.Failure)?.reason ?: "Proyek tidak ditemukan.")
    }

    private fun readDecoded(file: File): ProjectCodec.DecodeResult? = try {
        if (file.exists()) ProjectCodec.decode(file.readText()) else null
    } catch (e: IOException) {
        ProjectCodec.DecodeResult.Failure("Tidak bisa membaca file: ${e.message}")
    }

    /** Atomic save. Throws IOException on failure so callers can report it. */
    suspend fun save(project: Project) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val d = dir(project.id).apply { mkdirs() }
            val tmp = temp(project.id)
            FileOutputStream(tmp).use { out ->
                out.write(ProjectCodec.encode(project).toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            val current = main(project.id)
            val bak = backup(project.id)
            if (current.exists()) {
                bak.delete()
                if (!current.renameTo(bak)) throw IOException("Gagal membuat cadangan proyek")
            }
            if (!tmp.renameTo(current)) throw IOException("Gagal menyimpan proyek di ${d.name}")
        }
    }

    suspend fun create(name: String, aspect: AspectRatio, nowMs: Long): Project {
        val project = Project.create(UUID.randomUUID().toString(), name, Canvas(aspect), nowMs)
        save(project)
        return project
    }

    suspend fun duplicate(id: String, nowMs: Long): Project? {
        val loaded = load(id) as? LoadResult.Loaded ?: return null
        val copy = loaded.project.copy(
            id = UUID.randomUUID().toString(),
            name = (loaded.project.name + " (salinan)").take(80),
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        save(copy)
        return copy
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { dir(id).deleteRecursively() }
        if (sessionProjectId() == id) clearSession()
    }

    // ---- Session marker: set while a project is open; survives process death -------------------

    fun markSessionOpen(id: String) = prefs.edit().putString(KEY_OPEN, id).apply()
    fun clearSession() = prefs.edit().remove(KEY_OPEN).apply()
    fun sessionProjectId(): String? = prefs.getString(KEY_OPEN, null)

    /** Removes temp files left by a crash in the middle of a save. */
    fun cleanupStaleTemps() {
        root.listFiles().orEmpty().forEach { d -> File(d, "$FILE.tmp").delete() }
    }

    private fun Project.toSummary(): ProjectSummary {
        val first = mainTrack.clips.firstOrNull()
        return ProjectSummary(
            id = id,
            name = name,
            updatedAtMs = updatedAtMs,
            durationUs = durationUs,
            clipCount = clipCount,
            aspect = canvas.aspect,
            coverUri = first?.let { assets[it.assetId]?.uri },
            coverTimeUs = first?.let { it.sourceInUs + minOf(it.durationUs / 3, 1_000_000L) } ?: 0L,
        )
    }

    companion object {
        private const val TAG = "ProjectRepository"
        private const val FILE = "project.json"
        private const val KEY_OPEN = "open_project"
    }
}
