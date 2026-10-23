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

/** Состояние системной загрузки (DownloadManager). */
data class DownloadStatus(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long, // -1, пока размер неизвестен
    val reason: Int,
)

/**
 * Всё, что связано с файлом модели: где он лежит, скачивание с Hugging Face
 * и копирование из памяти телефона.
 *
 * Скачивание делает системный DownloadManager: оно идёт в фоне, переживает
 * сворачивание приложения, докачивается после обрыва сети и сам показывает уведомление.
 */
class ModelStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    /** Единый путь модели и для скачанного, и для импортированного файла. */
    val modelFile: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, MODEL_FILE_NAME)

    /** Модель считается готовой, только если загрузка/копирование завершились успешно. */
    fun isModelReady(): Boolean =
        prefs.getBoolean(KEY_READY, false) && modelFile.exists() && modelFile.length() > 0

    fun markReady() {
        prefs.edit().putBoolean(KEY_READY, true).remove(KEY_DOWNLOAD_ID).apply()
    }

    fun deleteModel() {
        prefs.edit().remove(KEY_READY).apply()
        modelFile.delete()
    }

    // ───────── Скачивание с Hugging Face ─────────

    fun hasPendingDownload(): Boolean = prefs.contains(KEY_DOWNLOAD_ID)

    fun enqueueDownload() {
        deleteModel() // DownloadManager не перезаписывает существующий файл
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

    fun cancelDownload() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0) downloadManager.remove(id) // заодно удаляет недокачанный файл
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        deleteModel()
    }

    // ───────── Загрузка из памяти телефона ─────────

    /** Копирует выбранный файл в папку приложения (движку нужен обычный путь к файлу). */
    suspend fun importFrom(uri: Uri, onProgress: (done: Long, total: Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val (name, total) = queryNameAndSize(uri)
            require(name == null || name.contains(".litertlm", ignoreCase = true)) {
                "Нужен файл модели с расширением .litertlm"
            }
            deleteModel()
            val tmp = File(modelFile.parentFile, "$MODEL_FILE_NAME.tmp")
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
                if (!tmp.renameTo(modelFile)) throw IOException("Не удалось сохранить файл модели")
            } finally {
                tmp.delete() // после успешного rename файла уже нет — это безопасно
            }
        }

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
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE_NAME"

        private const val KEY_READY = "ready"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val REPORT_STEP = 8L * 1024 * 1024
    }
}
