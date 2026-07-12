package ir.vadana.extractor.data

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class PackageArchive(file: File) : Closeable {
    private val zip = ZipFile(file)

    fun names(): List<String> = zip.entries().asSequence().map { it.name }.toList()

    fun contains(name: String): Boolean = zip.getEntry(name) != null

    fun entry(name: String): ZipEntry? = zip.getEntry(name)

    fun readBytes(name: String, maxBytes: Long = MAX_TEXT_ENTRY_BYTES): ByteArray {
        val entry = zip.getEntry(name) ?: throw NoSuchElementException("$name does not exist in the package.")
        if (entry.size > maxBytes) throw IOException("Entry $name is too large.")
        return zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream(entry.size.coerceAtLeast(0).coerceAtMost(maxBytes).toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("Entry $name is too large.")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    fun readText(name: String): String = readBytes(name).toString(Charsets.UTF_8)

    fun extract(name: String, destination: File, maxBytes: Long = MAX_MEDIA_ENTRY_BYTES): File {
        val entry = zip.getEntry(name) ?: throw NoSuchElementException("$name does not exist in the package.")
        require(!entry.isDirectory) { "ZIP entry is a directory." }
        if (entry.size > maxBytes) throw IOException("Entry $name is too large.")
        destination.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        destination.delete()
                        throw IOException("Entry $name is too large.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return destination
    }

    fun entriesMatching(regex: Regex): List<ZipEntry> = zip.entries().asSequence()
        .filter { !it.isDirectory && regex.matches(it.name) }
        .toList()

    override fun close() = zip.close()

    private companion object {
        const val MAX_TEXT_ENTRY_BYTES = 128L * 1024 * 1024
        const val MAX_MEDIA_ENTRY_BYTES = 8L * 1024 * 1024 * 1024
    }
}
