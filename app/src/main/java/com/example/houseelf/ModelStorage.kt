package com.example.houseelf

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/** Состояние системной загрузки (DownloadManager). */
data class DownloadStatus(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long, // -1, пока размер неизвестен
    val reason: Int,
)

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
 * Скачивание делает системный DownloadManager: оно идёт в фоне, переживает
 * сворачивание приложения, докачивается после обрыва сети и сам показывает уведомление.
 */
class ModelStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    private val modelsDir: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    fun modelFile(fileName: String): File = File(modelsDir, fileName)

    // ───────── Библиотека моделей ─────────

    /** Все полностью загруженные модели. Недокачанный файл в список не попадает. */
    fun listModels(): List<ModelInfo> {
        val downloading = hasPendingDownload()
        return modelsDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(MODEL_EXT, ignoreCase = true) && it.length() > 0 }
            // DownloadManager пишет прямо в итоговый файл, пока качает
            .filterNot { downloading && it.name == MODEL_FILE_NAME }
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

    /** Удаляет одну модель. Вызывается только по явной команде пользователя. */
    fun deleteModel(fileName: String) {
        modelFile(fileName).delete()
        if (lastUsedModel == fileName) lastUsedModel = null
    }

    // ───────── Скачивание с Hugging Face ─────────

    fun hasPendingDownload(): Boolean = prefs.contains(KEY_DOWNLOAD_ID)

    fun enqueueDownload() {
        modelFile(MODEL_FILE_NAME).delete() // DownloadManager не перезаписывает существующий файл
        val request = DownloadManager.Request(MODEL_URL.toUri())
            .setTitle("Gemma 4 E2B")
            .setDescription("Загрузка модели для Домового")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, MODEL_FILE_NAME)
        val id = downloadManager.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
    }

    /** null — загрузки с таким id больше нет (отменена или удалена). */
    fun downloadStatus(): DownloadStatus? {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id < 0) return null
        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            return DownloadStatus(
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloadedBytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
    }

    /** Скачивание успешно завершилось: файл теперь обычная модель в библиотеке. */
    fun finishDownload() {
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
    }

    /**
     * Отменяет текущее скачивание и удаляет недокачанный файл.
     * Если скачивания нет — ничего не делает, чтобы случайно не задеть готовые модели.
     */
    fun cancelDownload() {
        if (!hasPendingDownload()) return
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0) downloadManager.remove(id) // заодно удаляет недокачанный файл
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        modelFile(MODEL_FILE_NAME).delete()
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
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val REPORT_STEP = 8L * 1024 * 1024
    }
}
