package com.kigtts.engines.sensevoice

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class SpeechEndpointTest {
    private fun frames(count: Int, level: Int) = ByteArray(count * 640).also { bytes ->
        for (offset in bytes.indices step 2) { bytes[offset] = level.toByte(); bytes[offset + 1] = (level shr 8).toByte() }
    }
    @Test fun silenceProducesNoFakeTextAndSplitPcmIsRetainedUntilSpeechEndpoint() {
        val detector = SpeechEndpoint()
        assertTrue(detector.accept(frames(50, 0)).isEmpty()); assertNull(detector.finish())
        val input = frames(10, 2000) + frames(25, 0)
        val completed = mutableListOf<ByteArray>()
        for (offset in input.indices step 126) completed += detector.accept(input.copyOfRange(offset, minOf(offset + 126, input.size)))
        assertEquals(1, completed.size); assertTrue(completed.single().size >= 35 * 640)
        assertNull(detector.finish())
    }
    @Test fun inputCompletionFlushesSpeechAndMaximumSegmentStaysBounded() {
        val detector = SpeechEndpoint()
        assertTrue(detector.accept(frames(10, 2000)).isEmpty()); assertNotNull(detector.finish())
        val completed = detector.accept(frames(601, 2000))
        assertEquals(1, completed.size); assertTrue(completed.single().size <= 16_000 * 2 * 12)
    }
    @Test fun ignoresAuxiliaryOnnxAndRequiresMatchingTokens() {
        val directory = Files.createTempDirectory("model-files").toFile()
        try {
            java.io.File(directory, "gtcrn.onnx").writeText("aux")
            java.io.File(directory, "tokens.txt").writeText("tokens")
            assertNull(ModelFiles.find(directory))
            java.io.File(directory, "model.int8.onnx").writeText("model")
            assertEquals("model.int8.onnx", ModelFiles.find(directory)!!.model.name)
        } finally { directory.deleteRecursively() }
    }
}
