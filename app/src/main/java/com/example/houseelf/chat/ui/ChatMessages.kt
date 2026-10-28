package com.example.houseelf.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.houseelf.chat.UiState
import com.example.houseelf.ui.components.SuggestionChip
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.theme.Duo

/** Высота полосы подсказок над полем ввода: сама подсказка (40) и отступ под ней (10). */
val SuggestionsOverlayHeight: Dp = 50.dp

/** Подсказки показываем после ответа домового или в пустом чате; пока он пишет или идёт запись — прячем. */
fun UiState.showsSuggestions(): Boolean =
    !generating && !recording && (messages.isEmpty() || !messages.last().fromUser)

/** Список сообщений — общий для главного чата и мини-чата. */
@Composable
fun ChatMessageList(
    state: UiState,
    eventActions: EventActions,
    messageActions: MessageActions,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 12.dp,
) {
    val listState = rememberLazyListState()

    // Список перевёрнут (reverseLayout): индекс 0 — самое нижнее сообщение,
    // поэтому растущий ответ модели остаётся прижатым к низу экрана.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val lastId = state.messages.lastOrNull()?.id
        items(state.messages.asReversed(), key = { it.id }) { message ->
            MessageItem(
                message, eventActions,
                typing = state.generating && message.id == lastId,
                actions = messageActions,
                isLast = message.id == lastId,
                busy = state.generating,
            )
        }
    }
}

/** Что написать в пустом чате, исходя из того, что умеет загруженная модель. */
fun UiState.emptyChatPrompt(): String = when {
    audioSupported -> "Напиши домовому, пришли фото\nили запиши голосовое"
    imagesSupported -> "Напиши домовому\nили пришли фото"
    else -> "Напиши что-нибудь домовому"
}

/** Пустой чат: ёж и подсказка. */
@Composable
fun ChatEmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hedgehog(96.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Duo.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Готовые просьбы, общие для всех мест, где есть чат. Каждая отправляется сразу, набранный текст не трогает. */
@Composable
fun RowScope.DefaultSuggestions(onSend: (String) -> Unit) {
    SuggestionChip("Что на сегодня?") { onSend("Что на сегодня?") }
    SuggestionChip("Разбить дело на шаги") {
        onSend("Разбей моё ближайшее дело на шаги. Найди его в календаре на неделю.")
    }
}
