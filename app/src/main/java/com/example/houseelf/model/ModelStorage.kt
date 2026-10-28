package com.example.houseelf.model

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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

    /** Режим раздумий в чате. По умолчанию выключен: так ответы приходят быстрее. */
    var thinkingMode: Boolean
        get() = prefs.getBoolean(KEY_THINKING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_THINKING, value).apply()
        }

    /**
     * Размер контекста в токенах (запрос + ответ + история), который пользователь выбрал ползунком.
     * 0 — «авто»: размер подбирается при загрузке по свободной памяти телефона. Действует при следующей загрузке модели.
     */
    var contextTokens: Int
        get() = prefs.getInt(KEY_CONTEXT_TOKENS, 0)
        set(value) {
            prefs.edit().putInt(KEY_CONTEXT_TOKENS, value).apply()
        }

    /** Удаляет одну модель. Вызывается только по явной команде пользователя. */
    fun deleteModel(fileName: String) {
        modelFile(fileName).delete()
        prefs.edit().remove(KEY_DEGRADE_PREFIX + fileName).apply() // новый файл — новый шанс на полный режим
        if (lastUsedModel == fileName) lastUsedModel = null
    }

    // ───────── Скачивание с Hugging Face ─────────

    /** Недокачанный файл. Докачка продолжается с его текущего размера. */
    fun partFile(fileName: String): File = File(modelsDir, "$fileName.part")

    fun hasPendingDownload(): Boolean = prefs.getBoolean(KEY_DOWNLOAD_ACTIVE, false)

    /** Модель из каталога, скачивание которой идёт (или было прервано перезапуском приложения). */
    fun pendingDownloadModel(): CatalogModel? =
        ModelCatalog.byFileName(prefs.getString(KEY_DOWNLOAD_FILE, null))

    /** Сколько раз для этой модели приходилось облегчать режим запуска из-за нехватки памяти. */
    fun degradeLevel(fileName: String): Int = prefs.getInt(KEY_DEGRADE_PREFIX + fileName, 0)

    fun setDegradeLevel(fileName: String, level: Int) {
        prefs.edit().putInt(KEY_DEGRADE_PREFIX + fileName, level).apply()
    }

    /** true — качать только по Wi-Fi (точнее, по сети без учёта трафика). */
    val downloadWifiOnly: Boolean get() = prefs.getBoolean(KEY_WIFI_ONLY, true)

    /** Подключены ли мы сейчас к сети без учёта трафика (Wi-Fi). Нет сети — тоже false. */
    fun isOnUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        // isActiveNetworkMetered учитывает и VPN: у VPN-сети собственных флагов «без учёта трафика» может не быть
        return !cm.isActiveNetworkMetered
    }

    /**
     * Запускает скачивание выбранной модели (или докачку, если для неё уже есть недокачанный файл).
     * Повторный вызов с другим [wifiOnly] перезапускает сервис с новой настройкой.
     * Скачивать одновременно можно только одну модель.
     */
    fun startDownload(model: CatalogModel, wifiOnly: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DOWNLOAD_ACTIVE, true)
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .putString(KEY_DOWNLOAD_FILE, model.fileName)
            .apply()
        DownloadTracker.status.value = DownloadStatus(
            if (wifiOnly) DownloadPhase.WaitingWifi else DownloadPhase.WaitingNetwork,
            partFile(model.fileName).length(), -1L, fileName = model.fileName,
        )
        ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java))
    }

    /** Перезапустить уже идущее скачивание с другой настройкой сети («Скачать по мобильной сети»). */
    fun restartPendingDownload(wifiOnly: Boolean) {
        val model = pendingDownloadModel()
        if (model == null) clearDownloadState() else startDownload(model, wifiOnly)
    }

    /** Приложение перезапустили, а скачивание числится активным, но сервис не работает. */
    fun resumeDownloadIfNeeded() {
        if (hasPendingDownload() && DownloadTracker.status.value == null) restartPendingDownload(downloadWifiOnly)
    }

    /** Сервис вызывает это, когда скачивание закончилось (успешно или с ошибкой). */
    fun clearDownloadState() {
        prefs.edit().remove(KEY_DOWNLOAD_ACTIVE).remove(KEY_WIFI_ONLY).remove(KEY_DOWNLOAD_FILE).apply()
    }

    /**
     * Отменяет текущее скачивание и удаляет недокачанный файл.
     * Если скачивания нет — ничего не делает, чтобы случайно не задеть готовые модели.
     */
    fun cancelDownload() {
        if (!hasPendingDownload()) return
        val pending = pendingDownloadModel()
        clearDownloadState()
        context.stopService(Intent(context, ModelDownloadService::class.java))
        DownloadTracker.status.value = null
        pending?.let { partFile(it.fileName).delete() }
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

        private const val KEY_LAST_USED = "last_used"
        private const val KEY_AUTO_LOAD = "auto_load_on_start"
        private const val KEY_CONFIRM_ACTIONS = "confirm_actions"
        private const val KEY_THINKING = "thinking_mode"
        private const val KEY_CONTEXT_TOKENS = "context_tokens"
        private const val KEY_DOWNLOAD_ACTIVE = "download_active"
        private const val KEY_WIFI_ONLY = "download_wifi_only"
        private const val KEY_DOWNLOAD_FILE = "download_file"
        private const val KEY_DEGRADE_PREFIX = "degrade_"
        private const val REPORT_STEP = 8L * 1024 * 1024
    }
}
