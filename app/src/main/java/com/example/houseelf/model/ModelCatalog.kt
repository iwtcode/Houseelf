package com.example.houseelf.model

/**
 * Что модель умеет в этом приложении. Это «потолок»: движок при загрузке может оказаться скромнее
 * (например, если не хватило памяти на фото), тогда ориентируемся на то, что реально поднялось.
 */
data class ModelProfile(
    /** Понимает изображения. */
    val vision: Boolean,
    /** Понимает звук (голосовые и аудиофайлы). */
    val audio: Boolean,
    /** Умеет нативно вызывать инструменты — без этого модель не может вносить события в календарь. */
    val tools: Boolean,
    /** Сколько изображений можно приложить к одному сообщению. */
    val maxImages: Int,
    /**
     * Нужен новый движок на каждый новый чат. У Qwen3.5 рекуррентное состояние не сбрасывается
     * при создании нового разговора на том же движке, и ответы «помнят» прошлый чат.
     */
    val freshEngineEachChat: Boolean,
    /** Пробовать ли GPU. У Qwen3.5 на Android GPU съедает память и на части телефонов роняет систему — только CPU. */
    val allowGpu: Boolean,
    /** Задаём ли размер контекста сами. У Qwen3.5 бюджет в 4096 токенов зашит в файл модели. */
    val customContext: Boolean,
)

/** Модель, которую приложение умеет скачать с Hugging Face. */
data class CatalogModel(
    val fileName: String,
    val title: String,
    val url: String,
    /** Размер для показа пользователю (реальный размер сервис берёт из ответа сервера). */
    val sizeLabel: String,
    val description: String,
    /** Сколько оперативной памяти нужно по замерам авторов конвертации, если они есть. */
    val ramHint: String?,
    val profile: ModelProfile,
)

object ModelCatalog {

    private val GEMMA = ModelProfile(
        vision = true, audio = true, tools = true, maxImages = 4,
        freshEngineEachChat = false, allowGpu = true, customContext = true,
    )
    private val QWEN_VISION = ModelProfile(
        vision = true, audio = false, tools = false, maxImages = 1,
        freshEngineEachChat = true, allowGpu = false, customContext = false,
    )
    private val QWEN_TEXT = ModelProfile(
        vision = false, audio = false, tools = false, maxImages = 0,
        freshEngineEachChat = true, allowGpu = false, customContext = false,
    )

    private fun hf(repo: String, file: String) =
        "https://huggingface.co/litert-community/$repo/resolve/main/$file"

    val models: List<CatalogModel> = listOf(
        CatalogModel(
            fileName = "gemma-4-E2B-it.litertlm",
            title = "Gemma 4 E2B",
            url = hf("gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm"),
            sizeLabel = "≈ 2,6 ГБ",
            description = "Лёгкая и быстрая. Понимает фото и голос, вносит события в календарь.",
            ramHint = null,
            profile = GEMMA,
        ),
        CatalogModel(
            fileName = "gemma-4-E4B-it.litertlm",
            title = "Gemma 4 E4B",
            url = hf("gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm"),
            sizeLabel = "≈ 3,7 ГБ",
            description = "Умнее E2B, но тяжелее. Понимает фото и голос, вносит события в календарь.",
            ramHint = null,
            profile = GEMMA,
        ),
        CatalogModel(
            fileName = "Qwen3.5-2B-VL_int8.litertlm",
            title = "Qwen3.5 2B (с фото)",
            url = hf("Qwen3.5-2B", "Qwen3.5-2B-VL_int8.litertlm"),
            sizeLabel = "≈ 3,2 ГБ",
            description = "Отвечает на тексты и понимает одно фото за сообщение. " +
                "Голос не слушает, в календарь события не вносит.",
            ramHint = "в работе занимает около 3,3 ГБ памяти",
            profile = QWEN_VISION,
        ),
        CatalogModel(
            fileName = "Qwen3.5-4B_int8.litertlm",
            title = "Qwen3.5 4B",
            url = hf("Qwen3.5-4B", "Qwen3.5-4B_int8.litertlm"),
            sizeLabel = "≈ 4,4 ГБ",
            description = "Только текст, зато самая сильная из списка. " +
                "Ни фото, ни голос, ни календарь не поддерживает.",
            ramHint = "в работе занимает около 5,5 ГБ памяти — нужен телефон с 8 ГБ и больше",
            profile = QWEN_TEXT,
        ),
    )

    fun byFileName(fileName: String?): CatalogModel? =
        models.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }

    /**
     * Профиль по имени файла. Для своих файлов, которых нет в каталоге, угадываем по имени:
     * Qwen — как Qwen, всё остальное — как Gemma (движок сам откатится к тексту, если фото и звука нет).
     */
    fun profileFor(fileName: String?): ModelProfile {
        byFileName(fileName)?.let { return it.profile }
        val name = fileName.orEmpty()
        return when {
            name.contains("qwen", ignoreCase = true) ->
                if (name.contains("vl", ignoreCase = true)) QWEN_VISION else QWEN_TEXT
            else -> GEMMA
        }
    }
}
