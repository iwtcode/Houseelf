package com.example.houseelf.library

import androidx.compose.runtime.Composable
import com.example.houseelf.ui.components.DuoDialog

/** Скачивание начинается не по Wi-Fi: пользователь решает, ждать Wi-Fi или качать по мобильной сети. */
@Composable
fun NetworkChoiceDialog(
    sizeLabel: String,
    onWaitWifi: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    DuoDialog(
        title = "Нет подключения к Wi-Fi",
        text = "Модель весит $sizeLabel, а скачивание пойдёт через мобильный интернет — " +
            "это может израсходовать трафик. Подождать Wi-Fi или продолжить?",
        confirmText = "Продолжить",
        dismissText = "Ждать Wi-Fi",
        onConfirm = onContinue,
        onDismiss = onWaitWifi,
        onDismissRequest = onDismiss,
    )
}
