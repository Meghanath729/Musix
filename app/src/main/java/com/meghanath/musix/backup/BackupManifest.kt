package com.meghanath.musix.backup

import com.meghanath.musix.data.Artist
import com.meghanath.musix.data.Playlist
import com.meghanath.musix.data.PlaylistSong
import com.meghanath.musix.data.Song
import org.json.JSONArray
import org.json.JSONObject

/** Everything in the library at one moment, as plain lists. */
data class LibrarySnapshot(
    val artists: List<Artist>,
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val links: List<PlaylistSong>
)

object BackupManifest {

    const val FORMAT_VERSION = 1

    fun toJson(snapshot: LibrarySnapshot): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("createdAt", System.currentTimeMillis())

        val artists = JSONArray()
        snapshot.artists.forEach { a ->
            artists.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("addedAt", a.addedAt)
            )
        }
        root.put("artists", artists)

        val songs = JSONArray()
        snapshot.songs.forEach { s ->
            songs.put(
                JSONObject()
                    .put("id", s.id)
                    .put("artistId", s.artistId)
                    .put("title", s.title)
                    .put("isLive", s.isLive)
                    .put("hasVideo", s.hasVideo)
                    .put("durationMs", s.durationMs)
                    .put("fileName", s.fileName)
                    .put("sizeBytes", s.sizeBytes)
                    .put("addedAt", s.addedAt)
                    .put("isFavorite", s.isFavorite)
            )
        }
        root.put("songs", songs)

        val playlists = JSONArray()
        snapshot.playlists.forEach { p ->
            playlists.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("createdAt", p.createdAt)
            )
        }
        root.put("playlists", playlists)

        val links = JSONArray()
        snapshot.links.forEach { l ->
            links.put(
                JSONObject()
                    .put("playlistId", l.playlistId)
                    .put("songId", l.songId)
                    .put("addedAt", l.addedAt)
                    .put("position", l.position)
            )
        }
        root.put("playlistSongs", links)

        return root.toString(2)
    }

    /**
     * Reads a manifest. Throws if anything is missing or malformed, so the caller
     * can stop BEFORE touching the existing library.
     */
    fun fromJson(text: String): LibrarySnapshot {
        val root = JSONObject(text)
        val version = root.optInt("formatVersion", 0)
        require(version in 1..FORMAT_VERSION) { "This backup was made by a newer or unknown version." }

        val artists = root.getJSONArray("artists").objects().map { o ->
            Artist(
                id = o.getLong("id"),
                name = o.getString("name"),
                addedAt = o.getLong("addedAt")
            )
        }

        val songs = root.getJSONArray("songs").objects().map { o ->
            Song(
                id = o.getLong("id"),
                artistId = o.getLong("artistId"),
                title = o.getString("title"),
                isLive = o.getBoolean("isLive"),
                hasVideo = o.getBoolean("hasVideo"),
                durationMs = o.getLong("durationMs"),
                fileName = o.getString("fileName"),
                sizeBytes = o.getLong("sizeBytes"),
                addedAt = o.getLong("addedAt"),
                isFavorite = o.getBoolean("isFavorite")
            )
        }

        val playlists = root.getJSONArray("playlists").objects().map { o ->
            Playlist(
                id = o.getLong("id"),
                name = o.getString("name"),
                createdAt = o.getLong("createdAt")
            )
        }

        val rawLinks = root.getJSONArray("playlistSongs").objects().map { o ->
            PlaylistSong(
                playlistId = o.getLong("playlistId"),
                songId = o.getLong("songId"),
                addedAt = o.getLong("addedAt"),
                position = o.optLong("position", -1L)
            )
        }

        // Backups made before reordering existed have no position. For those, number the
        // songs in each playlist in the order they were added, which is how they looked before.
        val links = if (rawLinks.any { it.position < 0 }) {
            rawLinks.groupBy { it.playlistId }.values.flatMap { group ->
                group.sortedWith(compareBy({ it.addedAt }, { it.songId }))
                    .mapIndexed { index, link -> link.copy(position = index.toLong()) }
            }
        } else {
            rawLinks
        }

        return LibrarySnapshot(artists, songs, playlists, links)
    }
}

private fun JSONArray.objects(): List<JSONObject> =
    List(length()) { index -> getJSONObject(index) }