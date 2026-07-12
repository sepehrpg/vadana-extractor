package ir.vadana.extractor.render

import android.graphics.pdf.PdfDocument
import ir.vadana.extractor.domain.Whiteboard
import java.io.File

class WhiteboardPdfExporter(
    private val renderer: WhiteboardRenderer = WhiteboardRenderer(),
) {
    fun export(
        whiteboard: Whiteboard,
        destination: File,
        backgrounds: PdfBackgroundSource? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): File? {
        val pages = whiteboard.pages
        if (pages.isEmpty()) return null
        destination.parentFile?.mkdirs()
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, pageKey ->
                val width = 1600
                val height = 1200
                val background = backgrounds?.render(pageKey, width, height)
                val bitmap = renderer.renderPage(
                    shapes = whiteboard.finalShapes[pageKey].orEmpty().values,
                    width = width,
                    height = height,
                    background = background,
                    label = "page ${index + 1}",
                )
                val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = document.startPage(info)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
                bitmap.recycle()
                onProgress(index + 1, pages.size)
            }
            destination.outputStream().buffered().use(document::writeTo)
            return destination
        } finally {
            document.close()
        }
    }
}
