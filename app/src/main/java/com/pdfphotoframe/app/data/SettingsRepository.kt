package com.pdfphotoframe.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pdf_photo_frame_settings")

enum class OrderMode { SEQUENTIAL, RANDOM }

data class SlideshowSettings(
    val pdfUri: String? = null,
    val intervalMs: Long = 10_000L,
    val orderMode: OrderMode = OrderMode.SEQUENTIAL,
    val excludedPages: Set<Int> = emptySet()
)

/**
 * Persists slideshow configuration via Jetpack DataStore so it survives process death
 * and app restarts. Exposed as a Flow so screens stay in sync automatically.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val PDF_URI = stringPreferencesKey("pdf_uri")
        val INTERVAL_MS = longPreferencesKey("interval_ms")
        val ORDER_MODE = stringPreferencesKey("order_mode")
        val EXCLUDED_PAGES = stringPreferencesKey("excluded_pages")
    }

    val settingsFlow: Flow<SlideshowSettings> = context.dataStore.data.map { prefs ->
        SlideshowSettings(
            pdfUri = prefs[Keys.PDF_URI],
            intervalMs = prefs[Keys.INTERVAL_MS] ?: 10_000L,
            orderMode = prefs[Keys.ORDER_MODE]?.let {
                runCatching { OrderMode.valueOf(it) }.getOrDefault(OrderMode.SEQUENTIAL)
            } ?: OrderMode.SEQUENTIAL,
            excludedPages = prefs[Keys.EXCLUDED_PAGES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                ?: emptySet()
        )
    }

    suspend fun setPdfUri(uri: String) {
        context.dataStore.edit { it[Keys.PDF_URI] = uri }
    }

    suspend fun setIntervalMs(intervalMs: Long) {
        context.dataStore.edit { it[Keys.INTERVAL_MS] = intervalMs }
    }

    suspend fun setOrderMode(mode: OrderMode) {
        context.dataStore.edit { it[Keys.ORDER_MODE] = mode.name }
    }

    suspend fun setExcludedPages(pages: Set<Int>) {
        context.dataStore.edit { it[Keys.EXCLUDED_PAGES] = pages.sorted().joinToString(",") }
    }
}
