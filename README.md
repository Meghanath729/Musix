# Musix

Musix is a personal offline music player for Android. It organizes songs by artist, keeps original and live versions separate, plays audio or video, and keeps playing with the screen off. It has no ads and does not need an account.

The app is built for personal use. All songs live in the app's own private storage and are not visible to other apps.

## Features

- Artist library with search, and add or delete for whole artists
- Songs per artist, each saved as Original or Live, with audio only or with video
- Add songs from a file on the device or from a pasted link (audio only, or 720p video)
- Background playback with a notification, a mini player and a full player screen
- Shuffle, repeat, previous and next
- Favorites, kept separately for each version of a song
- Playlists built by manually adding saved songs
- Storage screen showing app size, per-artist usage, the largest songs and a low-space warning
- Backup and restore of the whole library as a single zip file, plus export of a single song
- Dark theme only

## Tech stack

- Kotlin and Jetpack Compose (Material 3)
- Room for the local database
- Media3 (ExoPlayer and MediaSession) for playback
- youtubedl-android with FFmpeg for downloads
- Gradle with Kotlin DSL and a version catalog

## Requirements

- Android 13 (API 33) or newer
- Android Studio, to build and run the project

## Build and run

1. Clone the repository and open the root folder in Android Studio.
2. Wait for the Gradle sync to finish.
3. Select an emulator or a connected phone and click Run.

To build a signed release APK you need your own keystore. The signing key is not part of this repository, and the release build targets arm64 devices only.

## Project structure

All code is under `app/src/main/java/com/meghanath/musix/`:

| Folder      | Contents |
|    ---      |    ---   |
| `data`      | Room entities, DAOs, repositories and storage helpers |
| `ui`        | ViewModels, mini player and shared UI pieces |
| `ui/screens`| Home, artist, add song, player, playlists, favorites, storage and backup screens |
| `ui/theme`  | Colors, typography and theme |
| `playback`  | Playback service, player ViewModel and queue helpers |
| `download`  | Downloader engine and download service |
| `backup`    | Backup, restore and single-song export |

## Notes

The download feature is meant for content you have the right to save. Please follow the terms of service of the sites you download from and the copyright rules that apply to you.

## Author

Meghanath K S
