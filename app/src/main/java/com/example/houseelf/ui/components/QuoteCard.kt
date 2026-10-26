package com.example.houseelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/**
 * Выделенный текст как вложение: цитата с полоской слева. В пузыре пользователя ([onDark]) она светлая
 * на бирюзовом, в черновике — бирюзовая на белом. С [expandable] нажатие раскрывает весь текст.
 */
@Composable
internal fun QuoteCard(
    att: TextAttachment,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    expandable: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    var expanded by remember(att.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val textColor = if (onDark) Color.White else Duo.Ink
    val captionColor = if (onDark) Color.White.copy(alpha = 0.8f) else Duo.TealDark
    val longText = att.text.length > 140 || att.text.count { it == '\n' } >= 3

    Row(
        modifier
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(if (onDark) Color.White.copy(alpha = 0.18f) else Duo.TealSoft)
            .then(if (expandable && longText) Modifier.clickable { expanded = !expanded } else Modifier),
    ) {
        Box(Modifier.fillMaxHeight().width(4.dp).background(if (onDark) Color.White else Duo.Teal))
        Column(
            (if (onRemove != null) Modifier.weight(1f) else Modifier)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(att.caption(), style = MaterialTheme.typography.labelMedium, color = captionColor, maxLines = 1)
            Text(
                att.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = if (expanded) Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()) else Modifier,
            )
            if (expandable && longText) {
                Text(
                    if (expanded) "Свернуть" else "Показать весь текст",
                    style = MaterialTheme.typography.labelMedium,
                    color = captionColor,
                )
            }
        }
        if (onRemove != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DuoIcons.Close, contentDescription = "Убрать текст", tint = Duo.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
