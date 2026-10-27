package com.example.houseelf.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.ui.theme.Duo

@Composable
internal fun DuoDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = Duo.Teal,
    onDismissRequest: () -> Unit = onDismiss,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Duo.Muted) },
        dismissButton = { DialogAction(dismissText, Duo.Muted, onDismiss) },
        confirmButton = { DialogAction(confirmText, confirmColor, onConfirm) },
    )
}

@Composable
internal fun DialogAction(text: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text.uppercase(), color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}
