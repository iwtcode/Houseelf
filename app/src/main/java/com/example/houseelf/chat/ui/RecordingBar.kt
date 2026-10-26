package com.example.houseelf.chat.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.houseelf.media.Media
import com.example.houseelf.media.formatDuration
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.components.DuoProgressBar
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/** Панель на время записи голоса. */
@Composable
internal fun RecordingBar(elapsedMs: Long, onStop: () -> Unit, onCancel: () -> Unit) {
    val maxMs = Media.MAX_AUDIO_SECONDS * 1000L
    val pulse = rememberInfiniteTransition(label = "rec")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "recDot",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DuoProgressBar(progress = (elapsedMs.toFloat() / maxMs).coerceIn(0f, 1f), color = Duo.Red)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .clip(CircleShape)
                    .background(Duo.Red),
            )
            Text(
                "Запись ${formatDuration(elapsedMs)} / ${formatDuration(maxMs)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            DuoIconButton(DuoIcons.Close, "Отменить запись", onCancel)
            DuoButton("Готово", onStop, Modifier.width(120.dp), icon = DuoIcons.Check, height = 44.dp)
        }
    }
}
