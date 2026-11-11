package id.haseda.margaretha.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.haseda.margaretha.home.fieldColors
import id.haseda.margaretha.media.ThumbnailCache
import id.haseda.margaretha.timeline.AspectRatio
import id.haseda.margaretha.timeline.Canvas
import id.haseda.margaretha.timeline.ClearKeyframes
import id.haseda.margaretha.timeline.Clip
import id.haseda.margaretha.timeline.Keyframes
import id.haseda.margaretha.timeline.RemoveKeyframe
import id.haseda.margaretha.timeline.SetKeyframe
import id.haseda.margaretha.timeline.ClipFx
import id.haseda.margaretha.timeline.ColorGrade
import id.haseda.margaretha.timeline.ColorLook
import id.haseda.margaretha.timeline.Crop
import id.haseda.margaretha.timeline.Edit
import id.haseda.margaretha.timeline.FontChoice
import id.haseda.margaretha.timeline.MediaAsset
import id.haseda.margaretha.timeline.MediaKind
import id.haseda.margaretha.timeline.Project
import id.haseda.margaretha.timeline.SetClipAudio
import id.haseda.margaretha.timeline.SetClipFx
import id.haseda.margaretha.timeline.SetClipGrade
import id.haseda.margaretha.timeline.SetClipSpeed
import id.haseda.margaretha.timeline.SetClipTransform
import id.haseda.margaretha.timeline.SetClipTransitions
import id.haseda.margaretha.timeline.TextAlign
import id.haseda.margaretha.timeline.TextAnimation
import id.haseda.margaretha.timeline.TextItem
import id.haseda.margaretha.timeline.TextStyle
import id.haseda.margaretha.timeline.Timecode
import id.haseda.margaretha.timeline.Track
import id.haseda.margaretha.timeline.TrackKind
import id.haseda.margaretha.timeline.Transform
import id.haseda.margaretha.timeline.Transition
import id.haseda.margaretha.timeline.TransitionKind
import id.haseda.margaretha.timeline.UpdateText
import id.haseda.margaretha.timeline.US_PER_SECOND
import id.haseda.margaretha.ui.HardSwitch
import id.haseda.margaretha.ui.IconAction
import id.haseda.margaretha.ui.Knob
import id.haseda.margaretha.ui.KnobStrip
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.MiniChip
import id.haseda.margaretha.ui.PanelLabel
import id.haseda.margaretha.ui.QuietButton
import id.haseda.margaretha.ui.SliderRow
import id.haseda.margaretha.ui.Swatch
import id.haseda.margaretha.ui.VideoFrame
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Which inline panel is open under the timeline. Panels keep the preview visible while editing. */
enum class Panel { NONE, SPEED, TRANSFORM, AUDIO, LOOK, GRADE, FX, TRANSITION, TEXT, CANVAS, MEDIA }

/** Returns [action] only when [condition] holds; panels use it to hide a pointless reset button. */
private fun onlyIf(condition: Boolean, action: () -> Unit): (() -> Unit)? = if (condition) action else null

private fun percent(v: Float) = "${(v * 100).roundToInt()}"
private fun signed(v: Float) = (v * 100).roundToInt().let { if (it > 0) "+$it" else "$it" }
private fun seconds(us: Long) = String.format(java.util.Locale.US, "%.1f dtk", us / 1_000_000f)

/** Frame shared by every panel: title, optional reset, close button and a scrollable body. */
@Composable
fun PanelFrame(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().background(MColors.Surface1)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MColors.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing()
            if (onReset != null) IconAction(MIcons.Reset, "Kembalikan ke awal", onReset, size = 44.dp, tint = MColors.TextMuted)
            IconAction(MIcons.Check, "Selesai", onClose, size = 44.dp, tint = MColors.Accent)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 236.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
        ) { content() }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/**
 * Slider that only commits on release. Used for values whose change rebuilds the player
 * pipeline (speed, crop, transition length), where a live preview would stutter.
 */
@Composable
private fun CommitSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    text: (Float) -> String,
    resetTo: Float? = null,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    SliderRow(label, local, range, text(local), onChange = { local = it }, onDone = { if (local != value) onCommit(local) }, resetTo = resetTo)
}

