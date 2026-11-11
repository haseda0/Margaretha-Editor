package id.haseda.margaretha.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import id.haseda.margaretha.media.ThumbnailCache

/** Asynchronously decoded video frame; shows a quiet placeholder until ready. */
@Composable
fun VideoFrame(cache: ThumbnailCache, uri: String?, timeUs: Long, heightDp: Dp, modifier: Modifier = Modifier) {
    val heightPx = with(LocalDensity.current) { heightDp.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = uri?.let { cache.peek(it, timeUs, heightPx) }, uri, timeUs / 250_000, heightPx) {
        if (uri != null && value == null) value = cache.get(uri, timeUs, heightPx)
    }
    Box(modifier.background(MColors.Surface3)) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

