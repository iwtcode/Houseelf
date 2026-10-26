package com.example.houseelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/** Нижняя навигация. 0 — Сегодня, 1 — Домовой, 2 — Мозг. */
@Composable
fun DuoNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Сегодня" to DuoIcons.Home,
        "Домовой" to DuoIcons.Chat,
        "Мозг" to DuoIcons.Chip,
    )
    Column(Modifier.background(Color.White)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Duo.Line),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                val shape = RoundedCornerShape(14.dp)
                val tint = if (isSelected) Duo.TealDark else Duo.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(shape)
                        .background(if (isSelected) Duo.TealSoft else Color.Transparent)
                        .border(2.dp, if (isSelected) Duo.Teal else Color.Transparent, shape)
                        .clickable(role = Role.Tab) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.second, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(item.first, color = tint, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
