package com.example.houseelf.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.chat.UiState
import com.example.houseelf.model.modelDisplayName
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.hedgehog.Mood
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/** Строка под именем в шапке: что сейчас делает домовой и на какой он модели. */
fun UiState.chatSubtitle(): String = when {
    generating -> "Думает…"
    thinking -> "● ${modelDisplayName(activeModel)} · $backend · раздумья"
    else -> "● ${modelDisplayName(activeModel)} · $backend"
}

/**
 * Шапка чата — одна на главный чат и мини-чат. [leading] и [trailing] — кнопки, которые есть только у хозяина
 * шапки (например, «Назад» или «Закрыть»). Без [onOpenLibrary] имя не нажимается.
 */
@Composable
fun ChatHeader(
    state: UiState,
    actions: ChatActions,
    modifier: Modifier = Modifier,
    subtitle: String = state.chatSubtitle(),
    onOpenLibrary: (() -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading()
            Hedgehog(44.dp, mood = if (state.generating) Mood.Thinking else Mood.Happy)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = onOpenLibrary != null && !state.generating) { onOpenLibrary?.invoke() },
            ) {
                Text("Домовой", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Duo.TealDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Режим раздумий: включён — кнопка бирюзовая
            DuoIconButton(
                DuoIcons.Bulb,
                if (state.thinking) "Режим раздумий включён" else "Режим раздумий выключен",
                { actions.onSetThinking(!state.thinking) },
                filled = state.thinking,
                enabled = !state.generating,
            )
            DuoIconButton(DuoIcons.Plus, "Новый чат", actions.onNewChat, enabled = !state.generating)
            trailing()
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Duo.Line))
    }
}
