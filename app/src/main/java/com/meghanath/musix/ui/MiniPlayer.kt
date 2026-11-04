package com.meghanath.musix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.meghanath.musix.R
import com.meghanath.musix.playback.PlayerViewModel
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    onOpenPlayer: () -> Unit
) {
    val state by playerViewModel.state.collectAsState()
    var positionMs by remember { mutableLongStateOf(0L) }

    // Refresh the progress line twice a second; pauses by itself when the screen is off.
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = playerViewModel.currentPositionMs()
            delay(500)
            withFrameMillis { }
        }
    }

    val progress = (positionMs.toFloat() / state.durationMs.coerceAtLeast(1L).toFloat())
        .coerceIn(0f, 1f)
    val subtitle = listOf(state.artistName, state.versionLabel)
        .filter { it.isNotBlank() }
        .joinToString(" - ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { playerViewModel.previous() }) {
                    Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous")
                }
                IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = { playerViewModel.next() }) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next")
                }

                IconButton(onClick = { playerViewModel.stopAndClear() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close player")
                }
            }
        }
    }
}