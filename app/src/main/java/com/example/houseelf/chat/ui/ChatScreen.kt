package com.example.houseelf.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.houseelf.chat.UiState
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/** Главный чат на весь экран. Те же шапка, список и поле ввода использует и мини-чат. */
@Composable
fun ChatScreen(
    state: UiState,
    actions: ChatActions,
    onOpenLibrary: () -> Unit,
    onBack: () -> Unit,
    eventActions: EventActions,
    messageActions: MessageActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Duo.Bg)) {
        ChatHeader(
            state = state,
            actions = actions,
            onOpenLibrary = onOpenLibrary,
            leading = { DuoIconButton(DuoIcons.Back, "Назад", onBack) },
        )

        val showSuggestions = state.showsSuggestions()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                ChatEmptyState(state.emptyChatPrompt(), Modifier.align(Alignment.Center))
            }
            ChatMessageList(
                state, eventActions, messageActions,
                modifier = Modifier.fillMaxSize(),
                // Снизу оставляем место под подсказки, чтобы они не закрывали последнее сообщение
                bottomPadding = if (showSuggestions) SuggestionsOverlayHeight + 12.dp else 12.dp,
            )
            // Без своего фона: сообщения видны сквозь полосу подсказок
            if (showSuggestions) {
                val send = rememberSendAndHideKeyboard(actions.onSend)
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DefaultSuggestions(send)
                }
            }
        }

        ChatComposer(state, actions, messageActions)
    }
}
