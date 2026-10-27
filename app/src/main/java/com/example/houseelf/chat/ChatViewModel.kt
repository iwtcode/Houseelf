package com.example.houseelf.chat

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.houseelf.calendar.CalendarTools
import com.example.houseelf.calendar.EventAction
import com.example.houseelf.calendar.EventDefaults
import com.example.houseelf.calendar.CalendarEventInfo
import com.example.houseelf.calendar.EventProposal
import com.example.houseelf.calendar.EventStatus
import com.example.houseelf.calendar.applyCalendarAction
import com.example.houseelf.calendar.currentTimeForPrompt
import com.example.houseelf.calendar.formatEventWhen
import com.example.houseelf.calendar.hasCalendarPermission
import com.example.houseelf.media.Attachment
import com.example.houseelf.media.AudioAttachment
import com.example.houseelf.media.ImageAttachment
import com.example.houseelf.media.Media
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.media.TextOrigin
import com.example.houseelf.model.DownloadPhase
import com.example.houseelf.model.DownloadStatus
import com.example.houseelf.model.CatalogModel
import com.example.houseelf.model.DownloadTracker
import com.example.houseelf.model.ModelCatalog
import com.example.houseelf.model.ModelProfile
import com.example.houseelf.model.ModelStorage
import com.example.houseelf.util.normalizeCapturedText
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = ModelStorage(app)
    private val engineFactory = EngineFactory(storage, app.cacheDir.path) { ramInfo().second }
    private val voiceRecorder = VoiceRecorder()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var setupJob: Job? = null // скачивание или импорт
    private var loadJob: Job? = null // загрузка модели в движок
    private var targetModel: String? = null // модель, которую грузим (для «Повторить»)
    private var activeProfile: ModelProfile = ModelCatalog.profileFor(null) // что умеет загруженная модель
    private var requestedDownload: CatalogModel? = null // модель, которую пользователь нажал «Скачать»
    private var generateJob: Job? = null
    @Volatile private var stoppedByUser = false // ответ прервали кнопкой: это не ошибка
    @Volatile private var rewinding = false // разговор с моделью пересоздаётся (удаление, повтор, смена режима раздумий)
    private var nextId = 0L
    private var nextAttachmentId = 0L
    private val nextEventId = AtomicLong(0)
    @Volatile private var currentBotId = -1L // ответ, в который попадут карточки событий
    @Volatile private var permissionInFlight = false // системное окно запроса разрешения уже на экране
    @Volatile private var permissionWaiter: CompletableFuture<Boolean>? = null // инструмент ждёт ответа на запрос
    private val isolatedRunLock = Mutex() // идёт фоновая задача в отдельном разговоре (Домовой.Событие)

    // Значения по умолчанию — те же, что в Домовом.Событие: модель не переспрашивает, а вносит событие сразу
    private val eventDefaults = EventDefaults()

    // Инструменты, доступные модели. Вызываются из потока генерации.
    private val calendarTools = CalendarTools(
        context = app.applicationContext,
        nextId = { nextEventId.incrementAndGet() },
        ensurePermission = { awaitCalendarPermission() },
        handler = { handleProposal(it) },
        defaults = eventDefaults,
    )

    init {
        Media.clearAttachments(app) // файлы вложений от прошлого запуска больше не нужны
        val models = storage.listModels()
        val autoLoad = storage.autoLoadOnStart
        _state.update { it.copy(autoLoad = autoLoad, confirmActions = storage.confirmActions, thinking = storage.thinkingMode) }
        when {
            storage.hasPendingDownload() -> { // приложение перезапустили во время скачивания
                storage.resumeDownloadIfNeeded()
                watchDownload()
            }
            else -> {
                // Если включена автозагрузка — открываем последнюю использованную модель;
                // если выбора нет или автозагрузка выключена — показываем список
                val auto = storage.lastUsedModel?.takeIf { name -> models.any { it.fileName == name } }
                    ?: models.singleOrNull()?.fileName
                if (autoLoad && auto != null) loadEngine(auto) else showLibrary()
            }
        }
    }

    // ───────── Библиотека моделей ─────────

    private fun showLibrary(error: String? = null) {
        val (free, total) = ramInfo()
        _state.update {
            it.copy(phase = Phase.Library, progress = null, error = error, waitingWifi = false,
                models = storage.listModels(), freeRamBytes = free, totalRamBytes = total)
        }
    }

    /** Свободная и общая оперативная память телефона. */
    private fun ramInfo(): Pair<Long, Long> {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem to info.totalMem
    }

    /**
     * Выгрузить модель из оперативной памяти. Файл модели остаётся на телефоне.
     * Текущий диалог закрывается — вместе с моделью пропадает и его контекст.
     */
    fun unloadModel() {
        if (isBusy() || loadJob?.isActive == true) return
        if (engine == null && _state.value.activeModel == null) return
        stopRecording(discard = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                closeEngine()
                Media.clearAttachments(getApplication())
            }
            _state.update {
                it.copy(activeModel = null, messages = emptyList(), backend = "",
                    imagesSupported = false, audioSupported = false, maxImages = 0,
                    toolsSupported = false, draft = emptyList(), notice = null)
            }
            showLibrary()
        }
    }

    fun setAutoLoad(enabled: Boolean) {
        storage.autoLoadOnStart = enabled
        _state.update { it.copy(autoLoad = enabled) }
    }

    /** Открыть список моделей. Загруженная модель и чат остаются в памяти. */
    fun openLibrary() {
        if (isBusy()) return
        stopRecording(discard = true)
        showLibrary()
    }

    /** Вернуться из списка в текущий чат (если модель загружена). */
    fun backToChat() {
        if (engine != null && _state.value.activeModel != null) {
            _state.update { it.copy(phase = Phase.Chat, error = null) }
        }
    }

    /** Выбрать модель из уже загруженных. */
    fun selectModel(fileName: String) {
        if (isBusy()) return
        if (fileName == _state.value.activeModel && engine != null) {
            backToChat() // эта модель уже открыта — просто возвращаемся к чату
        } else {
            loadEngine(fileName)
        }
    }

    /** Удалить одну модель с телефона (только по явной просьбе пользователя). */
    fun deleteModel(fileName: String) {
        if (isBusy()) return
        val wasActive = fileName == _state.value.activeModel
        if (wasActive) stopRecording(discard = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (wasActive) closeEngine() // сначала освобождаем файл, потом удаляем
                storage.deleteModel(fileName)
                if (wasActive) Media.clearAttachments(getApplication())
            }
            _state.update {
                if (wasActive) it.copy(activeModel = null, messages = emptyList(), backend = "",
                    imagesSupported = false, audioSupported = false, maxImages = 0,
                    toolsSupported = false, draft = emptyList(), notice = null)
                else it
            }
            showLibrary()
        }
    }

    // ───────── Получение модели ─────────

    /**
     * Нажата кнопка «Скачать». Если мы на Wi-Fi — качаем сразу (и только по Wi-Fi),
     * иначе спрашиваем: ждать Wi-Fi или продолжить по мобильной сети.
     */
    fun requestDownload(model: CatalogModel) {
        if (setupJob?.isActive == true) return
        requestedDownload = model
        if (storage.isOnUnmeteredNetwork()) {
            startDownload(wifiOnly = true)
        } else {
            _state.update { it.copy(askNetworkChoice = true, networkChoiceSize = model.sizeLabel) }
        }
    }

    fun dismissNetworkChoice() {
        _state.update { it.copy(askNetworkChoice = false) }
    }

    fun startDownload(wifiOnly: Boolean) {
        _state.update { it.copy(askNetworkChoice = false) }
        if (setupJob?.isActive == true) return
        val model = requestedDownload ?: return
        storage.startDownload(model, wifiOnly)
        watchDownload()
    }

    /** Кнопка на экране ожидания Wi-Fi: не ждать, качать по мобильной сети. */
    fun continueOnMobile() {
        if (!storage.hasPendingDownload()) return
        storage.restartPendingDownload(wifiOnly = false) // сервис перезапустится и докачает с того же места
    }

    private fun watchDownload() {
        setupJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = Phase.Downloading, progress = null, error = null, waitingWifi = false,
                    progressText = "Подключаюсь к Hugging Face…")
            }
            while (true) {
                val s = DownloadTracker.status.value
                if (s == null) { // скачивание отменили снаружи
                    storage.cancelDownload()
                    showLibrary()
                    return@launch
                }
                when (s.phase) {
                    DownloadPhase.Done -> {
                        DownloadTracker.status.value = null
                        val fileName = s.fileName
                        if (fileName == null) showLibrary() else loadEngine(fileName)
                        return@launch
                    }
                    DownloadPhase.Failed -> {
                        DownloadTracker.status.value = null
                        showLibrary(s.error ?: "Не удалось скачать модель. Проверьте сеть и свободное место, затем повторите.")
                        return@launch
                    }
                    DownloadPhase.WaitingWifi ->
                        _state.update { it.copy(progress = progressOf(s), waitingWifi = true,
                            progressText = "Жду Wi-Fi… ${sizeText(s.downloadedBytes, s.totalBytes)}") }
                    DownloadPhase.WaitingNetwork ->
                        _state.update { it.copy(progress = progressOf(s), waitingWifi = false,
                            progressText = "Ожидание сети… ${sizeText(s.downloadedBytes, s.totalBytes)}") }
                    DownloadPhase.Retrying ->
                        _state.update { it.copy(progress = progressOf(s), waitingWifi = false,
                            progressText = "Повторное подключение… ${sizeText(s.downloadedBytes, s.totalBytes)}") }
                    DownloadPhase.Running ->
                        _state.update { it.copy(progress = progressOf(s), waitingWifi = false,
                            progressText = "Скачивание: ${sizeText(s.downloadedBytes, s.totalBytes)}") }
                }
                delay(500)
            }
        }
    }

    fun importModel(uri: Uri) {
        if (setupJob?.isActive == true) return
        setupJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = Phase.Importing, progress = null, error = null,
                    progressText = "Копирую модель в память приложения…")
            }
            try {
                val fileName = storage.importFrom(uri) { done, total ->
                    _state.update {
                        it.copy(
                            progress = if (total > 0) done.toFloat() / total else null,
                            progressText = "Копирование: ${sizeText(done, total)}",
                        )
                    }
                }
                loadEngine(fileName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showLibrary(e.message ?: "Не удалось загрузить файл")
            }
        }
    }

    /** Отмена скачивания или копирования. Уже загруженные модели не трогаем. */
    fun cancelSetup() {
        setupJob?.cancel()
        setupJob = null
        storage.cancelDownload() // удаляет только недокачанный файл, и только если скачивание шло
        showLibrary()
    }

    // ───────── Движок LiteRT-LM ─────────

    /** Повторить загрузку модели, на которой случилась ошибка. */
    fun retryLoad() {
        val name = targetModel
        if (name != null) loadEngine(name) else showLibrary()
    }

    /**
     * [noticeAfter] — что сказать пользователю над полем ввода, когда модель загрузится (например, о смене режима).
     * [keep] — переписка, которую нужно сохранить: она остаётся на экране и пересказывается модели.
     * [onLoaded] — что сделать, когда модель загрузилась и разговор готов.
     */
    private fun loadEngine(
        fileName: String,
        noticeAfter: String? = null,
        keep: List<ChatMessage>? = null,
        onLoaded: (() -> Unit)? = null,
    ) {
        if (loadJob?.isActive == true) return // уже идёт загрузка модели
        targetModel = fileName
        activeProfile = ModelCatalog.profileFor(fileName)
        stopRecording(discard = true)
        _state.update {
            it.copy(phase = Phase.Loading, progress = null, error = null,
                activeModel = null, loadingModel = fileName, models = storage.listModels())
        }
        loadJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.Default) {
                    closeEngine() // освобождаем память от предыдущей модели
                    // Вложения сохранённой переписки нужны для «Повторить»
                    if (keep == null) Media.clearAttachments(getApplication())
                    engineFactory.create(fileName)
                }
                val c = try {
                    withContext(Dispatchers.Default) { loaded.engine.createConversation(conversationConfig(replayOf(keep.orEmpty()))) }
                } catch (t: Throwable) {
                    runCatching { loaded.engine.close() }
                    throw t
                }
                engine = loaded.engine
                conversation = c
                storage.lastUsedModel = fileName
                _state.update {
                    it.copy(phase = Phase.Chat, messages = keep ?: emptyList(), backend = loaded.backend,
                        activeModel = fileName, loadingModel = null,
                        imagesSupported = loaded.vision, audioSupported = loaded.audio,
                        maxImages = if (loaded.vision) activeProfile.maxImages else 0,
                        toolsSupported = activeProfile.tools,
                        // Текст, который прислали из другого приложения, пока модель грузилась, не теряем
                        draft = it.draft.filterIsInstance<TextAttachment>(), notice = noticeAfter)
                }
                onLoaded?.invoke()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.update {
                    it.copy(phase = Phase.Error, loadingModel = null,
                        error = "Не удалось загрузить модель: ${t.message ?: t.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * Дату и время берём в момент создания диалога — поэтому «завтра» модель понимает правильно.
     * [history] — прежняя переписка, если разговор пересоздаётся посреди чата.
     * Режим раздумий тоже задаётся здесь: чтобы его сменить, разговор создаётся заново.
     */
    private fun conversationConfig(history: List<Message> = emptyList()): ConversationConfig {
        eventDefaults.reset()
        return ConversationConfig(
            systemInstruction = Contents.of(
                Prompts.systemInstruction(currentTimeForPrompt(), calendar = activeProfile.tools)
            ),
            initialMessages = history,
            extraContext = mapOf("enable_thinking" to _state.value.thinking),
            // Модели без нативных вызовов инструментов (Qwen3.5) отдали бы вызов сырым текстом в чат
            tools = if (activeProfile.tools) listOf(tool(calendarTools)) else emptyList(),
        )
    }

    private fun closeEngine() {
        generateJob?.cancel()
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
    }

    // ───────── Вложения ─────────

    fun showNotice(text: String?) {
        _state.update { it.copy(notice = text) }
    }

    /** Прикрепить изображения (выбор из галереи). */
    fun attachImages(uris: List<Uri>) {
        val maxImages = _state.value.maxImages
        if (uris.isEmpty() || _state.value.attaching || maxImages <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(attaching = true, notice = null) }
            var skipped = false
            for (uri in uris) {
                if (_state.value.draft.count { it is ImageAttachment } >= maxImages) {
                    skipped = true
                    break
                }
                try {
                    val id = nextAttachmentId++
                    val att = withContext(Dispatchers.IO) { Media.processImage(getApplication(), uri, id) }
                    _state.update { it.copy(draft = it.draft + att) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(notice = "Не удалось добавить изображение: ${t.message ?: t.javaClass.simpleName}") }
                }
            }
            if (skipped) {
                val text = if (maxImages == 1) "К сообщению можно приложить только одно изображение"
                else "К сообщению можно приложить не больше $maxImages изображений"
                _state.update { it.copy(notice = text) }
            }
            _state.update { it.copy(attaching = false) }
        }
    }

    /** Прикрепить аудиофайл. Длиннее 30 секунд — берём начало. */
    fun attachAudio(uri: Uri) {
        val s = _state.value
        if (s.attaching || !s.audioSupported) return
        if (s.draft.any { it is AudioAttachment }) {
            showNotice("К сообщению можно приложить только одно аудио. Уберите текущее.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(attaching = true, notice = null) }
            try {
                val id = nextAttachmentId++
                val (att, truncated) = withContext(Dispatchers.IO) { Media.processAudioFile(getApplication(), uri, id) }
                _state.update {
                    it.copy(
                        draft = it.draft + att,
                        notice = if (truncated) "Аудио длиннее ${Media.MAX_AUDIO_SECONDS} с — взяты первые ${Media.MAX_AUDIO_SECONDS} секунд" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(notice = "Не удалось добавить аудио: ${t.message ?: t.javaClass.simpleName}") }
            } finally {
                _state.update { it.copy(attaching = false) }
            }
        }
    }

    /**
     * Прикрепить текст из другого приложения (выделение, «Поделиться», буфер обмена).
     * Вложение одно: новый текст заменяет прежний. Вернёт false, если текст пустой.
     */
    fun attachText(raw: CharSequence, source: String?, origin: TextOrigin): Boolean {
        val captured = normalizeCapturedText(raw, MAX_CAPTURE_CHARS) ?: return false
        val att = TextAttachment(
            id = nextAttachmentId++,
            text = captured.text,
            source = source?.takeIf { it.isNotBlank() },
            origin = origin,
            truncated = captured.truncated,
        )
        _state.update { s -> s.copy(draft = s.draft.filter { it !is TextAttachment } + att, notice = null) }
        return true
    }

    /** Номер, который получит следующее сообщение: мини-чат показывает только то, что началось после него. */
    fun peekNextMessageId(): Long = nextId

    /** Загрузить модель, которой пользовались в прошлый раз (или единственную). Для мини-чата без выбора из списка. */
    fun loadPreferredModel() {
        val models = storage.listModels()
        val name = storage.lastUsedModel?.takeIf { n -> models.any { it.fileName == n } }
            ?: models.firstOrNull()?.fileName
        if (name != null) selectModel(name) else showNotice("Сначала скачай модель в приложении")
    }

    fun removeAttachment(id: Long) {
        val att = _state.value.draft.firstOrNull { it.id == id } ?: return
        Media.deleteFile(att)
        _state.update { it.copy(draft = it.draft.filter { a -> a.id != id }, notice = null) }
    }

    // ───────── Голосовой ввод ─────────

    /** Начать запись. Разрешение на микрофон должно быть уже получено. */
    fun startRecording() {
        val s = _state.value
        if (s.recording || s.attaching || s.phase != Phase.Chat || !s.audioSupported) return
        if (s.draft.any { it is AudioAttachment }) {
            showNotice("К сообщению можно приложить только одно аудио. Уберите текущее.")
            return
        }
        voiceRecorder.prepare()
        _state.update { it.copy(recording = true, recordingMs = 0, notice = null) }
        val id = nextAttachmentId++

        viewModelScope.launch {
            val result = voiceRecorder.record(getApplication(), id) { ms ->
                _state.update { it.copy(recordingMs = ms) }
            }
            _state.update {
                it.copy(
                    recording = false,
                    recordingMs = 0,
                    draft = result.attachment?.let { att -> it.draft + att } ?: it.draft,
                    notice = result.notice,
                )
            }
        }
    }

    /** Закончить запись: discard = true — выбросить, false — прикрепить к сообщению. */
    fun stopRecording(discard: Boolean) {
        if (!_state.value.recording) return
        voiceRecorder.requestStop(discard)
    }

    // ───────── Чат ─────────

    fun send(text: String) {
        if (conversation == null) return
        val s = _state.value
        val prompt = text.trim()
        val attachments = s.draft
        if (prompt.isEmpty() && attachments.isEmpty()) return
        if (isolatedRunLock.isLocked) {
            showNotice("Домовой сейчас вносит события в календарь — подожди секунду")
            return
        }
        if (s.generating || s.recording || s.attaching || rewinding) return
        submit(prompt, attachments, clearDraft = true)
    }

    /** Отправляет сообщение модели и начинает писать ответ. [clearDraft] — убрать ли вложения из поля ввода. */
    private fun submit(prompt: String, attachments: List<Attachment>, clearDraft: Boolean) {
        val conv = conversation ?: return
        val botId = nextId + 1
        currentBotId = botId
        stoppedByUser = false
        val user = ChatMessage(nextId, fromUser = true, text = prompt, attachments = attachments)
        val bot = ChatMessage(botId, fromUser = false, text = "")
        nextId += 2
        _state.update {
            it.copy(
                messages = it.messages + user + bot, generating = true,
                draft = if (clearDraft) emptyList() else it.draft, notice = null,
            )
        }

        generateJob = viewModelScope.launch {
            var recoverModel: String? = null // модель, которую нужно перезапустить в облегчённом режиме
            try {
                val contents = if (attachments.isEmpty()) null
                else withContext(Dispatchers.IO) { MessageContentBuilder.build(prompt, attachments) }
                withContext(Dispatchers.Default) {
                    val flow = if (contents == null) conv.sendMessageAsync(prompt)
                    else conv.sendMessageAsync(Contents.of(contents))
                    // Каждый элемент потока — очередной кусочек ответа; размышления идут отдельным каналом
                    flow.collect { chunk ->
                        appendToBot(botId, chunk.toString(), chunk.channels["thought"]?.toString().orEmpty())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val name = _state.value.activeModel
                val details = t.message ?: t.javaClass.simpleName
                when {
                    stoppedByUser -> Unit // движок мог ответить ошибкой на отмену — пользователю она не нужна
                    name != null && EngineFactory.isResourceFailure(t) && engineFactory.degrade(name) -> {
                        // После такой ошибки движок уже не рабочий — пересоздаём его с меньшими запросами к памяти
                        recoverModel = name
                        appendToBot(botId, "\n\n⚠️ Модели не хватило памяти. Перезапускаю её в облегчённом режиме…")
                    }
                    name != null && EngineFactory.isResourceFailure(t) -> appendToBot(
                        botId,
                        "\n\n⚠️ Модели не хватило памяти даже в самом лёгком режиме. " +
                            "Закрой другие приложения или выбери модель поменьше.\n\n$details",
                    )
                    else -> appendToBot(botId, "\n\n⚠️ Ошибка: $details")
                }
            } finally {
                _state.update { it.copy(generating = false) }
            }
            recoverModel?.let {
                loadEngine(it, noticeAfter = "Модель перезапущена в облегчённом режиме. Отправь сообщение ещё раз.")
            }
        }
    }

    // ───────── Действия с сообщениями и режим раздумий ─────────

    /**
     * Включить или выключить режим раздумий. Режим задаётся при создании разговора, поэтому разговор
     * создаётся заново; прежняя переписка остаётся на экране и пересказывается модели.
     */
    fun setThinking(enabled: Boolean) {
        val s = _state.value
        if (enabled == s.thinking || isBusy()) return
        storage.thinkingMode = enabled
        _state.update { it.copy(thinking = enabled) }
        if (engine != null && s.phase == Phase.Chat) rewindTo(s.messages)
    }

    /** Прервать ответ, который сейчас пишется. Уже написанное остаётся в чате. */
    fun stopGenerating() {
        if (!_state.value.generating) return
        stoppedByUser = true
        val id = currentBotId
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == id) it.copy(stopped = true) else it }) }
        runCatching { conversation?.cancelProcess() } // остановить расчёт в движке
        generateJob?.cancel()
    }

    /** Удалить сообщение. Вопрос и ответ идут парой, поэтому исчезают вместе, а модель «забывает» этот обмен. */
    fun deleteMessage(id: Long) {
        val s = _state.value
        if (isBusy() || s.recording || s.attaching || engine == null) return
        val index = s.messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val message = s.messages[index]
        val from = if (message.fromUser) index else if (s.messages.getOrNull(index - 1)?.fromUser == true) index - 1 else index
        val to = if (message.fromUser && s.messages.getOrNull(index + 1)?.fromUser == false) index + 1 else index
        val removed = s.messages.subList(from, to + 1).toList()
        removed.flatMap { it.attachments }.forEach { Media.deleteFile(it) }
        rewindTo(s.messages.filterIndexed { i, _ -> i < from || i > to })
    }

    /** Повторить последний ответ: модель отвечает на тот же вопрос заново. */
    fun repeatLast() {
        val s = _state.value
        if (isBusy() || s.recording || s.attaching || conversation == null) return
        val answer = s.messages.lastOrNull()?.takeIf { !it.fromUser } ?: return
        val question = s.messages.getOrNull(s.messages.lastIndex - 1)?.takeIf { it.fromUser } ?: return
        if (answer.events.any { it.status == EventStatus.Added || it.status == EventStatus.NeedsPermission }) {
            // Повтор снова вызвал бы инструмент и записал те же события второй раз
            showNotice("В этом ответе уже внесены события в календарь — повтор создал бы их второй раз")
            return
        }
        rewindTo(s.messages.dropLast(2)) { submit(question.text, question.attachments, clearDraft = false) }
    }

    /**
     * Пересоздаёт разговор с моделью так, будто в нём была только переписка [history].
     * На экране она появляется сразу. [then] вызывается, когда разговор готов.
     */
    private fun rewindTo(history: List<ChatMessage>, then: (() -> Unit)? = null) {
        val e = engine ?: return
        val model = _state.value.activeModel ?: return
        val dirty = _state.value.messages.isNotEmpty()
        stopRecording(discard = true)
        _state.update { it.copy(messages = history, notice = null) }
        if (activeProfile.freshEngineEachChat && dirty) {
            // Qwen3.5: новый разговор на старом движке «помнит» прошлый — нужен свежий движок
            loadEngine(model, keep = history, onLoaded = then)
            return
        }
        rewinding = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    runCatching { conversation?.close() }
                    conversation = e.createConversation(conversationConfig(replayOf(history)))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                conversation = null
                _state.update { it.copy(notice = "Не удалось обновить разговор: ${t.message ?: t.javaClass.simpleName}. Начни новый чат.") }
                return@launch
            } finally {
                rewinding = false
            }
            then?.invoke()
        }
    }

    /**
     * Переписка в виде, понятном модели: пары «вопрос — ответ» с текстом.
     * Вложения, размышления и карточки событий заново не пересказываются.
     */
    private fun replayOf(messages: List<ChatMessage>): List<Message> {
        val result = mutableListOf<Message>()
        var i = 0
        while (i + 1 < messages.size) {
            val question = messages[i]
            val answer = messages[i + 1]
            if (question.fromUser && !answer.fromUser) {
                if (answer.text.isNotBlank()) {
                    result += Message.user(question.text.ifBlank { ATTACHMENT_PLACEHOLDER })
                    result += Message.model(answer.text.trim())
                }
                i += 2
            } else {
                i++
            }
        }
        return result
    }

    // ───────── Действия модели: календарь ─────────

    fun setConfirmActions(enabled: Boolean) {
        storage.confirmActions = enabled
        _state.update { it.copy(confirmActions = enabled) }
    }

    /**
     * Модель вызвала инструмент: добавить, изменить или удалить событие. Выполняется в потоке генерации.
     * Возвращает ответ для модели: получилось ли и что сказать пользователю.
     */
    private fun handleProposal(ev: EventProposal): Pair<Boolean, String> {
        val app = getApplication<Application>()
        if (_state.value.confirmActions) {
            addEvent(ev.copy(status = EventStatus.Pending))
            return true to when (ev.action) {
                EventAction.Add -> "The event '${ev.title}' (${formatEventWhen(ev)}) is shown to the user as a card with Add and Reject buttons. " +
                    "It is NOT in the calendar yet. $CONTINUE_OR_REPORT Then ask the user to confirm with the button."
                EventAction.Update -> "The change of '${ev.title}' (now ${formatEventWhen(ev)}) is shown to the user as a card with Change and Reject buttons. " +
                    "The event is NOT changed yet. $CONTINUE_OR_REPORT Then ask the user to confirm with the button."
                EventAction.Delete -> "The deletion is shown to the user as a card with Delete and Keep buttons. " +
                    "The event is NOT deleted yet. $CONTINUE_OR_REPORT Then ask the user to confirm with the button."
            }
        }
        if (!hasCalendarPermission(app)) {
            addEvent(ev.copy(status = EventStatus.NeedsPermission))
            requestCalendarPermission()
            return true to "The user is being asked to allow calendar access; the action will be done " +
                "automatically as soon as they allow it. $CONTINUE_OR_REPORT"
        }
        return try {
            val calendarEventId = applyCalendarAction(app, ev)
            addEvent(ev.copy(status = EventStatus.Added, calendarEventId = calendarEventId))
            true to when (ev.action) {
                EventAction.Add -> "The event '${ev.title}' has been added to the user's calendar: ${formatEventWhen(ev)}. $CONTINUE_OR_REPORT"
                EventAction.Update -> "The event '${ev.title}' has been changed in the user's calendar, now: ${formatEventWhen(ev)}. $CONTINUE_OR_REPORT"
                EventAction.Delete -> "The event '${ev.title}' has been deleted from the user's calendar. $CONTINUE_OR_REPORT"
            }
        } catch (t: Throwable) {
            val reason = t.message ?: t.javaClass.simpleName
            addEvent(ev.copy(status = EventStatus.Failed, error = reason))
            val what = when (ev.action) {
                EventAction.Add -> "add the event"
                EventAction.Update -> "change the event"
                EventAction.Delete -> "delete the event"
            }
            false to "Could not $what: $reason. $CONTINUE_OR_REPORT Mention that this one failed."
        }
    }

    private fun addEvent(ev: EventProposal) {
        val botId = currentBotId
        _state.update { s ->
            // Запоминаем, на каком месте ответа модель вызвала инструмент: текст после вызова должен идти после карточки
            s.copy(messages = s.messages.map {
                if (it.id == botId) it.copy(events = it.events + ev.copy(textPos = it.text.length)) else it
            })
        }
    }

    private fun findEvent(id: Long): EventProposal? =
        _state.value.messages.asSequence().flatMap { it.events.asSequence() }.firstOrNull { it.id == id }

    private fun updateEvent(id: Long, change: (EventProposal) -> EventProposal) {
        _state.update { s ->
            s.copy(messages = s.messages.map { m ->
                if (m.events.any { it.id == id }) m.copy(events = m.events.map { if (it.id == id) change(it) else it })
                else m
            })
        }
    }

    /** Человек сам изменил дело в календаре (из чата или с экрана «Сегодня»): карточки в чате показывают новые данные. */
    fun onCalendarEventEdited(info: CalendarEventInfo) {
        _state.update { s ->
            s.copy(messages = s.messages.map { m ->
                if (m.events.none { it.calendarEventId == info.id }) m
                else m.copy(events = m.events.map { ev ->
                    if (ev.calendarEventId != info.id || ev.action == EventAction.Delete) ev
                    else ev.copy(
                        title = info.title, start = info.start, end = info.end, allDay = info.allDay,
                        location = info.location, description = info.description, recurring = info.recurring,
                    )
                })
            })
        }
    }

    /** Человек сам удалил дело: карточки в чате показывают, что события в календаре больше нет. */
    fun onCalendarEventDeleted(eventId: Long) {
        _state.update { s ->
            s.copy(messages = s.messages.map { m ->
                if (m.events.none { it.calendarEventId == eventId }) m
                else m.copy(events = m.events.map { ev ->
                    if (ev.calendarEventId == eventId && ev.status == EventStatus.Added && ev.action != EventAction.Delete) {
                        ev.copy(action = EventAction.Delete)
                    } else ev
                })
            })
        }
    }

    /** Нажата кнопка подтверждения на карточке (добавить, изменить, удалить) или «Повторить» после ошибки. */
    fun confirmEvent(eventId: Long) {
        val ev = findEvent(eventId) ?: return
        if (ev.status == EventStatus.Added || ev.status == EventStatus.NeedsPermission) return
        if (!hasCalendarPermission(getApplication())) {
            updateEvent(eventId) { it.copy(status = EventStatus.NeedsPermission, error = null) }
            requestCalendarPermission()
            return
        }
        insertEventAsync(eventId)
    }

    fun dismissEvent(eventId: Long) {
        updateEvent(eventId) { if (it.status == EventStatus.Pending) it.copy(status = EventStatus.Dismissed) else it }
    }

    private fun insertEventAsync(eventId: Long) {
        viewModelScope.launch {
            val ev = findEvent(eventId) ?: return@launch
            if (ev.status == EventStatus.Added) return@launch
            try {
                val calendarEventId = withContext(Dispatchers.IO) { applyCalendarAction(getApplication(), ev) }
                updateEvent(eventId) { it.copy(status = EventStatus.Added, calendarEventId = calendarEventId, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                updateEvent(eventId) { it.copy(status = EventStatus.Failed, error = t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    /**
     * Для инструментов, которым результат нужен сразу (чтение, изменение, удаление): если доступа к календарю
     * нет — просим его и ждём ответа пользователя. Вызывается из потока генерации, не из главного.
     */
    private fun awaitCalendarPermission(): Boolean {
        val app = getApplication<Application>()
        if (hasCalendarPermission(app)) return true
        val waiter = permissionWaiter?.takeIf { !it.isDone }
            ?: CompletableFuture<Boolean>().also { permissionWaiter = it }
        requestCalendarPermission()
        if (Looper.myLooper() == Looper.getMainLooper()) return false // главный поток блокировать нельзя
        return try {
            waiter.get(PERMISSION_WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (_: Throwable) {
            _state.update { it.copy(needCalendarPermission = false) } // окно так и не показали — не показываем позже
            hasCalendarPermission(app)
        }
    }

    /** Попросить интерфейс показать системный запрос. Пока предыдущий не закрыт, второй не показываем. */
    private fun requestCalendarPermission() {
        if (permissionInFlight) return
        _state.update { it.copy(needCalendarPermission = true) }
    }

    /** Интерфейс показал окно запроса — сбрасываем флаг, чтобы не показать его повторно. */
    fun calendarPermissionRequested(): Boolean {
        // Окон может быть два (главное и мини-чат): запрос показывает только тот, кто успел первым
        val first = !permissionInFlight
        permissionInFlight = true
        _state.update { it.copy(needCalendarPermission = false) }
        return first
    }

    /** Окно закрыто: записываем ожидавшие события или помечаем их как неудавшиеся. */
    fun onCalendarPermissionResult() {
        permissionInFlight = false
        // Смотрим на реальное состояние разрешения, а не на ответ окна: он бывает пустым, если окно прервали
        val granted = hasCalendarPermission(getApplication())
        permissionWaiter?.complete(granted)
        val waiting = _state.value.messages.flatMap { it.events }.filter { it.status == EventStatus.NeedsPermission }
        for (ev in waiting) {
            if (granted) insertEventAsync(ev.id)
            else updateEvent(ev.id) { it.copy(status = EventStatus.Failed, error = "нет доступа к календарю") }
        }
    }

    // ───────── Фоновые задачи без чата (Домовой.Событие) ─────────

    /** Чат отвечает или модель занята фоновой задачей: пока так, менять модель и переписку нельзя. */
    private fun isBusy(): Boolean = _state.value.generating || isolatedRunLock.isLocked || rewinding

    /**
     * Выполняет разовую задачу модели в отдельном разговоре: переписка в чате не меняется и не видит задачу.
     * Модель, если она ещё не загружена, загружается сама. Задачи идут строго по одной.
     * Бросает [IllegalStateException] с понятным пользователю текстом, если модель недоступна.
     * Вызывать из главного потока: загрузка модели управляется оттуда.
     */
    suspend fun runIsolated(
        config: ConversationConfig,
        needsTools: Boolean = false,
        task: suspend (Conversation) -> Unit,
    ) {
        isolatedRunLock.withLock {
            val loaded = awaitLoadedEngine()
            if (needsTools && !activeProfile.tools) throw IllegalStateException(NO_TOOLS_MODEL)
            awaitChatIdle()
            withContext(Dispatchers.Default) {
                val isolated = loaded.createConversation(config)
                try {
                    task(isolated)
                } finally {
                    runCatching { isolated.close() }
                }
            }
        }
    }

    /** Движок с загруженной моделью. Если её нет в памяти — загружает ту, которой пользовались в прошлый раз. */
    private suspend fun awaitLoadedEngine(): Engine {
        engine?.let { return it }
        if (_state.value.phase == Phase.Library) {
            if (storage.listModels().isEmpty()) throw IllegalStateException("На телефоне нет модели. Скачай её в приложении.")
            loadPreferredModel()
        }
        val settled = withTimeoutOrNull(MODEL_WAIT_MS) { _state.first { it.phase == Phase.Chat || it.phase == Phase.Error } }
            ?: throw IllegalStateException("Модель не успела загрузиться")
        if (settled.phase == Phase.Error) throw IllegalStateException(settled.error ?: "Не удалось загрузить модель")
        return engine ?: throw IllegalStateException("Модель не загружена")
    }

    /** Ждёт, пока чат допишет ответ: два разговора с одной моделью одновременно не ведём. */
    private suspend fun awaitChatIdle() {
        withTimeoutOrNull(MODEL_WAIT_MS) { _state.first { !it.generating } }
            ?: throw IllegalStateException("Чат ещё отвечает, попробуй позже")
    }

    fun newChat() {
        val e = engine ?: return
        if (isBusy()) return
        stopRecording(discard = true)
        val model = _state.value.activeModel
        if (activeProfile.freshEngineEachChat && model != null) {
            // Qwen3.5: новый разговор на старом движке подхватывает состояние прошлого — нужен свежий движок
            loadEngine(model)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                runCatching { conversation?.close() }
                conversation = e.createConversation(conversationConfig())
                Media.clearAttachments(getApplication())
            }
            _state.update { it.copy(messages = emptyList(), draft = emptyList(), notice = null) }
        }
    }

    private fun appendToBot(id: Long, chunk: String, thought: String = "") {
        if (chunk.isEmpty() && thought.isEmpty()) return
        _state.update { s ->
            s.copy(messages = s.messages.map {
                if (it.id == id) it.copy(text = it.text + chunk, thinking = it.thinking + thought) else it
            })
        }
    }

    override fun onCleared() {
        voiceRecorder.requestStop(discard = true)
        closeEngine()
    }

    // ───────── Вспомогательное ─────────

    private fun progressOf(s: DownloadStatus): Float? =
        if (s.totalBytes > 0) (s.downloadedBytes.toFloat() / s.totalBytes).coerceIn(0f, 1f) else null

    private fun sizeText(done: Long, total: Long): String {
        val mb = 1024L * 1024L
        return if (total > 0) "${done / mb} / ${total / mb} МБ" else "${done / mb} МБ"
    }

    companion object {
        /** Сколько изображений можно приложить к одному сообщению. */
        const val MAX_IMAGES = 4

        /** Сколько символов выделенного текста берём в сообщение: длиннее модель на телефоне не осилит. */
        const val MAX_CAPTURE_CHARS = 5_000

        private const val PERMISSION_WAIT_SECONDS = 120L

        /**
         * Хвост каждого ответа инструмента. В сообщении может быть несколько событий: модель не должна
         * останавливаться после первого и не должна переспрашивать — как в Домовом.Событие.
         */
        private const val CONTINUE_OR_REPORT = "If the user's message has more events that are not handled yet, " +
            "call the tool for the next one right now, without asking anything. " +
            "When all are handled, briefly tell the user in one or two sentences what was done."

        /** Сколько фоновая задача ждёт загрузку модели или конец ответа в чате. */
        private const val MODEL_WAIT_MS = 120_000L

        /** Вместо вопроса, у которого был только файл: модель не принимает пустые сообщения. */
        private const val ATTACHMENT_PLACEHOLDER = "(вложение)"

        const val NO_TOOLS_MODEL = "Эта модель не умеет вносить события в календарь. Открой Gemma 4 в списке моделей."
    }
}
