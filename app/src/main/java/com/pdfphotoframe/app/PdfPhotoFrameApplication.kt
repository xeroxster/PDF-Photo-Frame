package com.pdfphotoframe.app

import android.app.Application
import com.pdfphotoframe.app.data.PdfPageRepository
import com.pdfphotoframe.app.data.SettingsRepository

/**
 * Holds the app's two long-lived singletons:
 *  - SettingsRepository: persisted config (PDF uri, interval, order mode, excluded pages)
 *  - PdfPageRepository: the currently open PDF document + page rendering
 *
 * Both the setup screen (thumbnails) and the slideshow screen (fullscreen pages)
 * share the same open PdfPageRepository so the document isn't reopened on navigation.
 */
class PdfPhotoFrameApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var pdfPageRepository: PdfPageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        pdfPageRepository = PdfPageRepository(this)
    }
}
