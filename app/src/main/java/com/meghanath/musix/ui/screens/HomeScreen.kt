package com.meghanath.musix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.meghanath.musix.data.RenameResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meghanath.musix.data.AddResult
import com.meghanath.musix.data.Artist
import com.meghanath.musix.ui.HomeViewModel
import com.meghanath.musix.data.DeviceStorage
import com.meghanath.musix.ui.LowSpaceBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenStorage: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val freeBytes = androidx.compose.runtime.remember { DeviceStorage.read(context).freeBytes }

    var showAddDialog by remember { mutableStateOf(false) }
    var artistToDelete by remember { mutableStateOf<Artist?>(null) }
    var artistToRename by remember { mutableStateOf<Artist?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Musix") },
                actions = {
                    TextButton(onClick = onOpenFavorites) { Text("Favorites") }
                    TextButton(onClick = onOpenPlaylists) { Text("Playlists") }
                    TextButton(onClick = onOpenStorage) { Text("Storage") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add artist")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (freeBytes < DeviceStorage.LOW_SPACE_BYTES) {
                LowSpaceBanner(freeBytes = freeBytes, onClick = onOpenStorage)
            }
            OutlinedTextField(
                value = searchText,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search artists") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            if (artists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchText.isBlank()) {
                            "No artists yet. Tap + to add one."
                        } else {
                            "No artists match your search."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artists, key = { it.id }) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { onOpenArtist(artist.id) },
                            onRename = { artistToRename = artist },
                            onDelete = { artistToDelete = artist }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddArtistDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, onResult -> viewModel.addArtist(name, onResult) }
        )
    }

    artistToRename?.let { artist ->
        RenameArtistDialog(
            currentName = artist.name,
            onDismiss = { artistToRename = null },
            onRename = { name, onResult -> viewModel.renameArtist(artist, name, onResult) }
        )
    }

    artistToDelete?.let { artist ->
        AlertDialog(
            onDismissRequest = { artistToDelete = null },
            title = { Text("Delete ${artist.name}?") },
            text = { Text("This removes the artist and all their songs from the app, including the song files stored here.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteArtist(artist)
                    artistToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { artistToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddArtistDialog(
    onDismiss: () -> Unit,
    onAdd: (String, (AddResult) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add artist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Artist or band name") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(name) { result ->
                    when (result) {
                        AddResult.ADDED -> onDismiss()
                        AddResult.EMPTY -> error = "Enter a name"
                        AddResult.DUPLICATE -> error = "Already in your library"
                    }
                }
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 44.dp)
            )

            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options for ${artist.name}"
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameArtistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String, (RenameResult) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename artist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Artist or band name") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    if (name.trim() == currentName) {
                        onDismiss()
                    } else {
                        onRename(name) { result ->
                            when (result) {
                                RenameResult.RENAMED -> onDismiss()
                                RenameResult.EMPTY -> error = "Enter a name"
                                RenameResult.DUPLICATE -> error = "Already in your library"
                            }
                        }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}