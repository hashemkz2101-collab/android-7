package ir.mahroch.tapekhash.ui.screens.tape

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/** نمایش تمام‌صفحه‌ی عکس تپه؛ با دو انگشت زوم می‌شود و اگر چند عکس باشد با کشیدن جابه‌جا می‌شود. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerDialog(urls: List<String>, startIndex: Int = 0, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, urls.lastIndex)) { urls.size }
    var zoomed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableImage(url = urls[page], onZoomChanged = { zoomed = it })
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) { Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.White) }

            if (urls.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            // دو بار لمس: برگشت به اندازه‌ی اصلی
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f; offsetX = 0f; offsetY = 0f
                    onZoomChanged(false)
                })
            }
            // زوم/جابه‌جایی فقط با دو انگشت یا وقتی عکس زوم شده؛
            // با یک انگشت روی عکس زوم‌نشده، رویداد مصرف نمی‌شود تا Pager بتواند صفحه را عوض کند.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val multiTouch = event.changes.size > 1
                        if (multiTouch || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale == 1f) {
                                offsetX = 0f; offsetY = 0f
                            } else {
                                offsetX += pan.x; offsetY += pan.y
                            }
                            onZoomChanged(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offsetX, translationY = offsetY
            )
    )
}
