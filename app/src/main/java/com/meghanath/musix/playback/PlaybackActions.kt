package com.meghanath.musix.playback

/**
 * Plays a list of songs starting at startIndex.
 * shuffle = true  -> shuffle on
 * shuffle = false -> shuffle off
 * shuffle = null  -> leave shuffle as the user had it
 */
fun PlayerViewModel.playList(queue: List<QueueItem>, startIndex: Int, shuffle: Boolean? = null) {
    val wasShuffled = state.value.shuffleEnabled
    playQueue(queue, startIndex)
    if (shuffle != null && shuffle != wasShuffled) toggleShuffle()
}