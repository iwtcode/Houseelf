package com.example.houseelf.capture

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.houseelf.R

/** Плитка в шторке быстрых настроек: «скопировал → потянул шторку → нажал». */
class ClipboardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(R.string.tile_subtitle)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_clipboard)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = CaptureActivity.clipboardIntent(this)
        // На заблокированном телефоне сначала просим разблокировать: так принято для плиток
        if (isLocked) unlockAndRun { launch(intent) } else launch(intent)
    }

    private fun launch(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
