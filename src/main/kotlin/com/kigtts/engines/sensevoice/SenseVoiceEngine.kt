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
internal class SenseVoiceEngine(private val modelDirectory: File, private val language: () -> String) : AsrEngine {
    private val session = Mutex()
    override val info get() = AsrInfo(streaming = StreamingKind.SEGMENTED,
        availability = if (ModelFiles.find(modelDirectory) != null) EngineAvailability.READY else EngineAvailability.NEEDS_RESOURCES)
    override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult = withContext(Dispatchers.IO) {
        validate(audio)
        if (audio.bytes.isEmpty()) return@withContext RecognitionResult("")
        require(audio.bytes.size <= 16_000 * 2 * info.maxBatchSeconds) { "完整录音最多 ${info.maxBatchSeconds} 秒" }
        session.withLock {
            val recognizer = createRecognizer()
            try { transcribe(recognizer, audio) } finally { recognizer.release() }
        }
    }
    override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
        session.withLock {
            val recognizer = createRecognizer()
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
            } finally { recognizer.release() }
        }
    }.flowOn(Dispatchers.IO)
    private fun createRecognizer(): OfflineRecognizer {
        val resources = ModelFiles.find(modelDirectory)
            ?: throw EngineException(EngineError.NOT_CONFIGURED, "SenseVoice 模型尚未导入，请打开插件设置")
        val feature = FeatureConfig(sampleRate = 16_000, featureDim = 80, dither = 0f)
        val model = OfflineModelConfig(senseVoice = OfflineSenseVoiceModelConfig(
            model = resources.model.absolutePath, language = language(), useInverseTextNormalization = true),
            tokens = resources.tokens.absolutePath, numThreads = 2, provider = "cpu", modelType = "sense_voice")
        return try { OfflineRecognizer(null, OfflineRecognizerConfig(featConfig = feature, modelConfig = model,
            decodingMethod = "greedy_search", maxActivePaths = 4, blankPenalty = 0f)) }
        catch (error: Exception) { throw EngineException(EngineError.INITIALIZATION, "SenseVoice 模型初始化失败", error) }
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
            throw EngineException(EngineError.UNSUPPORTED_FORMAT, "SenseVoice 需要 16 kHz 单声道 PCM16")
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
