package com.example.houseelf.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Текст из буфера обмена или null, если там пусто или лежит не текст.
 * Начиная с Android 10 читать буфер можно только окну, у которого есть фокус ввода.
 */
fun Context.readClipboardText(): CharSequence? {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
    if (clip.itemCount == 0 || !clip.description.hasMimeType("text/*")) return null
    return clip.getItemAt(0).coerceToText(this)?.takeIf { it.isNotBlank() }
}

/** Кладёт текст в буфер обмена. */
fun Context.copyText(text: CharSequence) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    runCatching { manager.setPrimaryClip(ClipData.newPlainText("Домовой", text)) }
}
