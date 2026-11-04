package com.meghanath.musix.data

import kotlinx.coroutines.flow.Flow

enum class AddResult { ADDED, EMPTY, DUPLICATE }
enum class RenameResult { RENAMED, EMPTY, DUPLICATE }

class ArtistRepository(private val dao: ArtistDao) {

    fun search(query: String): Flow<List<Artist>> = dao.search(query)

    suspend fun add(name: String): AddResult {
        val clean = name.trim()
        if (clean.isEmpty()) return AddResult.EMPTY
        val newId = dao.insert(Artist(name = clean))
        return if (newId == -1L) AddResult.DUPLICATE else AddResult.ADDED
    }

    suspend fun rename(artist: Artist, newName: String): RenameResult {
        val clean = newName.trim()
        if (clean.isEmpty()) return RenameResult.EMPTY
        val changed = dao.rename(artist.id, clean)
        return if (changed == 0) RenameResult.DUPLICATE else RenameResult.RENAMED
    }

    suspend fun delete(artist: Artist) = dao.delete(artist)
}