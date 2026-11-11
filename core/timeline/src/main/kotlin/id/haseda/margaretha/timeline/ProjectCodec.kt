package id.haseda.margaretha.timeline

/**
 * Versioned project file format (see docs/PROJECT_FORMAT.md).
 * Decoding never throws: corrupted or unsupported files produce [DecodeResult.Failure].
 */
object ProjectCodec {
    const val SCHEMA_VERSION = 3
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
            else -> DecodeResult.Success(validate(decodeCurrent(migrate(root, version))))
        }
    } catch (e: JsonException) {
        DecodeResult.Failure("File proyek rusak: ${e.message}")
    } catch (e: IllegalArgumentException) {
        DecodeResult.Failure("File proyek tidak konsisten: ${e.message}")
    }

    /** Upgrades older schema documents step by step. v1 clips receive default color look. */
    private fun migrate(root: Json.Obj, fromVersion: Int): Json.Obj {
        var doc = root
        var v = fromVersion
        while (v < SCHEMA_VERSION) {
            when (v) {
                1 -> doc = migrate1to2(doc)
                // v3 only adds optional fields (speed, transform, grade, effects, transitions, audio and
                // text tracks); every one of them has a neutral default when absent.
                2 -> doc = Json.Obj(doc.fields + ("schemaVersion" to 3.j()))
            }
            v++
        }
        return doc
    }

    private fun migrate1to2(root: Json.Obj): Json.Obj {
        val fields = root.fields.toMutableMap()
        val tracks = root.arr("tracks").map { trackValue ->
            val track = trackValue.obj()
            val trackFields = track.fields.toMutableMap()
            val clips = track.arr("clips").map { clipValue ->
                val clipFields = clipValue.obj().fields.toMutableMap()
                clipFields.putIfAbsent("colorLook", ColorLook.ORIGINAL.name.j())
                Json.Obj(clipFields)
            }
            trackFields["clips"] = Json.Arr(clips)
            Json.Obj(trackFields)
        }
        fields["tracks"] = Json.Arr(tracks)
        fields["schemaVersion"] = 2.j()
        return Json.Obj(fields)
    }

    private fun decodeCurrent(root: Json.Obj): Project {
        val canvasObj = root.req("canvas").obj()
        val aspect = runCatching { AspectRatio.valueOf(canvasObj.str("aspect")) }
            .getOrElse { throw JsonException("Rasio aspek tidak dikenal") }
        val assets = root.arr("assets").map { decodeAsset(it.obj()) }
        if (assets.map { it.id }.toSet().size != assets.size) {
            throw JsonException("ID media ganda.")
        }
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
        "kind" to a.kind.name.j(),
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
        kind = enumOf(o, "kind", MediaKind.VIDEO, "Jenis media tidak dikenal"),
    )

    private inline fun <reified E : Enum<E>> enumOf(o: Json.Obj, key: String, default: E, error: String): E {
        val raw = (o.fields[key] as? Json.Str)?.value ?: return default
        return runCatching { enumValueOf<E>(raw) }.getOrElse { throw JsonException(error) }
    }

    private fun Json.Obj.optObj(key: String): Json.Obj? = fields[key] as? Json.Obj

    private fun Json.Obj.optLong(key: String, default: Long): Long = if (fields.containsKey(key)) long(key) else default

    private fun encodeTrack(t: Track): Json = jsonOf(
        "id" to t.id.j(),
        "kind" to t.kind.name.j(),
        "magnetic" to t.magnetic.j(),
        "enabled" to t.enabled.j(),
        "locked" to t.locked.j(),
        "clips" to Json.Arr(t.clips.map(::encodeClip)),
        "texts" to Json.Arr(t.texts.map(::encodeText)),
    )

    private fun decodeTrack(o: Json.Obj): Track {
        val kind = runCatching { TrackKind.valueOf(o.str("kind")) }.getOrElse { throw JsonException("Jenis trek tidak dikenal") }
        return Track(
            id = o.str("id"),
            kind = kind,
            clips = o.arr("clips").map { decodeClip(it.obj()) },
            magnetic = o.bool("magnetic", kind == TrackKind.MAIN_VIDEO),
            texts = (o.fields["texts"] as? Json.Arr)?.items.orEmpty().map { decodeText(it.obj()) },
            enabled = o.bool("enabled", true),
            locked = o.bool("locked", false),
        )
    }

    private fun encodeClip(c: Clip): Json {
        val fields = linkedMapOf<String, Json>(
            "id" to c.id.j(),
            "assetId" to c.assetId.j(),
            "sourceInUs" to c.sourceInUs.j(),
            "sourceOutUs" to c.sourceOutUs.j(),
            "timelineStartUs" to c.timelineStartUs.j(),
            "volume" to c.volume.j(),
            "muted" to c.muted.j(),
            "colorLook" to c.colorLook.name.j(),
            "speedPermille" to c.speedPermille.j(),
            "audioFadeInUs" to c.audioFadeInUs.j(),
            "audioFadeOutUs" to c.audioFadeOutUs.j(),
        )
        // Neutral values are omitted to keep project files small and readable.
        if (!c.transform.isIdentity) fields["transform"] = c.transform.let {
            jsonOf(
                "scale" to it.scale.j(), "rotation" to it.rotation.j(), "offsetX" to it.offsetX.j(), "offsetY" to it.offsetY.j(),
                "flipH" to it.flipH.j(), "flipV" to it.flipV.j(), "opacity" to it.opacity.j(),
            )
        }
        if (!c.crop.isNone) fields["crop"] = c.crop.let {
            jsonOf("left" to it.left.j(), "top" to it.top.j(), "right" to it.right.j(), "bottom" to it.bottom.j())
        }
        if (!c.grade.isNeutral) fields["grade"] = c.grade.let {
            jsonOf(
                "exposure" to it.exposure.j(), "brightness" to it.brightness.j(), "contrast" to it.contrast.j(),
                "saturation" to it.saturation.j(), "temperature" to it.temperature.j(), "tint" to it.tint.j(),
                "highlights" to it.highlights.j(), "shadows" to it.shadows.j(), "whites" to it.whites.j(), "blacks" to it.blacks.j(),
            )
        }
        if (!c.fx.isNone) fields["fx"] = c.fx.let {
            jsonOf("blur" to it.blur.j(), "sharpen" to it.sharpen.j(), "vignette" to it.vignette.j(), "grain" to it.grain.j())
        }
        if (!c.enter.isNone) fields["enter"] = encodeTransition(c.enter)
        if (!c.exit.isNone) fields["exit"] = encodeTransition(c.exit)
        return Json.Obj(fields)
    }

    private fun encodeTransition(t: Transition): Json = jsonOf("kind" to t.kind.name.j(), "durationUs" to t.durationUs.j())

    private fun decodeTransition(o: Json.Obj?): Transition = if (o == null) Transition.NONE else Transition(
        kind = enumOf(o, "kind", TransitionKind.NONE, "Transisi tidak dikenal"),
        durationUs = o.optLong("durationUs", Transition.DEFAULT_US),
    )

    private fun decodeClip(o: Json.Obj) = Clip(
        id = o.str("id"),
        assetId = o.str("assetId"),
        sourceInUs = o.long("sourceInUs"),
        sourceOutUs = o.long("sourceOutUs"),
        timelineStartUs = o.long("timelineStartUs"),
        volume = o.float("volume", 1f),
        muted = o.bool("muted", false),
        colorLook = enumOf(o, "colorLook", ColorLook.ORIGINAL, "Look warna tidak dikenal"),
        speedPermille = o.optLong("speedPermille", 1000L).toInt(),
        audioFadeInUs = o.optLong("audioFadeInUs", 0L),
        audioFadeOutUs = o.optLong("audioFadeOutUs", 0L),
        transform = o.optObj("transform")?.let {
            Transform(
                scale = it.float("scale", 1f), rotation = it.float("rotation", 0f),
                offsetX = it.float("offsetX", 0f), offsetY = it.float("offsetY", 0f),
                flipH = it.bool("flipH", false), flipV = it.bool("flipV", false), opacity = it.float("opacity", 1f),
            )
        } ?: Transform.IDENTITY,
        crop = o.optObj("crop")?.let {
            Crop(it.float("left", 0f), it.float("top", 0f), it.float("right", 0f), it.float("bottom", 0f))
        } ?: Crop.NONE,
        grade = o.optObj("grade")?.let {
            ColorGrade(
                it.float("exposure", 0f), it.float("brightness", 0f), it.float("contrast", 0f), it.float("saturation", 0f),
                it.float("temperature", 0f), it.float("tint", 0f), it.float("highlights", 0f), it.float("shadows", 0f),
                it.float("whites", 0f), it.float("blacks", 0f),
            )
        } ?: ColorGrade.NEUTRAL,
        fx = o.optObj("fx")?.let {
            ClipFx(it.float("blur", 0f), it.float("sharpen", 0f), it.float("vignette", 0f), it.float("grain", 0f))
        } ?: ClipFx.NONE,
        enter = decodeTransition(o.optObj("enter")),
        exit = decodeTransition(o.optObj("exit")),
    )

    private fun encodeText(t: TextItem): Json = jsonOf(
        "id" to t.id.j(),
        "text" to t.text.j(),
        "startUs" to t.startUs.j(),
        "durationUs" to t.durationUs.j(),
        "x" to t.x.j(),
        "y" to t.y.j(),
        "animation" to t.animation.name.j(),
        "style" to t.style.let {
            jsonOf(
                "font" to it.font.name.j(), "size" to it.size.j(), "bold" to it.bold.j(), "italic" to it.italic.j(),
                "color" to it.color.j(), "align" to it.align.name.j(), "strokeColor" to it.strokeColor.j(),
                "strokeWidth" to it.strokeWidth.j(), "shadow" to it.shadow.j(), "backgroundColor" to it.backgroundColor.j(),
            )
        },
    )

    private fun decodeText(o: Json.Obj): TextItem {
        val st = o.optObj("style")
        val base = TextStyle.TITLE
        return TextItem(
            id = o.str("id"),
            text = o.str("text"),
            startUs = o.long("startUs"),
            durationUs = o.long("durationUs"),
            x = o.float("x", 0.5f),
            y = o.float("y", 0.5f),
            animation = enumOf(o, "animation", TextAnimation.NONE, "Animasi teks tidak dikenal"),
            style = if (st == null) base else TextStyle(
                font = enumOf(st, "font", base.font, "Font tidak dikenal"),
                size = st.float("size", base.size),
                bold = st.bool("bold", base.bold),
                italic = st.bool("italic", base.italic),
                color = st.optLong("color", base.color),
                align = enumOf(st, "align", base.align, "Perataan teks tidak dikenal"),
                strokeColor = st.optLong("strokeColor", base.strokeColor),
                strokeWidth = st.float("strokeWidth", base.strokeWidth),
                shadow = st.float("shadow", base.shadow),
                backgroundColor = st.optLong("backgroundColor", base.backgroundColor),
            ),
        )
    }
}

