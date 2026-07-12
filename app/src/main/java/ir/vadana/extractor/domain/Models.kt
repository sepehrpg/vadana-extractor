package ir.vadana.extractor.domain

import android.graphics.PointF

data class Recording(
    val originalUrl: String,
    val host: String,
    val recordingId: String,
    val sessionToken: String,
) {
    val baseUrl: String get() = "$host/$recordingId/"
}

data class SharedFile(
    val sourceBase: String,
    val fileName: String,
    val category: FileCategory,
)

enum class FileCategory { DOCUMENT, AUDIO, VIDEO, IMAGE, OTHER }

enum class StreamType {
    CAMERA_VOIP,
    SCREEN_SHARE,
    OTHER;

    companion object {
        fun fromWire(value: String): StreamType = when (value.lowercase()) {
            "cameravoip" -> CAMERA_VOIP
            "screenshare" -> SCREEN_SHARE
            else -> OTHER
        }
    }
}

data class StreamSegment(
    val startMs: Long,
    val name: String,
    val publisherId: String,
    val type: StreamType,
)

data class PageKey(val podIndex: Int, val pageNumber: Int)

sealed interface BoardShape {
    val depth: Int
    val timestampMs: Long

    data class Pencil(
        override val depth: Int,
        override val timestampMs: Long,
        val points: List<PointF>,
        val color: Int,
        val width: Float,
    ) : BoardShape

    data class Text(
        override val depth: Int,
        override val timestampMs: Long,
        val x: Float,
        val y: Float,
        val lines: List<String>,
        val color: Int,
        val textSize: Float,
    ) : BoardShape
}

data class BoardEvent(
    val timestampMs: Long,
    val page: PageKey,
    val shapeId: String,
    val shape: BoardShape?,
)

data class PageNavigationEvent(
    val timestampMs: Long,
    val page: PageKey,
)

data class PointerEvent(
    val timestampMs: Long,
    val xPercent: Float,
    val yPercent: Float,
    val visible: Boolean,
)

data class Whiteboard(
    val finalShapes: Map<PageKey, Map<String, BoardShape>>,
    val events: List<BoardEvent>,
    val navigation: List<PageNavigationEvent>,
) {
    val pages: List<PageKey>
        get() = finalShapes.filterValues { it.isNotEmpty() }.keys.sortedWith(
            compareBy<PageKey> { it.podIndex }.thenBy { it.pageNumber }
        )

    val durationMs: Long
        get() = events.maxOfOrNull { it.timestampMs } ?: 0L
}

data class RecordingAnalysis(
    val recording: Recording,
    val packagePath: String,
    val sharedFiles: List<SharedFile>,
    val whiteboardPages: Int,
    val whiteboardEvents: Int,
    val hasAudio: Boolean,
    val hasScreenShare: Boolean,
    val hasSharedPdfTimeline: Boolean,
    val estimatedDurationMs: Long,
) {
    val hasWhiteboard: Boolean get() = whiteboardPages > 0
}

enum class OutputKind {
    SHARED_FILES,
    WHITEBOARD_PDF,
    AUDIO_M4A,
    SYNCED_VIDEO,
}

enum class VideoQuality(val width: Int, val height: Int, val label: String) {
    HD_720(1280, 720, "720p"),
    FULL_HD(1920, 1080, "1080p"),
    QHD(2560, 1440, "1440p"),
}

data class ExtractionRequest(
    val recordingUrl: String,
    val outputKinds: Set<OutputKind>,
    val quality: VideoQuality,
    val maxFrameRate: Float = 4f,
)

data class ExportedItem(
    val displayName: String,
    val uri: String,
    val mimeType: String,
)
