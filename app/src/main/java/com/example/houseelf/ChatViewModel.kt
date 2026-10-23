package com.example.houseelf

import android.app.Application
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase { Checking, Library, Downloading, Importing, Loading, Chat, Error }

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String)

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

    init {
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (wasActive) closeEngine() // сначала освобождаем файл, потом удаляем
                storage.deleteModel(fileName)
            }
            _state.update {
                if (wasActive) it.copy(activeModel = null, messages = emptyList(), backend = "")
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

    private fun loadEngine(fileName: String) {
        if (loadJob?.isActive == true) return // уже идёт загрузка модели
        targetModel = fileName
        _state.update {
            it.copy(phase = Phase.Loading, progress = null, error = null,
                activeModel = null, loadingModel = fileName, models = storage.listModels())
        }
        loadJob = viewModelScope.launch {
            try {
                val (e, backendName) = withContext(Dispatchers.Default) {
                    closeEngine() // освобождаем память от предыдущей модели
                    createEngine(fileName)
                }
                val c = try {
                    withContext(Dispatchers.Default) { e.createConversation(conversationConfig()) }
                } catch (t: Throwable) {
                    runCatching { e.close() }
                    throw t
                }
                engine = e
                conversation = c
                storage.lastUsedModel = fileName
                _state.update {
                    it.copy(phase = Phase.Chat, messages = emptyList(), backend = backendName,
                        activeModel = fileName, loadingModel = null)
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

    /** Сначала GPU (быстрее и экономнее по памяти), при ошибке — CPU. */
    private fun createEngine(fileName: String): Pair<Engine, String> {
        val path = storage.modelFile(fileName).absolutePath
        val cacheDir = getApplication<Application>().cacheDir.path

        fun init(backend: Backend): Engine {
            val e = Engine(EngineConfig(modelPath = path, backend = backend, cacheDir = cacheDir))
            try {
                e.initialize() // долгая операция — поэтому вызываем не в UI-потоке
            } catch (t: Throwable) {
                runCatching { e.close() }
                throw t
            }
            return e
        }

        return try {
            init(Backend.GPU()) to "GPU"
        } catch (_: Throwable) {
            init(Backend.CPU()) to "CPU"
        }
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

    // ───────── Чат ─────────

    fun send(text: String) {
        val conv = conversation ?: return
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.generating) return

        val botId = nextId + 1
        val user = ChatMessage(nextId, fromUser = true, text = prompt)
        val bot = ChatMessage(botId, fromUser = false, text = "")
        nextId += 2
        _state.update { it.copy(messages = it.messages + user + bot, generating = true) }

        generateJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    // Каждый элемент потока — очередной кусочек ответа
                    conv.sendMessageAsync(prompt).collect { chunk -> appendToBot(botId, chunk.toString()) }
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

    fun newChat() {
        val e = engine ?: return
        if (_state.value.generating) return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                runCatching { conversation?.close() }
                conversation = e.createConversation(conversationConfig())
            }
            _state.update { it.copy(messages = emptyList()) }
        }
    }

    private fun appendToBot(id: Long, chunk: String) {
        _state.update { s ->
            s.copy(messages = s.messages.map { if (it.id == id) it.copy(text = it.text + chunk) else it })
        }
    }

    override fun onCleared() {
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
        private const val SYSTEM_PROMPT =
            "Ты — Карманный домовой, дружелюбный помощник. Отвечай на языке собеседника, коротко и по делу."
    }
}
