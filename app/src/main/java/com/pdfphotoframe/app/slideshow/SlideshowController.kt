package com.pdfphotoframe.app.slideshow

import com.pdfphotoframe.app.data.OrderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Decides which page index should be showing and when to advance.
 *
 * Sequential mode walks the eligible pages in order and loops.
 * Random mode shuffles the full eligible-page list and walks that shuffled order,
 * reshuffling each time it's exhausted -- this gives every page one showing per
 * "lap" instead of picking independently each tick, which tends to repeat pages
 * and leave others un-shown for long stretches.
 */
class SlideshowController(private val scope: CoroutineScope) {

    private val _currentPageIndex = MutableStateFlow<Int?>(null)
    val currentPageIndex: StateFlow<Int?> = _currentPageIndex

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var orderedPages: List<Int> = emptyList()
    private var positionInOrder = 0
    private var loopJob: Job? = null
    private var currentOrderMode = OrderMode.SEQUENTIAL

    fun start(totalPages: Int, excludedPages: Set<Int>, orderMode: OrderMode, intervalMs: Long) {
        stop()
        val eligiblePages = (0 until totalPages).filterNot { it in excludedPages }
        if (eligiblePages.isEmpty()) {
            _currentPageIndex.value = null
            return
        }

        currentOrderMode = orderMode
        orderedPages = if (orderMode == OrderMode.RANDOM) eligiblePages.shuffled() else eligiblePages
        positionInOrder = 0
        _currentPageIndex.value = orderedPages[positionInOrder]
        _isRunning.value = true

        loopJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                advance()
            }
        }
    }

    private fun advance() {
        positionInOrder++
        if (positionInOrder >= orderedPages.size) {
            positionInOrder = 0
            if (currentOrderMode == OrderMode.RANDOM) {
                orderedPages = orderedPages.shuffled()
            }
        }
        _currentPageIndex.value = orderedPages[positionInOrder]
    }

    fun pause() {
        loopJob?.cancel()
        loopJob = null
        _isRunning.value = false
    }

    fun stop() {
        pause()
        orderedPages = emptyList()
        positionInOrder = 0
        _currentPageIndex.value = null
    }
}
