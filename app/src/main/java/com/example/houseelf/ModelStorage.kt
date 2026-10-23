package com.example.houseelf

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

enum class DownloadPhase {
    WaitingNetwork, // нет сети
    WaitingWifi,    // ждём Wi-Fi (пользователь выбрал не качать по мобильной сети)
    Running,
    Done,
    Failed,
}

/** Состояние скачивания модели. */
data class DownloadStatus(
    val phase: DownloadPhase,
    val downloadedBytes: Long,
    val totalBytes: Long, // -1, пока размер неизвестен
    val error: String? = null,
)

/** Связь между сервисом скачивания и интерфейсом. null — скачивание не идёт. */
object DownloadTracker {
    val status = MutableStateFlow<DownloadStatus?>(null)
}

/** Модель, лежащая на телефоне. */
data class ModelInfo(val fileName: String, val sizeBytes: Long) {
    val displayName: String get() = modelDisplayName(fileName)
}

/** Человекочитаемое имя модели по имени файла. */
fun modelDisplayName(fileName: String?): String {
    if (fileName == null) return ""
    if (fileName == ModelStorage.MODEL_FILE_NAME) return "Gemma 4 E2B"
    return fileName.removeSuffix(ModelStorage.MODEL_EXT)
}

fun formatSize(bytes: Long): String {
    val gb = 1024.0 * 1024.0 * 1024.0
    val mb = 1024.0 * 1024.0
    return if (bytes >= gb) String.format(Locale.getDefault(), "%.1f ГБ", bytes / gb)
    else String.format(Locale.getDefault(), "%d МБ", (bytes / mb).toLong())
}

/**
 * Всё, что связано с файлами моделей: где они лежат, скачивание с Hugging Face
 * и копирование из памяти телефона.
 *
 * Моделей может быть несколько: любой файл *.litertlm в папке приложения
 * считается загруженной моделью. Удаляется модель только явным действием пользователя.
 *
 * Скачивание делает ModelDownloadService (foreground-сервис): оно идёт в фоне,
 * переживает сворачивание приложения, докачивается после обрыва сети и показывает уведомление.
 * Системный DownloadManager не используется: большие файлы он сам откладывает до Wi-Fi.
 */
class ModelStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE)

    private val modelsDir: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    fun modelFile(fileName: String): File = File(modelsDir, fileName)

    // ───────── Библиотека моделей ─────────

    /** Все полностью загруженные модели. Недокачанный файл в список не попадает. */
    fun listModels(): List<ModelInfo> {
        return modelsDir.listFiles().orEmpty()
            // Недокачанный файл называется *.part, поэтому в список не попадает
            .filter { it.isFile && it.name.endsWith(MODEL_EXT, ignoreCase = true) && it.length() > 0 }
            .map { ModelInfo(it.name, it.length()) }
            .sortedBy { it.displayName.lowercase() }
    }

    /** Последняя успешно открытая модель — её приложение откроет при запуске. */
    var lastUsedModel: String?
        get() = prefs.getString(KEY_LAST_USED, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_LAST_USED) else editor.putString(KEY_LAST_USED, value)
            editor.apply()
        }

    /** Открывать ли последнюю модель (загружать её в память) при запуске приложения. */
    var autoLoadOnStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOAD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_LOAD, value).apply()
        }

    /**
     * Просить ли подтверждение, прежде чем модель что-то сделает на телефоне (например, добавит событие
     * в календарь). По умолчанию выключено: модель действует сама.
     */
    var confirmActions: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_ACTIONS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CONFIRM_ACTIONS, value).apply()
        }

    /** Удаляет одну модель. Вызывается только по явной команде пользователя. */
    fun deleteModel(fileName: String) {
        modelFile(fileName).delete()
        if (lastUsedModel == fileName) lastUsedModel = null
    }

    // ───────── Скачивание с Hugging Face ─────────

    /** Недокачанный файл. Докачка продолжается с его текущего размера. */
    fun partFile(): File = File(modelsDir, "$MODEL_FILE_NAME.part")

    fun hasPendingDownload(): Boolean = prefs.getBoolean(KEY_DOWNLOAD_ACTIVE, false)

    /** true — качать только по Wi-Fi (точнее, по сети без учёта трафика). */
    val downloadWifiOnly: Boolean get() = prefs.getBoolean(KEY_WIFI_ONLY, true)

    /** Подключены ли мы сейчас к сети без учёта трафика (Wi-Fi). Нет сети — тоже false. */
    fun isOnUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Запускает скачивание (или докачку, если уже есть недокачанный файл).
     * Повторный вызов с другим [wifiOnly] перезапускает сервис с новой настройкой.
     */
    fun startDownload(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_ACTIVE, true).putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        DownloadTracker.status.value = DownloadStatus(
            if (wifiOnly) DownloadPhase.WaitingWifi else DownloadPhase.WaitingNetwork,
            partFile().length(), -1L,
        )
        ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java))
    }

    /** Приложение перезапустили, а скачивание числится активным, но сервис не работает. */
    fun resumeDownloadIfNeeded() {
        if (hasPendingDownload() && DownloadTracker.status.value == null) startDownload(downloadWifiOnly)
    }

    /** Сервис вызывает это, когда скачивание закончилось (успешно или с ошибкой). */
    fun clearDownloadState() {
        prefs.edit().remove(KEY_DOWNLOAD_ACTIVE).remove(KEY_WIFI_ONLY).apply()
    }

    /**
     * Отменяет текущее скачивание и удаляет недокачанный файл.
     * Если скачивания нет — ничего не делает, чтобы случайно не задеть готовые модели.
     */
    fun cancelDownload() {
        if (!hasPendingDownload()) return
        clearDownloadState()
        context.stopService(Intent(context, ModelDownloadService::class.java))
        DownloadTracker.status.value = null
        partFile().delete()
    }

    // ───────── Загрузка из памяти телефона ─────────

    /**
     * Копирует выбранный файл в папку приложения (движку нужен обычный путь к файлу).
     * Файл сохраняется под своим именем, другие модели не затрагиваются.
     * Возвращает имя сохранённого файла.
     */
    suspend fun importFrom(uri: Uri, onProgress: (done: Long, total: Long) -> Unit): String =
        withContext(Dispatchers.IO) {
            val (name, total) = queryNameAndSize(uri)
            require(name == null || name.endsWith(MODEL_EXT, ignoreCase = true)) {
                "Нужен файл модели с расширением $MODEL_EXT"
            }
            val fileName = name?.let(::sanitize) ?: "model-${System.currentTimeMillis()}$MODEL_EXT"
            val target = modelFile(fileName)
            val tmp = File(modelsDir, "$fileName.tmp")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Не удалось открыть выбранный файл")
                input.use { src ->
                    tmp.outputStream().use { dst ->
                        val buffer = ByteArray(1 shl 20)
                        var done = 0L
                        var lastReported = 0L
                        while (true) {
                            ensureActive() // позволяет отменить копирование
                            val n = src.read(buffer)
                            if (n < 0) break
                            dst.write(buffer, 0, n)
                            done += n
                            if (done - lastReported >= REPORT_STEP) {
                                onProgress(done, total)
                                lastReported = done
                            }
                        }
                    }
                }
                // Старая модель с тем же именем заменяется только после полного копирования
                if (!tmp.renameTo(target)) throw IOException("Не удалось сохранить файл модели")
            } finally {
                tmp.delete() // после успешного rename файла уже нет — это безопасно
            }
            fileName
        }

    private fun sanitize(name: String): String =
        File(name).name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long> {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 }?.let { c.getString(it) }
                val size = c.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) } ?: -1L
                return name to size
            }
        }
        return null to -1L
    }

    companion object {
        const val MODEL_EXT = ".litertlm"
        const val MODEL_FILE_NAME = "gemma-4-E2B-it$MODEL_EXT"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE_NAME"

        private const val KEY_LAST_USED = "last_used"
        private const val KEY_AUTO_LOAD = "auto_load_on_start"
        private const val KEY_CONFIRM_ACTIONS = "confirm_actions"
        private const val KEY_DOWNLOAD_ACTIVE = "download_active"
        private const val KEY_WIFI_ONLY = "download_wifi_only"
        private const val REPORT_STEP = 8L * 1024 * 1024
    }
}
