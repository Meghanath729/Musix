package com.meghanath.musix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meghanath.musix.data.DeviceStorage
import com.meghanath.musix.data.SongWithArtist
import com.meghanath.musix.ui.StorageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: StorageViewModel = viewModel()
) {
    val summary by viewModel.summary.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val largest by viewModel.largestSongs.collectAsState()
    var songToDelete by remember { mutableStateOf<SongWithArtist?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Musix is using", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DeviceStorage.format(summary.musixBytes),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text("Audio: ${DeviceStorage.format(summary.audioBytes)}")
                        Text("Video: ${DeviceStorage.format(summary.videoBytes)}")
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Phone storage", style = MaterialTheme.typography.bodyMedium)
                        val total = summary.device.totalBytes
                        val usedFraction =
                            if (total > 0) summary.device.usedBytes.toFloat() / total.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { usedFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${DeviceStorage.format(summary.device.freeBytes)} free of " +
                                    DeviceStorage.format(total)
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = onOpenBackup,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Backup and restore") }
            }

            item { Text("By artist", style = MaterialTheme.typography.titleMedium) }

            if (artists.isEmpty()) {
                item { Text("No artists yet") }
            }
            items(artists, key = { "artist_${it.artistId}" }) { a ->
                ListItem(
                    headlineContent = { Text(a.artistName) },
                    supportingContent = {
                        Text(if (a.songCount == 1) "1 song" else "${a.songCount} songs")
                    },
                    trailingContent = { Text(DeviceStorage.format(a.totalBytes)) }
                )
            }

            item { Text("Largest songs", style = MaterialTheme.typography.titleMedium) }

            if (largest.isEmpty()) {
                item { Text("No songs yet") }
            }
            items(largest, key = { "song_${it.song.id}" }) { row ->
                val version = (if (row.song.isLive) "Live" else "Original") +
                        " - " + (if (row.song.hasVideo) "Video" else "Audio")
                ListItem(
                    headlineContent = { Text(row.song.title) },
                    supportingContent = { Text("${row.artistName} - $version") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(DeviceStorage.format(row.song.sizeBytes))
                            IconButton(onClick = { songToDelete = row }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete song")
                            }
                        }
                    }
                )
            }
        }
    }

    songToDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("Delete song?") },
            text = {
                Text(
                    "\"${row.song.title}\" will be removed from your phone, " +
                            "and from any playlists and favorites."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSong(row.song)
                    songToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) { Text("Cancel") }
            }
        )
    }
}