package ir.vadana.extractor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import kotlin.math.max
import kotlin.math.roundToInt

internal data class RenderedPdfPage(
    val bitmap: Bitmap,
    val contentRect: RectF,
)

internal fun renderPdfPageFitted(
    page: PdfRenderer.Page,
    outputWidth: Int,
    outputHeight: Int,
): RenderedPdfPage {
    require(outputWidth > 0 && outputHeight > 0)
    val pageAspect = page.width.toFloat() / max(1, page.height)
    val outputAspect = outputWidth.toFloat() / outputHeight
    val contentWidth: Int
    val contentHeight: Int
    if (pageAspect >= outputAspect) {
        contentWidth = outputWidth
        contentHeight = (outputWidth / pageAspect).roundToInt().coerceAtLeast(1)
    } else {
        contentHeight = outputHeight
        contentWidth = (outputHeight * pageAspect).roundToInt().coerceAtLeast(1)
    }

    val pageBitmap = Bitmap.createBitmap(contentWidth, contentHeight, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
    }
    page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

    if (contentWidth == outputWidth && contentHeight == outputHeight) {
        return RenderedPdfPage(pageBitmap, RectF(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat()))
    }

    val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
    }
    val left = (outputWidth - contentWidth) / 2f
    val top = (outputHeight - contentHeight) / 2f
    Canvas(output).drawBitmap(pageBitmap, left, top, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    pageBitmap.recycle()
    return RenderedPdfPage(
        bitmap = output,
        contentRect = RectF(left, top, left + contentWidth, top + contentHeight),
    )
}
