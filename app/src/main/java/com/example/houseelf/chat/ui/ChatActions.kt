package com.example.houseelf.chat.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.houseelf.calendar.CALENDAR_PERMISSIONS
import com.example.houseelf.calendar.calendarInsertIntent
import com.example.houseelf.calendar.calendarViewIntent
import com.example.houseelf.chat.ChatViewModel
import com.example.houseelf.today.TaskChange
import com.example.houseelf.util.copyText
import com.example.houseelf.util.isActivityStarted

/**
 * Всё, что человек делает в поле ввода и шапке чата. Один набор на главный чат и мини-чат:
 * оба работают с одним [ChatViewModel], а значит, это один и тот же чат.
 */
class ChatActions(
    val onSend: (String) -> Unit,
    val onNewChat: () -> Unit,
    val onSetThinking: (Boolean) -> Unit,
    val onPickImages: () -> Unit,
    val onPickAudio: () -> Unit,
    val onStartRecording: () -> Unit,
    val onStopRecording: () -> Unit,
    val onCancelRecording: () -> Unit,
    val onRemoveAttachment: (Long) -> Unit,
)

/**
 * Собирает [ChatActions] вместе с системными окнами, которые они открывают (галерея, файлы, микрофон).
 * [onSystemUi] сообщает хозяину экрана, что системное окно открылось (true) или закрылось (false):
 * пока оно на виду, мини-чат не должен считать, что человек ушёл.
 */
@Composable
fun rememberChatActions(vm: ChatViewModel, onSystemUi: (Boolean) -> Unit = {}): ChatActions {
    val context = LocalContext.current

    // Выбор изображений из галереи (системный Photo Picker, разрешения не нужны)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ChatViewModel.MAX_IMAGES),
    ) { uris ->
        onSystemUi(false)
        vm.attachImages(uris)
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onSystemUi(false)
        if (uri != null) vm.attachAudio(uri)
    }

    // Голосовой ввод: сначала разрешение на микрофон, потом запись
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onSystemUi(false)
        if (granted) vm.startRecording()
        else vm.showNotice("Без доступа к микрофону голосовой ввод не работает")
    }

    return ChatActions(
        onSend = vm::send,
        onNewChat = vm::newChat,
        onSetThinking = vm::setThinking,
        onPickImages = {
            onSystemUi(true)
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onPickAudio = {
            onSystemUi(true)
            audioPicker.launch(arrayOf("audio/*"))
        },
        onStartRecording = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                vm.startRecording()
            } else {
                onSystemUi(true)
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onStopRecording = { vm.stopRecording(discard = false) },
        onCancelRecording = { vm.stopRecording(discard = true) },
        onRemoveAttachment = vm::removeAttachment,
    )
}

/** Действия над сообщениями: копировать, удалить, повторить, прервать. */
@Composable
fun rememberMessageActions(vm: ChatViewModel): MessageActions {
    val context = LocalContext.current
    return remember(vm, context) {
        MessageActions(
            onCopy = { message ->
                context.copyText(message.text)
                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
            },
            onDelete = vm::deleteMessage,
            onRepeat = vm::repeatLast,
            onStop = vm::stopGenerating,
        )
    }
}

/** Действия с карточками событий. Изменение и удаление открывают окно редактора, им занимается хозяин экрана. */
@Composable
fun rememberEventActions(
    vm: ChatViewModel,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
): EventActions {
    val context = LocalContext.current
    val open = { intent: Intent ->
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            vm.showNotice("Не найдено приложение календаря")
        }
    }
    return EventActions(
        onConfirm = vm::confirmEvent,
        onDismiss = vm::dismissEvent,
        onOpenForm = { event -> open(calendarInsertIntent(event)) },
        onView = { id -> open(calendarViewIntent(id)) },
        onEdit = onEdit,
        onDeleteEvent = onDelete,
    )
}

/**
 * Календарь: модель сама вносит события, поэтому разрешение запрашиваем в момент первого обращения.
 * Запрос показывает окно, которое сейчас на виду; второе (например, мини-чат) его не дублирует.
 */
@Composable
fun CalendarPermissionEffect(vm: ChatViewModel, needed: Boolean, onSystemUi: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onSystemUi(false)
        vm.onCalendarPermissionResult()
    }
    LaunchedEffect(needed) {
        if (needed && context.isActivityStarted() && vm.calendarPermissionRequested()) {
            onSystemUi(true)
            launcher.launch(CALENDAR_PERMISSIONS)
        }
    }
}

/** Итог ручного изменения дела в календаре: чат должен узнать о нём. */
fun ChatViewModel.onTaskChanged(change: TaskChange) {
    when (change) {
        is TaskChange.Updated -> onCalendarEventEdited(change.event)
        is TaskChange.Deleted -> onCalendarEventDeleted(change.eventId)
        is TaskChange.Created -> Unit
    }
}
