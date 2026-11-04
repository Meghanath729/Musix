package com.meghanath.musix.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(context: Context) {
    private val db = MusixDatabase.get(context)
    private val playlistDao = db.playlistDao()
    private val songDao = db.songDao()

    fun observePlaylists(): Flow<List<PlaylistWithCount>> = playlistDao.observePlaylistsWithCount()
    fun observePlaylist(id: Long): Flow<Playlist?> = playlistDao.observePlaylist(id)
    fun observeSongs(id: Long): Flow<List<SongWithArtist>> = playlistDao.observeSongsWithArtist(id)
    fun observeAllSongs(): Flow<List<SongWithArtist>> = songDao.observeAllWithArtist()

    suspend fun create(name: String): Long =
        playlistDao.insertPlaylist(Playlist(name = name.trim()))

    suspend fun rename(id: Long, name: String) =
        playlistDao.renamePlaylist(id, name.trim())

    suspend fun delete(id: Long) = playlistDao.deletePlaylist(id)

    // true = added, false = already in this playlist. New songs go to the end.
    suspend fun addSong(playlistId: Long, songId: Long): Boolean {
        val next = (playlistDao.maxPosition(playlistId) ?: -1L) + 1L
        return playlistDao.addSong(
            PlaylistSong(playlistId = playlistId, songId = songId, position = next)
        ) != -1L
    }

    suspend fun removeSong(playlistId: Long, songId: Long) =
        playlistDao.removeSong(playlistId, songId)

    // Swaps the positions of two songs in a playlist (used by move up / move down)
    suspend fun swap(playlistId: Long, songA: Long, songB: Long) {
        val positionA = playlistDao.getPosition(playlistId, songA) ?: return
        val positionB = playlistDao.getPosition(playlistId, songB) ?: return
        playlistDao.swapPositions(playlistId, songA, songB, positionA, positionB)
    }
}