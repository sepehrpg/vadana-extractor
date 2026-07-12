package ir.vadana.extractor.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ir.vadana.extractor.R
import ir.vadana.extractor.VadanaApplication
import ir.vadana.extractor.data.PackageArchive
import ir.vadana.extractor.data.TimelineParser
import ir.vadana.extractor.data.VadanaRepository
import ir.vadana.extractor.data.WhiteboardParser
import ir.vadana.extractor.domain.OutputKind
import ir.vadana.extractor.domain.StreamType
import ir.vadana.extractor.media.AudioExtractor
import ir.vadana.extractor.media.FfmpegEngine
import ir.vadana.extractor.media.VideoComposer
import ir.vadana.extractor.render.PdfBackgroundSource
import ir.vadana.extractor.render.WhiteboardPdfExporter
import ir.vadana.extractor.storage.PublicOutputStore
import ir.vadana.extractor.storage.SecureJobStore
import kotlinx.coroutines.CancellationException
import java.io.File

class VadanaExtractionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val ffmpeg = FfmpegEngine()
    private var composer: VideoComposer? = null

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID)
            ?: return Result.failure(errorData("Missing job ID."))

        val jobStore = SecureJobStore(applicationContext)

        val request = runCatching {
            jobStore.load(jobId)
        }.getOrElse {
            return Result.failure(
                errorData(it.message ?: "Could not read the request.")
            )
        }

        val repository = VadanaRepository(applicationContext)
        val publicStore = PublicOutputStore(applicationContext)
        var workDirectory: File? = null

        return try {
            update("Downloading class package", 1)

            val analysis = repository.analyze(request.recordingUrl) { downloaded, total ->
                val percent = if (total > 0L) {
                    (downloaded * 18L / total).toInt().coerceIn(1, 18)
                } else {
                    5
                }

                setProgressAsync(
                    progressData("Downloading class package", percent)
                )
            }

            workDirectory = repository.workDirectory(analysis.recording).apply {
                deleteRecursively()
                mkdirs()
            }

            val exported = mutableListOf<String>()

            val needsPdfs =
                OutputKind.WHITEBOARD_PDF in request.outputKinds ||
                        OutputKind.SYNCED_VIDEO in request.outputKinds

            val allSharedDirectory = File(workDirectory, "shared")

            val downloadedShared = if (OutputKind.SHARED_FILES in request.outputKinds) {
                update("Downloading shared files", 20)

                repository.downloadSharedFiles(
                    analysis,
                    allSharedDirectory,
                ) { item, total, downloaded, bytes ->
                    val itemFraction = if (bytes > 0L) {
                        downloaded.toFloat() / bytes.toFloat()
                    } else {
                        0f
                    }

                    val percent = 20 + (
                            (item - 1 + itemFraction) /
                                    total.coerceAtLeast(1) * 16
                            ).toInt()

                    setProgressAsync(
                        progressData("Downloading shared files", percent)
                    )
                }
            } else {
                emptyList()
            }

            if (OutputKind.SHARED_FILES in request.outputKinds) {
                downloadedShared.forEach { file ->
                    val item = publicStore.publish(
                        file,
                        file.name,
                        publicStore.mimeTypeFor(file),
                    )
                    exported += item.uri
                }
            }

            val pdfFiles = if (needsPdfs) {
                val existing = downloadedShared.filter {
                    it.extension.equals("pdf", ignoreCase = true)
                }

                if (existing.isNotEmpty()) {
                    existing
                } else {
                    val pdfItems = analysis.sharedFiles.filter {
                        it.fileName.endsWith(".pdf", ignoreCase = true)
                    }

                    if (pdfItems.isEmpty()) {
                        emptyList()
                    } else {
                        repository.downloadSharedFiles(
                            analysis,
                            File(workDirectory, "pdfs"),
                            pdfItems,
                        ) { _, _, _, _ -> }
                    }
                }
            } else {
                emptyList()
            }

            PackageArchive(File(analysis.packagePath)).use { archive ->
                if (OutputKind.WHITEBOARD_PDF in request.outputKinds) {
                    update("Building whiteboard PDF", 38)

                    val whiteboard = WhiteboardParser.loadFromPackage(archive)

                    PdfBackgroundSource.openMatching(
                        pdfFiles,
                        whiteboard.pages,
                    ).use { backgrounds ->
                        val output = File(
                            workDirectory,
                            "${analysis.recording.recordingId}_whiteboard.pdf",
                        )

                        val result = WhiteboardPdfExporter().export(
                            whiteboard,
                            output,
                            backgrounds,
                        ) { done, total ->
                            val percent = 38 + (
                                    done * 14 / total.coerceAtLeast(1)
                                    )

                            setProgressAsync(
                                progressData("Building whiteboard PDF", percent)
                            )
                        }

                        result?.let { file ->
                            val item = publicStore.publish(
                                file,
                                file.name,
                                "application/pdf",
                            )
                            exported += item.uri
                        }
                    }
                }

                var audioOutput: File? = null

                if (OutputKind.AUDIO_M4A in request.outputKinds) {
                    update("Extracting audio", 53)

                    audioOutput = File(
                        workDirectory,
                        "${analysis.recording.recordingId}.m4a",
                    )

                    val streams = if (archive.contains("indexstream.xml")) {
                        TimelineParser.parseStreams(
                            archive.readText("indexstream.xml")
                        )
                    } else {
                        emptyList()
                    }

                    val duration = analysis.estimatedDurationMs
                    val extractor = AudioExtractor(ffmpeg)

                    val result = if (
                        streams.any { it.type == StreamType.CAMERA_VOIP }
                    ) {
                        extractor.buildMasterAudio(
                            archive,
                            streams,
                            File(workDirectory, "audio"),
                            audioOutput,
                            duration,
                        ) { progress ->
                            setProgressAsync(
                                progressData(
                                    "Extracting audio",
                                    53 + (progress * 12).toInt(),
                                )
                            )
                        }
                    } else {
                        extractor.extractSequentialAudio(
                            archive,
                            File(workDirectory, "audio"),
                            audioOutput,
                        ) { progress ->
                            setProgressAsync(
                                progressData(
                                    "Extracting audio",
                                    53 + (progress * 12).toInt(),
                                )
                            )
                        }
                    }

                    result?.let { file ->
                        val item = publicStore.publish(
                            file,
                            file.name,
                            "audio/mp4",
                        )
                        exported += item.uri
                    }
                }

                if (OutputKind.SYNCED_VIDEO in request.outputKinds) {
                    update("Building synced video", 65)

                    val output = File(
                        workDirectory,
                        "${analysis.recording.recordingId}_${request.quality.label}.mp4",
                    )

                    composer = VideoComposer(ffmpeg)

                    val video = composer?.compose(
                        archive,
                        File(workDirectory, "video-build"),
                        output,
                        pdfFiles,
                        request.quality,
                        request.maxFrameRate,
                    ) { stage, percent ->
                        val mapped = 65 + (percent * 34 / 100)

                        setProgressAsync(
                            progressData(stage, mapped.coerceAtMost(99))
                        )
                    }

                    if (video != null) {
                        val item = publicStore.publish(
                            video,
                            video.name,
                            "video/mp4",
                        )
                        exported += item.uri
                    } else if (audioOutput == null) {
                        val fallback = File(
                            workDirectory,
                            "${analysis.recording.recordingId}.m4a",
                        )

                        AudioExtractor(ffmpeg)
                            .extractSequentialAudio(
                                archive,
                                File(workDirectory, "fallback-audio"),
                                fallback,
                            )
                            ?.let { file ->
                                val item = publicStore.publish(
                                    file,
                                    file.name,
                                    "audio/mp4",
                                )
                                exported += item.uri
                            }
                    }
                }
            }

            update("Done", 100)
            jobStore.delete(jobId)

            Result.success(
                workDataOf(
                    KEY_EXPORTED_COUNT to exported.size,
                    KEY_LAST_URI to exported.lastOrNull().orEmpty(),
                )
            )
        } catch (e: CancellationException) {
            // In newer WorkManager versions, onStopped is final in CoroutineWorker.
            // Therefore, cancel FFmpeg when the coroutine is cancelled.
            composer?.cancel()
            ffmpeg.cancel()
            jobStore.delete(jobId)

            // Rethrow so WorkManager records the cancelled state.
            throw e
        } catch (t: Throwable) {
            jobStore.delete(jobId)

            Result.failure(
                errorData(t.message ?: t.javaClass.simpleName)
            )
        } finally {
            composer = null
            workDirectory?.deleteRecursively()
        }
    }

    private suspend fun update(stage: String, percent: Int) {
        setProgress(progressData(stage, percent))
        setForeground(foregroundInfo(stage, percent))
    }

    private fun foregroundInfo(stage: String, percent: Int): ForegroundInfo {
        val cancelIntent = WorkManager
            .getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            VadanaApplication.CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Vadana Extractor")
            .setContentText(stage)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelIntent,
            )
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun progressData(stage: String, percent: Int): Data = workDataOf(
        KEY_STAGE to stage,
        KEY_PERCENT to percent.coerceIn(0, 100),
    )

    private fun errorData(message: String): Data = workDataOf(
        KEY_ERROR to message.take(4_000)
    )

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"
        const val KEY_ERROR = "error"
        const val KEY_EXPORTED_COUNT = "exported_count"
        const val KEY_LAST_URI = "last_uri"
        const val NOTIFICATION_ID = 4402
    }
}
