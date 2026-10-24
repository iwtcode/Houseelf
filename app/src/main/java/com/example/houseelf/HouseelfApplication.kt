package com.example.houseelf

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Приложение хранит [ChatViewModel] само, а не отдаёт его одной активити.
 * Так главный экран и мини-чат поверх других приложений ([CaptureActivity]) работают с одним
 * и тем же чатом и одной загруженной моделью: вторая копия модели в памяти телефону не по силам.
 */
class HouseelfApplication : Application(), ViewModelStoreOwner {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        // Домовой в верхнем ряду меню «Поделиться». Публикуем в фоне: это обращение к системе.
        Thread { runCatching { CaptureSetup.publishShareShortcut(this) } }.start()
    }
}

/** Общий на весь процесс чат. Вызывать только из главного потока. */
fun sharedChatViewModel(app: Application): ChatViewModel {
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(app) as T
    }
    return ViewModelProvider(app as ViewModelStoreOwner, factory)[ChatViewModel::class.java]
}
