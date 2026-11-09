package id.haseda.margaretha.timeline

/**
 * Versioned project file format (see docs/PROJECT_FORMAT.md).
 * Decoding never throws: corrupted or unsupported files produce [DecodeResult.Failure].
 */
object ProjectCodec {
    const val SCHEMA_VERSION = 1
    const val FORMAT_ID = "margaretha-editor-project"

    sealed interface DecodeResult {
        data class Success(val project: Project) : DecodeResult
        data class Failure(val reason: String) : DecodeResult
    }

    fun encode(project: Project): String = Json.write(
        jsonOf(
            "format" to FORMAT_ID.j(),
            "schemaVersion" to SCHEMA_VERSION.j(),
            "id" to project.id.j(),
            "name" to project.name.j(),
            "createdAtMs" to project.createdAtMs.j(),
            "updatedAtMs" to project.updatedAtMs.j(),
            "canvas" to jsonOf(
                "aspect" to project.canvas.aspect.name.j(),
                "frameRate" to project.canvas.frameRate.j(),
            ),
            "assets" to Json.Arr(project.assets.values.sortedBy { it.id }.map(::encodeAsset)),
            "tracks" to Json.Arr(project.tracks.map(::encodeTrack)),
        ),
    )

    fun decode(text: String): DecodeResult = try {
        val root = Json.parse(text).obj()
        if (root.fields["format"] != Json.Str(FORMAT_ID)) throw JsonException("Bukan file proyek Margaretha")
        val version = root.int("schemaVersion")
        when {
            version > SCHEMA_VERSION -> DecodeResult.Failure(
                "Proyek dibuat oleh versi Margaretha Editor yang lebih baru (skema $version).",
            )
            version < 1 -> DecodeResult.Failure("Versi skema tidak valid.")
            else -> DecodeResult.Success(validate(decodeV1(migrate(root, version))))
        }
    } catch (e: JsonException) {
        DecodeResult.Failure("File proyek rusak: ${e.message}")
    } catch (e: IllegalArgumentException) {
        DecodeResult.Failure("File proyek tidak konsisten: ${e.message}")
    }

    /** Upgrades older schema documents step by step. v1 is the first schema, nothing to do yet. */
    private fun migrate(root: Json.Obj, fromVersion: Int): Json.Obj {
        var doc = root
        var v = fromVersion
        while (v < SCHEMA_VERSION) {
            // Future: when (v) { 1 -> doc = migrate1to2(doc) }
            v++
        }
        return doc
    }

    private fun decodeV1(root: Json.Obj): Project {
        val canvasObj = root.req("canvas").obj()
        val aspect = runCatching { AspectRatio.valueOf(canvasObj.str("aspect")) }
            .getOrElse { throw JsonException("Rasio aspek tidak dikenal") }
        val assets = root.arr("assets").map { decodeAsset(it.obj()) }
        return Project(
            id = root.str("id"),
            name = root.str("name"),
            createdAtMs = root.long("createdAtMs"),
            updatedAtMs = root.long("updatedAtMs"),
            canvas = Canvas(aspect, canvasObj.int("frameRate")),
            assets = assets.associateBy { it.id },
            tracks = root.arr("tracks").map { decodeTrack(it.obj()) },
        )
    }

    private fun validate(project: Project): Project {
        val issues = ProjectValidator.issues(project)
        if (issues.isNotEmpty()) throw IllegalArgumentException(issues.first())
        return project
    }

    private fun encodeAsset(a: MediaAsset): Json = jsonOf(
        "id" to a.id.j(),
        "uri" to a.uri.j(),
        "displayName" to a.displayName.j(),
        "mimeType" to a.mimeType.j(),
        "durationUs" to a.durationUs.j(),
        "width" to a.width.j(),
        "height" to a.height.j(),
        "rotationDegrees" to a.rotationDegrees.j(),
        "hasVideo" to a.hasVideo.j(),
        "hasAudio" to a.hasAudio.j(),
        "frameRate" to a.frameRate.j(),
    )

    private fun decodeAsset(o: Json.Obj) = MediaAsset(
        id = o.str("id"),
        uri = o.str("uri"),
        displayName = o.str("displayName"),
        mimeType = o.str("mimeType"),
        durationUs = o.long("durationUs"),
        width = o.int("width"),
        height = o.int("height"),
        rotationDegrees = o.int("rotationDegrees"),
        hasVideo = o.bool("hasVideo", true),
        hasAudio = o.bool("hasAudio", false),
        frameRate = o.float("frameRate", 0f),
    )

    private fun encodeTrack(t: Track): Json = jsonOf(
        "id" to t.id.j(),
        "kind" to t.kind.name.j(),
        "magnetic" to t.magnetic.j(),
        "clips" to Json.Arr(t.clips.map(::encodeClip)),
    )

    private fun decodeTrack(o: Json.Obj): Track {
        val kind = runCatching { TrackKind.valueOf(o.str("kind")) }.getOrElse { throw JsonException("Jenis trek tidak dikenal") }
        return Track(
            id = o.str("id"),
            kind = kind,
            clips = o.arr("clips").map { decodeClip(it.obj()) },
            magnetic = o.bool("magnetic", kind == TrackKind.MAIN_VIDEO),
        )
    }

    private fun encodeClip(c: Clip): Json = jsonOf(
        "id" to c.id.j(),
        "assetId" to c.assetId.j(),
        "sourceInUs" to c.sourceInUs.j(),
        "sourceOutUs" to c.sourceOutUs.j(),
        "timelineStartUs" to c.timelineStartUs.j(),
        "volume" to c.volume.j(),
        "muted" to c.muted.j(),
    )

    private fun decodeClip(o: Json.Obj) = Clip(
        id = o.str("id"),
        assetId = o.str("assetId"),
        sourceInUs = o.long("sourceInUs"),
        sourceOutUs = o.long("sourceOutUs"),
        timelineStartUs = o.long("timelineStartUs"),
        volume = o.float("volume", 1f),
        muted = o.bool("muted", false),
    )
}

object ProjectValidator {
    /** Returns human readable integrity problems; empty when the project is consistent. */
    fun issues(project: Project): List<String> {
        val out = mutableListOf<String>()
        if (project.tracks.count { it.kind == TrackKind.MAIN_VIDEO } != 1) out += "Proyek harus punya tepat satu trek utama."
        val ids = project.tracks.flatMap { t -> t.clips.map { it.id } }
        if (ids.size != ids.toSet().size) out += "ID klip ganda."
        for (track in project.tracks) {
            for (clip in track.clips) {
                val asset = project.assets[clip.assetId]
                if (asset == null) {
                    out += "Klip ${clip.id} merujuk media yang tidak ada."
                } else if (clip.sourceOutUs > asset.durationUs) {
                    out += "Klip ${clip.id} melewati durasi media."
                }
            }
            if (track.magnetic && track.normalized() != track) out += "Trek ${track.id} tidak rapat."
        }
        return out
    }
}
