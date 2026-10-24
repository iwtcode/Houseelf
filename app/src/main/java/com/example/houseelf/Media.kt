package com.example.houseelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** Вложение к сообщению. Сами данные лежат файлом в кэше приложения, в памяти — только путь и превью. */
sealed interface Attachment {
    val id: Long
    val path: String
}

data class ImageAttachment(
    override val id: Long,
    override val path: String,
    /** Маленькая копия для показа в чате. */
    val thumbnail: Bitmap,
) : Attachment

data class AudioAttachment(
    override val id: Long,
    override val path: String,
    val durationMs: Long,
    /** true — записано микрофоном (голосовой ввод), false — выбранный файл. */
    val isVoice: Boolean,
) : Attachment

/** Откуда пришёл текст. */
enum class TextOrigin { Selection, Share, Clipboard }

/**
 * Текст из другого приложения — выделенный, отправленный через «Поделиться» или скопированный.
 * Файла нет: текст короткий (не длиннее [ChatViewModel.MAX_CAPTURE_CHARS]) и лежит прямо в сообщении.
 */
data class TextAttachment(
    override val id: Long,
    val text: String,
    /** Название приложения-источника («Telegram») или null, если система его не сообщила. */
    val source: String?,
    val origin: TextOrigin,
    /** Исходный текст был длиннее лимита, взято только начало. */
    val truncated: Boolean = false,
    override val path: String = "",
) : Attachment {
    /** Подпись над цитатой. */
    fun caption(): String {
        val base = when {
            origin == TextOrigin.Clipboard -> "Буфер обмена"
            source != null -> "Из «$source»"
            origin == TextOrigin.Share -> "Отправлено через «Поделиться»"
            else -> "Выделенный текст"
        }
        return if (truncated) "$base · начало текста" else base
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * Подготовка вложений под требования Gemma:
 *  - изображения уменьшаем (длинная сторона ≤ 1024 px) и учитываем поворот из EXIF;
 *  - аудио приводим к WAV 16 кГц, моно, 16 бит и обрезаем до 30 секунд — это предел модели.
 */
object Media {
    const val SAMPLE_RATE = 16_000
    const val MAX_AUDIO_SECONDS = 30
    private const val MIN_AUDIO_MS = 300
    private const val MAX_IMAGE_SIDE = 1024
    private const val THUMB_SIDE = 512

    private fun dir(context: Context): File = File(context.cacheDir, "attachments").also { it.mkdirs() }

    /** Удалить все файлы вложений (новый чат, смена модели, запуск приложения). */
    fun clearAttachments(context: Context) {
        File(context.cacheDir, "attachments").listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun deleteFile(att: Attachment) {
        runCatching { File(att.path).delete() }
    }

    // ───────── Изображения ─────────

    fun processImage(context: Context, uri: Uri, id: Long): ImageAttachment {
        val resolver = context.contentResolver

        // 1. Узнаём размер, не загружая картинку целиком
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Не удалось прочитать изображение")

        // 2. Грубое уменьшение при чтении — чтобы большое фото не съело память
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Не удалось прочитать изображение")

        // 3. BitmapFactory не поворачивает фото по EXIF — делаем сами
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val bitmap = rotateAndFit(decoded, orientation)

        // 4. Сохраняем в PNG и делаем превью для чата
        val file = File(dir(context), "img_$id.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val k = THUMB_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val thumb = if (k < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * k).toInt().coerceAtLeast(1),
                (bitmap.height * k).toInt().coerceAtLeast(1),
                true,
            )
        } else bitmap
        if (thumb !== bitmap) bitmap.recycle()

        return ImageAttachment(id, file.absolutePath, thumb)
    }

    private fun rotateAndFit(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
        }
        val k = MAX_IMAGE_SIDE.toFloat() / max(src.width, src.height)
        if (k < 1f) m.postScale(k, k)
        if (m.isIdentity) return src
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    // ───────── Аудио ─────────

    class Decoded(val pcm: ShortArray, val truncated: Boolean)

    /** Аудиофайл, выбранный пользователем. Возвращает вложение и признак «обрезано до 30 секунд». */
    fun processAudioFile(context: Context, uri: Uri, id: Long): Pair<AudioAttachment, Boolean> {
        val decoded = decodeAudio(context, uri)
        if (decoded.pcm.size < SAMPLE_RATE * MIN_AUDIO_MS / 1000) throw IOException("Аудио слишком короткое")
        val file = File(dir(context), "audio_$id.wav")
        writeWav(file, decoded.pcm)
        val att = AudioAttachment(id, file.absolutePath, decoded.pcm.size * 1000L / SAMPLE_RATE, isVoice = false)
        return att to decoded.truncated
    }

    /** Запись с микрофона: на входе уже 16 кГц моно PCM. */
    fun saveVoice(context: Context, pcm: ShortArray, id: Long): AudioAttachment {
        val file = File(dir(context), "voice_$id.wav")
        writeWav(file, pcm)
        return AudioAttachment(id, file.absolutePath, pcm.size * 1000L / SAMPLE_RATE, isVoice = true)
    }

    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    /**
     * Раскодировать любой аудиоформат, который понимает Android (mp3, m4a, ogg, wav, flac, amr…),
     * в моно 16 кГц. Декодируем не дольше нужного — длинные файлы не читаются целиком.
     */
    fun decodeAudio(context: Context, uri: Uri): Decoded {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var track = -1
            var found: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    found = f
                    break
                }
            }
            val format = found ?: throw IOException("В файле нет звуковой дорожки")
            extractor.selectTrack(track)

            var rate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: SAMPLE_RATE
            var channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var mono = ShortArray(rate * 4)
            var count = 0
            var limit = rate * MAX_AUDIO_SECONDS
            var truncated = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = System.nanoTime() + 60_000_000_000L

            while (!outputDone) {
                if (System.nanoTime() > deadline) throw IOException("Не удалось раскодировать аудио")

                if (!inputDone) {
                    val inIdx = c.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val size = extractor.readSampleData(c.getInputBuffer(inIdx)!!, 0)
                        if (size < 0) {
                            c.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = c.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val buf = c.getOutputBuffer(outIdx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val ch = max(channels, 1)
                        val frames = shorts.remaining() / ch
                        if (count + frames > mono.size) mono = mono.copyOf(max(mono.size * 2, count + frames))
                        for (f in 0 until frames) { // сводим каналы в один
                            var sum = 0
                            for (k in 0 until ch) sum += shorts.get()
                            mono[count++] = (sum / ch).toShort()
                        }
                    }
                    c.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    if (count > limit) {
                        truncated = true
                        outputDone = true
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Реальные параметры звука известны только после запуска декодера
                    val f = c.outputFormat
                    f.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.let { rate = it }
                    f.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.let { channels = it }
                    limit = rate * MAX_AUDIO_SECONDS
                }
            }

            val length = if (truncated) limit else count
            val pcm = resample(mono, length, rate)
            val maxLen = SAMPLE_RATE * MAX_AUDIO_SECONDS
            return Decoded(if (pcm.size > maxLen) pcm.copyOf(maxLen) else pcm, truncated)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Приведение к 16 кГц: при понижении частоты усредняем отсчёты (чтобы не было «звона»), при повышении — интерполируем. */
    private fun resample(src: ShortArray, len: Int, rate: Int): ShortArray {
        if (rate == SAMPLE_RATE) return src.copyOf(len)
        val outLen = (len.toLong() * SAMPLE_RATE / rate).toInt()
        val out = ShortArray(outLen)
        val ratio = rate.toDouble() / SAMPLE_RATE
        if (ratio > 1.0) {
            for (i in 0 until outLen) {
                val start = (i * ratio).toInt()
                val end = minOf(len, max(start + 1, ((i + 1) * ratio).toInt()))
                var sum = 0L
                for (j in start until end) sum += src[j]
                out[i] = (sum / (end - start)).toShort()
            }
        } else {
            for (i in 0 until outLen) {
                val pos = i * ratio
                val i0 = pos.toInt()
                val frac = pos - i0
                val s0 = src[i0].toInt()
                val s1 = if (i0 + 1 < len) src[i0 + 1].toInt() else s0
                out[i] = (s0 + (s1 - s0) * frac).toInt().toShort()
            }
        }
        return out
    }

    /** WAV: PCM, 16 кГц, моно, 16 бит. */
    private fun writeWav(file: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + dataLen)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1) // PCM
        bb.putShort(1) // моно
        bb.putInt(SAMPLE_RATE)
        bb.putInt(SAMPLE_RATE * 2) // байт в секунду
        bb.putShort(2) // байт на отсчёт
        bb.putShort(16) // бит на отсчёт
        bb.put("data".toByteArray())
        bb.putInt(dataLen)
        for (s in pcm) bb.putShort(s)
        file.writeBytes(bb.array())
    }
}
