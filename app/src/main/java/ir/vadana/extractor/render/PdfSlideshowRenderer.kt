package ir.vadana.extractor.render

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class PdfSlideshowRenderer {
    data class TimedFrame(val startSeconds: Double, val file: File)

    fun render(
        pdfFiles: List<File>,
        outputDirectory: File,
        outWidth: Int,
        outHeight: Int,
        durationSeconds: Double,
    ): List<TimedFrame> {
        outputDirectory.mkdirs()
        val pageRefs = mutableListOf<Pair<File, Int>>()
        pdfFiles.forEach { file ->
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        repeat(renderer.pageCount) { pageRefs += file to it }
                    }
                }
            }
        }
        if (pageRefs.isEmpty()) return emptyList()
        val perPage = if (durationSeconds > 0) (durationSeconds / pageRefs.size).coerceAtLeast(2.0) else 4.0
        return pageRefs.mapIndexed { index, (file, pageIndex) ->
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            val bitmap = renderer.openPage(pageIndex).use { page ->
                renderPdfPageFitted(page, outWidth, outHeight).bitmap
            }
            renderer.close()
            descriptor.close()
            val output = File(outputDirectory, "s${index.toString().padStart(5, '0')}.png")
            output.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            TimedFrame(index * perPage, output)
        }.let { frames ->
            if (durationSeconds > 0 && frames.last().startSeconds < durationSeconds) {
                frames + TimedFrame(durationSeconds, frames.last().file)
            } else frames
        }
    }
}
