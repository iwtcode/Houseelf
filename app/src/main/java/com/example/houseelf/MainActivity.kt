package com.example.houseelf

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.houseelf.capture.CaptureActivity
import com.example.houseelf.capture.CaptureSetup
import com.example.houseelf.chat.ChatViewModel
import com.example.houseelf.ui.HouseelfApp
import com.example.houseelf.ui.theme.Duo
import com.example.houseelf.ui.theme.HouseelfTheme

class MainActivity : ComponentActivity() {
    // Один чат на весь процесс: его же использует мини-чат поверх других приложений (CaptureActivity)
    private val chatViewModel: ChatViewModel by lazy { sharedChatViewModel(application) }

    /** Растёт каждый раз, когда из мини-чата просят открыть именно чат, а не «Сегодня». */
    private var openChatTick by mutableIntStateOf(0)

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            if (intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true) openChatTick++
            CaptureSetup.offerQuickTile(this) // один раз: плитка «Домовой» в шторке
        }
        // Тема приложения всегда светлая, поэтому и значки в системных панелях всегда тёмные
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            HouseelfTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Duo.Bg) { innerPadding ->
                    HouseelfApp(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            // Обычное поведение клавиатуры: экран сразу занимает итоговый размер,
                            // а сама клавиатура выезжает/уезжает системной анимацией. Без imePadding(),
                            // который перестраивает весь экран на каждом кадре и «спотыкается» в конце.
                            .windowInsetsPadding(WindowInsets.imeAnimationTarget),
                        vm = chatViewModel,
                        openChatTick = openChatTick,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CHAT, false)) openChatTick++
    }

    override fun onStop() {
        super.onStop()
        // В фоне Android глушит микрофон: сохраняем то, что успели записать.
        // При повороте экрана запись не прерываем.
        if (!isChangingConfigurations) chatViewModel.stopRecording(discard = false)
    }

    companion object {
        /** Открыть чат, а не экран «Сегодня» (из мини-чата по кнопке «Открыть в приложении»). */
        const val EXTRA_OPEN_CHAT = "com.example.houseelf.extra.OPEN_CHAT"
    }
}
