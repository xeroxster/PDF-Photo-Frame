package com.pdfphotoframe.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps Android's native PdfRenderer. PdfRenderer only allows one Page open at a time
 * -- across the *entire* renderer, not just per-caller -- so this repository serializes
 * every open()/renderPage() call with a Mutex. Without that, the setup screen's thumbnail
 * loop and the slideshow screen's page rendering can call openPage() concurrently (e.g. if
 * the user taps "Start Slideshow" while thumbnails are still being generated in the
 * background) and crash with "Current page not closed".
 * The underlying ParcelFileDescriptor + PdfRenderer stay open for the whole document's
 * lifetime, shared between the setup screen (thumbnails) and the slideshow screen (fullscreen).
 */
class PdfPageRepository(private val context: Context) {

    private val mutex = Mutex()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    var pageCount: Int = 0
        private set

    suspend fun open(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext false
                val newRenderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                renderer = newRenderer
                pageCount = newRenderer.pageCount
                true
            } catch (e: Exception) {
                closeLocked()
                false
            }
        }
    }

    /**
     * Renders one page, scaled to fit within the given target dimensions while
     * preserving the page's aspect ratio (so it displays correctly on any screen size).
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int, targetHeightPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val currentRenderer = renderer ?: return@withContext null
                if (index !in 0 until currentRenderer.pageCount) return@withContext null

                currentRenderer.openPage(index).use { page ->
                    val scale = minOf(
                        targetWidthPx.toFloat() / page.width,
                        targetHeightPx.toFloat() / page.height
                    )
                    val bitmapWidth = (page.width * scale).toInt().coerceAtLeast(1)
                    val bitmapHeight = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE) // PDF pages assume an opaque white background
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock { closeLocked() }
    }

    private fun closeLocked() {
        renderer?.close()
        fileDescriptor?.close()
        renderer = null
        fileDescriptor = null
        pageCount = 0
    }
}
