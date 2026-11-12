package id.haseda.margaretha.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.haseda.margaretha.ui.AccentPill
import id.haseda.margaretha.ui.MColors
import id.haseda.margaretha.ui.studioBackdrop
import id.haseda.margaretha.ui.MIcons
import id.haseda.margaretha.ui.raised

private data class Page(val icon: ImageVector, val title: String, val body: String)

private val PAGES = listOf(
    Page(MIcons.Film, "Studio di sakumu", "Potong, gabung, atur kecepatan, warna, efek, musik, teks, dan subtitle — lalu ekspor MP4 sungguhan."),
    Page(MIcons.Library, "Kamu yang memilih tempat simpan", "Proyek disimpan di aplikasi. Kamu bisa memilih folder sendiri — termasuk kartu SD atau USB — untuk hasil ekspor dan cadangan, supaya tetap ada walau aplikasi dihapus."),
    Page(MIcons.Lock, "Privat sejak awal", "Video, foto, dan audio tidak pernah diunggah. Penyuntingan berjalan penuh tanpa internet."),
    Page(MIcons.Sparkle, "Akun itu pilihan", "Masuk hanya untuk menandai proyek sebagai milikmu di perangkat bersama. Tanpa akun pun semua alat tetap terbuka."),
)

/** First-run introduction. Shown once; "Lewati" is always available. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = index > 0) { index-- }
    val page = PAGES[index]
    Box(Modifier.fillMaxSize().studioBackdrop().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("Lewati", color = MColors.TextMuted) }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp).raised(28.dp), contentAlignment = Alignment.Center) {
                Icon(page.icon, contentDescription = null, tint = MColors.Accent, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(page.title, style = MaterialTheme.typography.headlineSmall, color = MColors.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MColors.TextMuted, textAlign = TextAlign.Center)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAGES.indices.forEach { i ->
                    Box(Modifier.size(width = if (i == index) 20.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i == index) MColors.Accent else MColors.Surface3))
                }
            }
            Spacer(Modifier.height(20.dp))
            AccentPill(if (index == PAGES.lastIndex) "Mulai" else "Lanjut", { if (index == PAGES.lastIndex) onDone() else index++ }, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp))
        }
    }
}
