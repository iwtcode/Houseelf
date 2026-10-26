package com.example.houseelf.event

import android.content.Intent
import com.example.houseelf.R
import com.example.houseelf.quick.QuickTileService

/** Плитка «Домовой.Событие»: вносит в календарь события из текста в буфере обмена. */
class EventTileService : QuickTileService() {
    override val labelRes = R.string.event_label
    override val subtitleRes = R.string.event_tile_subtitle
    override val iconRes = R.drawable.ic_event

    override fun launchIntent(): Intent = EventCaptureActivity.clipboardIntent(this)
}
