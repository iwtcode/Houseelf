package com.example.houseelf.quick

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import com.example.houseelf.R
import com.example.houseelf.capture.ChatTileService
import com.example.houseelf.event.EventTileService

/**
 * Предлагает добавить плитки «Домовой.Чат» и «Домовой.Событие» в шторку быстрых настроек (Android 13+).
 * Каждую плитку предлагаем один раз, по очереди: системное окно запроса показывается по одному.
 * На старых версиях плитку пользователь добавляет сам — она есть в списке плиток.
 */
object QuickTiles {

    private class Offer(val prefKey: String, val service: Class<*>, val labelRes: Int, val iconRes: Int)

    // Ключ чата остался прежним: у тех, кому плитку уже предлагали, она не появится снова
    private val offers = listOf(
        Offer("tile_offered", ChatTileService::class.java, R.string.chat_label, R.drawable.ic_clipboard),
        Offer("tile_offered_event", EventTileService::class.java, R.string.event_label, R.drawable.ic_event),
    )

    private const val PREFS = "capture"

    fun offerAll(activity: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        offerNext(activity, offers.iterator())
    }

    private fun offerNext(activity: Activity, remaining: Iterator<Offer>) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        while (remaining.hasNext()) {
            val offer = remaining.next()
            if (prefs.getBoolean(offer.prefKey, false)) continue
            prefs.edit().putBoolean(offer.prefKey, true).apply()
            request(activity, offer) { offerNext(activity, remaining) }
            return
        }
    }

    private fun request(activity: Activity, offer: Offer, onDone: () -> Unit) {
        runCatching {
            val bar = activity.getSystemService(StatusBarManager::class.java) ?: return
            bar.requestAddTileService(
                ComponentName(activity, offer.service),
                activity.getString(offer.labelRes),
                Icon.createWithResource(activity, offer.iconRes),
                activity.mainExecutor,
            ) { _: Int -> onDone() }
        }
    }
}