// ---- Speed ---------------------------------------------------------------------------------------

@Composable
fun SpeedPanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    PanelFrame("Kecepatan", onClose, onReset = onlyIf(clip.speedPermille != 1000) { vm.apply(SetClipSpeed(clip.id, 1000)) }) {
        ChipRow {
            listOf(250, 500, 1000, 1500, 2000, 4000).forEach { p ->
                MiniChip("${p / 1000f}×".replace(".0×", "×"), clip.speedPermille == p, { vm.apply(SetClipSpeed(clip.id, p)) })
            }
        }
        CommitSlider("Kecepatan", clip.speed, 0.25f..4f, { String.format(java.util.Locale.US, "%.2f×", it) }, resetTo = 1f) {
            vm.apply(SetClipSpeed(clip.id, (it * 20).roundToInt() * 50))
        }
        Text(
            "Durasi klip menjadi ${Timecode.short(clip.durationUs)}. Suara ikut dipercepat/diperlambat.",
            style = MaterialTheme.typography.bodySmall,
            color = MColors.TextFaint,
        )
    }
}

// ---- Transform & crop ----------------------------------------------------------------------------

/**
 * Position, size and motion. Without keyframes the sliders set one static pose. Once the clip has
 * keyframes, every change is written to a keyframe at the playhead, so moving the playhead and
 * adjusting again animates the clip between the two poses.
 */
@Composable
fun TransformPanel(vm: EditorViewModel, clip: Clip, positionUs: Long, onClose: () -> Unit) {
    val offsetUs = (positionUs - clip.timelineStartUs).coerceIn(0L, clip.durationUs)
    val animated = clip.keyframes.isNotEmpty()
    val t = clip.transformAtOffsetUs(offsetUs)
    val onKeyframe = Keyframes.near(clip.keyframes, clip.sourceAtOffsetUs(offsetUs)) != null
    fun edit(new: Transform): Edit = if (animated) SetKeyframe(clip.id, offsetUs, new) else SetClipTransform(clip.id, new, clip.crop)
    fun draft(new: Transform) = vm.draft(edit(new))
    val changed = !clip.transform.isIdentity || !clip.crop.isNone || animated
    PanelFrame(
        "Posisi & gerak",
        onClose,
        // With motion, reset removes the keyframes first (keeping the current pose); a second reset clears the pose.
        onReset = onlyIf(changed) {
            vm.apply(if (animated) ClearKeyframes(clip.id, offsetUs) else SetClipTransform(clip.id, Transform.IDENTITY, Crop.NONE))
        },
    ) {
        KnobStrip {
            Knob("Skala", t.scale, 0.1f..3f, "${percent(t.scale)}%", { draft(t.copy(scale = it)) }, vm::commitDraft, resetTo = 1f)
            Knob("Rotasi", t.rotation, -180f..180f, "${t.rotation.roundToInt()}°", { draft(t.copy(rotation = it.roundToInt().toFloat())) }, vm::commitDraft, resetTo = 0f)
            Knob("Opasitas", t.opacity, 0f..1f, "${percent(t.opacity)}%", { draft(t.copy(opacity = it)) }, vm::commitDraft, resetTo = 1f, arcFrom = 0f)
        }
        SliderRow("Geser X", t.offsetX, -1f..1f, signed(t.offsetX), { draft(t.copy(offsetX = it)) }, vm::commitDraft, resetTo = 0f)
        SliderRow("Geser Y", t.offsetY, -1f..1f, signed(t.offsetY), { draft(t.copy(offsetY = it)) }, vm::commitDraft, resetTo = 0f)
        ChipRow {
            MiniChip("Balik horizontal", t.flipH, { vm.apply(edit(t.copy(flipH = !t.flipH))) })
            MiniChip("Balik vertikal", t.flipV, { vm.apply(edit(t.copy(flipV = !t.flipV))) })
        }
        PanelLabel("Keyframe")
        ChipRow {
            MiniChip(if (onKeyframe) "◆ Hapus di sini" else "◇ Tambah di sini", onKeyframe, {
                vm.apply(if (onKeyframe) RemoveKeyframe(clip.id, offsetUs) else SetKeyframe(clip.id, offsetUs, t))
            })
            MiniChip("‹ Sebelumnya", false, { vm.seekKeyframe(clip, forward = false) }, enabled = animated)
            MiniChip("Berikutnya ›", false, { vm.seekKeyframe(clip, forward = true) }, enabled = animated)
            MiniChip("Hapus semua", false, { vm.apply(ClearKeyframes(clip.id, offsetUs)) }, enabled = animated)
        }
        Text(
            if (animated) "${clip.keyframes.size} keyframe. Perubahan di atas disimpan pada keyframe di posisi playhead; di antaranya klip bergerak halus."
            else "Tambah keyframe, pindahkan playhead, lalu ubah posisi/skala/rotasi/opasitas untuk membuat gerakan.",
            style = MaterialTheme.typography.bodySmall,
            color = MColors.TextFaint,
            modifier = Modifier.padding(top = 6.dp),
        )
        PanelLabel("Potong tepi")
        val c = clip.crop
        fun crop(new: () -> Crop) = runCatching(new).getOrNull()?.let { vm.apply(SetClipTransform(clip.id, clip.transform, it)) }
        CommitSlider("Kiri", c.left, 0f..Crop.MAX, { "${percent(it)}%" }, 0f) { crop { c.copy(left = it) } }
        CommitSlider("Kanan", c.right, 0f..Crop.MAX, { "${percent(it)}%" }, 0f) { crop { c.copy(right = it) } }
        CommitSlider("Atas", c.top, 0f..Crop.MAX, { "${percent(it)}%" }, 0f) { crop { c.copy(top = it) } }
        CommitSlider("Bawah", c.bottom, 0f..Crop.MAX, { "${percent(it)}%" }, 0f) { crop { c.copy(bottom = it) } }
    }
}

