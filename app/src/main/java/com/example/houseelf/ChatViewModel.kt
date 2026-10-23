package com.example.houseelf

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

enum class Phase { Checking, Library, Downloading, Importing, Loading, Chat, Error }

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
)

data class UiState(
    val phase: Phase = Phase.Checking,
    /** 0..1, либо null — когда прогресс неизвестен. */
    val progress: Float? = null,
    val progressText: String = "",
    val error: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val generating: Boolean = false,
    val backend: String = "",
    /** Все модели, лежащие на телефоне. */
    val models: List<ModelInfo> = emptyList(),
    /** Файл модели, которая сейчас загружена в движок (null — никакая). */
    val activeModel: String? = null,
    /** Файл модели, которая загружается прямо сейчас. */
    val loadingModel: String? = null,
    /** Показать окно «ждать Wi-Fi или продолжить по мобильной сети». */
    val askNetworkChoice: Boolean = false,
    /** Скачивание сейчас ждёт Wi-Fi. */
    val waitingWifi: Boolean = false,
    /** Движок загружен с поддержкой изображений и звука. */
    val mediaSupported: Boolean = false,
    /** Вложения, прикреплённые к ещё не отправленному сообщению. */
    val draft: List<Attachment> = emptyList(),
    /** Файлы из выбора сейчас обрабатываются. */
    val attaching: Boolean = false,
    /** Идёт запись с микрофона. */
    val recording: Boolean = false,
    val recordingMs: Long = 0,
    /** Короткое сообщение пользователю над полем ввода (ошибка вложения и т. п.). */
    val notice: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = ModelStorage(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var setupJob: Job? = null // скачивание или импорт
    private var loadJob: Job? = null // загрузка модели в движок
    private var targetModel: String? = null // модель, которую грузим (для «Повторить»)
    private var generateJob: Job? = null
    private var nextId = 0L
    private var nextAttachmentId = 0L

    // Управление записью голоса: поток записи проверяет эти флаги
    @Volatile private var recStop = false
    @Volatile private var recDiscard = false

    init {
        Media.clearAttachments(app) // файлы вложений от прошлого запуска больше не нужны
        val models = storage.listModels()
        when {
            storage.hasPendingDownload() -> { // приложение перезапустили во время скачивания
                storage.resumeDownloadIfNeeded()
                watchDownload()
            }
            else -> {
                // Открываем последнюю использованную модель; если выбора нет — показываем список
                val auto = storage.lastUsedModel?.takeIf { name -> models.any { it.fileName == name } }
                    ?: models.singleOrNull()?.fileName
                if (auto != null) loadEngine(auto) else showLibrary()
            }
        }
    }

    // ───────── Библиотека моделей ─────────

    private fun showLibrary(error: String? = null) {
        _state.update {
            it.copy(phase = Phase.Library, progress = null, error = error, waitingWifi = false,
                models = storage.listModels())
        }
    }

    /** Открыть список моделей. Загруженная модель и чат остаются в памяти. */
    fun openLibrary() {
        if (_state.value.generating) return
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
        if (_state.value.generating) return
        if (fileName == _state.value.activeModel && engine != null) {
            backToChat() // эта модель уже открыта — просто возвращаемся к чату
        } else {
            loadEngine(fileName)
        }
    }

    /** Удалить одну модель с телефона (только по явной просьбе пользователя). */
    fun deleteModel(fileName: String) {
        if (_state.value.generating) return
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
                    mediaSupported = false, draft = emptyList(), notice = null)
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
    fun requestDownload() {
        if (setupJob?.isActive == true) return
        if (storage.isOnUnmeteredNetwork()) {
            startDownload(wifiOnly = true)
        } else {
            _state.update { it.copy(askNetworkChoice = true) }
        }
    }

    fun dismissNetworkChoice() {
        _state.update { it.copy(askNetworkChoice = false) }
    }

    fun startDownload(wifiOnly: Boolean) {
        _state.update { it.copy(askNetworkChoice = false) }
        if (setupJob?.isActive == true) return
        storage.startDownload(wifiOnly)
        watchDownload()
    }

    /** Кнопка на экране ожидания Wi-Fi: не ждать, качать по мобильной сети. */
    fun continueOnMobile() {
        if (!storage.hasPendingDownload()) return
        storage.startDownload(wifiOnly = false) // сервис перезапустится и докачает с того же места
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
                        loadEngine(ModelStorage.MODEL_FILE_NAME)
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

    private class LoadedEngine(val engine: Engine, val backend: String, val media: Boolean)

    private fun loadEngine(fileName: String) {
        if (loadJob?.isActive == true) return // уже идёт загрузка модели
        targetModel = fileName
        stopRecording(discard = true)
        _state.update {
            it.copy(phase = Phase.Loading, progress = null, error = null,
                activeModel = null, loadingModel = fileName, models = storage.listModels())
        }
        loadJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.Default) {
                    closeEngine() // освобождаем память от предыдущей модели
                    Media.clearAttachments(getApplication())
                    createEngine(fileName)
                }
                val c = try {
                    withContext(Dispatchers.Default) { loaded.engine.createConversation(conversationConfig()) }
                } catch (t: Throwable) {
                    runCatching { loaded.engine.close() }
                    throw t
                }
                engine = loaded.engine
                conversation = c
                storage.lastUsedModel = fileName
                _state.update {
                    it.copy(phase = Phase.Chat, messages = emptyList(), backend = loaded.backend,
                        activeModel = fileName, loadingModel = null, mediaSupported = loaded.media,
                        draft = emptyList(), notice = null)
                }
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
     * Пробуем по порядку: GPU и CPU с поддержкой изображений и звука, затем то же без неё
     * (на случай, если открыта текстовая модель без «глаз» и «ушей»).
     */
    private fun createEngine(fileName: String): LoadedEngine {
        val path = storage.modelFile(fileName).absolutePath
        val cacheDir = getApplication<Application>().cacheDir.path

        fun init(gpu: Boolean, media: Boolean): Engine {
            val main = if (gpu) Backend.GPU() else Backend.CPU()
            val config = if (media) {
                EngineConfig(
                    modelPath = path,
                    backend = main,
                    visionBackend = if (gpu) Backend.GPU() else Backend.CPU(),
                    audioBackend = Backend.CPU(), // звуковой энкодер работает на CPU
                    cacheDir = cacheDir,
                )
            } else {
                EngineConfig(modelPath = path, backend = main, cacheDir = cacheDir)
            }
            val e = Engine(config)
            try {
                e.initialize() // долгая операция — поэтому вызываем не в UI-потоке
            } catch (t: Throwable) {
                runCatching { e.close() }
                throw t
            }
            return e
        }

        var lastError: Throwable? = null
        for ((gpu, media) in listOf(true to true, false to true, true to false, false to false)) {
            try {
                return LoadedEngine(init(gpu, media), if (gpu) "GPU" else "CPU", media)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Не удалось запустить движок")
    }

    private fun conversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(SYSTEM_PROMPT),
    )

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
        if (uris.isEmpty() || _state.value.attaching) return
        viewModelScope.launch {
            _state.update { it.copy(attaching = true, notice = null) }
            var skipped = false
            for (uri in uris) {
                if (_state.value.draft.count { it is ImageAttachment } >= MAX_IMAGES) {
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
            if (skipped) _state.update { it.copy(notice = "К сообщению можно приложить не больше $MAX_IMAGES изображений") }
            _state.update { it.copy(attaching = false) }
        }
    }

    /** Прикрепить аудиофайл. Длиннее 30 секунд — берём начало. */
    fun attachAudio(uri: Uri) {
        val s = _state.value
        if (s.attaching) return
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

    fun removeAttachment(id: Long) {
        val att = _state.value.draft.firstOrNull { it.id == id } ?: return
        Media.deleteFile(att)
        _state.update { it.copy(draft = it.draft.filter { a -> a.id != id }, notice = null) }
    }

    // ───────── Голосовой ввод ─────────

    /** Начать запись. Разрешение на микрофон должно быть уже получено. */
    @SuppressLint("MissingPermission")
    fun startRecording() {
        val s = _state.value
        if (s.recording || s.attaching || s.phase != Phase.Chat || !s.mediaSupported) return
        if (s.draft.any { it is AudioAttachment }) {
            showNotice("К сообщению можно приложить только одно аудио. Уберите текущее.")
            return
        }
        recStop = false
        recDiscard = false
        _state.update { it.copy(recording = true, recordingMs = 0, notice = null) }
        val id = nextAttachmentId++

        viewModelScope.launch(Dispatchers.IO) {
            val rate = Media.SAMPLE_RATE
            val data = ShortArray(rate * Media.MAX_AUDIO_SECONDS)
            var n = 0
            var error: String? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) throw IllegalStateException("микрофон недоступен")
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuf, rate),
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec.release()
                    throw IllegalStateException("не удалось открыть микрофон")
                }
                try {
                    rec.startRecording()
                    val chunk = ShortArray(rate / 10) // по 100 мс
                    while (!recStop && isActive && n < data.size) {
                        val r = rec.read(chunk, 0, minOf(chunk.size, data.size - n))
                        if (r < 0) break
                        System.arraycopy(chunk, 0, data, n, r)
                        n += r
                        val ms = n * 1000L / rate
                        _state.update { it.copy(recordingMs = ms) }
                    }
                } finally {
                    runCatching { rec.stop() }
                    rec.release()
                }
            } catch (t: Throwable) {
                error = "Ошибка записи: ${t.message ?: t.javaClass.simpleName}"
            }

            var att: AudioAttachment? = null
            var notice: String? = error
            if (error == null && !recDiscard) {
                if (n < rate / 2) {
                    notice = "Запись слишком короткая"
                } else {
                    try {
                        att = Media.saveVoice(getApplication(), data.copyOf(n), id)
                        if (n >= data.size) notice = "Запись остановлена: максимум ${Media.MAX_AUDIO_SECONDS} секунд"
                    } catch (t: Throwable) {
                        notice = "Не удалось сохранить запись: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
            }
            _state.update {
                it.copy(
                    recording = false,
                    recordingMs = 0,
                    draft = if (att != null) it.draft + att else it.draft,
                    notice = notice,
                )
            }
        }
    }

    /** Закончить запись: discard = true — выбросить, false — прикрепить к сообщению. */
    fun stopRecording(discard: Boolean) {
        if (!_state.value.recording) return
        recDiscard = discard
        recStop = true
    }

    // ───────── Чат ─────────

    fun send(text: String) {
        val conv = conversation ?: return
        val s = _state.value
        val prompt = text.trim()
        val attachments = s.draft
        if (prompt.isEmpty() && attachments.isEmpty()) return
        if (s.generating || s.recording || s.attaching) return

        val botId = nextId + 1
        val user = ChatMessage(nextId, fromUser = true, text = prompt, attachments = attachments)
        val bot = ChatMessage(botId, fromUser = false, text = "")
        nextId += 2
        _state.update {
            it.copy(messages = it.messages + user + bot, generating = true, draft = emptyList(), notice = null)
        }

        generateJob = viewModelScope.launch {
            try {
                val contents = if (attachments.isEmpty()) null
                else withContext(Dispatchers.IO) { buildContents(prompt, attachments) }
                withContext(Dispatchers.Default) {
                    val flow = if (contents == null) conv.sendMessageAsync(prompt)
                    else conv.sendMessageAsync(Contents.of(contents))
                    // Каждый элемент потока — очередной кусочек ответа
                    flow.collect { chunk -> appendToBot(botId, chunk.toString()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                appendToBot(botId, "\n\n⚠️ Ошибка: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                _state.update { it.copy(generating = false) }
            }
        }
    }

    /** Картинки и звук — первыми, текст — последним: так модель лучше понимает, что от неё хотят. */
    private fun buildContents(prompt: String, attachments: List<Attachment>): List<Content> {
        val parts = mutableListOf<Content>()
        for (a in attachments) if (a is ImageAttachment) parts += Content.ImageBytes(File(a.path).readBytes())
        for (a in attachments) if (a is AudioAttachment) parts += Content.AudioBytes(File(a.path).readBytes())
        val text = when {
            prompt.isNotEmpty() -> prompt
            // Голосовое сообщение без текста — просим ответить на то, что сказано
            attachments.any { it is AudioAttachment && it.isVoice } -> VOICE_HINT
            else -> ""
        }
        if (text.isNotEmpty()) parts += Content.Text(text)
        return parts
    }

    fun newChat() {
        val e = engine ?: return
        if (_state.value.generating) return
        stopRecording(discard = true)
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                runCatching { conversation?.close() }
                conversation = e.createConversation(conversationConfig())
                Media.clearAttachments(getApplication())
            }
            _state.update { it.copy(messages = emptyList(), draft = emptyList(), notice = null) }
        }
    }

    private fun appendToBot(id: Long, chunk: String) {
        _state.update { s ->
            s.copy(messages = s.messages.map { if (it.id == id) it.copy(text = it.text + chunk) else it })
        }
    }

    override fun onCleared() {
        recDiscard = true
        recStop = true
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

        private const val SYSTEM_PROMPT =
            "Ты — Карманный домовой, дружелюбный помощник. Отвечай на языке собеседника, коротко и по делу."

        private const val VOICE_HINT = "Это голосовое сообщение от пользователя. Ответь на него."
    }
}
