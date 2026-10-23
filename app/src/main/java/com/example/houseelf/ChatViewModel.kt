package com.example.houseelf

import android.app.Application
import android.app.DownloadManager
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

enum class Phase { Checking, NoModel, Downloading, Importing, Loading, Chat, Error }

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
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = ModelStorage(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var setupJob: Job? = null // скачивание или импорт
    private var generateJob: Job? = null
    private var nextId = 0L

    init {
        when {
            storage.isModelReady() -> loadEngine()
            storage.hasPendingDownload() -> watchDownload() // приложение перезапустили во время скачивания
            else -> _state.update { it.copy(phase = Phase.NoModel) }
        }
    }

    // ───────── Получение модели ─────────

    fun startDownload() {
        if (setupJob?.isActive == true) return
        storage.enqueueDownload()
        watchDownload()
    }

    private fun watchDownload() {
        setupJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = Phase.Downloading, progress = null, error = null,
                    progressText = "Подключаюсь к Hugging Face…")
            }
            while (true) {
                val s = storage.downloadStatus()
                if (s == null) { // загрузку убрали снаружи
                    storage.cancelDownload()
                    _state.update { it.copy(phase = Phase.NoModel) }
                    return@launch
                }
                when (s.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        storage.markReady()
                        loadEngine()
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        storage.cancelDownload()
                        _state.update {
                            it.copy(phase = Phase.NoModel,
                                error = "Не удалось скачать модель (код ${s.reason}). " +
                                    "Проверьте сеть и свободное место, затем повторите.")
                        }
                        return@launch
                    }
                    DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING ->
                        _state.update { it.copy(progress = progressOf(s), progressText = "Ожидание сети… ${sizeText(s.downloadedBytes, s.totalBytes)}") }
                    else ->
                        _state.update { it.copy(progress = progressOf(s), progressText = "Скачивание: ${sizeText(s.downloadedBytes, s.totalBytes)}") }
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
                storage.importFrom(uri) { done, total ->
                    _state.update {
                        it.copy(
                            progress = if (total > 0) done.toFloat() / total else null,
                            progressText = "Копирование: ${sizeText(done, total)}",
                        )
                    }
                }
                storage.markReady()
                loadEngine()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                storage.deleteModel()
                _state.update {
                    it.copy(phase = Phase.NoModel, error = e.message ?: "Не удалось загрузить файл")
                }
            }
        }
    }

    /** Отмена скачивания или копирования. */
    fun cancelSetup() {
        setupJob?.cancel()
        setupJob = null
        storage.cancelDownload()
        _state.update { it.copy(phase = Phase.NoModel, error = null) }
    }

    /** Удалить текущую модель и вернуться к выбору. */
    fun changeModel() {
        if (_state.value.generating) return
        closeEngine()
        storage.deleteModel()
        _state.update { UiState(phase = Phase.NoModel) }
    }

    // ───────── Движок LiteRT-LM ─────────

    fun retryLoad() = loadEngine()

    private fun loadEngine() {
        _state.update { it.copy(phase = Phase.Loading, progress = null, error = null) }
        viewModelScope.launch {
            try {
                val (e, backendName) = withContext(Dispatchers.Default) { createEngine() }
                val c = withContext(Dispatchers.Default) { e.createConversation(conversationConfig()) }
                engine = e
                conversation = c
                _state.update { it.copy(phase = Phase.Chat, messages = emptyList(), backend = backendName) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(phase = Phase.Error,
                        error = "Не удалось загрузить модель: ${t.message ?: t.javaClass.simpleName}")
                }
            }
        }
    }

    /** Сначала GPU (быстрее и экономнее по памяти), при ошибке — CPU. */
    private fun createEngine(): Pair<Engine, String> {
        val path = storage.modelFile.absolutePath
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
