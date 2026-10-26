package com.example.houseelf.event

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.houseelf.calendar.CALENDAR_PERMISSIONS
import com.example.houseelf.calendar.hasCalendarPermission
import com.example.houseelf.chat.ChatViewModel
import com.example.houseelf.util.normalizeCapturedText
import com.example.houseelf.util.readClipboardText

/**
 * Вход в Домового.Событие. Окна у него нет: активити берёт текст, при необходимости просит доступ
 * к календарю, передаёт текст [EventService] и тут же закрывается — пользователь остаётся в своём приложении.
 *
 * Текст приходит тремя путями, как и в чате:
 *  1. пункт «Домовой.Событие» в меню выделения текста — ACTION_PROCESS_TEXT;
 *  2. «Поделиться» — ACTION_SEND;
 *  3. буфер обмена — плитка в шторке, ярлык на иконке и виджет — [ACTION_EVENT_FROM_CLIPBOARD].
 */
class EventCaptureActivity : ComponentActivity() {

    private val notifier by lazy { EventNotifier(this) }

    /** Читать буфер можно только с фокусом окна, поэтому чтение ждёт первого фокуса. */
    private var clipboardPending = false

    /** Текст, который ждёт ответа на запрос доступа к календарю. */
    private var textAwaitingPermission: String? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermissionResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clipboardPending) {
            clipboardPending = false
            submit(readClipboardText())
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> submit(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
            Intent.ACTION_SEND -> submit(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
            ACTION_EVENT_FROM_CLIPBOARD -> if (hasWindowFocus()) submit(readClipboardText()) else clipboardPending = true
            else -> finish()
        }
    }

    private fun submit(raw: CharSequence?) {
        val text = raw?.let { normalizeCapturedText(it, ChatViewModel.MAX_CAPTURE_CHARS) }?.text
        when {
            text == null -> finishWith("Нет текста. Сначала скопируй его")
            hasCalendarPermission(this) -> startProcessing(text)
            else -> {
                textAwaitingPermission = text
                permissionLauncher.launch(CALENDAR_PERMISSIONS)
            }
        }
    }

    private fun onPermissionResult() {
        val text = textAwaitingPermission ?: return finish()
        textAwaitingPermission = null
        if (hasCalendarPermission(this)) startProcessing(text) else finishWith(QuickEventRunner.NO_CALENDAR_ACCESS)
    }

    private fun startProcessing(text: String) {
        EventService.start(this, text)
        finish()
    }

    private fun finishWith(message: String) {
        notifier.showMessage(message)
        finish()
    }

    companion object {
        /** Взять текст из буфера обмена. Плитка, ярлык и виджет запускают активити с этим действием. */
        const val ACTION_EVENT_FROM_CLIPBOARD = "com.example.houseelf.action.EVENT_FROM_CLIPBOARD"

        fun clipboardIntent(context: Context): Intent =
            Intent(context, EventCaptureActivity::class.java)
                .setAction(ACTION_EVENT_FROM_CLIPBOARD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
