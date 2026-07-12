package ir.vadana.extractor.render

import android.graphics.Bitmap
import ir.vadana.extractor.domain.BoardEvent
import ir.vadana.extractor.domain.BoardShape
import ir.vadana.extractor.domain.PageKey
import ir.vadana.extractor.domain.Whiteboard
import java.io.File

class WhiteboardFrameRenderer(
    private val renderer: WhiteboardRenderer = WhiteboardRenderer(),
) {
    data class TimedFrame(val startSeconds: Double, val file: File)

    fun render(
        whiteboard: Whiteboard,
        framesDirectory: File,
        outWidth: Int,
        outHeight: Int,
        maxFps: Float,
        backgrounds: PdfBackgroundSource? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<TimedFrame> {
        framesDirectory.mkdirs()
        val intervalMs = 1000.0 / maxFps.coerceIn(0.5f, 10f)
        val state = mutableMapOf<PageKey, MutableMap<String, BoardShape>>()
        val frames = mutableListOf<TimedFrame>()
        var frameIndex = 0
        var lastEmit = -1e12

        fun emit(timeMs: Long, page: PageKey) {
            val shapes = state.getOrPut(page) { linkedMapOf() }.values
            val boardWidth = 1600
            val boardHeight = 1200
            val background = backgrounds?.render(page, boardWidth, boardHeight)
            val board = renderer.renderPage(shapes, boardWidth, boardHeight, background)
            val fitted = renderer.fitToCanvas(board, outWidth, outHeight)
            board.recycle()
            val file = File(framesDirectory, "f${frameIndex.toString().padStart(6, '0')}.png")
            file.outputStream().buffered().use { fitted.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fitted.recycle()
            frames += TimedFrame(timeMs / 1000.0, file)
            frameIndex++
        }

        val nav = cleanNavigation(whiteboard.navigation.map { it.timestampMs to it.page })
        if (nav.isNotEmpty()) {
            data class TimelineItem(
                val time: Long,
                val showPage: PageKey? = null,
                val drawEvent: BoardEvent? = null,
            )
            val items = buildList {
                nav.forEach { add(TimelineItem(it.first, showPage = it.second)) }
                whiteboard.events.forEach { add(TimelineItem(it.timestampMs, drawEvent = it)) }
            }.sortedWith(compareBy<TimelineItem> { it.time }.thenBy { if (it.showPage != null) 0 else 1 })
            var displayed: PageKey? = null
            items.forEachIndexed { index, item ->
                val showPage = item.showPage
                val drawEvent = item.drawEvent
                if (showPage != null) {
                    if (displayed != showPage) {
                        displayed = showPage
                        state.getOrPut(showPage) { linkedMapOf() }
                        emit(item.time, showPage)
                        lastEmit = item.time.toDouble()
                    }
                } else if (drawEvent != null) {
                    applyEvent(state, drawEvent)
                    if (drawEvent.page == displayed && item.time - lastEmit >= intervalMs) {
                        emit(item.time, drawEvent.page)
                        lastEmit = item.time.toDouble()
                    }
                }
                if (index % 15 == 0) onProgress(index + 1, items.size)
            }
            displayed?.let { page ->
                if (frames.isEmpty() || frames.last().startSeconds < whiteboard.durationMs / 1000.0) {
                    emit(whiteboard.durationMs, page)
                }
            }
            return frames
        }

        var currentPage: PageKey? = null
        whiteboard.events.forEachIndexed { index, event ->
            applyEvent(state, event)
            currentPage = event.page
            if (event.timestampMs - lastEmit >= intervalMs) {
                emit(event.timestampMs, event.page)
                lastEmit = event.timestampMs.toDouble()
            }
            if (index % 15 == 0) onProgress(index + 1, whiteboard.events.size)
        }
        currentPage?.let { page ->
            if (frames.isEmpty() || frames.last().startSeconds < whiteboard.durationMs / 1000.0) {
                emit(whiteboard.durationMs, page)
            }
        }
        return frames
    }

    private fun applyEvent(
        state: MutableMap<PageKey, MutableMap<String, BoardShape>>,
        event: BoardEvent,
    ) {
        val page = state.getOrPut(event.page) { linkedMapOf() }
        if (event.shape == null) page.remove(event.shapeId) else page[event.shapeId] = event.shape
    }

    private fun cleanNavigation(
        navigation: List<Pair<Long, PageKey>>,
        minShowMs: Long = 500,
    ): List<Pair<Long, PageKey>> {
        val sorted = navigation.distinct().sortedBy { it.first }
        val result = sorted.filterIndexed { index, (time, _) ->
            val next = sorted.getOrNull(index + 1)?.first ?: Long.MAX_VALUE
            next - time >= minShowMs
        }
        return result.ifEmpty { sorted.takeLast(1) }
    }
}
