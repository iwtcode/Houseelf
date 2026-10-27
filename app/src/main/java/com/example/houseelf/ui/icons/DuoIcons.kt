package com.example.houseelf.ui.icons

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Контурные иконки 24×24 из макета. Нарисованы путями, поэтому отдельная библиотека иконок не нужна.
private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (d in paths) {
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object DuoIcons {
    val Flame = icon("flame", "M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-6 1-9z")
    val Bolt = icon("bolt", "M13 2L5 14h6l-1 8 8-12h-6z")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Calendar = icon(
        "calendar",
        "M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
        "M4 10h16M9 3v4M15 3v4",
    )
    val Lock = icon(
        "lock",
        "M7 11h10a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z",
        "M8 11V8a4 4 0 0 1 8 0v3",
    )
    val Home = icon("home", "M4 11l8-7 8 7v9H4z")
    val Chat = icon("chat", "M4 5h16v11H9l-5 4z")
    val Chip = icon(
        "chip",
        "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        "M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4",
    )
    val Image = icon(
        "image",
        "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z",
        "M7.5 9a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        "M20 15l-5-5-8 8",
    )
    val Music = icon(
        "music",
        "M9 18V5l11-2v13",
        "M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
        "M14 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    )
    val Clip = icon("clip", "M20 11l-8 8a5 5 0 0 1-7-7l9-9a3.5 3.5 0 0 1 5 5l-9 9a2 2 0 0 1-3-3l8-8")
    val Mic = icon(
        "mic",
        "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z",
        "M5 11a7 7 0 0 0 14 0M12 18v3",
    )
    val Send = icon("send", "M4 12l16-8-6 16-3-7z")
    val Trash = icon("trash", "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13")
    val Download = icon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Folder = icon("folder", "M4 7h6l2 2h8v10H4z")
    val Play = icon("play", "M8 5l11 7-11 7z")
    val Stop = icon("stop", "M7 7h10v10H7z")
    val Pin = icon(
        "pin",
        "M12 21s-6-5.5-6-11a6 6 0 0 1 12 0c0 5.5-6 11-6 11z",
        "M10 10a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
    )
    val External = icon("external", "M14 4h6v6M20 4l-9 9M18 14v5H5V6h5")
    val Bulb = icon(
        "bulb",
        "M12 2.5a6.5 6.5 0 0 0-3.6 11.9c.7.5 1.1 1.2 1.1 2.1h5c0-.9.4-1.6 1.1-2.1A6.5 6.5 0 0 0 12 2.5z",
        "M9.5 19.5h5M10.5 22h3",
    )
    val Copy = icon(
        "copy",
        "M11 9h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z",
        "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1",
    )
    val Pencil = icon("pencil", "M4 20l1-5L16 4l4 4L9 19z", "M13.5 6.5l4 4")
    val Refresh = icon("refresh", "M22 4v6h-6", "M20 15a9 9 0 1 1-2.1-9.4L22 10")
}
