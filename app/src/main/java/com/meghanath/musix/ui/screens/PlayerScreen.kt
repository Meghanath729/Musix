package com.meghanath.musix.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.withFrameMillis
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.meghanath.musix.R
import com.meghanath.musix.playback.rememberPlayerViewModel
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meghanath.musix.ui.FavoritesViewModel

@Composable
fun PlayerScreen(onBack: () -> Unit) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val playerViewModel = rememberPlayerViewModel()
    val state by playerViewModel.state.collectAsState()

    // Leave full screen automatically if the current song has no video
    LaunchedEffect(state.hasVideo) {
        if (!state.hasVideo) fullscreen = false
    }

    if (fullscreen && state.hasVideo) {
        FullscreenPlayer(onExit = { fullscreen = false })
    } else {
        PlayerContent(onBack = onBack, onEnterFullscreen = { fullscreen = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(onBack: () -> Unit, onEnterFullscreen: () -> Unit) {
    val playerViewModel = rememberPlayerViewModel()
    val state by playerViewModel.state.collectAsState()
    val favoritesViewModel: FavoritesViewModel = viewModel()
    val favoriteIds by favoritesViewModel.favoriteIds.collectAsState()

    var positionMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // Ask the player for its position a few times a second.
    // Waiting for a screen frame means this pauses by itself when the screen is off.
    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging) positionMs = playerViewModel.currentPositionMs()
            delay(250)
            withFrameMillis { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.hasVideo) {
                        IconButton(onClick = onEnterFullscreen) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fullscreen),
                                contentDescription = "Full screen"
                            )
                        }
                    }
                    val songId = state.currentSongId
                    if (songId != null) {
                        val isFavorite = songId in favoriteIds
                        IconButton(onClick = { favoritesViewModel.setFavorite(songId, !isFavorite) }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from favorites"
                                else "Add to favorites",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.currentSongId == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Nothing is playing", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val duration = state.durationMs.coerceAtLeast(1L)
            val shownPosition = if (isDragging) dragValue else positionMs.toFloat()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Video area (video songs) or a plain block (audio-only songs)
                if (state.hasVideo) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = false
                                player = playerViewModel.player
                            }
                        },
                        update = { view -> view.player = playerViewModel.player },
                        onRelease = { view -> view.player = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Audio only", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Title, artist, version
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = state.artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.versionLabel.isNotBlank()) {
                    Text(
                        text = state.versionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Seek bar and times
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = shownPosition.coerceIn(0f, duration.toFloat()),
                        onValueChange = {
                            isDragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            playerViewModel.seekTo(dragValue.toLong())
                            positionMs = dragValue.toLong()
                            isDragging = false
                        },
                        valueRange = 0f..duration.toFloat()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(shownPosition.toLong()), style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Skip 10 seconds back / forward
                Row(
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkipButton(forward = false) { playerViewModel.seekBy(-SKIP_MS) }
                    SkipButton(forward = true) { playerViewModel.seekBy(SKIP_MS) }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shuffle),
                            contentDescription = "Shuffle",
                            tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { playerViewModel.previous() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_previous),
                            contentDescription = "Previous",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(
                        onClick = { playerViewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = { playerViewModel.next() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = { playerViewModel.cycleRepeatMode() }) {
                        val repeatIcon = if (state.repeatMode == Player.REPEAT_MODE_ONE)
                            R.drawable.ic_repeat_one else R.drawable.ic_repeat
                        Icon(
                            painter = painterResource(repeatIcon),
                            contentDescription = "Repeat",
                            tint = if (state.repeatMode == Player.REPEAT_MODE_OFF)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun FullscreenPlayer(onExit: () -> Unit) {
    val playerViewModel = rememberPlayerViewModel()
    val state by playerViewModel.state.collectAsState()
    val context = LocalContext.current

    var positionMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var touchTick by remember { mutableIntStateOf(0) }

    var skipFeedback by remember { mutableIntStateOf(0) } // -1 back, 1 forward, 0 none
    var skipTick by remember { mutableIntStateOf(0) }

    // Hide the "+10 s" / "-10 s" label shortly after it appears
    LaunchedEffect(skipTick) {
        delay(700)
        skipFeedback = 0
    }

    // The system Back button leaves full screen instead of leaving the Player screen
    BackHandler(onBack = onExit)

    // Landscape, hidden system bars and screen kept awake while this is shown.
    // onDispose undoes all of it when full screen ends.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Same position polling as the normal Player screen
    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging) positionMs = playerViewModel.currentPositionMs()
            delay(250)
            withFrameMillis { }
        }
    }

    // Hide the controls 3 seconds after the last touch, but only while playing
    LaunchedEffect(controlsVisible, state.isPlaying, isDragging, touchTick) {
        if (controlsVisible && state.isPlaying && !isDragging) {
            delay(3000)
            controlsVisible = false
        }
    }

    val duration = state.durationMs.coerceAtLeast(1L)
    val shownPosition = if (isDragging) dragValue else positionMs.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = playerViewModel.player
                }
            },
            update = { view -> view.player = playerViewModel.player },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // Invisible layer: tap to show or hide the controls,
        // double-tap the left or right half to skip 10 seconds
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            touchTick++
                        },
                        onDoubleTap = { offset ->
                            val forward = offset.x > size.width / 2
                            playerViewModel.seekBy(if (forward) SKIP_MS else -SKIP_MS)
                            skipFeedback = if (forward) 1 else -1
                            skipTick++
                            touchTick++
                        }
                    )
                }
        )

        // Brief "+10 s" / "-10 s" label on the side that was double-tapped
        if (skipFeedback != 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = if (skipFeedback < 0) Alignment.CenterStart
                else Alignment.CenterEnd
            ) {
                Text(
                    text = if (skipFeedback < 0) "-10 s" else "+10 s",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
        }

        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .safeDrawingPadding()
            ) {
                // Top: song title and exit button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onExit) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fullscreen_exit),
                            contentDescription = "Exit full screen",
                            tint = Color.White
                        )
                    }
                }

                // Middle: previous, play/pause, next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        playerViewModel.previous()
                        touchTick++
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_previous),
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            playerViewModel.togglePlayPause()
                            touchTick++
                        },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = {
                        playerViewModel.next()
                        touchTick++
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Bottom: seek bar and times
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Slider(
                        value = shownPosition.coerceIn(0f, duration.toFloat()),
                        onValueChange = {
                            isDragging = true
                            dragValue = it
                            touchTick++
                        },
                        onValueChangeFinished = {
                            playerViewModel.seekTo(dragValue.toLong())
                            positionMs = dragValue.toLong()
                            isDragging = false
                        },
                        valueRange = 0f..duration.toFloat()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatTime(shownPosition.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Text(
                            formatTime(duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// Finds the Activity behind a Compose Context (needed to rotate the screen and hide the bars)
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val SKIP_MS = 10_000L

@Composable
private fun SkipButton(forward: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = if (forward) "Skip forward 10 seconds"
                else "Skip back 10 seconds",
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer(scaleX = if (forward) 1f else -1f)
            )
            Text("10", style = MaterialTheme.typography.labelSmall)
        }
    }
}