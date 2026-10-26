package com.example.houseelf.capture

import android.content.Intent
import com.example.houseelf.R
import com.example.houseelf.quick.QuickTileService

/** Плитка «Домовой.Чат»: открывает мини-чат с текстом из буфера обмена. */
class ChatTileService : QuickTileService() {
    override val labelRes = R.string.chat_label
    override val subtitleRes = R.string.chat_tile_subtitle
    override val iconRes = R.drawable.ic_clipboard

    override fun launchIntent(): Intent = CaptureActivity.clipboardIntent(this)
}
