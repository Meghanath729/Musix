package com.meghanath.musix.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meghanath.musix.data.Song
import com.meghanath.musix.data.RenameResult
import com.meghanath.musix.ui.ArtistViewModel
import com.meghanath.musix.ui.FavoritesViewModel
import com.meghanath.musix.playback.rememberPlayerViewModel
import com.meghanath.musix.playback.toQueueItem
import com.meghanath.musix.playback.rememberNotificationPermissionRequester
import android.widget.Toast
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.meghanath.musix.ui.PlaylistsViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.LinearProgressIndicator
import com.meghanath.musix.backup.SongExporter
import com.meghanath.musix.data.DeviceStorage
import com.meghanath.musix.ui.ExportState
import com.meghanath.musix.ui.ExportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: Long,
    onOpenAddSong: () -> Unit,
    onOpenPlayer: () -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel = viewModel(
        factory = ArtistViewModel.factory(
            LocalContext.current.applicationContext as Application,
            artistId
        )
    )
) {
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val searchText by viewModel.query.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val playerViewModel = rememberPlayerViewModel()
    val context = LocalContext.current
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val favoritesViewModel: FavoritesViewModel = viewModel()
    val playlistsViewModel: PlaylistsViewModel = viewModel()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()

    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var menuSongId by remember { mutableStateOf<Long?>(null) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }
    var songToExport by remember { mutableStateOf<Song?>(null) }
    var songToRename by remember { mutableStateOf<Song?>(null) }

    val exportViewModel: ExportViewModel = viewModel()
    val exportState by exportViewModel.state.collectAsStateWithLifecycle()

    // Android's own "Save as" screen; we get back the file the user picked.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val song = songToExport
        songToExport = null
        if (uri != null && song != null) exportViewModel.start(song, uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(artist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenAddSong) {
                Icon(Icons.Default.Add, contentDescription = "Add song")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search songs") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchText.isBlank()) {
                            "No songs yet. Tap + to add one."
                        } else {
                            "No songs match your search."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(songs, key = { it.id }) { song ->
                        ListItem(
                            headlineContent = { Text(song.title) },
                            supportingContent = { Text(versionLabel(song)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formatDuration(song.durationMs),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { favoritesViewModel.setFavorite(song.id, !song.isFavorite) }
                                    ) {
                                        Icon(
                                            imageVector = if (song.isFavorite) Icons.Default.Favorite
                                            else Icons.Default.FavoriteBorder,
                                            contentDescription = if (song.isFavorite) {
                                                "Remove ${song.title} from favorites"
                                            } else {
                                                "Add ${song.title} to favorites"
                                            },
                                            tint = if (song.isFavorite) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menuSongId = song.id }) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "More options for ${song.title}"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuSongId == song.id,
                                            onDismissRequest = { menuSongId = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                onClick = {
                                                    menuSongId = null
                                                    songToRename = song
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Add to playlist") },
                                                onClick = {
                                                    menuSongId = null
                                                    songToAdd = song
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Export") },
                                                onClick = {
                                                    menuSongId = null
                                                    songToExport = song
                                                    exportLauncher.launch(SongExporter.suggestedName(song))
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = {
                                                    menuSongId = null
                                                    songToDelete = song
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val queue = allSongs.map { it.toQueueItem(context, artist?.name.orEmpty()) }
                                val startIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                playerViewModel.playQueue(queue, startIndex)
                                requestNotificationPermission()
                                onOpenPlayer()
                            }
                        )
                    }
                }
            }
        }
    }
    songToAdd?.let { song ->
        AlertDialog(
            onDismissRequest = { songToAdd = null },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create one from the Playlists screen.")
                } else {
                    LazyColumn {
                        items(playlists, key = { it.playlist.id }) { item ->
                            ListItem(
                                headlineContent = { Text(item.playlist.name) },
                                supportingContent = {
                                    Text(
                                        if (item.songCount == 1) "1 song" else "${item.songCount} songs"
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    playlistsViewModel.addSong(item.playlist.id, song.id) { added ->
                                        val message = if (added) {
                                            "Added to ${item.playlist.name}"
                                        } else {
                                            "Already in ${item.playlist.name}"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                    songToAdd = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { songToAdd = null }) { Text("Cancel") }
            }
        )
    }

    songToRename?.let { song ->
        var newTitle by remember(song.id) { mutableStateOf(song.title) }
        AlertDialog(
            onDismissRequest = { songToRename = null },
            title = { Text("Rename song") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        if (newTitle.trim() == song.title) {
                            songToRename = null
                        } else {
                            viewModel.renameSong(song, newTitle) { result ->
                                when (result) {
                                    RenameResult.RENAMED -> {
                                        songToRename = null
                                        Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                                    }
                                    RenameResult.EMPTY -> Toast.makeText(
                                        context, "The title cannot be empty", Toast.LENGTH_SHORT
                                    ).show()
                                    RenameResult.DUPLICATE -> Toast.makeText(
                                        context,
                                        "This artist already has a song with that title in the same version",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { songToRename = null }) { Text("Cancel") }
            }
        )
    }

    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("Delete ${song.title}?") },
            text = { Text("This removes the song from this artist.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSong(song)
                    songToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) { Text("Cancel") }
            }
        )
    }

    when (val export = exportState) {
        is ExportState.Idle -> Unit

        is ExportState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(export.title)
                    if (export.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { export.doneBytes.toFloat() / export.totalBytes.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "${DeviceStorage.format(export.doneBytes)} of " +
                                DeviceStorage.format(export.totalBytes)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { exportViewModel.cancel() }) { Text("Cancel") }
            }
        )

        is ExportState.Done -> AlertDialog(
            onDismissRequest = { exportViewModel.dismiss() },
            title = { Text("Exported") },
            text = { Text("\"${export.title}\" was saved to the place you chose.") },
            confirmButton = {
                TextButton(onClick = { exportViewModel.dismiss() }) { Text("OK") }
            }
        )

        is ExportState.Failed -> AlertDialog(
            onDismissRequest = { exportViewModel.dismiss() },
            title = { Text("Export failed") },
            text = { Text(export.message) },
            confirmButton = {
                TextButton(onClick = { exportViewModel.dismiss() }) { Text("OK") }
            }
        )
    }
}

private fun versionLabel(song: Song): String {
    val kind = if (song.isLive) "Live" else "Original"
    val media = if (song.hasVideo) "Video" else "Audio"
    return "$kind - $media"
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}