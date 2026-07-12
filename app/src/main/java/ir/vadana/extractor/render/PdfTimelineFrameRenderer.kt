package ir.vadana.extractor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import ir.vadana.extractor.domain.PointerEvent
import java.io.File

class PdfTimelineFrameRenderer {
    data class TimedFrame(val startSeconds: Double, val file: File)
    data class Result(val frames: List<TimedFrame>, val window: ClosedFloatingPointRange<Double>?)

    fun render(
        pdfFiles: List<File>,
        navigation: List<Pair<Long, Int>>,
        pointer: List<PointerEvent>,
        outputDirectory: File,
        outWidth: Int,
        outHeight: Int,
    ): Result {
        if (pdfFiles.isEmpty() || navigation.isEmpty()) return Result(emptyList(), null)
        val maxPage = navigation.maxOf { it.second }
        val candidates = pdfFiles.mapNotNull { file ->
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer -> renderer.pageCount to file }
                }
            }.getOrNull()
        }
        val selected = candidates.filter { it.first > maxPage }.minByOrNull { it.first }
            ?: candidates.maxByOrNull { it.first }
            ?: return Result(emptyList(), null)

        outputDirectory.mkdirs()
        val descriptor = ParcelFileDescriptor.open(selected.second, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val basePages = mutableMapOf<Int, RenderedPdfPage>()
        fun basePage(index: Int): RenderedPdfPage {
            val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
            return basePages.getOrPut(safeIndex) {
                renderer.openPage(safeIndex).use { page ->
                    renderPdfPageFitted(page, outWidth, outHeight)
                }
            }
        }

        val pageChanges = cleanPageChanges(navigation)
        data class Event(val time: Long, val page: Int? = null, val pointer: PointerEvent? = null)
        val events = buildList {
            pageChanges.forEach { add(Event(it.first, page = it.second)) }
            pointer.forEach { add(Event(it.timestampMs, pointer = it)) }
        }.sortedWith(compareBy<Event> { it.time }.thenBy { if (it.page != null) 0 else 1 })

        var currentPage = pageChanges.firstOrNull()?.second ?: 0
        var currentPointer: PointerEvent? = null
        val frames = mutableListOf<TimedFrame>()
        events.forEachIndexed { index, event ->
            event.page?.let { currentPage = it }
            event.pointer?.let { currentPointer = it }
            val base = basePage(currentPage)
            val bitmap = base.bitmap.copy(Bitmap.Config.ARGB_8888, true)
            currentPointer?.takeIf { it.visible }?.let { ptr ->
                val canvas = Canvas(bitmap)
                val rect = base.contentRect
                val x = rect.left + ptr.xPercent.coerceIn(0f, 100f) / 100f * rect.width()
                val y = rect.top + ptr.yPercent.coerceIn(0f, 100f) / 100f * rect.height()
                val radius = (outWidth / 150f).coerceAtLeast(7f)
                canvas.drawCircle(x, y, radius * 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(70, 255, 45, 45)
                })
                canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(220, 0, 0)
                })
            }
            val file = File(outputDirectory, "p${index.toString().padStart(6, '0')}.png")
            file.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            frames += TimedFrame(event.time / 1000.0, file)
        }
        basePages.values.forEach { it.bitmap.recycle() }
        renderer.close()
        descriptor.close()
        val window = navigation.first().first / 1000.0..navigation.last().first / 1000.0
        return Result(frames, window)
    }

    private fun cleanPageChanges(nav: List<Pair<Long, Int>>, minShowMs: Long = 800): List<Pair<Long, Int>> {
        val changes = mutableListOf<Pair<Long, Int>>()
        var last: Int? = null
        nav.sortedBy { it.first }.forEach { item ->
            if (item.second != last) {
                changes += item
                last = item.second
            }
        }
        val filtered = changes.filterIndexed { index, (time, _) ->
            val next = changes.getOrNull(index + 1)?.first ?: Long.MAX_VALUE
            next - time >= minShowMs
        }
        return filtered.ifEmpty { changes.takeLast(1) }
    }
}
