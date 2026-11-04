package com.meghanath.musix

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meghanath.musix.playback.rememberPlayerViewModel
import com.meghanath.musix.ui.MiniPlayer
import com.meghanath.musix.ui.screens.AddSongScreen
import com.meghanath.musix.ui.screens.ArtistScreen
import com.meghanath.musix.ui.screens.FavoritesScreen
import com.meghanath.musix.ui.screens.HomeScreen
import com.meghanath.musix.ui.screens.PlayerScreen
import com.meghanath.musix.ui.screens.PlaylistsScreen
import com.meghanath.musix.ui.screens.PlaylistDetailScreen
import com.meghanath.musix.ui.screens.StorageScreen
import com.meghanath.musix.ui.screens.BackupScreen

object Routes {
    const val HOME = "home"
    const val ARTIST = "artist/{artistId}"
    const val ADD_SONG = "add_song/{artistId}"
    const val PLAYER = "player"
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
    const val STORAGE = "storage"
    const val BACKUP = "backup"
    const val PLAYLIST = "playlist/{playlistId}"

    fun artist(id: Long) = "artist/$id"
    fun addSong(artistId: Long) = "add_song/$artistId"
    fun playlist(id: Long) = "playlist/$id"
}

@Composable
fun MusixNavHost() {
    // Also connects to the playback service as soon as the app opens
    val playerViewModel = rememberPlayerViewModel()
    val playerState by playerViewModel.state.collectAsState()

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val showMiniPlayer = playerState.currentSongId != null &&
            currentRoute != Routes.PLAYER &&
            currentRoute != Routes.ADD_SONG

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showMiniPlayer) {
                MiniPlayer(
                    playerViewModel = playerViewModel,
                    onOpenPlayer = {
                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                    onOpenPlaylists = { navController.navigate(Routes.PLAYLISTS) },
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                    onOpenStorage = { navController.navigate(Routes.STORAGE) }
                )
            }
            composable(
                route = Routes.ARTIST,
                arguments = listOf(navArgument("artistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getLong("artistId") ?: 0L
                ArtistScreen(
                    artistId = artistId,
                    onOpenAddSong = { navController.navigate(Routes.addSong(artistId)) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.ADD_SONG,
                arguments = listOf(navArgument("artistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getLong("artistId") ?: 0L
                AddSongScreen(artistId = artistId, onBack = { navController.popBackStack() })
            }
            composable(Routes.PLAYER) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) }
                )
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } }
                )
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } }
                )
            }
            composable(Routes.STORAGE) {
                StorageScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) }
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}