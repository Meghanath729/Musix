package com.meghanath.musix.playback

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

/** Returns the one PlayerViewModel shared by every screen in the app. */
@Composable
fun rememberPlayerViewModel(): PlayerViewModel {
    val activity = findComponentActivity(LocalContext.current)
    return viewModel<PlayerViewModel>(viewModelStoreOwner = activity)
}

private fun findComponentActivity(context: Context): ComponentActivity {
    var current = context
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    error("No ComponentActivity found")
}