object ProjectValidator {
    /** Returns human readable integrity problems; empty when the project is consistent. */
    fun issues(project: Project): List<String> {
        val out = mutableListOf<String>()
        if (project.tracks.count { it.kind == TrackKind.MAIN_VIDEO } != 1) out += "Proyek harus punya tepat satu trek utama."
        for ((assetId, asset) in project.assets) {
            if (assetId != asset.id || asset.id.isBlank()) out += "ID media tidak konsisten."
            if (asset.uri.isBlank() || asset.displayName.isBlank() || asset.mimeType.isBlank()) {
                out += "Metadata media ${asset.id.ifBlank { "tanpa ID" }} tidak lengkap."
            }
            if (asset.durationUs <= 0L || (asset.kind != MediaKind.AUDIO && (asset.width <= 0 || asset.height <= 0))) {
                out += "Metadata media ${asset.id.ifBlank { "tanpa ID" }} tidak valid."
            }
        }
        val ids = project.tracks.flatMap { t -> t.clips.map { it.id } + t.texts.map { it.id } }
        if (ids.size != ids.toSet().size) out += "ID klip ganda."
        if (project.tracks.map { it.id }.toSet().size != project.tracks.size) out += "ID trek ganda."
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
            if (track.isTextual && track.clips.isNotEmpty()) out += "Trek teks ${track.id} berisi klip media."
            if (!track.isTextual && track.texts.isNotEmpty()) out += "Trek ${track.id} berisi teks."
            if (!track.magnetic && track.clips.any { track.overlaps(it) }) out += "Klip di trek ${track.id} bertumpuk."
            if (track.kind == TrackKind.AUDIO && track.clips.any { project.assets[it.assetId]?.hasAudio == false }) {
                out += "Trek audio ${track.id} berisi media tanpa suara."
            }
        }
        return out
    }
}
