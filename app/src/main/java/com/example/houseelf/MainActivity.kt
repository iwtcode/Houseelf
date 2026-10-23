package com.example.houseelf

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.houseelf.ui.theme.Duo
import com.example.houseelf.ui.theme.HouseelfTheme

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тема приложения всегда светлая, поэтому и значки в системных панелях всегда тёмные
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            HouseelfTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Duo.Bg) { innerPadding ->
                    HouseelfApp(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .imePadding(),
                        vm = chatViewModel,
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // В фоне Android глушит микрофон: сохраняем то, что успели записать.
        // При повороте экрана запись не прерываем.
        if (!isChangingConfigurations) chatViewModel.stopRecording(discard = false)
    }
}

@Composable
fun HouseelfApp(modifier: Modifier = Modifier, vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    // «Сегодня» — домашний экран: открывается вместо чата, пока человек сам не зашёл в «Домовой»
    var showToday by rememberSaveable { mutableStateOf(true) }
    // Меняется, когда пользователь ответил на запрос календаря на экране «Сегодня»
    var calendarTick by remember { mutableIntStateOf(0) }

    // Системный выбор файла. Тип */*: у .litertlm нет собственного MIME-типа.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    // Разрешение на уведомления (Android 13+): без него не будет уведомления о ходе скачивания.
    // Отказ не мешает скачиванию, поэтому в любом случае продолжаем.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.requestDownload()
    }
    val onDownload = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.requestDownload()
        }
    }

    // Выбор изображений из галереи (системный Photo Picker, разрешения не нужны)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ChatViewModel.MAX_IMAGES)
    ) { uris -> vm.attachImages(uris) }

    // Выбор аудиофайла
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachAudio(uri)
    }

    // Голосовой ввод: сначала разрешение на микрофон, потом запись
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
        else vm.showNotice("Без доступа к микрофону голосовой ввод не работает")
    }
    val onMic = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Календарь: модель сама вносит события, поэтому разрешение запрашиваем в момент первого обращения
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onCalendarPermissionResult()
    }
    LaunchedEffect(state.needCalendarPermission) {
        if (state.needCalendarPermission) {
            vm.calendarPermissionRequested()
            calendarPermission.launch(CALENDAR_PERMISSIONS)
        }
    }
    val todayCalendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        calendarTick++
    }
    val openIntent = { intent: Intent ->
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            vm.showNotice("Не найдено приложение календаря")
        }
    }
    val eventActions = EventActions(
        onConfirm = vm::confirmEvent,
        onDismiss = vm::dismissEvent,
        onOpenForm = { ev -> openIntent(calendarInsertIntent(ev)) },
        onView = { id -> openIntent(calendarViewIntent(id)) },
    )

    // Системная кнопка «Назад» в списке моделей возвращает в чат, а не закрывает приложение
    BackHandler(enabled = state.phase == Phase.Library && state.activeModel != null) {
        vm.backToChat()
    }
    // Из чата «Назад» ведёт на «Сегодня»
    BackHandler(enabled = state.phase == Phase.Chat && !showToday) {
        showToday = true
    }

    when (state.phase) {
        Phase.Checking, Phase.Loading -> LoadingScreen(
            text = if (state.phase == Phase.Loading) {
                "Открываю «${modelDisplayName(state.loadingModel)}»…\nПервый запуск может занять до минуты"
            } else "",
            modifier = modifier,
        )
        Phase.Library -> LibraryScreen(
            models = state.models,
            activeModel = state.activeModel,
            error = state.error,
            freeRamBytes = state.freeRamBytes,
            totalRamBytes = state.totalRamBytes,
            autoLoad = state.autoLoad,
            confirmActions = state.confirmActions,
            chatHasMessages = state.messages.isNotEmpty(),
            onSelect = { fileName ->
                showToday = false
                vm.selectModel(fileName)
            },
            onUnload = vm::unloadModel,
            onAutoLoadChange = vm::setAutoLoad,
            onConfirmActionsChange = vm::setConfirmActions,
            onDelete = vm::deleteModel,
            onDownload = onDownload,
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            onBack = vm::backToChat,
            modifier = modifier,
        )
        Phase.Downloading, Phase.Importing -> ProgressScreen(
            title = if (state.phase == Phase.Downloading) "Скачиваю модель" else "Загружаю модель",
            progress = state.progress,
            text = state.progressText,
            hint = if (state.phase == Phase.Downloading) "Скачивание идёт в фоне — приложение можно свернуть." else null,
            onCancel = vm::cancelSetup,
            modifier = modifier,
            extraActionText = if (state.phase == Phase.Downloading && state.waitingWifi) "Скачать по мобильной сети" else null,
            onExtraAction = vm::continueOnMobile,
        )
        Phase.Error -> ErrorScreen(
            error = state.error.orEmpty(),
            onRetry = vm::retryLoad,
            onOpenLibrary = vm::openLibrary,
            modifier = modifier,
        )
        Phase.Chat -> if (showToday) {
            TodayScreen(
                calendarGranted = remember(calendarTick) { hasCalendarPermission(context) },
                // Событие, добавленное из чата, сразу появляется на пути дня
                refreshKey = calendarTick + state.messages.sumOf { m -> m.events.count { it.status == EventStatus.Added } },
                onRequestCalendar = { todayCalendarPermission.launch(CALENDAR_PERMISSIONS) },
                onOpenChat = { showToday = false },
                onOpenLibrary = vm::openLibrary,
                onOpenEvent = { id -> openIntent(calendarViewIntent(id)) },
                modifier = modifier,
            )
        } else {
            ChatScreen(
                state = state,
                onSend = vm::send,
                onNewChat = vm::newChat,
                onOpenLibrary = vm::openLibrary,
                onBack = { showToday = true },
                onPickImages = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                onStartRecording = onMic,
                onStopRecording = { vm.stopRecording(discard = false) },
                onCancelRecording = { vm.stopRecording(discard = true) },
                onRemoveAttachment = vm::removeAttachment,
                eventActions = eventActions,
                modifier = modifier,
            )
        }
    }

    if (state.askNetworkChoice) {
        NetworkChoiceDialog(
            onWaitWifi = { vm.startDownload(wifiOnly = true) },
            onContinue = { vm.startDownload(wifiOnly = false) },
            onDismiss = vm::dismissNetworkChoice,
        )
    }
}
