package com.example.houseelf.capture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.example.houseelf.MainActivity
import com.example.houseelf.calendar.CALENDAR_PERMISSIONS
import com.example.houseelf.calendar.calendarInsertIntent
import com.example.houseelf.calendar.calendarViewIntent
import com.example.houseelf.chat.ChatViewModel
import com.example.houseelf.chat.ui.EventActions
import com.example.houseelf.media.TextOrigin
import com.example.houseelf.sharedChatViewModel
import com.example.houseelf.ui.theme.HouseelfTheme

/**
 * Мини-чат поверх любого приложения. Прозрачная активити: под ней остаётся то приложение,
 * из которого пришёл текст, а пользователь не уходит из него.
 *
 * Сюда ведут три пути, текст везде прикрепляется к сообщению как вложение:
 *  1. пункт «Домовой» в меню выделения текста — ACTION_PROCESS_TEXT;
 *  2. «Поделиться» и ярлык прямой отправки — ACTION_SEND;
 *  3. буфер обмена — плитка в шторке, ярлык на иконке и виджет — [ACTION_CAPTURE_CLIPBOARD].
 */
class CaptureActivity : ComponentActivity() {

    private val vm: ChatViewModel by lazy { sharedChatViewModel(application) }

    /** С какого сообщения начинается этот мини-чат: предыдущая переписка в нём не показывается. */
    private var startId by mutableLongStateOf(0L)

    /** В буфере обмена нет текста — подсказываем это в интерфейсе. */
    private var clipboardMissing by mutableStateOf(false)

    /** Читать буфер можно только с фокусом окна, поэтому чтение ждёт первого фокуса. */
    private var clipboardPending = false

    /** Системное окно запроса разрешения на экране: уходить из активити в этот момент нельзя. */
    private var permissionPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Затемнение рисуем сами, поэтому значки статус-строки светлые, а нижняя панель — под белый лист
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        if (savedInstanceState == null) {
            startId = vm.peekNextMessageId()
            handleIntent(intent)
        } else {
            // Поворот экрана: текст уже лежит во ViewModel, повторно его брать не нужно
            startId = savedInstanceState.getLong(KEY_START_ID, vm.peekNextMessageId())
        }

        setContent {
            HouseelfTheme {
                val state by vm.state.collectAsState()

                // Календарь: модель может попросить доступ прямо из мини-чата
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    permissionPending = false
                    vm.onCalendarPermissionResult()
                }
                LaunchedEffect(state.needCalendarPermission) {
                    if (state.needCalendarPermission && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        if (vm.calendarPermissionRequested()) {
                            permissionPending = true
                            permissionLauncher.launch(CALENDAR_PERMISSIONS)
                        }
                    }
                }

                val eventActions = remember {
                    EventActions(
                        onConfirm = vm::confirmEvent,
                        onDismiss = vm::dismissEvent,
                        onOpenForm = { ev -> openExternal(calendarInsertIntent(ev)) },
                        onView = { id -> openExternal(calendarViewIntent(id)) },
                    )
                }

                CaptureOverlay(
                    state = state,
                    startId = startId,
                    clipboardMissing = clipboardMissing,
                    onSend = vm::send,
                    onRemoveAttachment = vm::removeAttachment,
                    onPaste = { readClipboard() },
                    onOpenApp = { openApp() },
                    onClose = { finish() },
                    onLoadModel = vm::loadPreferredModel,
                    onRetry = vm::retryLoad,
                    eventActions = eventActions,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Новый захват начинает новый мини-чат, но не обрывает ответ, который ещё пишется
        if (!vm.state.value.generating) startId = vm.peekNextMessageId()
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_START_ID, startId)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clipboardPending) {
            clipboardPending = false
            readClipboard()
        }
    }

    override fun onStop() {
        super.onStop()
        // Мини-чат живёт, пока на виду. Ушли на главный экран или выключили экран — закрываем.
        // Ответ, который ещё пишется, продолжается во ViewModel и появится в главном чате.
        if (!isChangingConfigurations && !isFinishing && !permissionPending) finish()
    }

    // ───────── Приём данных ─────────

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            // 1. Меню выделения. Текст не меняем, поэтому результат не возвращаем.
            Intent.ACTION_PROCESS_TEXT ->
                capture(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT), TextOrigin.Selection)
            // 2. «Поделиться» и ярлык прямой отправки
            Intent.ACTION_SEND ->
                capture(intent.getCharSequenceExtra(Intent.EXTRA_TEXT), TextOrigin.Share)
            // 3. Буфер обмена
            ACTION_CAPTURE_CLIPBOARD ->
                if (hasWindowFocus()) readClipboard() else clipboardPending = true
        }
    }

    private fun capture(text: CharSequence?, origin: TextOrigin) {
        clipboardMissing = false
        if (text.isNullOrBlank()) return
        vm.attachText(text, sourceAppLabel(), origin)
    }

    private fun readClipboard() {
        val text = CaptureSetup.readClipboardText(this)
        if (text == null) {
            clipboardMissing = true
        } else {
            clipboardMissing = false
            vm.attachText(text, null, TextOrigin.Clipboard)
        }
    }

    /** Название приложения, из которого пришёл текст («Telegram»). Если система его не сообщила — null. */
    private fun sourceAppLabel(): String? {
        val ref = referrer ?: return null
        if (ref.scheme != "android-app") return null
        val pkg = ref.host ?: return null
        if (pkg == packageName || pkg == "android") return null
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Throwable) {
            null // Android 11+ не показывает чужие приложения без особого разрешения
        }
    }

    // ───────── Переходы ─────────

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_CHAT, true),
        )
        finish()
    }

    private fun openExternal(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            vm.showNotice("Не найдено приложение календаря")
        }
    }

    companion object {
        /** Третий способ: взять текст из буфера обмена. Плитка, ярлык и виджет запускают активити с этим действием. */
        const val ACTION_CAPTURE_CLIPBOARD = "com.example.houseelf.action.CAPTURE_CLIPBOARD"

        private const val KEY_START_ID = "start_id"

        fun clipboardIntent(context: Context): Intent =
            Intent(context, CaptureActivity::class.java)
                .setAction(ACTION_CAPTURE_CLIPBOARD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
