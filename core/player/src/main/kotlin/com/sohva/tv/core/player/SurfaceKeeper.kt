package com.sohva.tv.core.player

import android.view.Surface
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The session's view of the player, remembering the picture's output the screen gave it through
 * its controller. When the service rebuilds its idle player for a changed buffer profile
 * (PLAY-FR-87), the output moves to the new player; without it the next playback had sound and a
 * black picture until the screen happened to make a new surface (seen on the owner's Shield).
 * Media3's session hands over a surface holder of its own (1.11), or a bare surface in its legacy mode.
 */
@OptIn(UnstableApi::class)
internal class SurfaceKeeper(player: Player, output: Output?) : ForwardingPlayer(player) {
    /** What the picture is drawn into. */
    sealed interface Output {
        fun attach(player: Player)

        class Bare(val surface: Surface) : Output {
            override fun attach(player: Player) = player.setVideoSurface(surface)
        }

        class Holder(val holder: SurfaceHolder) : Output {
            override fun attach(player: Player) = player.setVideoSurfaceHolder(holder)
        }
    }

    var output: Output? = output
        private set

    init {
        output?.attach(player)
    }

    override fun setVideoSurface(surface: Surface?) {
        output = surface?.let(Output::Bare)
        super.setVideoSurface(surface)
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        output = surfaceHolder?.let(Output::Holder)
        super.setVideoSurfaceHolder(surfaceHolder)
    }

    override fun clearVideoSurface() {
        output = null
        super.clearVideoSurface()
    }

    override fun clearVideoSurface(surface: Surface?) {
        if ((output as? Output.Bare)?.surface === surface) output = null
        super.clearVideoSurface(surface)
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        if ((output as? Output.Holder)?.holder === surfaceHolder) output = null
        super.clearVideoSurfaceHolder(surfaceHolder)
    }
}
