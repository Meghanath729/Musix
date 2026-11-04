package com.meghanath.musix.data

import kotlinx.coroutines.flow.Flow

class SongRepository(private val songDao: SongDao) {

    fun observeSongs(artistId: Long, query: String): Flow<List<Song>> =
        songDao.observeSongs(artistId, query)

    suspend fun getSongsForArtist(artistId: Long): List<Song> =
        songDao.getSongsForArtist(artistId)

    suspend fun addSong(song: Song): Long = songDao.insert(song)

    suspend fun exists(artistId: Long, title: String, isLive: Boolean, hasVideo: Boolean): Boolean =
        songDao.countMatching(artistId, title, isLive, hasVideo) > 0

    suspend fun rename(song: Song, newTitle: String): RenameResult {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return RenameResult.EMPTY
        val clash = songDao.countMatchingExcluding(
            song.artistId, clean, song.isLive, song.hasVideo, song.id
        )
        if (clash > 0) return RenameResult.DUPLICATE
        songDao.rename(song.id, clean)
        return RenameResult.RENAMED
    }

    suspend fun deleteSong(song: Song) = songDao.delete(song)
}