// ---- Audio ---------------------------------------------------------------------------------------

@Composable
fun AudioPanel(vm: EditorViewModel, clip: Clip, hasSound: Boolean, onClose: () -> Unit) {
    PanelFrame("Audio klip", onClose) {
        if (!hasSound) {
            Text("Klip ini tidak memiliki suara.", style = MaterialTheme.typography.bodyMedium, color = MColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
            return@PanelFrame
        }
        fun draft(volume: Float = clip.volume, fadeIn: Long = clip.audioFadeInUs, fadeOut: Long = clip.audioFadeOutUs) =
            vm.draft(SetClipAudio(clip.id, volume, clip.muted, fadeIn, fadeOut))
        val maxFade = (clip.durationUs / 2).coerceIn(100_000L, 5_000_000L).toFloat()
        KnobStrip {
            Knob("Volume", clip.volume, 0f..1f, "${percent(clip.volume)}%", { draft(volume = it) }, vm::commitDraft, resetTo = 1f, arcFrom = 0f, enabled = !clip.muted)
            Knob("Fade masuk", clip.audioFadeInUs.toFloat(), 0f..maxFade, seconds(clip.audioFadeInUs), { draft(fadeIn = (it.toLong() / 100_000L) * 100_000L) }, vm::commitDraft, resetTo = 0f)
            Knob("Fade keluar", clip.audioFadeOutUs.toFloat(), 0f..maxFade, seconds(clip.audioFadeOutUs), { draft(fadeOut = (it.toLong() / 100_000L) * 100_000L) }, vm::commitDraft, resetTo = 0f)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bisukan", style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                Text("Putar knob untuk mengatur; ketuk dua kali untuk kembali ke awal.", style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint)
            }
            HardSwitch(clip.muted, { vm.apply(SetClipAudio(clip.id, clip.volume, it)) }, "Bisukan klip")
        }
    }
}

// ---- Colour --------------------------------------------------------------------------------------

/** Press-and-hold button: shows the untouched picture while held (before/after). */
@Composable
private fun CompareButton(onHold: (Boolean) -> Unit) {
    DisposableEffect(Unit) { onDispose { onHold(false) } }
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MColors.Surface2)
            .border(1.dp, MColors.Line, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onHold(true)
                    waitForUpOrCancellation()
                    onHold(false)
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MIcons.Compare, contentDescription = null, tint = MColors.TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Tahan: asli", style = MaterialTheme.typography.labelMedium, color = MColors.TextMuted)
    }
}

