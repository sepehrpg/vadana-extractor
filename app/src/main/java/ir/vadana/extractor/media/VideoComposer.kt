package ir.vadana.extractor.media

import android.graphics.Bitmap
import android.graphics.Color
import ir.vadana.extractor.data.MainstreamParser
import ir.vadana.extractor.data.PackageArchive
import ir.vadana.extractor.data.TimelineParser
import ir.vadana.extractor.data.WhiteboardParser
import ir.vadana.extractor.domain.PageKey
import ir.vadana.extractor.domain.PageNavigationEvent
import ir.vadana.extractor.domain.StreamType
import ir.vadana.extractor.domain.VideoQuality
import ir.vadana.extractor.render.PdfBackgroundSource
import ir.vadana.extractor.render.PdfSlideshowRenderer
import ir.vadana.extractor.render.PdfTimelineFrameRenderer
import ir.vadana.extractor.render.WhiteboardFrameRenderer
import ir.vadana.extractor.util.shellQuote
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlin.math.max

class VideoComposer(
    private val ffmpeg: FfmpegEngine,
    private val audioExtractor: AudioExtractor = AudioExtractor(ffmpeg),
) {
    private data class Frame(val time: Double, val file: File, val priority: Int)
    private data class Window(val start: Double, val end: Double)

    suspend fun compose(
        archive: PackageArchive,
        workDirectory: File,
        output: File,
        pdfFiles: List<File>,
        quality: VideoQuality,
        maxFps: Float,
        onStage: (stage: String, percent: Int) -> Unit,
    ): File? {
        workDirectory.deleteRecursively()
        workDirectory.mkdirs()
        val streams = if (archive.contains("indexstream.xml")) {
            TimelineParser.parseStreams(archive.readText("indexstream.xml"))
        } else emptyList()
        val whiteboard = WhiteboardParser.loadFromPackage(archive)
        val pdfNavigation = WhiteboardParser.loadPdfNavigation(archive)
        val pointer = WhiteboardParser.loadPointer(archive)
        val metadataSeconds = sequenceOf("mainstream.xml", "ftcontent1.xml")
            .filter(archive::contains)
            .maxOfOrNull { MainstreamParser.metadataDurationSeconds(archive.readText(it)) }
            ?: 0.0
        val sequentialAudioSeconds = archive.entriesMatching(Regex("(?i)cameraVoip.*\\.flv"))
            .sumOf { entry ->
                val xml = entry.name.substringBeforeLast('.') + ".xml"
                if (archive.contains(xml)) MainstreamParser.metadataDurationSeconds(archive.readText(xml)) else 0.0
            }
        var masterSeconds = max(metadataSeconds, max(whiteboard.durationMs / 1000.0, sequentialAudioSeconds))

        onStage("Audio", 8)
        val audioFile = File(workDirectory, "master.m4a")
        val audio = if (streams.any { it.type == StreamType.CAMERA_VOIP }) {
            audioExtractor.buildMasterAudio(
                archive,
                streams,
                workDirectory,
                audioFile,
                (masterSeconds * 1000).toLong(),
            ) { fraction -> onStage("Audio", 8 + (fraction * 12).toInt()) }
        } else {
            audioExtractor.extractSequentialAudio(archive, workDirectory, audioFile) { fraction ->
                onStage("Audio", 8 + (fraction * 12).toInt())
            }
        }

        val width = quality.width
        val height = quality.height
        val whiteboardFrames = mutableListOf<WhiteboardFrameRenderer.TimedFrame>()
        if (whiteboard.pages.isNotEmpty()) {
            onStage("Rendering whiteboard", 22)
            val effectiveWhiteboard = if (pdfNavigation.isNotEmpty() && pdfFiles.isNotEmpty()) {
                val pod = whiteboard.pages.first().podIndex
                val nav = pdfNavigation.map { PageNavigationEvent(it.first, PageKey(pod, it.second)) }.toMutableList()
                if (nav.isNotEmpty() && nav.first().timestampMs > 0) {
                    nav.add(0, PageNavigationEvent(0, nav.first().page))
                }
                whiteboard.copy(navigation = nav)
            } else whiteboard
            val viewKeys = (effectiveWhiteboard.pages + effectiveWhiteboard.navigation.map { it.page }).distinct()
            PdfBackgroundSource.openMatching(pdfFiles, viewKeys).use { backgrounds ->
                whiteboardFrames += WhiteboardFrameRenderer().render(
                    effectiveWhiteboard,
                    File(workDirectory, "whiteboard-frames"),
                    width,
                    height,
                    maxFps,
                    backgrounds,
                ) { done, total ->
                    onStage("Rendering whiteboard", 22 + (30f * done / total.coerceAtLeast(1)).toInt())
                }
            }
        }

        onStage("Screen share", 54)
        val screenFrames = mutableListOf<Frame>()
        val screenWindows = mutableListOf<Window>()
        val shares = streams.filter { it.type == StreamType.SCREEN_SHARE }
        shares.forEachIndexed { index, stream ->
            val entryName = "${stream.name}.flv"
            if (!archive.contains(entryName)) return@forEachIndexed
            val input = archive.extract(entryName, File(workDirectory, File(entryName).name))
            val duration = if (archive.contains("${stream.name}.xml")) {
                MainstreamParser.metadataDurationSeconds(archive.readText("${stream.name}.xml")).takeIf { it > 0 } ?: 60.0
            } else 60.0
            val directory = File(workDirectory, "screenshare/$index").apply { mkdirs() }
            val pattern = File(directory, "f%06d.png")
            val filter = "fps=1,scale=$width:$height:force_original_aspect_ratio=decrease," +
                "pad=$width:$height:(ow-iw)/2:(oh-ih)/2:color=black"
            val command = "-y -i ${shellQuote(input.absolutePath)} -vf ${shellQuote(filter)} ${shellQuote(pattern.absolutePath)}"
            ffmpeg.execute(command, (duration * 1000).toLong()) { fraction ->
                val base = 54 + (18f * index / shares.size.coerceAtLeast(1)).toInt()
                onStage("Screen share", base + (18f / shares.size.coerceAtLeast(1) * fraction).toInt())
            }
            val generated = directory.listFiles { file -> file.extension.equals("png", true) }
                ?.sortedBy { it.name }
                .orEmpty()
            val start = stream.startMs / 1000.0
            generated.forEachIndexed { frameIndex, file ->
                screenFrames += Frame(start + frameIndex, file, priority = 30)
            }
            if (generated.isNotEmpty()) screenWindows += Window(start, start + duration)
            masterSeconds = max(masterSeconds, start + duration)
        }

        var pdfResult = PdfTimelineFrameRenderer.Result(emptyList(), null)
        if (whiteboard.pages.isEmpty() && pdfNavigation.isNotEmpty() && pdfFiles.isNotEmpty()) {
            onStage("Rendering PDF", 72)
            pdfResult = PdfTimelineFrameRenderer().render(
                pdfFiles,
                pdfNavigation,
                pointer,
                File(workDirectory, "pdf-timeline"),
                width,
                height,
            )
        }

        var slideshow = emptyList<PdfSlideshowRenderer.TimedFrame>()
        if (whiteboardFrames.isEmpty() && screenFrames.isEmpty() && pdfResult.frames.isEmpty() && pdfFiles.isNotEmpty()) {
            onStage("Slides", 72)
            slideshow = PdfSlideshowRenderer().render(
                pdfFiles,
                File(workDirectory, "slideshow"),
                width,
                height,
                masterSeconds,
            )
        }

        val whiteFrames = whiteboardFrames.map { Frame(it.startSeconds, it.file, priority = 10) }
        val pdfFrames = pdfResult.frames.map { Frame(it.startSeconds, it.file, priority = 20) }
        val slideFrames = slideshow.map { Frame(it.startSeconds, it.file, priority = 10) }
        val pdfWindow = pdfResult.window?.let { Window(it.start, it.endInclusive) }
        val timeline = mergeTimeline(whiteFrames + slideFrames, pdfFrames, screenFrames, pdfWindow, screenWindows)
            .toMutableList()
        if (timeline.isEmpty()) return null

        if (timeline.first().time > 0.3) {
            val blank = File(workDirectory, "blank.png")
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            blank.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            timeline += Frame(0.0, blank, 0)
        }
        timeline.sortBy { it.time }
        masterSeconds = max(masterSeconds, timeline.last().time + 1.0)

        onStage("Encoding video", 78)
        mux(timeline, audio, output, workDirectory, masterSeconds) { fraction ->
            onStage("Encoding video", 78 + (fraction * 21).toInt())
        }
        onStage("Done", 100)
        return output
    }

    fun cancel() = ffmpeg.cancel()

    private fun mergeTimeline(
        whiteboard: List<Frame>,
        pdf: List<Frame>,
        screen: List<Frame>,
        pdfWindow: Window?,
        screenWindows: List<Window>,
    ): List<Frame> {
        fun inWindow(time: Double, window: Window?) = window != null && time >= window.start && time < window.end
        fun inAny(time: Double, windows: List<Window>) = windows.any { time >= it.start && time < it.end }
        val output = mutableListOf<Frame>()
        output += whiteboard.filter { !inAny(it.time, screenWindows) && !inWindow(it.time, pdfWindow) }
        output += pdf.filter { !inAny(it.time, screenWindows) }
        output += screen

        pdfWindow?.let { window ->
            whiteboard.filter { it.time <= window.end }.maxByOrNull { it.time }?.let {
                output += it.copy(time = window.end + 0.001)
            }
        }
        screenWindows.forEach { window ->
            val underlying = if (inWindow(window.end, pdfWindow)) {
                pdf.filter { it.time <= window.end }.maxByOrNull { it.time }
            } else {
                whiteboard.filter { it.time <= window.end }.maxByOrNull { it.time }
            }
            underlying?.let { output += it.copy(time = window.end + 0.001) }
        }

        return output.sortedWith(compareBy<Frame> { it.time }.thenBy { it.priority })
            .groupBy { "%.3f".format(java.util.Locale.US, it.time) }
            .values
            .map { sameTime -> sameTime.maxBy { it.priority } }
            .sortedBy { it.time }
    }

    private suspend fun mux(
        frames: List<Frame>,
        audio: File?,
        output: File,
        workDirectory: File,
        durationSeconds: Double,
        onProgress: (Float) -> Unit,
    ) {
        output.parentFile?.mkdirs()
        val list = File(workDirectory, "frames.txt")
        list.writeText(buildString {
            frames.forEachIndexed { index, frame ->
                val end = frames.getOrNull(index + 1)?.time ?: durationSeconds
                val duration = (end - frame.time).coerceAtLeast(0.04)
                appendLine("file '${frame.file.absolutePath.replace("'", "'\\''")}'")
                appendLine("duration ${"%.3f".format(java.util.Locale.US, duration)}")
            }
            appendLine("file '${frames.last().file.absolutePath.replace("'", "'\\''")}'")
        })
        val audioInput = audio?.let { "-i ${shellQuote(it.absolutePath)}" }.orEmpty()
        val audioArgs = if (audio != null) {
            "-c:a aac -b:a 96k -af ${shellQuote("highpass=f=85,afftdn=nr=12:nf=-25,dynaudnorm=f=200:g=6")}"
        } else ""
        val common = "-y -f concat -safe 0 -i ${shellQuote(list.absolutePath)} $audioInput " +
            "-pix_fmt yuv420p -vf ${shellQuote("scale=trunc(iw/2)*2:trunc(ih/2)*2")} " +
            "-r 4 -movflags +faststart -t ${"%.3f".format(java.util.Locale.US, durationSeconds)} $audioArgs"
        val x264 = "$common -c:v libx264 -preset veryfast -crf 26 ${shellQuote(output.absolutePath)}"
        try {
            ffmpeg.execute(x264, (durationSeconds * 1000).toLong(), onProgress)
        } catch (first: Throwable) {
            if (first is CancellationException) throw first
            output.delete()
            val fallback = "$common -c:v mpeg4 -q:v 5 ${shellQuote(output.absolutePath)}"
            ffmpeg.execute(fallback, (durationSeconds * 1000).toLong(), onProgress)
        }
    }
}
