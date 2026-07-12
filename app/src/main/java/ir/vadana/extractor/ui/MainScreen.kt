package ir.vadana.extractor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import ir.vadana.extractor.R
import ir.vadana.extractor.domain.OutputKind
import ir.vadana.extractor.domain.Recording
import ir.vadana.extractor.domain.RecordingAnalysis
import ir.vadana.extractor.domain.SharedFile
import ir.vadana.extractor.domain.FileCategory
import ir.vadana.extractor.domain.VideoQuality
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    MainScreenContent(
        state = state,
        snackbarHostState = snackbar,
        onUrlChange = viewModel::setUrl,
        onPaste = { clipboard.getText()?.text?.let(viewModel::setUrl) },
        onAnalyze = viewModel::analyze,
        onToggle = viewModel::toggleOutput,
        onQuality = viewModel::setQuality,
        onStart = {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.startExtraction()
        },
        onCancel = viewModel::cancelWork,
        onRetry = viewModel::analyze,
        onClearError = viewModel::clearError,
        onOpen = state.lastUri?.let { uri ->
            {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                }
            }
        },
        onNewExtraction = viewModel::startNewExtraction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    state: MainUiState,
    snackbarHostState: SnackbarHostState,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onToggle: (OutputKind) -> Unit,
    onQuality: (VideoQuality) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onOpen: (() -> Unit)?,
    onNewExtraction: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                item { ResponsivePane { HeroCard() } }
                item {
                    ResponsivePane {
                        HomeExtractionCard(
                            url = state.url,
                            analyzing = state.analyzing,
                            progress = state.analysisProgress,
                            onUrlChange = onUrlChange,
                            onPaste = onPaste,
                            onAnalyze = onAnalyze,
                        )
                    }
                }
                if (state.analyzing) {
                    item { ResponsivePane { AnalysisLoadingCard(state.analysisProgress) } }
                }
                state.analysis?.let { analysis ->
                    item { ResponsivePane { AnalysisCard(analysis) } }
                    item {
                        ResponsivePane {
                            OutputSelectionCard(
                                analysis = analysis,
                                selected = state.selectedOutputs,
                                quality = state.quality,
                                onToggle = onToggle,
                                onQuality = onQuality,
                            )
                        }
                    }
                    item {
                        ResponsivePane {
                            StartExtractionCard(state, onStart)
                        }
                    }
                }
                if (state.workId != null) {
                    item { ResponsivePane { WorkProgressCard(state, onCancel, onOpen, onNewExtraction) } }
                }
                state.error?.let { error ->
                    item { ResponsivePane { ErrorCard(message = error, onRetry = onRetry, onDismiss = onClearError) } }
                }
                item { ResponsivePane { SecurityNotice() } }
            }
        }
    }
}

@Composable
private fun ResponsivePane(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().widthIn(max = 920.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard() {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(stringResource(R.string.home_description), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepChip(stringResource(R.string.home_step_analyze))
                StepChip(stringResource(R.string.home_step_choose))
                StepChip(stringResource(R.string.home_step_process))
            }
        }
    }
}

@Composable
private fun StepChip(text: String) {
    AssistChip(onClick = {}, label = { Text(text) }, leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp)) })
}

@Composable
private fun HomeExtractionCard(
    url: String,
    analyzing: Boolean,
    progress: Float?,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
) {
    ElevatedCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader(Icons.Default.Search, stringResource(R.string.recording_link_label), stringResource(R.string.recording_link_supporting))
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !analyzing,
                minLines = 2,
                label = { Text(stringResource(R.string.recording_link_label)) },
                placeholder = { Text(stringResource(R.string.recording_url_placeholder)) },
                trailingIcon = { IconButton(onClick = onPaste, enabled = !analyzing) { Icon(Icons.Default.ContentPaste, stringResource(R.string.paste_content_description)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                isError = url.isBlank() && !analyzing,
            )
            Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !analyzing && url.isNotBlank()) {
                if (analyzing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Search, null)
                Text(if (analyzing) stringResource(R.string.analyzing_class) else stringResource(R.string.analyze_class), modifier = Modifier.padding(start = 10.dp))
            }
            AnimatedVisibility(analyzing) { ProgressBlock(progress, stringResource(R.string.analysis_loading_body)) }
        }
    }
}

