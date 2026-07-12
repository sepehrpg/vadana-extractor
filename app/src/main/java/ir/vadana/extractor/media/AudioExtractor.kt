package ir.vadana.extractor.media

import ir.vadana.extractor.data.MainstreamParser
import ir.vadana.extractor.data.PackageArchive
import ir.vadana.extractor.domain.StreamSegment
import ir.vadana.extractor.domain.StreamType
import ir.vadana.extractor.util.shellQuote
import java.io.File

class AudioExtractor(private val ffmpeg: FfmpegEngine) {

    suspend fun extractSequentialAudio(
        archive: PackageArchive,
        workDirectory: File,
        output: File,
        onProgress: (Float) -> Unit = {},
    ): File? {
        val entries = archive.entriesMatching(Regex("(?i)cameraVoip.*\\.flv"))
            .filter { it.size >= 100_000 }
            .sortedWith { a, b -> compareNumberLists(naturalNumbers(a.name), naturalNumbers(b.name)) }
        if (entries.isEmpty()) return null
        workDirectory.mkdirs()
        val files = entries.map { entry -> archive.extract(entry.name, File(workDirectory, File(entry.name).name)) }
        val totalSeconds = entries.sumOf { entry ->
            val xml = entry.name.substringBeforeLast('.') + ".xml"
            if (archive.contains(xml)) MainstreamParser.metadataDurationSeconds(archive.readText(xml)) else 0.0
        }
        output.parentFile?.mkdirs()
        val inputs = files.joinToString(" ") { "-i ${shellQuote(it.absolutePath)}" }
        val command = if (files.size == 1) {
            "-y $inputs -vn -c:a aac -b:a 96k ${shellQuote(output.absolutePath)}"
        } else {
            val labels = files.indices.joinToString("") { "[$it:a]" }
            "-y $inputs -filter_complex ${shellQuote("${labels}concat=n=${files.size}:v=0:a=1[a]")} " +
                "-map '[a]' -c:a aac -b:a 96k ${shellQuote(output.absolutePath)}"
        }
        ffmpeg.execute(command, (totalSeconds * 1000).toLong(), onProgress)
        return output
    }

    suspend fun buildMasterAudio(
        archive: PackageArchive,
        streams: List<StreamSegment>,
        workDirectory: File,
        output: File,
        expectedDurationMs: Long,
        onProgress: (Float) -> Unit = {},
    ): File? {
        val audioStreams = streams.filter { it.type == StreamType.CAMERA_VOIP }.mapNotNull { stream ->
            val entry = "${stream.name}.flv"
            archive.entry(entry)?.takeIf { it.size >= 50_000 }?.let {
                stream to archive.extract(entry, File(workDirectory, File(entry).name))
            }
        }
        if (audioStreams.isEmpty()) return null
        output.parentFile?.mkdirs()
        val inputs = audioStreams.joinToString(" ") { "-i ${shellQuote(it.second.absolutePath)}" }
        val filters = buildList {
            audioStreams.forEachIndexed { index, (stream, _) ->
                add("[$index:a]aresample=44100,adelay=${stream.startMs}|${stream.startMs}[a$index]")
            }
            add(audioStreams.indices.joinToString("") { "[a$it]" } +
                "amix=inputs=${audioStreams.size}:dropout_transition=0:normalize=0[a]")
        }.joinToString(";")
        val command = "-y $inputs -filter_complex ${shellQuote(filters)} -map '[a]' " +
            "-c:a aac -b:a 96k ${shellQuote(output.absolutePath)}"
        ffmpeg.execute(command, expectedDurationMs, onProgress)
        return output
    }

    private fun naturalNumbers(name: String): List<Int> = Regex("\\d+").findAll(name).map { it.value.toInt() }.toList()

    private fun compareNumberLists(a: List<Int>, b: List<Int>): Int {
        val size = minOf(a.size, b.size)
        for (i in 0 until size) {
            val result = a[i].compareTo(b[i])
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }
}
