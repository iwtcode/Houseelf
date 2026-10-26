package com.example.houseelf.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.chat.Phase
import com.example.houseelf.chat.UiState
import com.example.houseelf.chat.ui.EventActions
import com.example.houseelf.chat.ui.MessageItem
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.model.modelDisplayName
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.components.DuoProgressBar
import com.example.houseelf.ui.components.QuoteCard
import com.example.houseelf.ui.components.SuggestionChip
import com.example.houseelf.ui.components.TypingDots
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.hedgehog.Mood
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/** Готовые просьбы для выделенного текста: одно нажатие вместо набора. */
private val QuickPrompts = listOf(
    "В календарь" to "Внеси в календарь все встречи, дедлайны и события из этого текста.",
    "Коротко" to "Перескажи этот текст коротко.",
    "Выписать дела" to "Выпиши из этого текста список дел со сроками.",
)

/**
 * Мини-чат поверх чужого приложения: затемнение, снизу лист с выделенным текстом (вложением),
 * подсказками и полем ввода. Нажатие на затемнение закрывает лист.
 */
@Composable
fun CaptureOverlay(
    state: UiState,
    /** Сообщения с этого номера — «этот» мини-чат; всё, что было раньше, остаётся в главном чате. */
    startId: Long,
    clipboardMissing: Boolean,
    onSend: (String) -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onPaste: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    onLoadModel: () -> Unit,
    onRetry: () -> Unit,
    eventActions: EventActions,
) {
    val scrimSource = remember { MutableInteractionSource() }
    val sheetSource = remember { MutableInteractionSource() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = scrimSource, indication = null, onClick = onClose),
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
                SheetContent(
                    state, startId, clipboardMissing, onSend, onRemoveAttachment,
                    onPaste, onOpenApp, onClose, onLoadModel, onRetry, eventActions,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SheetContent(
    state: UiState,
    startId: Long,
    clipboardMissing: Boolean,
    onSend: (String) -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onPaste: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    onLoadModel: () -> Unit,
    onRetry: () -> Unit,
    eventActions: EventActions,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val quote = state.draft.filterIsInstance<TextAttachment>().firstOrNull()
    val otherAttachments = state.draft.count { it !is TextAttachment }
    val inChat = state.phase == Phase.Chat
    val busy = state.generating || state.attaching || state.recording

    // Любая отправка прячет клавиатуру, как и в главном чате
    val submit: (String) -> Unit = { text ->
        onSend(text)
        input = ""
        focusManager.clearFocus()
        keyboard?.hide()
    }

    // Ручка листа
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 8.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(CircleShape)
            .background(Duo.Line),
    )

    // Шапка
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Hedgehog(
            40.dp,
            mood = if (state.generating || state.phase == Phase.Loading) Mood.Thinking else Mood.Happy,
        )
        Column(Modifier.weight(1f)) {
            Text("Домовой", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
            Text(
                when {
                    state.generating -> "Думает…"
                    inChat -> "● ${modelDisplayName(state.activeModel)}"
                    else -> "Мини-чат"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Duo.TealDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DuoIconButton(DuoIcons.External, "Открыть в приложении", onOpenApp)
        DuoIconButton(DuoIcons.Close, "Закрыть", onClose)
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(Duo.Line))

    // Переписка этого мини-чата (или состояние модели, пока чат недоступен)
    val shownMessages = state.messages.filter { it.id >= startId }
    // Пока нечего показывать, пустую область не оставляем: лист ужимается до вложения и поля ввода
    val compact = inChat && shownMessages.isEmpty() && quote != null
    val scroll = rememberScrollState()
    // Ответ растёт построчно: держим низ прокрутки у конца
    LaunchedEffect(scroll) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = if (compact) 0.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state.phase) {
            Phase.Chat -> {
                if (shownMessages.isEmpty() && quote == null) {
                    Hint("Выдели текст в любом приложении и выбери «Домовой» в меню или в «Поделиться». " +
                        "Или скопируй текст и вставь его из буфера.")
                }
                val lastId = state.messages.lastOrNull()?.id
                shownMessages.forEach { m ->
                    key(m.id) { MessageItem(m, eventActions, typing = state.generating && m.id == lastId) }
                }
            }
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
                    DuoButton("Открыть приложение", onOpenApp, Modifier.fillMaxWidth())
                } else {
                    DuoButton("Загрузить модель", onLoadModel, Modifier.fillMaxWidth())
                }
            }
            Phase.Downloading, Phase.Importing -> {
                Status(title = "Модель ещё готовится", detail = state.progressText)
                DuoProgressBar(state.progress, Modifier.fillMaxWidth())
            }
            Phase.Error -> {
                Status(title = "Не получилось запустить модель", detail = state.error)
                DuoButton("Повторить", onRetry, Modifier.fillMaxWidth())
            }
        }
    }

    // Нижняя панель: вложение, подсказки, ввод
    Box(Modifier.fillMaxWidth().height(2.dp).background(Duo.Line))
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val notice = state.notice ?: if (clipboardMissing) "В буфере обмена нет текста. Сначала скопируй его." else null
        if (notice != null) {
            Text(notice, color = Duo.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
        }

        // Выделенный текст — вложение будущего сообщения
        if (quote != null) {
            QuoteCard(
                quote,
                Modifier.fillMaxWidth(),
                expandable = true,
                onRemove = { onRemoveAttachment(quote.id) },
            )
        }
        if (otherAttachments > 0) {
            Text(
                "К сообщению приложено ещё вложений: $otherAttachments",
                style = MaterialTheme.typography.bodySmall,
                color = Duo.Muted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (inChat) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip("Из буфера") { if (!busy) onPaste() }
                if (quote != null && !busy) {
                    QuickPrompts.forEach { (label, prompt) ->
                        SuggestionChip(label) { submit(prompt) }
                    }
                }
            }

            val hasContent = input.isNotBlank() || state.draft.isNotEmpty()
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(bottom = 3.dp)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Duo.Bg)
                        .border(2.dp, Duo.Line, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (input.isEmpty()) {
                        Text(
                            if (quote != null) "Что сделать с текстом?" else "Сообщение",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Duo.Muted,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Duo.Ink),
                        cursorBrush = SolidColor(Duo.Teal),
                        maxLines = 4,
                    )
                }
                DuoIconButton(
                    DuoIcons.Send,
                    "Отправить",
                    { submit(input) },
                    filled = true,
                    enabled = hasContent && !busy,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Duo.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
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
