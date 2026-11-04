package com.meghanath.musix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meghanath.musix.data.Song
import com.meghanath.musix.playback.playList
import com.meghanath.musix.playback.rememberNotificationPermissionRequester
import com.meghanath.musix.playback.rememberPlayerViewModel
import com.meghanath.musix.playback.toQueueItem
import com.meghanath.musix.ui.FavoritesViewModel
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: FavoritesViewModel = viewModel()
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playerViewModel = rememberPlayerViewModel()
    val context = LocalContext.current
    val requestNotificationPermission = rememberNotificationPermissionRequester()

    fun play(startIndex: Int, shuffle: Boolean?) {
        val queue = favorites.map { it.song.toQueueItem(context, it.artistName) }
        playerViewModel.playList(queue, startIndex, shuffle)
        requestNotificationPermission()
        onOpenPlayer()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No favorites yet. Tap the heart on a song to add it here.",
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
                        onClick = { play(Random.nextInt(favorites.size), true) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Shuffle") }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(favorites, key = { _, item -> item.song.id }) { index, item ->
                        ListItem(
                            headlineContent = { Text(item.song.title) },
                            supportingContent = {
                                Text("${item.artistName} - ${versionText(item.song)}")
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.setFavorite(item.song.id, false) }) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = "Remove ${item.song.title} from favorites",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { play(index, null) }
                        )
                    }
                }
            }
        }
    }
}

private fun versionText(song: Song): String {
    val kind = if (song.isLive) "Live" else "Original"
    val media = if (song.hasVideo) "Video" else "Audio"
    return "$kind - $media"
}