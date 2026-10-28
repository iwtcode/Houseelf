package com.example.houseelf.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.houseelf.chat.Phase
import com.example.houseelf.chat.UiState
import com.example.houseelf.chat.ui.ChatActions
import com.example.houseelf.chat.ui.ChatComposer
import com.example.houseelf.chat.ui.ChatEmptyState
import com.example.houseelf.chat.ui.ChatHeader
import com.example.houseelf.chat.ui.ChatMessageList
import com.example.houseelf.chat.ui.DefaultSuggestions
import com.example.houseelf.chat.ui.EventActions
import com.example.houseelf.chat.ui.MessageActions
import com.example.houseelf.chat.ui.rememberSendAndHideKeyboard
import com.example.houseelf.chat.ui.chatSubtitle
import com.example.houseelf.chat.ui.showsSuggestions
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.model.modelDisplayName
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.components.DuoProgressBar
import com.example.houseelf.ui.components.SuggestionChip
import com.example.houseelf.ui.components.TypingDots
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/** Готовые просьбы для выделенного текста: одно нажатие вместо набора. */
private val QuickPrompts = listOf(
    "В календарь" to "Внеси в календарь все встречи, дедлайны и события из этого текста.",
    "Коротко" to "Перескажи этот текст коротко.",
    "Выписать дела" to "Выпиши из этого текста список дел со сроками.",
)

private const val EMPTY_HINT = "Выдели текст в любом приложении и выбери «Домовой.Чат» в меню или в «Поделиться». " +
    "Или скопируй текст и вставь его из буфера."

/** Что делает мини-чат сверх обычного чата: закрывается, открывает приложение, берёт текст из буфера. */
class OverlayActions(
    val onPaste: () -> Unit,
    val onOpenApp: () -> Unit,
    val onClose: () -> Unit,
    val onLoadModel: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * Мини-чат поверх чужого приложения: затемнение и снизу лист с тем же чатом, что и на главном экране
 * (общие шапка, переписка и поле ввода). Нажатие на затемнение закрывает лист.
 */
@Composable
fun CaptureOverlay(
    state: UiState,
    clipboardMissing: Boolean,
    actions: ChatActions,
    messageActions: MessageActions,
    eventActions: EventActions,
    overlayActions: OverlayActions,
) {
    val scrimSource = remember { MutableInteractionSource() }
    val sheetSource = remember { MutableInteractionSource() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = scrimSource, indication = null, onClick = overlayActions.onClose),
    ) {
        val maxSheet = maxHeight * 0.9f
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        ) {
            Column(
                Modifier
                    // Лист поднимается над клавиатурой; отступ под жестовую панель — только когда клавиатуры нет
                    .imePadding()
                    .heightIn(max = maxSheet)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Duo.Bg)
                    // Нажатия по листу не должны доходить до затемнения и закрывать его
                    .clickable(interactionSource = sheetSource, indication = null) {}
                    .navigationBarsPadding(),
            ) {
                SheetContent(state, clipboardMissing, actions, messageActions, eventActions, overlayActions)
            }
        }
    }
}

@Composable
private fun ColumnScope.SheetContent(
    state: UiState,
    clipboardMissing: Boolean,
    actions: ChatActions,
    messageActions: MessageActions,
    eventActions: EventActions,
    overlay: OverlayActions,
) {
    val inChat = state.phase == Phase.Chat

    SheetHandle()
    ChatHeader(
        state = state,
        actions = actions,
        subtitle = if (inChat) state.chatSubtitle() else "Мини-чат",
        trailing = {
            DuoIconButton(DuoIcons.External, "Открыть в приложении", overlay.onOpenApp)
            DuoIconButton(DuoIcons.Close, "Закрыть", overlay.onClose)
        },
    )

    if (!inChat) {
        PhaseStatus(state, overlay, Modifier.weight(1f, fill = false))
        return
    }

    val hasQuote = state.draft.any { it is TextAttachment }
    when {
        state.messages.isNotEmpty() ->
            ChatMessageList(state, eventActions, messageActions, Modifier.weight(1f, fill = false))
        // Пока нечего показывать, пустую область не оставляем: лист ужимается до вложения и поля ввода
        !hasQuote -> ChatEmptyState(EMPTY_HINT, Modifier.weight(1f, fill = false))
    }

    ChatComposer(
        state = state,
        actions = actions,
        messageActions = messageActions,
        placeholder = if (hasQuote) "Что сделать с текстом?" else "Сообщение",
        notice = state.notice ?: if (clipboardMissing) "В буфере обмена нет текста. Сначала скопируй его." else null,
        expandableQuotes = true,
        aboveInput = { Suggestions(state, hasQuote, actions, overlay.onPaste) },
    )
}

/** Подсказки мини-чата: текст из буфера, просьбы к выделенному тексту и общие подсказки чата. */
@Composable
private fun Suggestions(state: UiState, hasQuote: Boolean, actions: ChatActions, onPaste: () -> Unit) {
    val busy = state.generating || state.attaching || state.recording
    val send = rememberSendAndHideKeyboard(actions.onSend)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip("Из буфера") { if (!busy) onPaste() }
        if (hasQuote && !busy) {
            QuickPrompts.forEach { (label, prompt) -> SuggestionChip(label) { send(prompt) } }
        }
        if (state.showsSuggestions()) DefaultSuggestions(send)
    }
}

/** Пока чат недоступен, лист показывает, что происходит с моделью. */
@Composable
private fun PhaseStatus(state: UiState, overlay: OverlayActions, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state.phase) {
            Phase.Checking, Phase.Loading -> Status(
                title = "Домовой просыпается…",
                detail = state.loadingModel?.let { "Открываю «${modelDisplayName(it)}»" },
                showDots = true,
            )
            Phase.Library -> {
                Status(
                    title = "Модель не загружена",
                    detail = if (state.models.isEmpty()) "Сначала скачай или выбери модель в приложении"
                    else "Текст уже прикреплён — загрузи модель, и можно спрашивать",
                )
                if (state.models.isEmpty()) {
                    DuoButton("Открыть приложение", overlay.onOpenApp, Modifier.fillMaxWidth())
                } else {
                    DuoButton("Загрузить модель", overlay.onLoadModel, Modifier.fillMaxWidth())
                }
            }
            Phase.Downloading, Phase.Importing -> {
                Status(title = "Модель ещё готовится", detail = state.progressText)
                DuoProgressBar(state.progress, Modifier.fillMaxWidth())
            }
            Phase.Error -> {
                Status(title = "Не получилось запустить модель", detail = state.error)
                DuoButton("Повторить", overlay.onRetry, Modifier.fillMaxWidth())
            }
            Phase.Chat -> Unit
        }
    }
}

/** Ручка листа. */
@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().background(Color.White).padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Duo.Line))
    }
}

@Composable
private fun Status(title: String, detail: String? = null, showDots: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Duo.Ink, textAlign = TextAlign.Center)
        if (!detail.isNullOrBlank()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Duo.Muted, textAlign = TextAlign.Center)
        }
        if (showDots) TypingDots()
    }
}
