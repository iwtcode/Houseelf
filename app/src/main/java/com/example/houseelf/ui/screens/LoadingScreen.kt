package com.example.houseelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.hedgehog.Mood
import com.example.houseelf.ui.theme.Duo

@Composable
fun LoadingScreen(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Duo.Bg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hedgehog(96.dp, mood = Mood.Thinking, bob = true)
        if (text.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Duo.Muted, textAlign = TextAlign.Center)
        }
    }
}
