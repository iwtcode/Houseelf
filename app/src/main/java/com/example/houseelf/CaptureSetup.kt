package com.example.houseelf

import android.app.Activity
import android.app.PendingIntent
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Всё, что нужно для способов захвата помимо самой активити:
 *  - ярлык прямой отправки в «Поделиться»;
 *  - предложение добавить плитку в шторку;
 *  - чтение буфера обмена.
 */
object CaptureSetup {
    /** Категория из res/xml/share_targets.xml: по ней система связывает ярлык с целью «Поделиться». */
    const val SHARE_CATEGORY = "com.example.houseelf.category.SHARE_TARGET"

    private const val PREFS = "capture"
    private const val KEY_TILE_OFFERED = "tile_offered"

    /** Ярлык прямой отправки: «Домовой» попадает в верхний ряд меню «Поделиться». */
    fun publishShareShortcut(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, "share_domovoy")
            .setShortLabel(context.getString(R.string.share_target_label))
            .setLongLived(true)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setCategories(setOf(SHARE_CATEGORY))
            // Обязательная заглушка: при отправке система открывает цель из share-target, а не этот intent
            .setIntent(Intent(Intent.ACTION_DEFAULT))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    /**
     * Один раз предлагает добавить плитку «Домовой» в шторку быстрых настроек (Android 13+).
     * На старых версиях плитку пользователь добавляет сам — она есть в списке плиток.
     */
    fun offerQuickTile(activity: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_TILE_OFFERED, false)) return
        prefs.edit().putBoolean(KEY_TILE_OFFERED, true).apply()
        runCatching {
            val bar = activity.getSystemService(StatusBarManager::class.java) ?: return
            bar.requestAddTileService(
                ComponentName(activity, ClipboardTileService::class.java),
                activity.getString(R.string.tile_label),
                Icon.createWithResource(activity, R.drawable.ic_clipboard),
                activity.mainExecutor,
            ) { _: Int -> }
        }
    }

    /**
     * Текст из буфера обмена или null, если там пусто или лежит не текст.
     * Начиная с Android 10 читать буфер можно только окну, у которого есть фокус ввода.
     */
    fun readClipboardText(context: Context): CharSequence? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0 || !clip.description.hasMimeType("text/*")) return null
        return clip.getItemAt(0).coerceToText(context)?.takeIf { it.isNotBlank() }
    }
}

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

/** Виджет на рабочий стол: одно нажатие — текст из буфера в мини-чате. */
class ClipboardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = PendingIntent.getActivity(
            context, 0, CaptureActivity.clipboardIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_capture)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
