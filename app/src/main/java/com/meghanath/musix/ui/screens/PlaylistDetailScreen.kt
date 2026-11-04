package com.meghanath.musix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meghanath.musix.data.Song
import com.meghanath.musix.data.SongWithArtist
import com.meghanath.musix.playback.playList
import com.meghanath.musix.playback.rememberNotificationPermissionRequester
import com.meghanath.musix.playback.rememberPlayerViewModel
import com.meghanath.musix.playback.toQueueItem
import com.meghanath.musix.ui.PlaylistDetailViewModel
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: PlaylistDetailViewModel = viewModel()
) {
    // Tell the ViewModel which playlist this screen shows
    LaunchedEffect(playlistId) { viewModel.setPlaylist(playlistId) }

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val playerViewModel = rememberPlayerViewModel()
    val context = LocalContext.current
    val requestNotificationPermission = rememberNotificationPermissionRequester()

    var showPicker by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }

    // If the playlist has fewer than 2 songs there is nothing to reorder
    LaunchedEffect(songs.size) {
        if (songs.size < 2) reordering = false
    }

    fun play(startIndex: Int, shuffle: Boolean?) {
        val queue = songs.map { it.song.toQueueItem(context, it.artistName) }
        playerViewModel.playList(queue, startIndex, shuffle)
        requestNotificationPermission()
        onOpenPlayer()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (songs.size > 1) {
                        TextButton(onClick = { reordering = !reordering }) {
                            Text(if (reordering) "Done" else "Reorder")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add songs")
            }
        }
    ) { innerPadding ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No songs yet. Tap + to add songs from your library.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { play(0, false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Play all") }
                    OutlinedButton(
                        onClick = { play(Random.nextInt(songs.size), true) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Shuffle") }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    itemsIndexed(songs, key = { _, item -> item.song.id }) { index, item ->
                        ListItem(
                            headlineContent = { Text(item.song.title) },
                            supportingContent = {
                                Text("${item.artistName} - ${songVersionText(item.song)}")
                            },
                            trailingContent = {
                                if (reordering) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.moveUp(item.song.id) },
                                            enabled = index > 0
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move ${item.song.title} up"
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveDown(item.song.id) },
                                            enabled = index < songs.lastIndex
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move ${item.song.title} down"
                                            )
                                        }
                                    }
                                } else {
                                    IconButton(onClick = { viewModel.removeSong(item.song.id) }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Remove ${item.song.title} from playlist"
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(enabled = !reordering) { play(index, null) }
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        AddSongsSheet(
            allSongs = allSongs,
            addedIds = songs.map { it.song.id }.toSet(),
            onAdd = { songId -> viewModel.addSong(songId) },
            onDismiss = { showPicker = false }
        )
    }
}

// Full-screen picker. It is a Dialog instead of a bottom sheet, so there is nothing
// to drag and the shaking / auto-closing can no longer happen.
@Composable
private fun AddSongsSheet(
    allSongs: List<SongWithArtist>,
    addedIds: Set<Long>,
    onAdd: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(allSongs, query) {
        if (query.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.song.title.contains(query, ignoreCase = true) ||
                        it.artistName.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add songs",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Done") }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search songs or artists") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (allSongs.isEmpty()) {
                                "Your library has no songs yet."
                            } else {
                                "No songs match your search."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filtered, key = { it.song.id }) { item ->
                            val added = item.song.id in addedIds
                            ListItem(
                                headlineContent = { Text(item.song.title) },
                                supportingContent = {
                                    Text("${item.artistName} - ${songVersionText(item.song)}")
                                },
                                trailingContent = {
                                    if (added) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Already in playlist",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        IconButton(onClick = { onAdd(item.song.id) }) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Add ${item.song.title}"
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable(enabled = !added) { onAdd(item.song.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun songVersionText(song: Song): String {
    val kind = if (song.isLive) "Live" else "Original"
    val media = if (song.hasVideo) "Video" else "Audio"
    return "$kind - $media"
}