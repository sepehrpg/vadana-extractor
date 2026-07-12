package ir.vadana.extractor.util

import java.io.File
import java.security.MessageDigest

fun File.ensureParent(): File {
    parentFile?.mkdirs()
    return this
}

fun safeFileName(raw: String): String {
    val leaf = raw.replace('\\', '/').substringAfterLast('/')
    val cleaned = leaf
        .replace(Regex("[:*?\"<>|\\p{Cntrl}]"), "_")
        .trim()
        .trimStart('.')
        .take(180)
    return cleaned.ifBlank { "file" }
}

fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
