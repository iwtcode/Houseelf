package com.example.houseelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.houseelf.ui.theme.HouseelfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HouseelfTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HouseelfApp(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .imePadding()
                    )
                }
            }
        }
    }
}

@Composable
fun HouseelfApp(modifier: Modifier = Modifier, vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    // Системный выбор файла. Тип */*: у .litertlm нет собственного MIME-типа.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    // Системная кнопка «Назад» в списке моделей возвращает в чат, а не закрывает приложение
    BackHandler(enabled = state.phase == Phase.Library && state.activeModel != null) {
        vm.backToChat()
    }

    when (state.phase) {
        Phase.Checking, Phase.Loading -> LoadingScreen(
            text = if (state.phase == Phase.Loading) {
                "Открываю «${modelDisplayName(state.loadingModel)}»…\nПервый запуск может занять до минуты"
            } else "",
            modifier = modifier,
        )
        Phase.Library -> LibraryScreen(
            models = state.models,
            activeModel = state.activeModel,
            error = state.error,
            onSelect = vm::selectModel,
            onDelete = vm::deleteModel,
            onDownload = vm::startDownload,
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            onBack = vm::backToChat,
            modifier = modifier,
        )
        Phase.Downloading, Phase.Importing -> ProgressScreen(
            title = if (state.phase == Phase.Downloading) "Скачиваю модель" else "Загружаю модель",
            progress = state.progress,
            text = state.progressText,
            hint = if (state.phase == Phase.Downloading) "Скачивание идёт в фоне — приложение можно свернуть." else null,
            onCancel = vm::cancelSetup,
            modifier = modifier,
        )
        Phase.Error -> ErrorScreen(
            error = state.error.orEmpty(),
            onRetry = vm::retryLoad,
            onOpenLibrary = vm::openLibrary,
            modifier = modifier,
        )
        Phase.Chat -> ChatScreen(
            state = state,
            onSend = vm::send,
            onNewChat = vm::newChat,
            onOpenLibrary = vm::openLibrary,
            modifier = modifier,
        )
    }
}
