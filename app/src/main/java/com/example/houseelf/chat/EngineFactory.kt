package com.example.houseelf.chat

import com.example.houseelf.model.ModelCatalog
import com.example.houseelf.model.ModelProfile
import com.example.houseelf.model.ModelStorage
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

/** Движок, который удалось запустить, и то, на чём он работает. */
internal class LoadedEngine(
    val engine: Engine,
    val backend: String,
    val vision: Boolean,
    val audio: Boolean,
)

/** Один вариант запуска: на чём считаем и какие «органы чувств» подключаем. */
private data class Mode(val gpu: Boolean, val vision: Boolean, val audio: Boolean)

/**
 * Создаёт и инициализирует движок LiteRT-LM для файла модели. Вызывать не из UI-потока.
 *
 * Режим зависит от модели ([ModelProfile]) и от «уровня облегчения» — он растёт, если модель уже
 * упала с нехваткой памяти ([ModelStorage.degradeLevel]):
 *  - уровень 0: всё, что умеет модель; для Gemma сначала GPU, потом CPU;
 *  - уровень 1: только CPU, без звука, контекст не больше 4096 токенов;
 *  - уровень 2: только CPU и только текст, контекст 3072 токена.
 */
internal class EngineFactory(
    private val storage: ModelStorage,
    private val cacheDir: String,
    private val totalRamBytes: () -> Long,
) {

    fun create(fileName: String): LoadedEngine {
        val path = storage.modelFile(fileName).absolutePath
        val profile = ModelCatalog.profileFor(fileName)
        val level = storage.degradeLevel(fileName).coerceIn(0, MAX_LEVEL)
        val context = contextFor(profile, level)

        var lastError: Throwable? = null
        for (mode in modes(profile, level)) {
            try {
                val engine = initEngine(path, mode, context)
                return LoadedEngine(engine, if (mode.gpu) "GPU" else "CPU", mode.vision, mode.audio)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Не удалось запустить движок")
    }

    /**
     * Запоминает, что для этой модели нужен более лёгкий режим. Следующая загрузка пойдёт уже в нём.
     * Возвращает false, если легче некуда.
     */
    fun degrade(fileName: String): Boolean {
        val level = storage.degradeLevel(fileName)
        if (level >= MAX_LEVEL) return false
        storage.setDegradeLevel(fileName, level + 1)
        return true
    }

    private fun modes(profile: ModelProfile, level: Int): List<Mode> {
        val vision = profile.vision && level <= 1
        val audio = profile.audio && level == 0
        val gpu = profile.allowGpu && level == 0
        val result = mutableListOf<Mode>()
        if (gpu) result += Mode(gpu = true, vision = vision, audio = audio)
        result += Mode(gpu = false, vision = vision, audio = audio)
        // Если открыта текстовая модель без «глаз» и «ушей» — пробуем то же без них
        if (vision || audio) {
            if (gpu) result += Mode(gpu = true, vision = false, audio = false)
            result += Mode(gpu = false, vision = false, audio = false)
        }
        return result
    }

    /**
     * Размер контекста (сумма токенов запроса и ответа). Без явного значения движок берёт максимум из файла
     * модели, и на CPU под него выделяется столько памяти, что первый же запрос падает с
     * «Failed to allocate tensors». Поэтому ограничиваем.
     * null — оставить как в файле (у Qwen3.5 там и так 4096).
     */
    private fun contextFor(profile: ModelProfile, level: Int): Int? {
        if (!profile.customContext) return null
        val base = if (totalRamBytes() >= BIG_PHONE_RAM) CONTEXT_BIG_PHONE else CONTEXT_DEFAULT
        return when (level) {
            0 -> base
            1 -> minOf(base, CONTEXT_DEFAULT)
            else -> CONTEXT_MIN
        }
    }

    private fun initEngine(path: String, mode: Mode, maxNumTokens: Int?): Engine {
        val main = if (mode.gpu) Backend.GPU() else Backend.CPU()
        val config = EngineConfig(
            modelPath = path,
            backend = main,
            visionBackend = if (mode.vision) (if (mode.gpu) Backend.GPU() else Backend.CPU()) else null,
            audioBackend = if (mode.audio) Backend.CPU() else null, // звуковой энкодер работает на CPU
            maxNumTokens = maxNumTokens,
            cacheDir = cacheDir,
        )
        val engine = Engine(config)
        try {
            engine.initialize() // долгая операция — поэтому вызываем не в UI-потоке
        } catch (t: Throwable) {
            runCatching { engine.close() }
            throw t
        }
        return engine
    }

    companion object {
        private const val MAX_LEVEL = 2
        private const val CONTEXT_DEFAULT = 4096
        private const val CONTEXT_BIG_PHONE = 8192
        private const val CONTEXT_MIN = 3072
        private const val BIG_PHONE_RAM = 10L * 1024 * 1024 * 1024 // Android показывает 12 ГБ как ~11

        /** Ошибка похожа на нехватку памяти: движок после неё лучше пересоздать в облегчённом режиме. */
        fun isResourceFailure(t: Throwable): Boolean {
            val text = generateSequence(t) { it.cause }.mapNotNull { it.message }.joinToString(" ")
            return text.contains("Failed to allocate", ignoreCase = true) ||
                text.contains("Failed to invoke the compiled model", ignoreCase = true) ||
                text.contains("Task failed with state", ignoreCase = true) ||
                text.contains("out of memory", ignoreCase = true)
        }
    }
}
