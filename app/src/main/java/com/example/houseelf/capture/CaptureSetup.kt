package com.example.houseelf.capture

import android.app.Activity
import android.app.StatusBarManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.houseelf.R

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
