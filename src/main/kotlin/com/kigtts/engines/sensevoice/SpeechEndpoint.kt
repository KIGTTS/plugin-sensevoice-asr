// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.sensevoice

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.math.sqrt

/** Lightweight baseline endpoint detector; replace inside this plugin without changing the host. */
internal class SpeechEndpoint {
    private val frame = ByteArray(640) // 20 ms @ 16 kHz PCM16
    private var pending = 0
    private val preRoll = ArrayDeque<ByteArray>()
    private val utterance = ByteArrayOutputStream()
    private var active = false
    private var silentFrames = 0
    private var voicedFrames = 0
    fun accept(bytes: ByteArray): List<ByteArray> {
        require(bytes.size % 2 == 0)
        val completed = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(frame.size - pending, bytes.size - offset)
            bytes.copyInto(frame, pending, offset, offset + count); pending += count; offset += count
            if (pending < frame.size) continue
            pending = 0
            val voiced = rms(frame) >= 0.012
            if (!active) {
                preRoll.addLast(frame.copyOf()); while (preRoll.size > 10) preRoll.removeFirst()
                if (!voiced) continue
                active = true; preRoll.forEach(utterance::write); preRoll.clear(); voicedFrames = 1
            } else {
                utterance.write(frame); if (voiced) voicedFrames++
            }
            silentFrames = if (voiced) 0 else silentFrames + 1
            if (silentFrames >= 25 || utterance.size() >= 16_000 * 2 * 12) {
                if (voicedFrames >= 5) completed += utterance.toByteArray()
                reset()
            }
        }
        return completed
    }
    fun finish(): ByteArray? {
        if (active && pending > 0) utterance.write(frame, 0, pending)
        val result = if (active && voicedFrames >= 5) utterance.toByteArray() else null
        reset(); pending = 0; return result
    }
    private fun reset() { utterance.reset(); active = false; silentFrames = 0; voicedFrames = 0; preRoll.clear() }
    private fun rms(bytes: ByteArray): Double {
        var sum = 0.0
        for (offset in bytes.indices step 2) {
            val value = ((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8)).toShort().toDouble() / 32768
            sum += value * value
        }
        return sqrt(sum / (bytes.size / 2))
    }
}
