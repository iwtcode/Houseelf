package com.example.houseelf.quick

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Плитка в шторке быстрых настроек: «скопировал → потянул шторку → нажал».
 * Что именно запускается и как плитка выглядит, решает наследник: общая только механика запуска.
 */
abstract class QuickTileService : TileService() {

    protected abstract val labelRes: Int
    protected abstract val subtitleRes: Int
    protected abstract val iconRes: Int

    /** Что открыть по нажатию. */
    protected abstract fun launchIntent(): Intent

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(labelRes)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(subtitleRes)
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = launchIntent()
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
