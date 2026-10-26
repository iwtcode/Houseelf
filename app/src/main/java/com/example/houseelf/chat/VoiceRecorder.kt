package com.example.houseelf.chat

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.houseelf.media.AudioAttachment
import com.example.houseelf.media.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Итог записи: готовое вложение либо сообщение для пользователя (или и то и другое). */
internal class RecordingResult(val attachment: AudioAttachment?, val notice: String?)

/** Записывает голос с микрофона в формате, который ждёт модель. Разрешение на микрофон должно быть получено. */
internal class VoiceRecorder {

    // Поток записи проверяет эти флаги
    @Volatile private var stopRequested = false
    @Volatile private var discardRequested = false

    /** Сбросить флаги перед новой записью. Вызывается сразу при нажатии, до запуска [record]. */
    fun prepare() {
        stopRequested = false
        discardRequested = false
    }

    /** Закончить запись: [discard] = true — выбросить, false — сохранить как вложение. */
    fun requestStop(discard: Boolean) {
        discardRequested = discard
        stopRequested = true
    }

    /**
     * Пишет, пока не придёт [requestStop], не кончится лимит или не отменят корутину.
     * [onProgress] получает длительность записи в миллисекундах.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(context: Context, attachmentId: Long, onProgress: (Long) -> Unit): RecordingResult =
        withContext(Dispatchers.IO) {
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
                    val coroutineContext = currentCoroutineContext()
                    while (!stopRequested && coroutineContext.isActive && n < data.size) {
                        val r = rec.read(chunk, 0, minOf(chunk.size, data.size - n))
                        if (r < 0) break
                        System.arraycopy(chunk, 0, data, n, r)
                        n += r
                        onProgress(n * 1000L / rate)
                    }
                } finally {
                    runCatching { rec.stop() }
                    rec.release()
                }
            } catch (t: Throwable) {
                error = "Ошибка записи: ${t.message ?: t.javaClass.simpleName}"
            }

            var attachment: AudioAttachment? = null
            var notice: String? = error
            if (error == null && !discardRequested) {
                if (n < rate / 2) {
                    notice = "Запись слишком короткая"
                } else {
                    try {
                        attachment = Media.saveVoice(context, data.copyOf(n), attachmentId)
                        if (n >= data.size) notice = "Запись остановлена: максимум ${Media.MAX_AUDIO_SECONDS} секунд"
                    } catch (t: Throwable) {
                        notice = "Не удалось сохранить запись: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
            }
            RecordingResult(attachment, notice)
        }
}
