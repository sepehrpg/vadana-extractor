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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import ir.vadana.extractor.domain.OutputKind
import ir.vadana.extractor.domain.RecordingAnalysis
import ir.vadana.extractor.domain.VideoQuality
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                        Column {
                            Text("استخراج‌گر وادانا", fontWeight = FontWeight.Bold)
                            Text("Adobe Connect Recording Extractor", fontSize = 11.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    UrlCard(
                        url = state.url,
                        analyzing = state.analyzing,
                        progress = state.analysisProgress,
                        onUrlChange = viewModel::setUrl,
                        onPaste = {
                            clipboard.getText()?.text?.let(viewModel::setUrl)
                        },
                        onAnalyze = viewModel::analyze,
                    )
                }

                state.analysis?.let { analysis ->
                    item { AnalysisCard(analysis) }
                    item {
                        OutputSelectionCard(
                            analysis = analysis,
                            selected = state.selectedOutputs,
                            quality = state.quality,
                            onToggle = viewModel::toggleOutput,
                            onQuality = viewModel::setQuality,
                        )
                    }
                    item {
                        val active = state.workState in setOf(
                            WorkInfo.State.ENQUEUED,
                            WorkInfo.State.RUNNING,
                            WorkInfo.State.BLOCKED,
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !active && state.selectedOutputs.isNotEmpty(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.startExtraction()
                            },
                        ) {
                            Icon(Icons.Default.Download, null)
                            Text("شروع استخراج", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                if (state.workId != null) {
                    item {
                        WorkProgressCard(
                            state = state,
                            onCancel = viewModel::cancelWork,
                            onOpen = state.lastUri?.let { uri ->
                                {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                item {
                    Text(
                        text = "فقط محتوایی را دریافت کنید که اجازهٔ دسترسی به آن را دارید. توکن session در فایل رمزگذاری‌شدهٔ موقت نگه‌داری و پس از پایان حذف می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UrlCard(
    url: String,
    analyzing: Boolean,
    progress: Float?,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("لینک ضبط کلاس", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !analyzing,
                minLines = 2,
                placeholder = { Text("https://vadana.../recording/?session=...") },
                trailingIcon = {
                    IconButton(onClick = onPaste, enabled = !analyzing) {
                        Icon(Icons.Default.ContentPaste, "چسباندن")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth(),
                enabled = !analyzing && url.isNotBlank(),
            ) {
                if (analyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Search, null)
                }
                Text(
                    if (analyzing) "در حال دریافت و تحلیل…" else "تحلیل کلاس",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (analyzing) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard(analysis: RecordingAnalysis) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null)
                Text(
                    "کلاس با موفقیت تحلیل شد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text("شناسه: ${analysis.recording.recordingId}")
            Text("مدت تقریبی: ${formatDuration(analysis.estimatedDurationMs)}")
            Text("فایل اشتراکی: ${analysis.sharedFiles.size}")
            Text("صفحهٔ وایت‌برد: ${analysis.whiteboardPages}  •  رویداد: ${analysis.whiteboardEvents}")
            Text(
                listOfNotNull(
                    "صوت".takeIf { analysis.hasAudio },
                    "اشتراک صفحه".takeIf { analysis.hasScreenShare },
                    "تایم‌لاین PDF".takeIf { analysis.hasSharedPdfTimeline },
                ).joinToString("  •  ").ifBlank { "رسانه‌ای در بسته تشخیص داده نشد" },
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
    val options = listOf(
        OutputOption(OutputKind.SHARED_FILES, "فایل‌های اشتراکی", "PDF، پاورپوینت، Word و سایر فایل‌ها", Icons.Default.FolderZip,
            analysis.sharedFiles.isNotEmpty()),
        OutputOption(OutputKind.WHITEBOARD_PDF, "وایت‌برد PDF", "یادداشت‌ها و دست‌خط استاد روی صفحات", Icons.Default.Description,
            analysis.hasWhiteboard),
        OutputOption(OutputKind.AUDIO_M4A, "فایل صوتی M4A", "صوت کلاس با حفظ فاصله‌های زمانی", Icons.Default.AudioFile,
            analysis.hasAudio),
        OutputOption(OutputKind.SYNCED_VIDEO, "ویدئوی همگام MP4", "وایت‌برد، PDF، اشتراک صفحه و صوت", Icons.Default.Movie,
            analysis.hasWhiteboard || analysis.hasScreenShare || analysis.hasSharedPdfTimeline ||
                analysis.sharedFiles.any { it.fileName.endsWith(".pdf", true) }),
    )
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("خروجی‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            options.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(option.icon, null)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(option.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (option.available) option.description else "در این کلاس موجود نیست",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = option.kind in selected,
                        enabled = option.available,
                        onCheckedChange = { onToggle(option.kind) },
                    )
                }
            }
            AnimatedVisibility(OutputKind.SYNCED_VIDEO in selected) {
                QualityPicker(quality, onQuality)
            }
        }
    }
}

private data class OutputOption(
    val kind: OutputKind,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val available: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityPicker(quality: VideoQuality, onQuality: (VideoQuality) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        Text("کیفیت ویدئو", style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = "${quality.label}  (${quality.width}×${quality.height})",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                VideoQuality.entries.forEach { item ->
                    DropdownMenuItem(
                        text = { Text("${item.label}  (${item.width}×${item.height})") },
                        onClick = {
                            onQuality(item)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkProgressCard(
    state: MainUiState,
    onCancel: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val active = state.workState in setOf(
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.RUNNING,
        WorkInfo.State.BLOCKED,
    )
    val succeeded = state.workState == WorkInfo.State.SUCCEEDED
    val cancelled = state.workState == WorkInfo.State.CANCELLED
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                succeeded -> MaterialTheme.colorScheme.primaryContainer
                cancelled -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        succeeded -> Icons.Default.CheckCircle
                        cancelled -> Icons.Default.Cancel
                        state.workState == WorkInfo.State.FAILED -> Icons.Default.Error
                        else -> Icons.Default.Download
                    },
                    null,
                )
                Text(
                    when {
                        succeeded -> "استخراج کامل شد"
                        cancelled -> "عملیات لغو شد"
                        state.workState == WorkInfo.State.FAILED -> "استخراج ناموفق بود"
                        else -> state.workStage.ifBlank { "در حال پردازش" }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (active) {
                LinearProgressIndicator(
                    progress = { state.workPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.workPercent}%")
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Cancel, null)
                    Text("لغو عملیات", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (succeeded) {
                Text("${state.exportedCount} فایل در پوشه‌های Vadana داخل Downloads، Movies یا Music ذخیره شد.")
                if (onOpen != null) {
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.OpenInNew, null)
                        Text("باز کردن آخرین خروجی", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
