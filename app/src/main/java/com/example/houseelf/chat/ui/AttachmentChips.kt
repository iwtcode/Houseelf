package com.example.houseelf.chat.ui

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.houseelf.media.AudioAttachment
import com.example.houseelf.media.ImageAttachment
import com.example.houseelf.media.formatDuration
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

@Composable
internal fun ImageThumb(att: ImageAttachment, side: Dp, onRemove: (() -> Unit)? = null) {
    val bitmap = remember(att.id) { att.thumbnail.asImageBitmap() }
    Box(Modifier.size(side)) {
        Image(
            bitmap = bitmap,
            contentDescription = "Изображение",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DuoIcons.Close, contentDescription = "Убрать", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Аудио-вложение: кнопка воспроизведения, подпись и (в черновике) крестик. */
@Composable
internal fun AudioChip(att: AudioAttachment, onRemove: (() -> Unit)? = null, onDark: Boolean = false) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    fun stop() {
        runCatching { player?.release() }
        player = null
        playing = false
    }

    fun toggle() {
        if (playing) {
            stop()
            return
        }
        val mp = MediaPlayer()
        try {
            mp.setDataSource(att.path)
            mp.setOnCompletionListener { stop() }
            mp.prepare()
            mp.start()
            player = mp
            playing = true
        } catch (_: Throwable) {
            runCatching { mp.release() }
        }
    }

    DisposableEffect(Unit) { onDispose { stop() } }

    val shape = RoundedCornerShape(24.dp)
    val textColor = if (onDark) Color.White else Duo.Ink
    Row(
        Modifier
            .clip(shape)
            .background(if (onDark) Color.White.copy(alpha = 0.18f) else Duo.Bg)
            .then(if (onDark) Modifier else Modifier.border(2.dp, Duo.Line, shape))
            .padding(start = 6.dp, end = if (onRemove != null) 4.dp else 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (onDark) Color.White else Duo.Teal)
                .clickable { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) DuoIcons.Stop else DuoIcons.Play,
                contentDescription = if (playing) "Остановить" else "Слушать",
                tint = if (onDark) Duo.Teal else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            "${if (att.isVoice) "Голосовое" else "Аудио"} · ${formatDuration(att.durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
        if (onRemove != null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DuoIcons.Close, contentDescription = "Убрать", tint = Duo.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
