package ir.vadana.extractor.data

import ir.vadana.extractor.domain.FileCategory
import ir.vadana.extractor.domain.SharedFile
import java.net.URI
import java.net.URLDecoder

object MainstreamParser {
    private val downloadRegex = Regex(
        "<downloadUrl><!\\[CDATA\\[([^]]+)]]></downloadUrl>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val metadataDurationRegex = Regex(
        "onMetaData.*?<Number><!\\[CDATA\\[([\\d.]+)]]>",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun findSharedFiles(xml: String): List<SharedFile> {
        val seen = linkedSetOf<String>()
        return buildList {
            downloadRegex.findAll(xml).forEach { match ->
                val raw = match.groupValues[1]
                val uri = runCatching { URI(raw) }.getOrNull() ?: return@forEach
                val query = parseQuery(uri.rawQuery.orEmpty())
                val sourceBase = query["download-url"]?.firstOrNull().orEmpty()
                val name = query["name"]?.firstOrNull()?.let(::decode).orEmpty().ifBlank { "file.pdf" }
                if (sourceBase.startsWith('/') && seen.add(name)) {
                    add(SharedFile(sourceBase, name, categoryOf(name)))
                }
            }
        }
    }

    fun metadataDurationSeconds(xml: String): Double = metadataDurationRegex.find(xml)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
        ?: 0.0

    private fun parseQuery(query: String): Map<String, List<String>> = query
        .split('&')
        .filter { it.isNotBlank() }
        .map { part ->
            val index = part.indexOf('=')
            val key = decode(if (index >= 0) part.substring(0, index) else part)
            val value = decode(if (index >= 0) part.substring(index + 1) else "")
            key to value
        }
        .groupBy({ it.first }, { it.second })

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())

    private fun categoryOf(name: String): FileCategory {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "txt" -> FileCategory.DOCUMENT
            "mp3", "wav", "m4a", "ogg", "aac" -> FileCategory.AUDIO
            "mp4", "mkv", "mov", "avi", "flv", "webm" -> FileCategory.VIDEO
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> FileCategory.IMAGE
            else -> FileCategory.OTHER
        }
    }
}
