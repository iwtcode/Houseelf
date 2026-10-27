package com.example.houseelf.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.houseelf.calendar.CALENDAR_PERMISSIONS
import com.example.houseelf.calendar.EventStatus
import com.example.houseelf.calendar.calendarInsertIntent
import com.example.houseelf.calendar.calendarViewIntent
import com.example.houseelf.calendar.hasCalendarPermission
import com.example.houseelf.chat.ChatViewModel
import com.example.houseelf.chat.Phase
import com.example.houseelf.chat.ui.ChatScreen
import com.example.houseelf.chat.ui.EventActions
import com.example.houseelf.library.LibraryScreen
import com.example.houseelf.library.NetworkChoiceDialog
import com.example.houseelf.model.CatalogModel
import com.example.houseelf.library.ProgressScreen
import com.example.houseelf.model.modelDisplayName
import com.example.houseelf.today.TodayScreen
import com.example.houseelf.ui.screens.ErrorScreen
import com.example.houseelf.ui.screens.LoadingScreen
import com.example.houseelf.ui.theme.Duo
import com.example.houseelf.util.isActivityStarted

/** Экраны, между которыми человек ходит сам. Служебные (загрузка, прогресс, ошибка) в историю не попадают. */
private enum class Dest { Today, Chat, Library }

/** Всё, что может быть на экране, — для анимации переходов. */
private enum class AppScreen { Loading, Library, Progress, Error, Today, Chat }

private val SlideScreens = setOf(AppScreen.Today, AppScreen.Chat, AppScreen.Library)

/**
 * Между экранами «Сегодня», «Домовой» и «Мозг» — сдвиг по горизонтали (вперёд справа, назад слева).
 * Служебные экраны сменяются мягким появлением: у них нет «направления».
 * [direction]: 1 — идём вперёд, -1 — возвращаемся.
 */
private fun screenTransition(from: AppScreen, to: AppScreen, direction: Int): ContentTransform {
    val forward = direction >= 0
    val transition = if (from in SlideScreens && to in SlideScreens) {
        val sign = if (forward) 1 else -1
        val move = tween<IntOffset>(durationMillis = 340, easing = FastOutSlowInEasing)
        val enter = slideInHorizontally(move) { w -> sign * w / 3 } + fadeIn(tween(260, delayMillis = 60))
        val exit = slideOutHorizontally(move) { w -> -sign * w / 3 } + fadeOut(tween(160))
        enter togetherWith exit
    } else {
        val enter = fadeIn(tween(280, delayMillis = 90)) +
            scaleIn(tween(280, delayMillis = 90), initialScale = 0.96f)
        enter togetherWith fadeOut(tween(130))
    }
    // Вперёд новый экран ложится поверх старого, назад — старый уезжает поверх возвращающегося
    transition.targetContentZIndex = if (forward) 1f else -1f
    return transition
}

