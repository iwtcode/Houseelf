package com.example.houseelf.event

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.houseelf.sharedChatViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Работа Домового.Событие. Живёт на переднем плане, пока модель разбирает текст: иначе система
 * может остановить процесс, едва пользователь уйдёт из приложения. В конце присылает итог уведомлением.
 */
class EventService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifier: EventNotifier

    override fun onCreate() {
        super.onCreate()
        notifier = EventNotifier(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Система требует выйти на передний план сразу, даже если работы не окажется
        ServiceCompat.startForeground(
            this, EventNotifier.FOREGROUND_ID, notifier.progress(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        val text = intent?.getStringExtra(EXTRA_TEXT)
        if (text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                process(text)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun process(text: String) {
        try {
            val runner = QuickEventRunner(application, sharedChatViewModel(application))
            notifier.showResult(runner.run(text))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            notifier.showMessage("Не получилось: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    companion object {
        private const val EXTRA_TEXT = "com.example.houseelf.extra.EVENT_TEXT"

        fun start(context: Context, text: String) {
            val intent = Intent(context, EventService::class.java).putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
