package id.haseda.margaretha.editor

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import id.haseda.margaretha.media.ExportOutput
import id.haseda.margaretha.media.ExportState
import id.haseda.margaretha.timeline.Clip
import id.haseda.margaretha.timeline.ExportPlanner
import id.haseda.margaretha.timeline.ExportSettings
import id.haseda.margaretha.timeline.PlanOutcome
import id.haseda.margaretha.timeline.QualityPreset
import id.haseda.margaretha.timeline.ResolutionPreset
import id.haseda.margaretha.timeline.Timecode
import id.haseda.margaretha.timeline.VideoCodec
import id.haseda.margaretha.ui.AccentPill
import id.haseda.margaretha.ui.IconAction
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.OptionChip
import id.haseda.margaretha.ui.QuietButton
import kotlinx.coroutines.delay

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: EditorViewModel, exportState: ExportState, onDismiss: () -> Unit) {
    val busy = exportState is ExportState.Running || exportState is ExportState.Verifying
    val currentBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy },
    )
    var playing by remember { mutableStateOf<ExportOutput?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MColors.Surface1) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Ekspor video", style = MaterialTheme.typography.headlineSmall, color = MColors.Text)
            Spacer(Modifier.height(4.dp))
            when (exportState) {
                is ExportState.Running -> Progress(exportState, onCancel = vm.export::cancel)
                ExportState.Verifying -> Verifying()
                is ExportState.Success -> Success(exportState, onPlay = { playing = exportState.output }, onDone = onDismiss)
                else -> Options(vm, exportState)
            }
        }
    }
    playing?.let { ExportedPlayer(it) { playing = null } }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Options(vm: EditorViewModel, exportState: ExportState) {
    val settings by vm.exportSettings.collectAsStateWithLifecycle()
    val editorState by vm.state.collectAsStateWithLifecycle()
    val project = editorState.project ?: return
    val options = remember(settings, project.canvas) { vm.exportOptions() } ?: return
    val plan = remember(settings, project) { (ExportPlanner.plan(project, settings) as? PlanOutcome.Ready)?.plan }
    val set = { s: ExportSettings -> vm.updateExportSettings(s) }

    if (exportState is ExportState.Failed) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MColors.Danger.copy(alpha = 0.10f))
                .border(1.dp, MColors.Danger.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Text("Ekspor gagal", style = MaterialTheme.typography.titleSmall, color = MColors.Danger)
            Text(exportState.message, style = MaterialTheme.typography.bodyMedium, color = MColors.Text)
            exportState.technical?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MColors.TextFaint, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    if (exportState is ExportState.Cancelled) {
        Text("Ekspor dibatalkan. Tidak ada file setengah jadi yang tersisa.", style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted)
    }

    Section("Resolusi")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResolutionPreset.entries.forEach { r ->
            val size = ExportPlanner.outputSize(project.canvas, r)
            val ok = r in options.supportedResolutions
            OptionChip(r.label, settings.resolution == r, { set(settings.copy(resolution = r)) }, enabled = ok, sub = if (ok) size.toString() else "tak didukung")
        }
    }
    Section("Frame rate (maksimum)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportSettings.FRAME_RATES.forEach { f -> OptionChip("$f", settings.frameRate == f, { set(settings.copy(frameRate = f)) }) }
    }
    Text(
        "Frame rate tidak dinaikkan melebihi sumber — video 30 FPS tetap 30 FPS.",
        style = MaterialTheme.typography.bodySmall,
        color = MColors.TextFaint,
        modifier = Modifier.padding(top = 6.dp),
    )
    Section("Kualitas")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QualityPreset.entries.forEach { q -> OptionChip(q.label, settings.quality == q, { set(settings.copy(quality = q, customVideoBitrate = null)) }) }
    }
    Section("Codec")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VideoCodec.entries.forEach { c ->
            val ok = c == VideoCodec.H264 || options.hevcAvailable
            OptionChip(c.label, settings.codec == c, { set(settings.copy(codec = c)) }, enabled = ok, sub = if (ok) null else "tak ada encoder")
        }
    }
    Section("Audio AAC")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportSettings.AUDIO_BITRATES.forEach { b -> OptionChip("${b / 1000} kbps", settings.audioBitrate == b, { set(settings.copy(audioBitrate = b)) }) }
    }

    Spacer(Modifier.height(16.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MColors.Surface2)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (plan != null) {
            Info("Ukuran", "${plan.size} · ${plan.frameRate} FPS maks")
            Info("Bitrate video", "%.1f Mbps".format(plan.videoBitrate / 1_000_000f))
            Info("Durasi", Timecode.format(plan.expectedDurationUs, project.canvas.frameRate))
            Info("Perkiraan ukuran file", "± " + Timecode.bytes(plan.estimatedBytes))
        }
        Info("Disimpan ke", vm.export.locationLabel)
    }
    Text(
        "Hasil berupa MP4 terkompresi (bukan lossless). Video beresolusi rendah tidak menjadi lebih tajam bila diekspor lebih besar.",
        style = MaterialTheme.typography.bodySmall,
        color = MColors.TextFaint,
        modifier = Modifier.padding(vertical = 10.dp),
    )
    AccentPill(
        if (exportState is ExportState.Failed) "Coba lagi" else "Ekspor sekarang",
        vm::startExport,
        icon = MIcons.Export,
        enabled = plan != null && settings.resolution in options.supportedResolutions,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MColors.TextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MColors.Text, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Progress(state: ExportState.Running, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsed = (now - state.startedAtMs).coerceAtLeast(0)
    Spacer(Modifier.height(20.dp))
    Text(
        state.progress?.let { "$it%" } ?: "Menyiapkan…",
        style = MaterialTheme.typography.displaySmall,
        color = MColors.Text,
    )
    Spacer(Modifier.height(10.dp))
    val pct = state.progress
    if (pct != null) {
        LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = MColors.Accent,
            trackColor = MColors.Surface3,
        )
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = MColors.Accent, trackColor = MColors.Surface3)
    }
    Spacer(Modifier.height(10.dp))
    Text("Berjalan ${Timecode.short(elapsed * 1000)} · biarkan layar tetap menyala", style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted)
    Spacer(Modifier.height(20.dp))
    QuietButton("Batalkan ekspor", onCancel, icon = MIcons.Close, color = MColors.Danger, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Verifying() {
    Spacer(Modifier.height(20.dp))
    Text("Memeriksa hasil…", style = MaterialTheme.typography.titleMedium, color = MColors.Text)
    Text("Durasi, trek video dan audio diverifikasi sebelum disimpan.", style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted)
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = MColors.Accent, trackColor = MColors.Surface3)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Success(state: ExportState.Success, onPlay: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val out = state.output
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MColors.Success.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(MIcons.Check, contentDescription = null, tint = MColors.Success)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Video selesai & terverifikasi", style = MaterialTheme.typography.titleMedium, color = MColors.Text)
            Text("Dirender dalam ${Timecode.short(state.elapsedMs * 1000)}", style = MaterialTheme.typography.bodySmall, color = MColors.TextMuted)
        }
    }
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MColors.Surface2).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Info("Ukuran", "${out.width}×${out.height}")
        Info("Durasi", Timecode.format(out.durationUs))
        Info("Ukuran file", Timecode.bytes(out.sizeBytes))
        Info("Nama file", out.fileName)
        Info("Lokasi", out.locationLabel)
        out.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MColors.Warning, modifier = Modifier.padding(vertical = 4.dp)) }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        AccentPill("Putar", onPlay, icon = MIcons.Play, modifier = Modifier.weight(1f))
        QuietButton("Bagikan", {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, out.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Bagikan video"))
        }, icon = MIcons.Share, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    QuietButton("Selesai", onDone, modifier = Modifier.fillMaxWidth())
}

/** Plays the exported file itself, so the user can confirm the real output. */
@OptIn(UnstableApi::class)
@Composable
private fun ExportedPlayer(output: ExportOutput, onClose: () -> Unit) {
    val context = LocalContext.current
    val uri: Uri = output.uri
    val ratio = if (output.height > 0) output.width.toFloat() / output.height else 16f / 9f
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(Color.Black).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hasil ekspor", style = MaterialTheme.typography.titleSmall, color = MColors.Text, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconAction(MIcons.Close, "Tutup", onClose)
            }
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(max = 560.dp)
                    .aspectRatio(ratio, matchHeightConstraintsFirst = true),
            )
        }
    }
}
