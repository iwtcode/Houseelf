package com.example.houseelf.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoKind
import com.example.houseelf.ui.components.DuoProgressBar
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.hedgehog.Mood
import com.example.houseelf.ui.theme.Duo

@Composable
fun ProgressScreen(
    title: String,
    progress: Float?,
    text: String,
    hint: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    extraActionText: String? = null,
    onExtraAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().background(Duo.Bg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hedgehog(96.dp, mood = Mood.Thinking, bob = true)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        DuoProgressBar(progress)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Duo.Muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        if (extraActionText != null && onExtraAction != null) {
            DuoButton(extraActionText, onExtraAction, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        DuoButton("Отмена", onCancel, Modifier.fillMaxWidth(), kind = DuoKind.Outline)
    }
}
