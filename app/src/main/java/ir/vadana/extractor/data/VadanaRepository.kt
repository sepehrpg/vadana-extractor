package ir.vadana.extractor.data

import android.content.Context
import ir.vadana.extractor.domain.Recording
import ir.vadana.extractor.domain.RecordingAnalysis
import ir.vadana.extractor.domain.StreamType
import ir.vadana.extractor.util.safeFileName
import ir.vadana.extractor.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VadanaRepository(private val context: Context) {

    suspend fun analyze(
        recordingUrl: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): RecordingAnalysis = withContext(Dispatchers.IO) {
        val recording = RecordingUrlParser.parse(recordingUrl)
        val dir = recordingDirectory(recording).apply { mkdirs() }
        val packageFile = File(dir, "package.zip")
        if (!isUsableZip(packageFile)) {
            ConnectClient(recording).downloadPackage(packageFile, onProgress)
        }

        PackageArchive(packageFile).use { archive ->
            val mainstream = archive.readText("mainstream.xml")
            val sharedFiles = MainstreamParser.findSharedFiles(mainstream)
            val whiteboard = WhiteboardParser.loadFromPackage(archive)
            val pdfNavigation = WhiteboardParser.loadPdfNavigation(archive)
            val streams = if (archive.contains("indexstream.xml")) {
                TimelineParser.parseStreams(archive.readText("indexstream.xml"))
            } else emptyList()
            val hasAudio = archive.entriesMatching(Regex("(?i)cameraVoip.*\\.flv")).any { it.size >= 50_000 }
            val hasScreenShare = streams.any { it.type == StreamType.SCREEN_SHARE }
            val metadataDuration = sequenceOf("mainstream.xml", "ftcontent1.xml")
                .filter(archive::contains)
                .maxOfOrNull { MainstreamParser.metadataDurationSeconds(archive.readText(it)) }
                ?: 0.0
            val maxStart = streams.maxOfOrNull { it.startMs / 1000.0 } ?: 0.0
            val durationMs = (maxOf(metadataDuration, maxStart, whiteboard.durationMs / 1000.0) * 1000).toLong()

            RecordingAnalysis(
                recording = recording,
                packagePath = packageFile.absolutePath,
                sharedFiles = sharedFiles,
                whiteboardPages = whiteboard.pages.size,
                whiteboardEvents = whiteboard.events.size,
                hasAudio = hasAudio,
                hasScreenShare = hasScreenShare,
                hasSharedPdfTimeline = pdfNavigation.isNotEmpty(),
                estimatedDurationMs = durationMs,
            )
        }
    }

    suspend fun downloadSharedFiles(
        analysis: RecordingAnalysis,
        targetDirectory: File,
        items: List<ir.vadana.extractor.domain.SharedFile> = analysis.sharedFiles,
        onItemProgress: (item: Int, total: Int, downloaded: Long, bytes: Long) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        targetDirectory.mkdirs()
        val client = ConnectClient(analysis.recording)
        items.mapIndexedNotNull { index, item ->
            val target = uniqueFile(targetDirectory, safeFileName(item.fileName))
            client.downloadSharedFile(item, target) { downloaded, total ->
                onItemProgress(index + 1, items.size, downloaded, total)
            }
        }
    }

    fun recordingDirectory(recording: Recording): File {
        val key = "${recording.host}/${recording.recordingId}".sha256().take(24)
        return File(context.filesDir, "recordings/$key")
    }

    fun workDirectory(recording: Recording): File {
        val key = "${recording.host}/${recording.recordingId}".sha256().take(24)
        return File(context.cacheDir, "vadana-work/$key")
    }

    private fun isUsableZip(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return runCatching {
            java.util.zip.ZipFile(file).use { zip ->
                zip.getEntry("mainstream.xml") != null
            }
        }.getOrDefault(false)
    }

    private fun uniqueFile(directory: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var candidate = File(directory, name)
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "$base ($index)$ext")
            index++
        }
        return candidate
    }
}
