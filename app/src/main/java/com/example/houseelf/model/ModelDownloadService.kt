package com.example.houseelf.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.example.houseelf.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Ошибка, при которой повторять попытку бессмысленно. */
private class FatalDownloadException(message: String) : IOException(message)

/**
 * Скачивает модель с Hugging Face в фоне.
 *
 * Почему не DownloadManager: большие файлы он сам ставит на паузу «до Wi-Fi», когда
 * телефон в мобильной сети, и приложение не может это отменить. Здесь решение принимает
 * пользователь: [ModelStorage.downloadWifiOnly] = true — ждём Wi-Fi, false — качаем по любой сети.
 *
 * Соединение идёт через сеть по умолчанию (так работает и VPN); «только Wi-Fi» проверяется по
 * признаку платной сети.
 *
 * Файл пишется в *.part и докачивается заголовком Range после обрыва сети или перезапуска.
 */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var total = -1L

    private lateinit var storage: ModelStorage
    private lateinit var notifications: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        storage = ModelStorage(this)
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Загрузка модели", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // После startForegroundService() нужно успеть вызвать startForeground()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, progressNotification("Подготовка…", 0L, -1L),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            // Система не разрешила фоновый запуск; при следующем открытии приложение запустит сервис само
            stopSelf()
            return START_NOT_STICKY
        }
        if (!storage.hasPendingDownload()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Повторный запуск (например, смена Wi-Fi → мобильная сеть) перезапускает загрузку с того же места
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin()
            runDownload()
        }
        return START_STICKY
    }

    /** Android 15+: у foreground-сервисов типа dataSync есть лимит по времени. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel()
        storage.clearDownloadState() // недокачанный файл остаётся — следующая загрузка продолжит с него
        DownloadTracker.status.value = DownloadStatus(
            DownloadPhase.Failed, storage.partFile().length(), total,
            "Система остановила скачивание из-за ограничения по времени. Нажмите «Скачать», чтобы продолжить.",
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ───────── Скачивание ─────────

    private suspend fun runDownload() {
        val part = storage.partFile()
        val target = storage.modelFile(ModelStorage.MODEL_FILE_NAME)
        val cm = getSystemService(ConnectivityManager::class.java)
        var failures = 0
        var lastError = ""
        total = -1L

        while (true) {
            currentCoroutineContext().ensureActive()
            val wifiOnly = storage.downloadWifiOnly
            if (!networkReady(cm, wifiOnly)) {
                publish(if (wifiOnly) DownloadPhase.WaitingWifi else DownloadPhase.WaitingNetwork, part.length())
                awaitNetwork(cm, wifiOnly)
            }
            if (failures > 0) publish(DownloadPhase.Retrying, part.length())
            val sizeBefore = part.length()
            try {
                downloadOnce(cm, wifiOnly, part)
                target.delete()
                if (!part.renameTo(target)) throw FatalDownloadException("Не удалось сохранить файл модели")
                finish(
                    DownloadStatus(DownloadPhase.Done, target.length(), target.length()),
                    "Модель загружена", "Gemma 4 E2B готова к работе", ok = true,
                )
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: FatalDownloadException) {
                part.delete()
                finish(
                    DownloadStatus(DownloadPhase.Failed, 0L, -1L, e.message),
                    "Не удалось скачать модель", e.message.orEmpty(), ok = false,
                )
                return
            } catch (e: IOException) {
                lastError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                if (part.length() > sizeBefore) failures = 0 // что-то докачали — это не «глухая» ошибка
                failures++
                if (failures > MAX_FAILURES) {
                    val msg = "Не удалось скачать модель: $lastError. Проверьте интернет или VPN и повторите — " +
                        "загрузка продолжится с того же места."
                    finish(DownloadStatus(DownloadPhase.Failed, part.length(), total, msg), "Не удалось скачать модель", msg, ok = false)
                    return
                }
                publish(DownloadPhase.Retrying, part.length())
                delay(RETRY_DELAY_MS * failures.coerceAtMost(5))
            }
        }
    }

    /** Одна попытка: докачивает файл до конца. Бросает IOException при обрыве. */
    private suspend fun downloadOnce(cm: ConnectivityManager, wifiOnly: Boolean, part: File) {
        var offset = if (part.exists()) part.length() else 0L
        // Обычное соединение через сеть по умолчанию — именно по ней идёт и трафик VPN.
        // Раньше соединение привязывалось к конкретной сети (Network.openConnection) и в обход VPN.
        val conn = URL(ModelStorage.MODEL_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 30_000 // через VPN и в эмуляторе соединение устанавливается дольше
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            // Без сжатия: иначе пропадает Content-Length и ломается докачка
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")

            val code = conn.responseCode
            val append: Boolean
            when (code) {
                HttpURLConnection.HTTP_OK -> { // сервер отдаёт файл с начала
                    offset = 0L
                    append = false
                    total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                }
                HttpURLConnection.HTTP_PARTIAL -> {
                    append = true
                    val len = conn.contentLengthLong
                    total = parseTotal(conn.getHeaderField("Content-Range"))
                        ?: if (len > 0) offset + len else -1L
                }
                416 -> { // Range Not Satisfiable: файл уже скачан целиком или .part испорчен
                    val known = parseTotal(conn.getHeaderField("Content-Range"))
                    if (known != null && known == offset) return
                    part.delete()
                    throw IOException("Недокачанный файл не подошёл, качаю заново")
                }
                else -> {
                    if (code in 400..499 && code != 408 && code != 429) {
                        throw FatalDownloadException(
                            "Сервер ответил кодом $code. Возможно, ссылка на модель устарела или нужен доступ."
                        )
                    }
                    throw IOException("HTTP $code")
                }
            }

            if (total > 0) {
                val need = total - (if (append) offset else 0L)
                val free = part.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (free < need) {
                    throw FatalDownloadException("Не хватает места на телефоне: нужно ещё ${formatSize(need)}.")
                }
            }

            publish(DownloadPhase.Running, offset)
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var done = offset
                    var lastPublish = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive() // позволяет отменить скачивание
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPublish >= 500) {
                            lastPublish = now
                            publish(DownloadPhase.Running, done)
                            // «Только Wi-Fi»: если сеть стала платной (Wi-Fi пропал), останавливаемся и ждём Wi-Fi
                            if (wifiOnly && cm.isActiveNetworkMetered) throw IOException("Wi-Fi пропал")
                        }
                    }
                    publish(DownloadPhase.Running, done)
                    if (total > 0 && done < total) throw IOException("Соединение оборвалось")
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTotal(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()

    /** Есть ли сейчас подходящая сеть: любая с интернетом или (wifiOnly) только без учёта трафика. */
    private fun networkReady(cm: ConnectivityManager, wifiOnly: Boolean): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        // isActiveNetworkMetered учитывает VPN: у VPN-сети своих флагов «без учёта трафика» может не быть
        return !wifiOnly || !cm.isActiveNetworkMetered
    }

    /**
     * Ждёт подходящую сеть, проверяя её раз в секунду. Проверка обычным опросом надёжнее
     * NetworkCallback: в эмуляторе и с VPN колбэки приходят не всегда, и ожидание зависало навсегда.
     * Само соединение потом идёт через сеть по умолчанию, а не привязывается к конкретной.
     */
    private suspend fun awaitNetwork(cm: ConnectivityManager, wifiOnly: Boolean) {
        while (!networkReady(cm, wifiOnly)) {
            delay(NETWORK_POLL_MS)
        }
    }

    // ───────── Состояние и уведомления ─────────

    private fun publish(phase: DownloadPhase, done: Long) {
        if (!storage.hasPendingDownload()) return // скачивание уже отменили
        DownloadTracker.status.value = DownloadStatus(phase, done, total)
        val text = when (phase) {
            DownloadPhase.WaitingWifi -> "Жду Wi-Fi…"
            DownloadPhase.WaitingNetwork -> "Жду сеть…"
            DownloadPhase.Retrying -> "Повторное подключение…"
            else -> sizeText(done, total)
        }
        notifications.notify(NOTIFICATION_ID, progressNotification(text, done, total))
    }

    /** Завершение (успех или ошибка): снимаем флаг, сообщаем интерфейсу, показываем итоговое уведомление. */
    private fun finish(status: DownloadStatus, title: String, text: String, ok: Boolean) {
        storage.clearDownloadState()
        DownloadTracker.status.value = status
        stopForeground(STOP_FOREGROUND_REMOVE)
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notifications.notify(RESULT_NOTIFICATION_ID, n)
        stopSelf()
    }

    private fun progressNotification(text: String, done: Long, total: Long): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Gemma 4 E2B")
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (total > 0) {
            builder.setProgress(100, ((done * 100) / total).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun sizeText(done: Long, total: Long): String {
        val mb = 1024L * 1024L
        return if (total > 0) "${done / mb} / ${total / mb} МБ" else "${done / mb} МБ"
    }

    companion object {
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1
        private const val RESULT_NOTIFICATION_ID = 2
        private const val MAX_FAILURES = 8
        private const val RETRY_DELAY_MS = 3_000L
        private const val NETWORK_POLL_MS = 1_000L
    }
}
