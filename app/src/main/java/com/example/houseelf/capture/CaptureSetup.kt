package com.example.houseelf.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.houseelf.R

/** Настройка входов Домового.Чата, которые не сводятся к самой активити: ярлык прямой отправки в «Поделиться». */
object CaptureSetup {
    /** Категория из res/xml/share_targets.xml: по ней система связывает ярлык с целью «Поделиться». */
    const val SHARE_CATEGORY = "com.example.houseelf.category.SHARE_TARGET"

    /** Ярлык прямой отправки: «Домовой.Чат» попадает в верхний ряд меню «Поделиться». */
    fun publishShareShortcut(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, "share_domovoy")
            .setShortLabel(context.getString(R.string.chat_label))
            .setLongLived(true)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setCategories(setOf(SHARE_CATEGORY))
            // Обязательная заглушка: при отправке система открывает цель из share-target, а не этот intent
            .setIntent(Intent(Intent.ACTION_DEFAULT))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}