@Composable
fun LookPanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    PanelFrame("Look warna", onClose, trailing = { CompareButton(vm::compareOriginal) }) {
        Text("Preset siap pakai untuk klip terpilih. Padukan dengan Grading untuk penyesuaian halus.", style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint)
        Spacer(Modifier.height(10.dp))
        ChipRow {
            ColorLook.entries.forEach { look -> MiniChip(look.title, clip.colorLook == look, { vm.setColorLook(clip.id, look) }) }
        }
        if (clip.colorLook != ColorLook.ORIGINAL) {
            CommitSlider("Intensitas", clip.lookIntensity, 0f..1f, { "${percent(it)}%" }, 1f) { vm.setColorLook(clip.id, clip.colorLook, it) }
        }
    }
}

@Composable
fun GradePanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    val g = clip.grade
    fun draft(new: ColorGrade) = vm.draft(SetClipGrade(clip.id, new))
    PanelFrame(
        "Color grading",
        onClose,
        onReset = onlyIf(!g.isNeutral) { vm.apply(SetClipGrade(clip.id, ColorGrade.NEUTRAL)) },
        trailing = { CompareButton(vm::compareOriginal) },
    ) {
        @Composable
        fun row(label: String, value: Float, set: (Float) -> ColorGrade) =
            SliderRow(label, value, -1f..1f, signed(value), { draft(set(it)) }, vm::commitDraft, resetTo = 0f)
        PanelLabel("Cahaya")
        row("Eksposur", g.exposure) { g.copy(exposure = it) }
        row("Kecerahan", g.brightness) { g.copy(brightness = it) }
        row("Kontras", g.contrast) { g.copy(contrast = it) }
        row("Highlight", g.highlights) { g.copy(highlights = it) }
        row("Bayangan", g.shadows) { g.copy(shadows = it) }
        row("Putih", g.whites) { g.copy(whites = it) }
        row("Hitam", g.blacks) { g.copy(blacks = it) }
        PanelLabel("Warna")
        row("Saturasi", g.saturation) { g.copy(saturation = it) }
        row("Suhu", g.temperature) { g.copy(temperature = it) }
        row("Tint", g.tint) { g.copy(tint = it) }
    }
}

// ---- Effects & transitions -----------------------------------------------------------------------

@Composable
fun FxPanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    val fx = clip.fx
    fun draft(new: ClipFx) = vm.draft(SetClipFx(clip.id, new))
    PanelFrame(
        "Efek",
        onClose,
        onReset = onlyIf(!fx.isNone) { vm.apply(SetClipFx(clip.id, ClipFx.NONE)) },
        trailing = { CompareButton(vm::compareOriginal) },
    ) {
        SliderRow("Blur", fx.blur, 0f..1f, percent(fx.blur), { draft(fx.copy(blur = it)) }, vm::commitDraft, resetTo = 0f)
        SliderRow("Tajamkan", fx.sharpen, 0f..1f, percent(fx.sharpen), { draft(fx.copy(sharpen = it)) }, vm::commitDraft, resetTo = 0f)
        SliderRow("Vignette", fx.vignette, 0f..1f, percent(fx.vignette), { draft(fx.copy(vignette = it)) }, vm::commitDraft, resetTo = 0f)
        SliderRow("Grain", fx.grain, 0f..1f, percent(fx.grain), { draft(fx.copy(grain = it)) }, vm::commitDraft, resetTo = 0f)
    }
}

