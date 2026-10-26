package com.example.houseelf.chat

import com.example.houseelf.model.ModelStorage
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

/** Движок, который удалось запустить, и то, на чём он работает. */
internal class LoadedEngine(val engine: Engine, val backend: String, val media: Boolean)

/** Создаёт и инициализирует движок LiteRT-LM для файла модели. Вызывать не из UI-потока. */
internal class EngineFactory(
    private val storage: ModelStorage,
    private val cacheDir: String,
) {

    /**
     * Пробуем по порядку: GPU и CPU с поддержкой изображений и звука, затем то же без неё
     * (на случай, если открыта текстовая модель без «глаз» и «ушей»).
     */
    fun create(fileName: String): LoadedEngine {
        val path = storage.modelFile(fileName).absolutePath

        var lastError: Throwable? = null
        for ((gpu, media) in listOf(true to true, false to true, true to false, false to false)) {
            try {
                return LoadedEngine(initEngine(path, gpu, media), if (gpu) "GPU" else "CPU", media)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Не удалось запустить движок")
    }

    private fun initEngine(path: String, gpu: Boolean, media: Boolean): Engine {
        val main = if (gpu) Backend.GPU() else Backend.CPU()
        val config = if (media) {
            EngineConfig(
                modelPath = path,
                backend = main,
                visionBackend = if (gpu) Backend.GPU() else Backend.CPU(),
                audioBackend = Backend.CPU(), // звуковой энкодер работает на CPU
                cacheDir = cacheDir,
            )
        } else {
            EngineConfig(modelPath = path, backend = main, cacheDir = cacheDir)
        }
        val engine = Engine(config)
        try {
            engine.initialize() // долгая операция — поэтому вызываем не в UI-потоке
        } catch (t: Throwable) {
            runCatching { engine.close() }
            throw t
        }
        return engine
    }
}
