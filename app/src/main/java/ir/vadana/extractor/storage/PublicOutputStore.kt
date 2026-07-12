package ir.vadana.extractor.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import ir.vadana.extractor.domain.ExportedItem
import java.io.File

class PublicOutputStore(private val context: Context) {

    fun publish(source: File, displayName: String, mimeType: String): ExportedItem {
        val (collection, relativePath) = when {
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                "${Environment.DIRECTORY_MOVIES}/Vadana"
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                "${Environment.DIRECTORY_MUSIC}/Vadana"
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to
                "${Environment.DIRECTORY_DOWNLOADS}/Vadana"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: error("ساخت فایل خروجی در حافظهٔ عمومی انجام نشد.")
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return ExportedItem(displayName, uri.toString(), mimeType)
        } catch (t: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw t
        }
    }

    fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "txt" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
