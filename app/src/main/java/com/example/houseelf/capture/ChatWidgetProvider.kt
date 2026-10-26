package com.example.houseelf.capture

import android.content.Context
import android.content.Intent
import com.example.houseelf.R
import com.example.houseelf.quick.QuickWidgetProvider

/** Виджет «Домовой.Чат»: одно нажатие — текст из буфера в мини-чате. */
class ChatWidgetProvider : QuickWidgetProvider() {
    override val iconRes = R.drawable.ic_clipboard
    override val textRes = R.string.chat_widget_text

    override fun launchIntent(context: Context): Intent = CaptureActivity.clipboardIntent(context)
}