@Composable
fun TransitionPanel(vm: EditorViewModel, clip: Clip, onClose: () -> Unit) {
    fun set(enter: Transition = clip.enter, exit: Transition = clip.exit) = vm.apply(SetClipTransitions(clip.id, enter, exit))
    val changed = !clip.enter.isNone || !clip.exit.isNone
    PanelFrame("Transisi", onClose, onReset = onlyIf(changed) { set(Transition.NONE, Transition.NONE) }) {
        @Composable
        fun side(label: String, current: Transition, apply: (Transition) -> Unit) {
            PanelLabel(label)
            ChipRow {
                TransitionKind.entries.forEach { kind -> MiniChip(kind.title, current.kind == kind, { apply(current.copy(kind = kind)) }) }
            }
            if (!current.isNone) {
                CommitSlider("Durasi", current.durationUs.toFloat(), Transition.MIN_US.toFloat()..Transition.MAX_US.toFloat(), { seconds(it.toLong()) }) {
                    apply(current.copy(durationUs = (it.toLong() / 100_000L) * 100_000L))
                }
            }
        }
        side("Saat klip muncul", clip.enter) { set(enter = it) }
        side("Saat klip berakhir", clip.exit) { set(exit = it) }
        Text(
            "Transisi dianimasikan terhadap layar hitam. Crossfade antar dua klip belum tersedia.",
            style = MaterialTheme.typography.bodySmall,
            color = MColors.TextFaint,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---- Text & subtitles ----------------------------------------------------------------------------

private val TEXT_COLORS = listOf(0xFFFFFFFF, 0xFF111111, 0xFFF6C3A2, 0xFFFFD84D, 0xFFFF5A5F, 0xFF57D9A3, 0xFF5AB0FF, 0xFFC792EA)

@Composable
fun TextPanel(vm: EditorViewModel, item: TextItem, isSubtitle: Boolean, onClose: () -> Unit) {
    val style = item.style
    fun apply(new: TextItem) = vm.apply(UpdateText(new))
    fun applyStyle(new: TextStyle) = apply(item.copy(style = new))
    fun draftStyle(new: TextStyle) = vm.draft(UpdateText(item.copy(style = new)))

    // Typing is previewed live and committed as one undo step after a short pause.
    var typed by remember(item.id) { mutableStateOf(item.text) }
    LaunchedEffect(typed) {
        if (typed == item.text) return@LaunchedEffect
        vm.draft(UpdateText(item.copy(text = typed)))
        delay(700)
        vm.commitDraft()
    }
    DisposableEffect(item.id) { onDispose { vm.commitDraft() } }

    PanelFrame(if (isSubtitle) "Subtitle" else "Teks", onClose) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.take(TextItem.MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (isSubtitle) "Isi subtitle" else "Isi teks") },
            isError = typed.isBlank(),
            supportingText = { if (typed.isBlank()) Text("Teks tidak boleh kosong") else Text("${Timecode.short(item.startUs)} – ${Timecode.short(item.endUs)} · geser di timeline untuk mengatur waktu") },
            maxLines = 3,
            colors = fieldColors(),
        )
        PanelLabel("Font")
        ChipRow {
            FontChoice.entries.forEach { f -> MiniChip(f.title, style.font == f, { applyStyle(style.copy(font = f)) }) }
            MiniChip("Tebal", style.bold, { applyStyle(style.copy(bold = !style.bold)) })
            MiniChip("Miring", style.italic, { applyStyle(style.copy(italic = !style.italic)) })
        }
        SliderRow("Ukuran", style.size, TextStyle.MIN_SIZE..0.2f, percent(style.size * 10), { draftStyle(style.copy(size = it)) }, vm::commitDraft)
        PanelLabel("Warna teks")
        ChipRow { TEXT_COLORS.forEach { c -> Swatch(Color(c), style.color == c, "Warna teks") { applyStyle(style.copy(color = c)) } } }
        PanelLabel("Perataan & posisi")
        ChipRow {
            MiniChip("Kiri", style.align == TextAlign.LEFT, { applyStyle(style.copy(align = TextAlign.LEFT)) })
            MiniChip("Tengah", style.align == TextAlign.CENTER, { applyStyle(style.copy(align = TextAlign.CENTER)) })
            MiniChip("Kanan", style.align == TextAlign.RIGHT, { applyStyle(style.copy(align = TextAlign.RIGHT)) })
            MiniChip("Atas", item.y == 0.14f, { apply(item.copy(x = 0.5f, y = 0.14f)) })
            MiniChip("Tengah layar", item.y == 0.5f && item.x == 0.5f, { apply(item.copy(x = 0.5f, y = 0.5f)) })
            MiniChip("Bawah", item.y == TextItem.SUBTITLE_Y, { apply(item.copy(x = 0.5f, y = TextItem.SUBTITLE_Y)) })
        }
        Text("Atau seret teks langsung di pratinjau.", style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint, modifier = Modifier.padding(top = 4.dp))
        SliderRow("Rotasi", item.rotation, -180f..180f, "${item.rotation.roundToInt()}°", { vm.draft(UpdateText(item.copy(rotation = it.roundToInt().toFloat()))) }, vm::commitDraft, resetTo = 0f)
        SliderRow("Opasitas", item.opacity, 0f..1f, "${percent(item.opacity)}%", { vm.draft(UpdateText(item.copy(opacity = it))) }, vm::commitDraft, resetTo = 1f)
        PanelLabel("Garis tepi & bayangan")
        SliderRow("Garis tepi", style.strokeWidth, 0f..0.3f, percent(style.strokeWidth / 0.3f), { draftStyle(style.copy(strokeWidth = it)) }, vm::commitDraft, resetTo = 0f)
        ChipRow {
            listOf(0xFF000000, 0xFFFFFFFF, 0xFFFF5A5F, 0xFF5AB0FF).forEach { c ->
                Swatch(Color(c), style.strokeColor == c, "Warna garis tepi") { applyStyle(style.copy(strokeColor = c)) }
            }
        }
        SliderRow("Bayangan", style.shadow, 0f..1f, percent(style.shadow), { draftStyle(style.copy(shadow = it)) }, vm::commitDraft, resetTo = 0f)
        PanelLabel("Latar")
        ChipRow {
            MiniChip("Tanpa", style.backgroundColor ushr 24 == 0L, { applyStyle(style.copy(backgroundColor = 0x00000000)) })
            MiniChip("Gelap", style.backgroundColor == 0x99000000, { applyStyle(style.copy(backgroundColor = 0x99000000)) })
            MiniChip("Hitam", style.backgroundColor == 0xFF000000, { applyStyle(style.copy(backgroundColor = 0xFF000000)) })
            MiniChip("Terang", style.backgroundColor == 0xCCFFFFFF, { applyStyle(style.copy(backgroundColor = 0xCCFFFFFF)) })
        }
        PanelLabel("Animasi")
        ChipRow { TextAnimation.entries.forEach { a -> MiniChip(a.title, item.animation == a, { apply(item.copy(animation = a)) }) } }
        val max = 15f * US_PER_SECOND
        CommitSlider("Durasi", item.durationUs.toFloat().coerceAtMost(max), TextItem.MIN_US.toFloat()..max, { seconds(it.toLong()) }) {
            apply(item.copy(durationUs = (it.toLong() / 100_000L).coerceAtLeast(1L) * 100_000L))
        }
    }
}

