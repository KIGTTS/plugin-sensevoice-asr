// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.sensevoice

import com.k2fsa.sherpa.onnx.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Offline configuration ported from KIGTTS AsrEngine; endpointing belongs entirely to this plugin. */
internal class SenseVoiceEngine(
    private val modelDirectory: File,
    private val language: () -> String,
    private val text: (String, Map<String, String>) -> String,
) : ControlledAsrEngine {
    private var managed = false
    private var cached: OfflineRecognizer? = null
    private var resourceKey: String? = null
    override suspend fun control(command: AsrControl): Boolean = withContext(Dispatchers.IO) {
        session.withLock {
            when (command.op) {
                AsrControlOp.PREPARE -> { managed = true; recognizer() }
                AsrControlOp.RELEASE -> { managed = false; release() }
                AsrControlOp.SET_RESIDENT -> Unit
            }
            true
        }
    }
    override suspend fun close() { control(AsrControl(AsrControlOp.RELEASE)) }
    private fun release() { cached?.release(); cached = null; resourceKey = null }
    private fun recognizer(): OfflineRecognizer {
        if (!managed) return createRecognizer()
        val files = ModelFiles.find(modelDirectory)
            ?: throw EngineException(EngineError.NOT_CONFIGURED, text("error.model_missing", emptyMap()))
        val key = "${files.model.path}:${files.model.lastModified()}:${files.model.length()}:${files.tokens.lastModified()}:${language()}"
        if (resourceKey != key) { release(); cached = createRecognizer(); resourceKey = key }
        return requireNotNull(cached)
    }
    private val session = Mutex()
    override val info get() = AsrInfo(streaming = StreamingKind.SEGMENTED,
        availability = if (ModelFiles.find(modelDirectory) != null) EngineAvailability.READY else EngineAvailability.NEEDS_RESOURCES)
    override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult = withContext(Dispatchers.IO) {
        validate(audio)
        if (audio.bytes.isEmpty()) return@withContext RecognitionResult("")
        require(audio.bytes.size <= 16_000 * 2 * info.maxBatchSeconds) {
            text("error.batch_length", mapOf("seconds" to info.maxBatchSeconds.toString()))
        }
        session.withLock {
            val recognizer = recognizer()
            try { transcribe(recognizer, audio) } finally { if (!managed) recognizer.release() }
        }
    }
    override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
        session.withLock {
            val recognizer = recognizer()
            try {
                emit(RecognitionEvent.Started)
                val endpoint = SpeechEndpoint()
                audio.collect { chunk ->
                    currentCoroutineContext().ensureActive(); validate(chunk)
                    endpoint.accept(chunk.bytes).forEach { utterance ->
                        emit(RecognitionEvent.Final(transcribe(recognizer, PcmAudio(utterance))))
                    }
                }
                endpoint.finish()?.let { emit(RecognitionEvent.Final(transcribe(recognizer, PcmAudio(it)))) }
            } finally { if (!managed) recognizer.release() }
        }
    }.flowOn(Dispatchers.IO)
    private fun createRecognizer(): OfflineRecognizer {
        val resources = ModelFiles.find(modelDirectory)
            ?: throw EngineException(EngineError.NOT_CONFIGURED, text("error.model_missing", emptyMap()))
        val feature = FeatureConfig(sampleRate = 16_000, featureDim = 80, dither = 0f)
        val model = OfflineModelConfig(senseVoice = OfflineSenseVoiceModelConfig(
            model = resources.model.absolutePath, language = language(), useInverseTextNormalization = true),
            tokens = resources.tokens.absolutePath, numThreads = 2, provider = "cpu", modelType = "sense_voice")
        return try { OfflineRecognizer(null, OfflineRecognizerConfig(featConfig = feature, modelConfig = model,
            decodingMethod = "greedy_search", maxActivePaths = 4, blankPenalty = 0f)) }
        catch (error: Exception) { throw EngineException(EngineError.INITIALIZATION, text("error.initialization", emptyMap()), error) }
    }
    private suspend fun transcribe(recognizer: OfflineRecognizer, audio: PcmAudio): RecognitionResult {
        currentCoroutineContext().ensureActive()
        val shorts = ByteBuffer.wrap(audio.bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(shorts.remaining()) { shorts.get().toFloat() / 32768f }
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, 16_000); recognizer.decode(stream)
            currentCoroutineContext().ensureActive()
            return RecognitionResult(recognizer.getResult(stream).text)
        } finally { stream.release() }
    }
    private fun validate(audio: PcmAudio) {
        if (audio.sampleRate != 16_000 || audio.channels != 1)
            throw EngineException(EngineError.UNSUPPORTED_FORMAT, text("error.audio_format", emptyMap()))
    }
}

internal data class ModelFiles(val model: File, val tokens: File) {
    companion object {
        fun find(directory: File): ModelFiles? = directory.walkTopDown().filter { it.isFile && it.extension == "onnx" }
            .sortedBy { if (it.name.contains("int8")) 0 else 1 }.mapNotNull { model ->
                val name = model.name.lowercase()
                if (listOf("vad", "silero", "punct", "speaker", "gtcrn", "dpdf").any { it in name }) return@mapNotNull null
                val tokens = File(model.parentFile, "tokens.txt")
                if (tokens.isFile) ModelFiles(model, tokens) else null
            }.firstOrNull()
    }
}
