// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.sensevoice

import com.kigtts.plugin.api.PluginStorage
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Download extraction remains a plugin implementation detail. No host engine import code. */
internal object ModelArchive {
    fun install(archive: File, storage: PluginStorage) {
        val staged = File(storage.temporary, "models-${UUID.randomUUID()}").apply { mkdirs() }
        val old = File(storage.temporary, "previous-${UUID.randomUUID()}")
        val target = storage.models
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().toList()
                require(entries.size in 1..10_000 && entries.map { it.name }.distinct().size == entries.size)
                require(entries.all { !it.name.startsWith('/') && !it.name.contains('\\') && !it.name.contains(':') &&
                    it.name.removeSuffix("/").split('/').none { part -> part == ".." || part == "." || part.isEmpty() } && it.size in 0..2L * 1024 * 1024 * 1024 })
                require(entries.sumOf { it.size } <= 2L * 1024 * 1024 * 1024)
                entries.forEach { entry ->
                    val file = File(staged, entry.name)
                    if (entry.isDirectory) file.mkdirs() else {
                        file.parentFile!!.mkdirs()
                        zip.getInputStream(entry).use { input -> file.outputStream().use { output ->
                            val copied = input.copyTo(output); require(copied == entry.size)
                        } }
                    }
                }
            }
            require(ModelFiles.find(staged) != null) { "ZIP 中没有配套的 SenseVoice ONNX 与 tokens.txt" }
            require(target.renameTo(old))
            if (!staged.renameTo(target)) { old.renameTo(target); error("模型资源替换失败") }
        } finally { staged.deleteRecursively(); old.deleteRecursively() }
    }
}