@Composable
private fun AnalysisLoadingCard(progress: Float?) {
    OutlinedCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Default.Info, stringResource(R.string.analysis_loading_title), stringResource(R.string.analysis_loading_body))
            ProgressBlock(progress, null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisCard(analysis: RecordingAnalysis) {
    ElevatedCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader(Icons.Default.CheckCircle, stringResource(R.string.analysis_success), stringResource(R.string.estimated_duration_format, formatDuration(analysis.estimatedDurationMs)))
            InfoGrid(
                listOf(
                    stringResource(R.string.recording_id_format, analysis.recording.recordingId),
                    stringResource(R.string.shared_files_count_format, analysis.sharedFiles.size),
                    stringResource(R.string.whiteboard_stats_format, analysis.whiteboardPages, analysis.whiteboardEvents),
                ),
            )
            Text(stringResource(R.string.available_streams), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetectionChip(stringResource(R.string.media_audio), analysis.hasAudio)
                DetectionChip(stringResource(R.string.media_screen_share), analysis.hasScreenShare)
                DetectionChip(stringResource(R.string.media_pdf_timeline), analysis.hasSharedPdfTimeline)
                DetectionChip(stringResource(R.string.media_whiteboard), analysis.hasWhiteboard)
            }
            Text(stringResource(R.string.detected_content), style = MaterialTheme.typography.titleMedium)
            Text(
                if (analysis.sharedFiles.isEmpty()) stringResource(R.string.no_media_detected) else analysis.sharedFiles.take(4).joinToString { it.fileName },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OutputSelectionCard(
    analysis: RecordingAnalysis,
    selected: Set<OutputKind>,
    quality: VideoQuality,
    onToggle: (OutputKind) -> Unit,
    onQuality: (VideoQuality) -> Unit,
) {
    val options = outputOptions(analysis)
    ElevatedCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(Icons.Default.Tune, stringResource(R.string.outputs_title), stringResource(R.string.outputs_description))
            options.forEach { option -> OutputOptionCard(option, option.kind in selected, onToggle) }
            AnimatedVisibility(OutputKind.SYNCED_VIDEO in selected) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QualityPicker(quality, onQuality)
                    StaticSetting(stringResource(R.string.frame_rate), stringResource(R.string.frame_rate_fixed))
                }
            }
            if (selected.isEmpty()) {
                Text(stringResource(R.string.validation_select_output), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun OutputOptionCard(option: OutputOption, checked: Boolean, onToggle: (OutputKind) -> Unit) {
    OutlinedCard(
        onClick = { if (option.available) onToggle(option.kind) },
        enabled = option.available,
        colors = CardDefaults.outlinedCardColors(containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(option.icon, null, tint = if (option.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.titleMedium)
                Text(if (option.available) option.description else stringResource(R.string.not_available_in_class), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked = checked, enabled = option.available, onCheckedChange = { onToggle(option.kind) })
        }
    }
}

@Composable
private fun StartExtractionCard(state: MainUiState, onStart: () -> Unit) {
    val active = state.workState in activeWorkStates
    Button(modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !active && state.selectedOutputs.isNotEmpty(), onClick = onStart) {
        Icon(Icons.Default.Download, null)
        Text(stringResource(R.string.start_extraction), modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun WorkProgressCard(state: MainUiState, onCancel: () -> Unit, onOpen: (() -> Unit)?, onNewExtraction: () -> Unit) {
    val active = state.workState in activeWorkStates
    val succeeded = state.workState == WorkInfo.State.SUCCEEDED
    val cancelled = state.workState == WorkInfo.State.CANCELLED
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = if (succeeded) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader(
                icon = when { succeeded -> Icons.Default.CheckCircle; cancelled -> Icons.Default.Cancel; state.workState == WorkInfo.State.FAILED -> Icons.Default.Error; else -> Icons.Default.Download },
                title = when { succeeded -> stringResource(R.string.extraction_complete); cancelled -> stringResource(R.string.operation_cancelled); state.workState == WorkInfo.State.FAILED -> stringResource(R.string.extraction_failed); else -> stringResource(R.string.processing_title) },
                body = if (succeeded) stringResource(R.string.completion_description) else stringResource(R.string.processing_description),
            )
            if (active) {
                StaticSetting(stringResource(R.string.current_stage), state.workStage.ifBlank { stringResource(R.string.processing) })
                ProgressBlock(state.workPercent / 100f, stringResource(R.string.progress_percent_format, state.workPercent))
                Text(stringResource(R.string.background_processing_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Cancel, null); Text(stringResource(R.string.cancel_operation), modifier = Modifier.padding(start = 8.dp)) }
            }
            if (succeeded) {
                StaticSetting(stringResource(R.string.saved_files_format, state.exportedCount), state.workStage.ifBlank { stringResource(R.string.stage_done) })
                if (onOpen != null) Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.OpenInNew, null); Text(stringResource(R.string.open_last_output), modifier = Modifier.padding(start = 8.dp)) }
                FilledTonalButton(onClick = onNewExtraction, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Text(stringResource(R.string.start_new_extraction), modifier = Modifier.padding(start = 8.dp)) }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Default.Error, stringResource(R.string.error_title), message)
            StaticSetting(stringResource(R.string.error_diagnostics), sanitizeDiagnostic(message))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Icon(Icons.Default.Refresh, null); Text(stringResource(R.string.retry), modifier = Modifier.padding(start = 8.dp)) }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

@Composable
private fun SecurityNotice() {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.security_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressBlock(progress: Float?, label: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoGrid(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
    }
}

@Composable
private fun DetectionChip(label: String, available: Boolean) {
    AssistChip(onClick = {}, label = { Text(label) }, leadingIcon = { Icon(if (available) Icons.Default.CheckCircle else Icons.Default.Cancel, null, Modifier.size(18.dp)) })
}

@Composable
private fun StaticSetting(label: String, value: String) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityPicker(quality: VideoQuality, onQuality: (VideoQuality) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = "${quality.label} (${quality.width}×${quality.height})",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.video_quality)) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VideoQuality.entries.forEach { item ->
                DropdownMenuItem(text = { Text("${item.label} (${item.width}×${item.height})") }, onClick = { onQuality(item); expanded = false })
            }
        }
    }
}

private val activeWorkStates = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)

private data class OutputOption(
    val kind: OutputKind,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val available: Boolean,
)

@Composable
private fun outputOptions(analysis: RecordingAnalysis) = listOf(
    OutputOption(OutputKind.SYNCED_VIDEO, stringResource(R.string.synced_video_title), stringResource(R.string.synced_video_description), Icons.Default.Movie, analysis.hasWhiteboard || analysis.hasScreenShare || analysis.hasSharedPdfTimeline || analysis.sharedFiles.any { it.fileName.endsWith(".pdf", true) }),
    OutputOption(OutputKind.AUDIO_M4A, stringResource(R.string.audio_m4a_title), stringResource(R.string.audio_m4a_description), Icons.Default.AudioFile, analysis.hasAudio),
    OutputOption(OutputKind.WHITEBOARD_PDF, stringResource(R.string.whiteboard_pdf_title), stringResource(R.string.whiteboard_pdf_description), Icons.Default.Description, analysis.hasWhiteboard),
    OutputOption(OutputKind.SHARED_FILES, stringResource(R.string.shared_files_title), stringResource(R.string.shared_files_description), Icons.Default.FolderZip, analysis.sharedFiles.isNotEmpty()),
)

private fun sanitizeDiagnostic(message: String): String = message
    .replace(Regex("session=[^&\\s]+", RegexOption.IGNORE_CASE), "session=••••")
    .replace(Regex("token=[^&\\s]+", RegexOption.IGNORE_CASE), "token=••••")

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds) else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun MainScreenPreview() {
    VadanaTheme {
        MainScreenContent(
            state = MainUiState(
                url = "https://example.adobeconnect.com/demo/?session=sample",
                analysis = previewAnalysis,
                selectedOutputs = setOf(OutputKind.SYNCED_VIDEO, OutputKind.AUDIO_M4A),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onUrlChange = {}, onPaste = {}, onAnalyze = {}, onToggle = {}, onQuality = {}, onStart = {}, onCancel = {}, onRetry = {}, onClearError = {}, onOpen = {}, onNewExtraction = {},
        )
    }
}

private val previewAnalysis = RecordingAnalysis(
    recording = Recording("https://example.adobeconnect.com/demo/?session=sample", "https://example.adobeconnect.com", "demo", "sample"),
    packagePath = "/tmp/package.zip",
    sharedFiles = listOf(SharedFile("", "slides.pdf", FileCategory.DOCUMENT), SharedFile("", "resources.zip", FileCategory.OTHER)),
    whiteboardPages = 12,
    whiteboardEvents = 320,
    hasAudio = true,
    hasScreenShare = true,
    hasSharedPdfTimeline = true,
    estimatedDurationMs = 4_200_000,
)
