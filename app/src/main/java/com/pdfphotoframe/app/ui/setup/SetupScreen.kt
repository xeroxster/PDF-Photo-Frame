package com.pdfphotoframe.app.ui.setup

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.pdfphotoframe.app.data.OrderMode
import com.pdfphotoframe.app.data.PdfPageRepository
import com.pdfphotoframe.app.data.SettingsRepository
import kotlinx.coroutines.launch

/** Display unit for the "change every N ___" control. Values are milliseconds-per-unit. */
private enum class IntervalUnit(val label: String, val millisPerUnit: Long) {
    SECONDS("Seconds", 1_000L),
    MINUTES("Minutes", 60_000L),
    HOURS("Hours", 3_600_000L)
}

/** Picks the largest whole unit that evenly divides the stored interval, for display. */
private fun millisToAmountAndUnit(intervalMs: Long): Pair<Long, IntervalUnit> {
    return when {
        intervalMs >= IntervalUnit.HOURS.millisPerUnit && intervalMs % IntervalUnit.HOURS.millisPerUnit == 0L ->
            (intervalMs / IntervalUnit.HOURS.millisPerUnit) to IntervalUnit.HOURS
        intervalMs >= IntervalUnit.MINUTES.millisPerUnit && intervalMs % IntervalUnit.MINUTES.millisPerUnit == 0L ->
            (intervalMs / IntervalUnit.MINUTES.millisPerUnit) to IntervalUnit.MINUTES
        else ->
            (intervalMs / IntervalUnit.SECONDS.millisPerUnit).coerceAtLeast(1L) to IntervalUnit.SECONDS
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    settingsRepository: SettingsRepository,
    pdfPageRepository: PdfPageRepository,
    onStartSlideshow: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settingsFlow.collectAsState(initial = null)

    var pageCount by remember { mutableStateOf(0) }
    var thumbnails by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var isLoadingPdf by remember { mutableStateOf(false) }

    var intervalAmountText by remember { mutableStateOf("10") }
    var intervalUnit by remember { mutableStateOf(IntervalUnit.SECONDS) }
    var intervalInitialized by remember { mutableStateOf(false) }
    var intervalUnitMenuExpanded by remember { mutableStateOf(false) }

    // Seed the amount/unit fields from the persisted interval exactly once, so
    // typing in the field afterward doesn't get stomped by the settings Flow.
    LaunchedEffect(settings?.intervalMs) {
        val intervalMs = settings?.intervalMs
        if (!intervalInitialized && intervalMs != null) {
            val (amount, unit) = millisToAmountAndUnit(intervalMs)
            intervalAmountText = amount.toString()
            intervalUnit = unit
            intervalInitialized = true
        }
    }

    fun updateInterval(amountText: String, unit: IntervalUnit) {
        val amount = amountText.toLongOrNull() ?: return
        if (amount <= 0) return
        scope.launch { settingsRepository.setIntervalMs(amount * unit.millisPerUnit) }
    }

    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoadingPdf = true
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            settingsRepository.setPdfUri(uri.toString())
            settingsRepository.setExcludedPages(emptySet())
            val opened = pdfPageRepository.open(uri)
            pageCount = if (opened) pdfPageRepository.pageCount else 0
            thumbnails = emptyMap()
            isLoadingPdf = false
        }
    }

    // Re-open the previously chosen PDF when this screen appears (e.g. coming back
    // from the slideshow) since PdfPageRepository's open document isn't re-fetched otherwise.
    LaunchedEffect(settings?.pdfUri) {
        val uriString = settings?.pdfUri ?: return@LaunchedEffect
        if (pageCount == 0 && !isLoadingPdf) {
            isLoadingPdf = true
            val opened = pdfPageRepository.open(Uri.parse(uriString))
            pageCount = if (opened) pdfPageRepository.pageCount else 0
            isLoadingPdf = false
        }
    }

    // Lazily render thumbnails once the page count is known.
    LaunchedEffect(pageCount) {
        if (pageCount == 0) return@LaunchedEffect
        for (i in 0 until pageCount) {
            if (thumbnails.containsKey(i)) continue
            val bitmap = pdfPageRepository.renderPage(i, targetWidthPx = 240, targetHeightPx = 320)
            if (bitmap != null) {
                thumbnails = thumbnails + (i to bitmap)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("PDF Photo Frame") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Button(onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) }) {
                Text(if (settings?.pdfUri == null) "Choose PDF" else "Choose a different PDF")
            }

            if (isLoadingPdf) {
                CircularProgressIndicator()
            }

            val currentSettings = settings
            if (pageCount > 0 && currentSettings != null) {

                Column {
                    Text("Order", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentSettings.orderMode == OrderMode.SEQUENTIAL,
                            onClick = { scope.launch { settingsRepository.setOrderMode(OrderMode.SEQUENTIAL) } },
                            label = { Text("Continuous") }
                        )
                        FilterChip(
                            selected = currentSettings.orderMode == OrderMode.RANDOM,
                            onClick = { scope.launch { settingsRepository.setOrderMode(OrderMode.RANDOM) } },
                            label = { Text("Random") }
                        )
                    }
                }

                Column {
                    Text("Change every", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = intervalAmountText,
                            onValueChange = { newText ->
                                val digitsOnly = newText.filter { it.isDigit() }
                                intervalAmountText = digitsOnly
                                updateInterval(digitsOnly, intervalUnit)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = intervalUnitMenuExpanded,
                            onExpandedChange = { intervalUnitMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = intervalUnit.label,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalUnitMenuExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .width(160.dp)
                            )
                            DropdownMenu(
                                expanded = intervalUnitMenuExpanded,
                                onDismissRequest = { intervalUnitMenuExpanded = false },
                                modifier = Modifier.exposedDropdownSize()
                            ) {
                                IntervalUnit.entries.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit.label) },
                                        onClick = {
                                            intervalUnit = unit
                                            intervalUnitMenuExpanded = false
                                            updateInterval(intervalAmountText, unit)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    "Tap pages to exclude them (${currentSettings.excludedPages.size} excluded)",
                    style = MaterialTheme.typography.titleMedium
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(pageCount) { index ->
                        val isExcluded = index in currentSettings.excludedPages
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.75f)
                                .border(
                                    width = if (isExcluded) 3.dp else 1.dp,
                                    color = if (isExcluded) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    scope.launch {
                                        val updated = if (isExcluded) {
                                            currentSettings.excludedPages - index
                                        } else {
                                            currentSettings.excludedPages + index
                                        }
                                        settingsRepository.setExcludedPages(updated)
                                    }
                                },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            thumbnails[index]?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (isExcluded) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                )
                            }
                            Text(
                                "${index + 1}",
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                    .padding(2.dp)
                            )
                        }
                    }
                }

                val eligibleCount = pageCount - currentSettings.excludedPages.size
                Button(
                    onClick = onStartSlideshow,
                    enabled = eligibleCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Slideshow ($eligibleCount pages)")
                }
            }
        }
    }
}
