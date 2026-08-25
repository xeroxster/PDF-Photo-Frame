package com.pdfphotoframe.app.ui.slideshow

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pdfphotoframe.app.data.PdfPageRepository
import com.pdfphotoframe.app.data.SettingsRepository
import com.pdfphotoframe.app.slideshow.SlideshowController
import kotlinx.coroutines.delay

@Composable
fun SlideshowScreen(
    settingsRepository: SettingsRepository,
    pdfPageRepository: PdfPageRepository,
    onExit: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settingsFlow.collectAsState(initial = null)

    val controller = remember { SlideshowController(scope) }
    val currentPageIndex by controller.currentPageIndex.collectAsState()

    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var overlayVisible by remember { mutableStateOf(false) }

    // Hide system bars (immersive) while this screen is shown; restore them on exit
    // so the picker/settings screens behave normally again.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val insetsController = window?.let { WindowInsetsControllerCompat(it, view) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            controller.stop()
        }
    }

    // (Re)start the controller whenever the relevant settings or page count change.
    LaunchedEffect(settings, pdfPageRepository.pageCount) {
        val s = settings ?: return@LaunchedEffect
        val totalPages = pdfPageRepository.pageCount
        if (totalPages == 0) return@LaunchedEffect
        controller.start(
            totalPages = totalPages,
            excludedPages = s.excludedPages,
            orderMode = s.orderMode,
            intervalMs = s.intervalMs
        )
    }

    // Render the bitmap for whatever page the controller currently points at.
    LaunchedEffect(currentPageIndex) {
        val index = currentPageIndex ?: return@LaunchedEffect
        val displayMetrics = context.resources.displayMetrics
        currentBitmap = pdfPageRepository.renderPage(
            index,
            targetWidthPx = displayMetrics.widthPixels,
            targetHeightPx = displayMetrics.heightPixels
        )
    }

    // Auto-hide the tap-to-reveal exit overlay after a few seconds.
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            delay(4000)
            overlayVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { overlayVisible = true }
    ) {
        currentBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = overlayVisible,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.Close, contentDescription = "Exit slideshow", tint = Color.White)
                }
            }
        }
    }
}
