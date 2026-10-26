package com.example.houseelf.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * В макете — Nunito. Чтобы подключить её, положите Nunito в res/font и замените значение:
 * FontFamily(Font(R.font.nunito_bold, FontWeight.Bold), Font(R.font.nunito_black, FontWeight.Black)).
 */
val DuoFont: FontFamily = FontFamily.Default

// Плотные веса — часть характера интерфейса: заголовки Black, основной текст Bold.
val Typography = Typography(
    headlineMedium = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = DuoFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 14.sp),
)

