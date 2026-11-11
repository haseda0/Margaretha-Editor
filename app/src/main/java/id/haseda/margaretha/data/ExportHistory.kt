package id.haseda.margaretha.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ExportRecord(
    val name: String,
    val uri: String,
    val location: String,
    val sizeBytes: Long,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val atMs: Long,
)

/** The last exports, newest first. Only files that were really written are recorded. */
class ExportHistory(context: Context) {
    private val file = File(context.filesDir, "exports.json")

    @Synchronized
    fun list(): List<ExportRecord> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ExportRecord(o.getString("name"), o.getString("uri"), o.getString("location"), o.getLong("sizeBytes"), o.getLong("durationUs"), o.getInt("width"), o.getInt("height"), o.getLong("atMs"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(record: ExportRecord) {
        val all = (listOf(record) + list()).take(MAX)
        val array = JSONArray()
        all.forEach { r ->
            array.put(
                JSONObject().put("name", r.name).put("uri", r.uri).put("location", r.location).put("sizeBytes", r.sizeBytes)
                    .put("durationUs", r.durationUs).put("width", r.width).put("height", r.height).put("atMs", r.atMs),
            )
        }
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(array.toString())
            tmp.renameTo(file)
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private companion object {
        const val MAX = 30
    }
}