@Composable
fun HouseelfApp(modifier: Modifier = Modifier, vm: ChatViewModel = viewModel(), openChatTick: Int = 0) {
    val state by vm.state.collectAsState()

    // «Сегодня» — домашний экран: открывается вместо чата, пока человек сам не зашёл в «Домовой»
    var showToday by rememberSaveable { mutableStateOf(true) }
    // Мини-чат попросил открыть чат: уходим с «Сегодня»
    LaunchedEffect(openChatTick) { if (openChatTick > 0) showToday = false }
    // Меняется, когда пользователь ответил на запрос календаря на экране «Сегодня»
    var calendarTick by remember { mutableIntStateOf(0) }

    // Какой из «своих» экранов виден сейчас (null — идёт загрузка, прогресс или ошибка)
    val destination: Dest? = when (state.phase) {
        Phase.Library -> Dest.Library
        Phase.Chat -> if (showToday) Dest.Today else Dest.Chat
        else -> null
    }
    // История: откуда пришли. Системная «Назад» и стрелки в шапках возвращают именно туда.
    val history: SnapshotStateList<Dest> = rememberSaveable(
        saver = listSaver<SnapshotStateList<Dest>, Int>(
            save = { list -> list.map { it.ordinal } },
            restore = { saved -> saved.map { Dest.values()[it] }.toMutableStateList() },
        ),
    ) { mutableStateListOf<Dest>() }
    var lastDest by rememberSaveable { mutableStateOf(-1) }
    // 1 — вперёд, -1 — назад: от этого зависит, с какой стороны выезжает экран
    var navDirection by remember { mutableIntStateOf(1) }

    LaunchedEffect(destination, state.activeModel) {
        val d = destination ?: return@LaunchedEffect
        val prev = lastDest
        when {
            // «Сегодня» — дом, а список моделей без модели — начало пути: дальше назад идти некуда
            d == Dest.Today -> history.clear()
            d == Dest.Library && state.activeModel == null -> history.clear()
            prev == -1 || prev == d.ordinal -> Unit
            else -> {
                val seen = history.lastIndexOf(d)
                if (seen >= 0) {
                    // Вернулись на уже пройденный экран (например, из списка моделей в чат): хвост истории обрезаем
                    while (history.size > seen) history.removeAt(history.lastIndex)
                } else {
                    history.add(Dest.values()[prev])
                }
            }
        }
        lastDest = d.ordinal
    }

    // Системный выбор файла. Тип */*: у .litertlm нет собственного MIME-типа.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    // Разрешение на уведомления (Android 13+): без него не будет уведомления о ходе скачивания.
    // Отказ не мешает скачиванию, поэтому в любом случае продолжаем.
    val context = LocalContext.current
    var pendingDownload by remember { mutableStateOf<CatalogModel?>(null) } // какую модель ждёт ответ на запрос разрешения
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingDownload?.let(vm::requestDownload)
        pendingDownload = null
    }
    val onDownload = { model: CatalogModel ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = model
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.requestDownload(model)
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
        // Запрос показывает окно, которое сейчас на виду; второе (например, мини-чат) его не дублирует
        if (state.needCalendarPermission && context.isActivityStarted()) {
            if (vm.calendarPermissionRequested()) calendarPermission.launch(CALENDAR_PERMISSIONS)
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

    // «Назад» возвращает на предыдущий экран, а не всегда на «Сегодня»
    val goBack: () -> Unit = back@{
        val target: Dest = history.lastOrNull() ?: when (destination) {
            Dest.Chat -> Dest.Today
            Dest.Library -> if (state.activeModel == null) return@back else if (showToday) Dest.Today else Dest.Chat
            else -> return@back
        }
        if (target == Dest.Library && state.generating) {
            vm.showNotice("Подожди, пока домовой закончит ответ")
            return@back
        }
        if (history.isNotEmpty()) history.removeAt(history.lastIndex)
        navDirection = -1
        lastDest = target.ordinal // уже «пришли» туда: возврат не должен попасть в историю как переход вперёд
        when (target) {
            Dest.Today -> {
                showToday = true
                if (destination == Dest.Library) vm.backToChat()
            }
            Dest.Chat -> {
                showToday = false
                if (destination == Dest.Library) vm.backToChat()
            }
            Dest.Library -> vm.openLibrary()
        }
    }
    BackHandler(
        enabled = destination != null && (
            history.isNotEmpty() || destination == Dest.Chat ||
                (destination == Dest.Library && state.activeModel != null)
            ),
        onBack = goBack,
    )

    val screen = when (state.phase) {
        Phase.Checking, Phase.Loading -> AppScreen.Loading
        Phase.Library -> AppScreen.Library
        Phase.Downloading, Phase.Importing -> AppScreen.Progress
        Phase.Error -> AppScreen.Error
        Phase.Chat -> if (showToday) AppScreen.Today else AppScreen.Chat
    }
    // Направление нужно только на время одного перехода
    LaunchedEffect(screen) { navDirection = 1 }

    AnimatedContent(
        targetState = screen,
        modifier = modifier.fillMaxSize().background(Duo.Bg),
        transitionSpec = { screenTransition(initialState, targetState, navDirection) },
        label = "screens",
    ) { current ->
        when (current) {
            AppScreen.Loading -> LoadingScreen(
                text = if (state.phase == Phase.Loading) {
                    "Открываю «${modelDisplayName(state.loadingModel)}»…\nПервый запуск может занять до минуты"
                } else "",
            )
            AppScreen.Library -> LibraryScreen(
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
                onBack = goBack,
            )
            AppScreen.Progress -> ProgressScreen(
                title = if (state.phase == Phase.Downloading) "Скачиваю модель" else "Загружаю модель",
                progress = state.progress,
                text = state.progressText,
                hint = if (state.phase == Phase.Downloading) "Скачивание идёт в фоне — приложение можно свернуть." else null,
                onCancel = vm::cancelSetup,
                extraActionText = if (state.phase == Phase.Downloading && state.waitingWifi) "Скачать по мобильной сети" else null,
                onExtraAction = vm::continueOnMobile,
            )
            AppScreen.Error -> ErrorScreen(
                error = state.error.orEmpty(),
                onRetry = vm::retryLoad,
                onOpenLibrary = vm::openLibrary,
            )
            AppScreen.Today -> TodayScreen(
                calendarGranted = remember(calendarTick) { hasCalendarPermission(context) },
                // Событие, добавленное из чата, сразу появляется на пути дня
                refreshKey = calendarTick + state.messages.sumOf { m -> m.events.count { it.status == EventStatus.Added } },
                onRequestCalendar = { todayCalendarPermission.launch(CALENDAR_PERMISSIONS) },
                onOpenChat = { showToday = false },
                onOpenLibrary = vm::openLibrary,
                onOpenEvent = { id -> openIntent(calendarViewIntent(id)) },
            )
            AppScreen.Chat -> ChatScreen(
                state = state,
                onSend = vm::send,
                onNewChat = vm::newChat,
                onOpenLibrary = vm::openLibrary,
                onBack = goBack,
                onPickImages = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                onStartRecording = onMic,
                onStopRecording = { vm.stopRecording(discard = false) },
                onCancelRecording = { vm.stopRecording(discard = true) },
                onRemoveAttachment = vm::removeAttachment,
                eventActions = eventActions,
            )
        }
    }

    if (state.askNetworkChoice) {
        NetworkChoiceDialog(
            sizeLabel = state.networkChoiceSize,
            onWaitWifi = { vm.startDownload(wifiOnly = true) },
            onContinue = { vm.startDownload(wifiOnly = false) },
            onDismiss = vm::dismissNetworkChoice,
        )
    }
}
