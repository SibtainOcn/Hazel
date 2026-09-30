package com.hazel.android.ui.components.player

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.hazel.android.R

/**
 * Where the picture is drawn, set up in `res/layout/player_surface.xml`. Media3's own
 * controls are off; the app draws its own over it.
 * Pass a null [player] to detach, so only one surface shows a player at a time.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.player_surface, null, false) as PlayerView
        },
        update = { view -> if (view.player !== player) view.player = player },
        onRelease = { view -> view.player = null },
        modifier = modifier
    )
}