// ---- Canvas --------------------------------------------------------------------------------------

@Composable
fun CanvasPanel(vm: EditorViewModel, project: Project, onClose: () -> Unit) {
    PanelFrame("Kanvas", onClose) {
        PanelLabel("Rasio aspek")
        val canvas = project.canvas
        ChipRow {
            AspectRatio.entries.forEach { a ->
                MiniChip(a.label, !canvas.isCustom && canvas.aspect == a, { vm.setCanvas(canvas.copy(aspect = a, customWidth = 0, customHeight = 0)) })
            }
            MiniChip("Kustom", canvas.isCustom, { if (!canvas.isCustom) vm.setCanvas(canvas.copy(customWidth = 1080, customHeight = 1350)) })
        }
        if (canvas.isCustom) {
            var w by remember(canvas.customWidth) { mutableStateOf(canvas.customWidth.toString()) }
            var h by remember(canvas.customHeight) { mutableStateOf(canvas.customHeight.toString()) }
            val wide = w.toIntOrNull()?.takeIf { it in Canvas.MIN_SIDE..Canvas.MAX_SIDE }
            val high = h.toIntOrNull()?.takeIf { it in Canvas.MIN_SIDE..Canvas.MAX_SIDE }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(w, { w = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Lebar") }, singleLine = true, isError = wide == null, colors = fieldColors())
                OutlinedTextField(h, { h = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Tinggi") }, singleLine = true, isError = high == null, colors = fieldColors())
                QuietButton("Terapkan", { if (wide != null && high != null) vm.setCanvas(canvas.copy(customWidth = wide, customHeight = high)) })
            }
            Text(
                "Sisi ${Canvas.MIN_SIDE}–${Canvas.MAX_SIDE} piksel. Ukuran ini menentukan bentuk bingkai; resolusi file dipilih saat ekspor.",
                style = MaterialTheme.typography.bodySmall,
                color = if (wide == null || high == null) MColors.Warning else MColors.TextFaint,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Text(canvas.aspect.usage, style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint, modifier = Modifier.padding(top = 6.dp))
        }
        PanelLabel("Frame rate proyek")
        ChipRow {
            listOf(24, 25, 30, 60).forEach { fps -> MiniChip("$fps", canvas.frameRate == fps, { vm.setCanvas(canvas.copy(frameRate = fps)) }) }
        }
        Text("Dipakai untuk langkah per-frame dan timecode. Frame rate file hasil dipilih saat ekspor.", style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---- Media library -------------------------------------------------------------------------------

@Composable
fun MediaPanel(
    vm: EditorViewModel,
    project: Project,
    missing: Set<String>,
    cache: ThumbnailCache,
    onImportVisual: () -> Unit,
    onImportAudio: () -> Unit,
    /** Clip whose media can be swapped from the library, when one is selected. */
    replaceTarget: Clip?,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<MediaKind?>(null) }
    var confirmRemove by remember { mutableStateOf<MediaAsset?>(null) }
    val uses = remember(project) { project.tracks.flatMap { it.clips }.groupingBy { it.assetId }.eachCount() }
    val assets = project.assets.values
        .filter { (filter == null || it.kind == filter) && it.displayName.contains(query.trim(), ignoreCase = true) }
        .sortedBy { it.displayName.lowercase() }

    PanelFrame("Pustaka media", onClose) {
        ChipRow {
            QuietButton("Video / foto", onImportVisual, icon = MIcons.Add)
            QuietButton("Audio", onImportAudio, icon = MIcons.Music)
        }
        if (project.assets.isEmpty()) {
            Text(
                "Belum ada media. Impor video, foto, atau musik untuk mulai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MColors.TextMuted,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@PanelFrame
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Cari nama file") },
            colors = fieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        ChipRow {
            MiniChip("Semua", filter == null, { filter = null })
            MiniChip("Video", filter == MediaKind.VIDEO, { filter = MediaKind.VIDEO })
            MiniChip("Foto", filter == MediaKind.IMAGE, { filter = MediaKind.IMAGE })
            MiniChip("Audio", filter == MediaKind.AUDIO, { filter = MediaKind.AUDIO })
        }
        if (assets.isEmpty()) {
            Text("Tidak ada media yang cocok.", style = MaterialTheme.typography.bodyMedium, color = MColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
        }
        assets.forEach { asset ->
            AssetRow(
                asset = asset,
                useCount = uses[asset.id] ?: 0,
                isMissing = asset.id in missing,
                cache = cache,
                onAdd = { vm.addAssetToTimeline(asset.id) },
                onAddSound = onlyIf(asset.kind == MediaKind.VIDEO && asset.hasAudio) { vm.addAssetAudio(asset.id, Project.VOICE_TRACK_ID) },
                onReplace = onlyIf(replaceTarget != null && replaceTarget.assetId != asset.id) { replaceTarget?.let { vm.replaceMedia(it.id, asset.id) } },
                onRemove = { confirmRemove = asset },
            )
        }
    }
    confirmRemove?.let { asset ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = MColors.Surface2,
            title = { Text("Hapus dari proyek?") },
            text = { Text("\"${asset.displayName}\" dihapus dari pustaka proyek ini. File aslinya di perangkat tidak disentuh.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; vm.removeAsset(asset.id) }) { Text("Hapus", color = MColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun AssetRow(
    asset: MediaAsset,
    useCount: Int,
    isMissing: Boolean,
    cache: ThumbnailCache,
    onAdd: () -> Unit,
    onAddSound: (() -> Unit)?,
    onReplace: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(MColors.Surface3), contentAlignment = Alignment.Center) {
            if (asset.kind == MediaKind.AUDIO) {
                Icon(MIcons.Music, contentDescription = null, tint = MColors.TrackMusic, modifier = Modifier.size(20.dp))
            } else {
                VideoFrame(cache, asset.uri, minOf(asset.durationUs / 3, 1_000_000L), 40.dp, Modifier.size(56.dp, 40.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(asset.displayName, style = MaterialTheme.typography.bodyMedium, color = MColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val info = when (asset.kind) {
                MediaKind.VIDEO -> "Video · ${Timecode.short(asset.durationUs)} · ${asset.displayWidth}×${asset.displayHeight}" + if (asset.hasAudio) "" else " · tanpa suara"
                MediaKind.IMAGE -> "Foto · ${asset.displayWidth}×${asset.displayHeight}"
                MediaKind.AUDIO -> "Audio · ${Timecode.short(asset.durationUs)}"
            }
            val usage = if (useCount == 0) "belum dipakai" else "dipakai $useCount×"
            Text(
                if (isMissing) "File tidak ditemukan · $usage" else "$info · $usage",
                style = MaterialTheme.typography.labelSmall,
                color = if (isMissing) MColors.Warning else MColors.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onReplace != null) IconAction(MIcons.Link, "Ganti media klip terpilih dengan ini", onReplace, size = 44.dp, tint = MColors.TextMuted)
        if (onAddSound != null) IconAction(MIcons.Volume, "Pakai suaranya saja di trek suara", onAddSound, size = 44.dp, tint = MColors.TextMuted)
        IconAction(MIcons.Add, "Tambahkan ke timeline", onAdd, size = 44.dp, tint = MColors.Accent)
        IconAction(MIcons.Delete, "Hapus dari proyek", onRemove, size = 44.dp, tint = MColors.TextMuted, enabled = useCount == 0)
    }
}

// ---- Tracks --------------------------------------------------------------------------------------

fun trackTitle(track: Track): String = when (track.id) {
    Project.MAIN_TRACK_ID -> "Video utama"
    Project.TEXT_TRACK_ID -> "Teks"
    Project.SUBTITLE_TRACK_ID -> "Subtitle"
    Project.MUSIC_TRACK_ID -> "Musik"
    Project.VOICE_TRACK_ID -> "Suara / efek"
    else -> track.id
}

fun trackIcon(track: Track): ImageVector = when (track.kind) {
    TrackKind.TEXT -> MIcons.Text
    TrackKind.SUBTITLE -> MIcons.Subtitle
    TrackKind.AUDIO -> MIcons.Music
    else -> MIcons.Film
}

@Composable
fun TracksDialog(project: Project, onFlags: (trackId: String, enabled: Boolean, locked: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MColors.Surface2,
        title = { Text("Trek") },
        text = {
            Column {
                Text("Sembunyikan/bisukan trek, atau kunci agar tidak berubah tanpa sengaja.", style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                project.tracks.forEach { track ->
                    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(trackIcon(track), contentDescription = null, tint = MColors.TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(trackTitle(track), style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
                            Text("${track.clips.size + track.texts.size} item", style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint)
                        }
                        if (track.kind != TrackKind.MAIN_VIDEO) {
                            val audio = track.kind == TrackKind.AUDIO
                            IconAction(
                                if (track.enabled) (if (audio) MIcons.Volume else MIcons.Eye) else (if (audio) MIcons.Mute else MIcons.EyeOff),
                                if (track.enabled) (if (audio) "Bisukan trek" else "Sembunyikan trek") else (if (audio) "Bunyikan trek" else "Tampilkan trek"),
                                { onFlags(track.id, !track.enabled, track.locked) },
                                tint = if (track.enabled) MColors.Text else MColors.Warning,
                            )
                        }
                        IconAction(
                            MIcons.Lock,
                            if (track.locked) "Buka kunci trek" else "Kunci trek",
                            { onFlags(track.id, track.enabled, !track.locked) },
                            tint = if (track.locked) MColors.Accent else MColors.TextFaint,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai", color = MColors.Accent) } },
    )
}
