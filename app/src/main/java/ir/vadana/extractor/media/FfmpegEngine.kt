package ir.vadana.extractor.media

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FfmpegEngine {
    private val activeSessionId = AtomicLong(0L)

    suspend fun execute(
        command: String,
        expectedDurationMs: Long = 0L,
        onProgress: (Float) -> Unit = {},
    ) = suspendCancellableCoroutine { continuation ->
        val session = FFmpegKit.executeAsync(
            command,
            { completed ->
                activeSessionId.compareAndSet(completed.sessionId, 0L)
                if (!continuation.isActive) return@executeAsync
                val returnCode = completed.returnCode
                when {
                    ReturnCode.isSuccess(returnCode) -> continuation.resume(Unit)
                    ReturnCode.isCancel(returnCode) -> continuation.resumeWithException(CancellationException("FFmpeg cancelled"))
                    else -> continuation.resumeWithException(
                        IllegalStateException(
                            "FFmpeg stopped with code ${returnCode?.value}.\n${completed.allLogsAsString.takeLast(4000)}"
                        )
                    )
                }
            },
            { /* Logs are available from the completed session on failure. */ },
            { statistics ->
                if (expectedDurationMs > 0) {
                    onProgress((statistics.time.toFloat() / expectedDurationMs).coerceIn(0f, 1f))
                }
            },
        )
        activeSessionId.set(session.sessionId)
        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    fun cancel() {
        val id = activeSessionId.getAndSet(0L)
        if (id > 0) FFmpegKit.cancel(id)
    }
}
