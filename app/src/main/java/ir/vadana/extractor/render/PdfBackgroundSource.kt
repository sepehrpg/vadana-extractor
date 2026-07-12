package ir.vadana.extractor.render

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import ir.vadana.extractor.domain.PageKey
import java.io.Closeable
import java.io.File

class PdfBackgroundSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val pageIndexByKey: Map<PageKey, Int>,
) : Closeable {
    private val cache = object : LruCache<String, Bitmap>(3) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (oldValue !== newValue && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    fun render(pageKey: PageKey, width: Int, height: Int): Bitmap? {
        val index = pageIndexByKey[pageKey] ?: return null
        val cacheKey = "$index:$width:$height"
        cache.get(cacheKey)?.let { return it }
        if (index !in 0 until renderer.pageCount) return null
        val bitmap = renderer.openPage(index).use { page ->
            renderPdfPageFitted(page, width, height).bitmap
        }
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    override fun close() {
        cache.evictAll()
        renderer.close()
        descriptor.close()
    }

    companion object {
        fun openMatching(pdfFiles: List<File>, pageKeys: List<PageKey>): PdfBackgroundSource? {
            if (pdfFiles.isEmpty() || pageKeys.isEmpty()) return null
            data class Candidate(val file: File, val count: Int)
            val candidates = pdfFiles.mapNotNull { file ->
                runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { Candidate(file, it.pageCount) }
                    }
                }.getOrNull()
            }
            val direct = candidates.firstOrNull { candidate ->
                pageKeys.all { it.pageNumber in 0 until candidate.count }
            }
            val ordered = candidates.firstOrNull { it.count == pageKeys.size }
            val selected = direct ?: ordered ?: return null
            val descriptor = ParcelFileDescriptor.open(selected.file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            val mapping = if (pageKeys.all { it.pageNumber in 0 until selected.count }) {
                pageKeys.associateWith { it.pageNumber }
            } else {
                pageKeys.withIndex().associate { it.value to it.index }
            }
            return PdfBackgroundSource(descriptor, renderer, mapping)
        }
    }
}
