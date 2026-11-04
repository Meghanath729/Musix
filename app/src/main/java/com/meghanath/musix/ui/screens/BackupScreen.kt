package com.meghanath.musix.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meghanath.musix.backup.RestoreStage
import com.meghanath.musix.data.DeviceStorage
import com.meghanath.musix.playback.rememberPlayerViewModel
import com.meghanath.musix.ui.BackupState
import com.meghanath.musix.ui.BackupViewModel
import com.meghanath.musix.ui.RestoreState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel()
) {
    val playerViewModel = rememberPlayerViewModel()
    val state by viewModel.state.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val libraryBytes by viewModel.libraryBytes.collectAsState()

    // The backup file the user picked, waiting for them to confirm.
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    // Android's own "Save as" screen.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.startBackup(uri)
    }

    // Android's own "Open file" screen.
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestore = uri
    }

    val busy = state is BackupState.Working || restoreState is RestoreState.Working

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup and restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------------- Backup ----------------
            Text("Backup", style = MaterialTheme.typography.titleLarge)
            Text(
                "Saves your artists, songs, playlists and favorites, plus every song file, " +
                        "into one zip file that you place yourself. Your library is currently " +
                        "${DeviceStorage.format(libraryBytes)}, so pick a place with at least " +
                        "that much free space."
            )

            Button(
                onClick = { saveLauncher.launch(defaultBackupName()) },
                enabled = !busy
            ) { Text("Create backup") }

            when (val s = state) {
                is BackupState.Idle -> Unit

                is BackupState.Working -> {
                    if (s.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { s.doneBytes.toFloat() / s.totalBytes.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${DeviceStorage.format(s.doneBytes)} of ${DeviceStorage.format(s.totalBytes)}")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Preparing...")
                    }
                    OutlinedButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
                }

                is BackupState.Done -> {
                    Text("Backup saved: ${s.songs} songs, ${DeviceStorage.format(s.bytes)}.")
                    if (s.skipped > 0) {
                        Text("${s.skipped} songs were left out because their files were missing.")
                    }
                    TextButton(onClick = { viewModel.dismiss() }) { Text("OK") }
                }

                is BackupState.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.dismiss() }) { Text("OK") }
                }
            }

            HorizontalDivider()

            // ---------------- Restore ----------------
            Text("Restore", style = MaterialTheme.typography.titleLarge)
            Text(
                "Replaces everything in Musix with the contents of a backup file made by this " +
                        "app. Anything added after that backup was made will be lost."
            )

            OutlinedButton(
                onClick = {
                    openLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"
                        )
                    )
                },
                enabled = !busy
            ) { Text("Restore from backup") }

            when (val r = restoreState) {
                is RestoreState.Idle -> Unit

                is RestoreState.Working -> {
                    when (r.stage) {
                        RestoreStage.CHECKING -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Checking the backup...")
                        }
                        RestoreStage.COPYING -> {
                            if (r.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { r.doneBytes.toFloat() / r.totalBytes.toFloat() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                "Copying songs: ${DeviceStorage.format(r.doneBytes)} of " +
                                        DeviceStorage.format(r.totalBytes)
                            )
                        }
                        RestoreStage.FINISHING -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Finishing...")
                        }
                    }
                    // The last stage cannot be stopped halfway, so no Cancel button there.
                    if (r.stage != RestoreStage.FINISHING) {
                        OutlinedButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
                    }
                }

                is RestoreState.Done -> {
                    Text(
                        "Restored ${r.artists} artists, ${r.songs} songs and " +
                                "${r.playlists} playlists."
                    )
                    TextButton(onClick = { viewModel.dismiss() }) { Text("OK") }
                }

                is RestoreState.Failed -> {
                    Text(r.message, color = MaterialTheme.colorScheme.error)
                    Text("Your current library was not changed.")
                    TextButton(onClick = { viewModel.dismiss() }) { Text("OK") }
                }
            }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace your library?") },
            text = {
                Text(
                    "Everything in Musix (artists, songs, playlists and favorites) will be " +
                            "replaced by the contents of this backup. Songs added after the backup " +
                            "was made will be lost. Music that is playing will stop."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    viewModel.startRestore(uri) { playerViewModel.stopAndClear() }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
    }
}

private fun defaultBackupName(): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
    return "musix-backup-$stamp.zip"
}