import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import id.haseda.margaretha.ui.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

@Composable
fun Showcase() {
    Column(Modifier.fillMaxSize().background(MColors.Base).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Readout("00:12.08", "01:04.00 · 30 fps")
            Spacer(Modifier.weight(1f))
            IconAction(MIcons.Stop, "stop", {}, size = 44.dp, tint = MColors.TextMuted)
            IconAction(MIcons.StepBack, "back", {}, size = 44.dp)
            IconAction(MIcons.Play, "play", {})
            IconAction(MIcons.StepForward, "fwd", {}, size = 44.dp)
        }
        JogWheel({}, Modifier.padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().background(MColors.Surface1).padding(horizontal = 6.dp)) {
            ToolButton(MIcons.Add, "Tambah", {})
            ToolButton(MIcons.Split, "Belah", {})
            ToolButton(MIcons.Speed, "Kecepatan", {}, active = true)
            ToolButton(MIcons.Transform, "Posisi", {}, enabled = false)
            ToolButton(MIcons.Delete, "Hapus", {}, danger = true)
        }
        Column(Modifier.background(MColors.Surface1).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("POSISI & UKURAN", style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint)
            KnobStrip {
                Knob("Skala", 1.4f, 0.1f..3f, "140%", {}, {}, resetTo = 1f)
                Knob("Rotasi", -45f, -180f..180f, "-45°", {}, {}, resetTo = 0f)
                Knob("Opasitas", 1f, 0f..1f, "100%", {}, {}, resetTo = 1f)
                Knob("Volume", 0.3f, 0f..1f, "30%", {}, {}, resetTo = 1f, enabled = false)
            }
            SliderRow("Geser X", 0.3f, -1f..1f, "+30", {}, {}, resetTo = 0f)
            SliderRow("Geser Y", 0f, -1f..1f, "0", {}, {}, resetTo = 0f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HardSwitch(true, {}, "on")
                Text("Bisukan", color = MColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                HardSwitch(false, {}, "off")
                MiniChip("Fade", true, {})
                MiniChip("Slide", false, {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentPill("Ekspor", {}, icon = MIcons.Export)
                QuietButton("Batal", {})
            }
        }
    }
}

fun main(args: Array<String>) {
    val scene = ImageComposeScene(412 * 2, 600 * 2, Density(2f)) {
        MaterialTheme(colorScheme = darkColorScheme()) { Showcase() }
    }
    val img = scene.render()
    File(args[0]).mkdirs()
    File(args[0], "tactile.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("ok")
}
