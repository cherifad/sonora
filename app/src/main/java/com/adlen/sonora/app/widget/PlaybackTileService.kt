// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.widget

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.adlen.sonora.app.di.SongPlayer
import com.adlen.sonora.engine.R

/** Quick Settings tile: one tap plays or pauses the current queue. */
class PlaybackTileService : TileService() {

    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        if (SongPlayer.isPlaying()) SongPlayer.pause(by = "tile") else SongPlayer.play(by = "tile")
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val playing = SongPlayer.isPlaying()
        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(
            this,
            if (playing) com.adlen.sonora.host.android.R.drawable.msp_ic_pause
            else com.adlen.sonora.host.android.R.drawable.msp_ic_play,
        )
        tile.updateTile()
    }
}
