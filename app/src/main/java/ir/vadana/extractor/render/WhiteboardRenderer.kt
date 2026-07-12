package ir.vadana.extractor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import ir.vadana.extractor.domain.BoardShape
import ir.vadana.extractor.domain.PageKey
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WhiteboardRenderer {
    companion object {
        const val NATIVE_WIDTH = 800
        const val NATIVE_HEIGHT = 600
    }

    fun renderPage(
        shapes: Collection<BoardShape>,
        width: Int,
        height: Int,
        background: Bitmap? = null,
        label: String? = null,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        background?.let {
            canvas.drawBitmap(it, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val scaleX = width / NATIVE_WIDTH.toFloat()
        val scaleY = height / NATIVE_HEIGHT.toFloat()
        shapes.sortedBy { it.depth }.forEach { drawShape(canvas, it, scaleX, scaleY) }
        if (!label.isNullOrBlank()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                textSize = max(16f, width / 80f)
            }
            canvas.drawText(label, 12f, paint.textSize + 8f, paint)
        }
        return bitmap
    }

    fun fitToCanvas(source: Bitmap, outWidth: Int, outHeight: Int, blackBars: Boolean = false): Bitmap {
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(if (blackBars) Color.BLACK else Color.WHITE)
        val sourceAspect = source.width / source.height.toFloat()
        val outputAspect = outWidth / outHeight.toFloat()
        val target = if (sourceAspect >= outputAspect) {
            val h = (outWidth / sourceAspect).roundToInt()
            Rect(0, (outHeight - h) / 2, outWidth, (outHeight + h) / 2)
        } else {
            val w = (outHeight * sourceAspect).roundToInt()
            Rect((outWidth - w) / 2, 0, (outWidth + w) / 2, outHeight)
        }
        canvas.drawBitmap(source, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun drawShape(canvas: Canvas, shape: BoardShape, scaleX: Float, scaleY: Float) {
        when (shape) {
            is BoardShape.Pencil -> drawPencil(canvas, shape, scaleX, scaleY)
            is BoardShape.Text -> drawText(canvas, shape, scaleX, scaleY)
        }
    }

    private fun drawPencil(canvas: Canvas, shape: BoardShape.Pencil, scaleX: Float, scaleY: Float) {
        val points = shape.points.mapNotNull { point ->
            if (!point.x.isFinite() || !point.y.isFinite()) null
            else PointF(
                point.x.coerceIn(0f, NATIVE_WIDTH.toFloat()) * scaleX,
                point.y.coerceIn(0f, NATIVE_HEIGHT.toFloat()) * scaleY,
            )
        }.distinctBy { it.x to it.y }
        if (points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shape.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(2f, shape.width * (scaleX + scaleY) / 2f)
        }
        if (points.size == 1) {
            canvas.drawCircle(points[0].x, points[0].y, paint.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
            return
        }
        val smooth = smooth(points)
        val path = Path().apply {
            moveTo(smooth.first().x, smooth.first().y)
            smooth.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawText(canvas: Canvas, shape: BoardShape.Text, scaleX: Float, scaleY: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shape.color
            textSize = max(8f, shape.textSize * (scaleX + scaleY) / 2f * 1.05f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        var y = shape.y.coerceIn(0f, NATIVE_HEIGHT.toFloat()) * scaleY
        val x = shape.x.coerceIn(0f, NATIVE_WIDTH.toFloat()) * scaleX
        val lineHeight = paint.textSize * 1.3f
        shape.lines.forEach { line ->
            y += paint.textSize
            canvas.drawText(line, x, y, paint)
            y += lineHeight - paint.textSize
        }
    }

    private fun smooth(points: List<PointF>, steps: Int = 6): List<PointF> {
        if (points.size < 3) return points
        val output = mutableListOf(points.first())
        for (i in 0 until points.lastIndex) {
            val p0 = if (i == 0) points[0] else points[i - 1]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i + 2 < points.size) points[i + 2] else points.last()
            for (j in 1..steps) {
                val t = j / steps.toFloat()
                val t2 = t * t
                val t3 = t2 * t
                val x = 0.5f * (2f * p1.x + (p2.x - p0.x) * t +
                    (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                    (3f * p1.x - p0.x - 3f * p2.x + p3.x) * t3)
                val y = 0.5f * (2f * p1.y + (p2.y - p0.y) * t +
                    (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                    (3f * p1.y - p0.y - 3f * p2.y + p3.y) * t3)
                output += PointF(x, y)
            }
        }
        return output
    }
}
