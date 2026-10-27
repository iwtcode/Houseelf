package com.example.houseelf.model

import java.util.Locale

/** Модель, лежащая на телефоне. */
data class ModelInfo(val fileName: String, val sizeBytes: Long) {
    val displayName: String get() = modelDisplayName(fileName)
}

/** Человекочитаемое имя модели по имени файла. */
fun modelDisplayName(fileName: String?): String {
    if (fileName == null) return ""
    ModelCatalog.byFileName(fileName)?.let { return it.title }
    return fileName.removeSuffix(ModelStorage.MODEL_EXT)
}

fun formatSize(bytes: Long): String {
    val gb = 1024.0 * 1024.0 * 1024.0
    val mb = 1024.0 * 1024.0
    return if (bytes >= gb) String.format(Locale.getDefault(), "%.1f ГБ", bytes / gb)
    else String.format(Locale.getDefault(), "%d МБ", (bytes / mb).toLong())
}
