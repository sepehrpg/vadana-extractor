package ir.vadana.extractor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import ir.vadana.extractor.R
import ir.vadana.extractor.data.VadanaRepository
import ir.vadana.extractor.domain.ExtractionRequest
import ir.vadana.extractor.domain.OutputKind
import ir.vadana.extractor.domain.RecordingAnalysis
import ir.vadana.extractor.domain.VideoQuality
import ir.vadana.extractor.storage.SecureJobStore
import ir.vadana.extractor.worker.VadanaExtractionWorker
import ir.vadana.extractor.util.sha256
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID


data class MainUiState(
    val url: String = "",
    val analyzing: Boolean = false,
    val analysisProgress: Float? = null,
    val analysis: RecordingAnalysis? = null,
    val selectedOutputs: Set<OutputKind> = setOf(OutputKind.SYNCED_VIDEO),
    val quality: VideoQuality = VideoQuality.FULL_HD,
    val error: String? = null,
    val workId: UUID? = null,
    val workState: WorkInfo.State? = null,
    val workStage: String = "",
    val workPercent: Int = 0,
    val exportedCount: Int = 0,
    val lastUri: String? = null,
)

class MainViewModel(private val applicationContext: Application) : AndroidViewModel(applicationContext) {
    private val repository = VadanaRepository(applicationContext)
    private val workManager = WorkManager.getInstance(applicationContext)
    private val jobStore = SecureJobStore(applicationContext)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var workObserver: Job? = null

    fun setUrl(value: String) {
        _state.update { current ->
            if (value == current.url) current else current.copy(
                url = value,
                analysis = null,
                analysisProgress = null,
                error = null,
            )
        }
    }

    fun analyze() {
        val url = state.value.url.trim()
        if (url.isBlank()) {
            _state.update { it.copy(error = applicationContext.getString(R.string.error_enter_class_link)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(analyzing = true, error = null, analysis = null, analysisProgress = null) }
            runCatching {
                repository.analyze(url) { downloaded, total ->
                    val progress = if (total > 0) downloaded.toFloat() / total else null
                    _state.update { it.copy(analysisProgress = progress) }
                }
            }.onSuccess { analysis ->
                val availableDefaults = buildSet {
                    if (analysis.hasWhiteboard) add(OutputKind.WHITEBOARD_PDF)
                    if (analysis.hasAudio) add(OutputKind.AUDIO_M4A)
                    if (analysis.hasWhiteboard || analysis.hasScreenShare || analysis.hasSharedPdfTimeline) {
                        add(OutputKind.SYNCED_VIDEO)
                    }
                    if (analysis.sharedFiles.isNotEmpty()) add(OutputKind.SHARED_FILES)
                }
                _state.update {
                    it.copy(
                        analyzing = false,
                        analysisProgress = 1f,
                        analysis = analysis,
                        selectedOutputs = if (availableDefaults.isEmpty()) emptySet() else availableDefaults,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(analyzing = false, error = error.message ?: applicationContext.getString(R.string.error_analysis_failed)) }
            }
        }
    }

    fun toggleOutput(kind: OutputKind) {
        _state.update { current ->
            val selected = current.selectedOutputs.toMutableSet()
            if (!selected.add(kind)) selected.remove(kind)
            current.copy(selectedOutputs = selected, error = null)
        }
    }

    fun setQuality(quality: VideoQuality) {
        _state.update { it.copy(quality = quality) }
    }

    fun startExtraction() {
        val current = state.value
        val analysis = current.analysis ?: run {
            _state.update { it.copy(error = applicationContext.getString(R.string.error_analyze_link_first)) }
            return
        }
        if (current.selectedOutputs.isEmpty()) {
            _state.update { it.copy(error = applicationContext.getString(R.string.error_select_output)) }
            return
        }
        val request = ExtractionRequest(
            recordingUrl = analysis.recording.originalUrl,
            outputKinds = current.selectedOutputs,
            quality = current.quality,
        )
        val secureJobId = jobStore.save(request)
        val work = OneTimeWorkRequestBuilder<VadanaExtractionWorker>()
            .setInputData(workDataOf(VadanaExtractionWorker.KEY_JOB_ID to secureJobId))
            .addTag("vadana-extraction")
            .build()
        val uniqueKey = "${analysis.recording.host}/${analysis.recording.recordingId}".sha256().take(16)
        workManager.enqueueUniqueWork(
            "vadana-$uniqueKey",
            ExistingWorkPolicy.REPLACE,
            work,
        )
        _state.update {
            it.copy(
                workId = work.id,
                workState = WorkInfo.State.ENQUEUED,
                workStage = applicationContext.getString(R.string.stage_queued),
                workPercent = 0,
                exportedCount = 0,
                lastUri = null,
                error = null,
            )
        }
        observeWork(work.id)
    }

    fun cancelWork() {
        state.value.workId?.let(workManager::cancelWorkById)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun startNewExtraction() {
        workObserver?.cancel()
        _state.value = MainUiState()
    }

    private fun observeWork(id: UUID) {
        workObserver?.cancel()
        workObserver = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collect { info ->
                if (info == null) return@collect
                val progress = info.progress
                val error = if (info.state == WorkInfo.State.FAILED) {
                    info.outputData.getString(VadanaExtractionWorker.KEY_ERROR) ?: applicationContext.getString(R.string.error_processing_failed)
                } else null
                _state.update {
                    it.copy(
                        workState = info.state,
                        workStage = progress.getString(VadanaExtractionWorker.KEY_STAGE)
                            ?: if (info.state == WorkInfo.State.SUCCEEDED) applicationContext.getString(R.string.stage_done) else it.workStage,
                        workPercent = if (info.state == WorkInfo.State.SUCCEEDED) 100 else
                            progress.getInt(VadanaExtractionWorker.KEY_PERCENT, it.workPercent),
                        exportedCount = info.outputData.getInt(VadanaExtractionWorker.KEY_EXPORTED_COUNT, 0),
                        lastUri = info.outputData.getString(VadanaExtractionWorker.KEY_LAST_URI)?.takeIf(String::isNotBlank),
                        error = error,
                    )
                }
            }
        }
    }
}
