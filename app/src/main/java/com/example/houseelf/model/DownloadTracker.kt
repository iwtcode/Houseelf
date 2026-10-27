package com.example.houseelf.model

import kotlinx.coroutines.flow.MutableStateFlow

enum class DownloadPhase {
    WaitingNetwork, // нет сети
    WaitingWifi,    // ждём Wi-Fi (пользователь выбрал не качать по мобильной сети)
    Running,
    Retrying,       // сеть есть, но соединение не удалось — пробуем ещё раз
    Done,
    Failed,
}

/** Состояние скачивания модели. */
data class DownloadStatus(
    val phase: DownloadPhase,
    val downloadedBytes: Long,
    val totalBytes: Long, // -1, пока размер неизвестен
    val error: String? = null,
    /** Файл модели, который скачивается (по нему интерфейс знает, что открывать после загрузки). */
    val fileName: String? = null,
)

/** Связь между сервисом скачивания и интерфейсом. null — скачивание не идёт. */
object DownloadTracker {
    val status = MutableStateFlow<DownloadStatus?>(null)
}
