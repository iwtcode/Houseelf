package com.example.houseelf.event

import android.content.Context
import android.content.Intent
import com.example.houseelf.R
import com.example.houseelf.quick.QuickWidgetProvider

/** Виджет «Домовой.Событие»: одно нажатие — события из буфера обмена попадают в календарь. */
class EventWidgetProvider : QuickWidgetProvider() {
    override val iconRes = R.drawable.ic_event
    override val textRes = R.string.event_widget_text

    override fun launchIntent(context: Context): Intent = EventCaptureActivity.clipboardIntent(context)
}
