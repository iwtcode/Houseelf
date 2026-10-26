package com.example.houseelf.ui.screens

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
import androidx.compose.ui.unit.dp
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoCard
import com.example.houseelf.ui.components.DuoKind
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.hedgehog.Mood
import com.example.houseelf.ui.theme.Duo

@Composable
fun ErrorScreen(
    error: String,
    onRetry: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().background(Duo.Bg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hedgehog(96.dp, mood = Mood.Sad)
        Spacer(Modifier.height(16.dp))
        Text("Что-то пошло не так", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        DuoCard(Modifier.fillMaxWidth(), borderColor = Duo.Red) {
            Text(
                error,
                Modifier.padding(14.dp),
                color = Duo.Red,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(28.dp))
        DuoButton("Повторить", onRetry, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        DuoButton("Выбрать другую модель", onOpenLibrary, Modifier.fillMaxWidth(), kind = DuoKind.Outline)
    }
}
