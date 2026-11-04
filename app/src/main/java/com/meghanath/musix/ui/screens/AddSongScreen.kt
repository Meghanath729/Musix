package com.meghanath.musix.ui.screens

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meghanath.musix.ui.AddSongViewModel
import com.meghanath.musix.data.DeviceStorage

private const val MAX_DURATION_MS = 10 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongScreen(
    artistId: Long,
    onBack: () -> Unit,
    viewModel: AddSongViewModel = viewModel(
        factory = AddSongViewModel.factory(
            LocalContext.current.applicationContext as Application,
            artistId
        )
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Add song") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.linkMode) {
                    Button(
                        onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f)
                    ) { Text("From file") }
                    OutlinedButton(
                        onClick = { viewModel.onModeChange(true) },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f)
                    ) { Text("From link") }
                } else {
                    OutlinedButton(
                        onClick = {
                            viewModel.onModeChange(false)
                            picker.launch(arrayOf("audio/*", "video/*"))
                        },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f)
                    ) { Text("From file") }
                    Button(
                        onClick = { viewModel.onModeChange(true) },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f)
                    ) { Text("From link") }
                }
            }

            if (!state.linkMode) {
                if (state.picked == null && !state.reading && state.error == null) {
                    Text(
                        "Tap \"From file\" above to choose a song.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.reading) {
                    CircularProgressIndicator()
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                state.picked?.let { picked ->
                    Text(picked.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${formatSize(picked.sizeBytes)} - " +
                                "${formatDuration(picked.durationMs)} - " +
                                if (picked.fileHasVideo) "Contains video" else "Audio only",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (picked.durationMs > MAX_DURATION_MS) {
                        Text(
                            text = "This song is longer than 10 minutes. You can still save it.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("Title") },
                        singleLine = true,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    )


                    Text("Version", style = MaterialTheme.typography.titleSmall)
                    ChoiceRow(
                        label = "Original",
                        selected = !state.isLive,
                        enabled = !state.saving,
                        onClick = { viewModel.onLiveChange(false) }
                    )
                    ChoiceRow(
                        label = "Live",
                        selected = state.isLive,
                        enabled = !state.saving,
                        onClick = { viewModel.onLiveChange(true) }
                    )

                    Text("Format", style = MaterialTheme.typography.titleSmall)
                    ChoiceRow(
                        label = "Audio only",
                        selected = !state.wantsVideo,
                        enabled = !state.saving,
                        onClick = { viewModel.onVideoChange(false) }
                    )
                    ChoiceRow(
                        label = if (picked.fileHasVideo) "With video" else "With video (this file has no video)",
                        selected = state.wantsVideo,
                        enabled = !state.saving && picked.fileHasVideo,
                        onClick = { viewModel.onVideoChange(true) }
                    )

                    if (state.saving) {
                        val progress = state.progress
                        if (progress == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Copying...")
                        } else {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Copying... ${(progress * 100).toInt()}%")
                        }
                    }

                    Button(
                        onClick = {
                            if (DeviceStorage.canFit(context, picked.sizeBytes)) {
                                viewModel.save(onBack)
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Not enough free space for this song. Free up space in Storage.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = state.title.isNotBlank() && !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.link,
                    onValueChange = viewModel::onLinkChange,
                    label = { Text("Link") },
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!text.isNullOrBlank()) viewModel.onLinkPasted(text)
                        },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Paste")
                    }
                    Button(
                        onClick = { viewModel.fetchLinkInfo() },
                        enabled = state.link.isNotBlank() && !state.fetching && !state.saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Fetch info")
                    }
                }

                if (state.fetching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Reading link... this can take a minute or two, especially the first time")
                }

                if (state.error != null || state.updating || state.notice != null) {
                    TextButton(
                        onClick = { viewModel.updateDownloader() },
                        enabled = !state.saving && !state.updating && !state.fetching
                    ) {
                        Text(if (state.updating) "Updating..." else "Update downloader")
                    }
                }
                if (state.updating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.notice?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                state.linkInfo?.let { info ->
                    Text(info.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatDuration(info.durationSec * 1000L) +
                                (info.uploader?.let { " - $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (info.durationSec * 1000L > MAX_DURATION_MS) {
                        Text(
                            text = "This song is longer than 10 minutes. You can still save it.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("Title") },
                        singleLine = true,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Version", style = MaterialTheme.typography.titleSmall)
                    ChoiceRow(
                        label = "Original",
                        selected = !state.isLive,
                        enabled = !state.saving,
                        onClick = { viewModel.onLiveChange(false) }
                    )
                    ChoiceRow(
                        label = "Live",
                        selected = state.isLive,
                        enabled = !state.saving,
                        onClick = { viewModel.onLiveChange(true) }
                    )

                    Text("Format", style = MaterialTheme.typography.titleSmall)
                    ChoiceRow(
                        label = "Audio only",
                        selected = !state.wantsVideo,
                        enabled = !state.saving,
                        onClick = { viewModel.onVideoChange(false) }
                    )
                    ChoiceRow(
                        label = "Video (720p)",
                        selected = state.wantsVideo,
                        enabled = !state.saving,
                        onClick = { viewModel.onVideoChange(true) }
                    )

                    if (state.saving) {
                        val progress = state.progress
                        if (progress == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("${state.statusText}...")
                        } else {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${state.statusText}... ${(progress * 100).toInt()}%")
                        }
                        if (state.downloading) {
                            OutlinedButton(
                                onClick = { viewModel.cancelDownload() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel download")
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val estimate = estimateBytes(info.durationSec, state.wantsVideo)
                            if (DeviceStorage.canFit(context, estimate)) {
                                viewModel.downloadAndSave(onBack)
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Not enough free space for this download. Free up space in Storage.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = state.title.isNotBlank() && !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download and save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = if (enabled) Color.Unspecified
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

// Generous upper estimate used only for the free-space check before a download.
private fun estimateBytes(durationSec: Int, video: Boolean): Long {
    val bytesPerSecond = if (video) 400_000L else 24_000L
    return durationSec.toLong() * bytesPerSecond
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "%.1f MB".format(mb) else "%d KB".format(bytes / 1024)
}