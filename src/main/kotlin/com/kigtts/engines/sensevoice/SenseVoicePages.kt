// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.sensevoice

import com.kigtts.plugin.api.*

internal val senseVoiceLanguages = listOf("auto", "zh", "en", "ja", "ko", "yue")

internal fun senseVoicePage(id: String, ready: Boolean, language: String, text: (String) -> String): PluginPage? = when (id) {
    "settings" -> PluginPage(id, "SenseVoice", listOf(
        PageBlock.Group(text("group.recognition"), listOf(
            PageBlock.Input(text("recognition.language"), text("language.${language.takeIf { it in senseVoiceLanguages } ?: "auto"}"), "language", icon = PluginIcon.MIC))),
        PageBlock.Group(text("group.resources"), listOf(
            PageBlock.Notice(text(if (ready) "model.ready" else "model.setup")),
            PageBlock.Input(text("model.import_zip"), text("model.zip_description"), "import-zip", icon = PluginIcon.ADD, symbol = "folder_zip"),
            PageBlock.Input(text("model.import_files"), "ONNX / tokens.txt", "import-file", icon = PluginIcon.ADD, symbol = "file_open"),
            PageBlock.Input(text("model.download"), text("model.download_description"), "download", icon = PluginIcon.ADD))),
        PageBlock.Group(text("group.diagnostics"), listOf(
            PageBlock.Input(text("diagnostics.check"), "", "verify-runtime", icon = PluginIcon.INFO))),
    ))
    "languages" -> PluginPage(id, text("recognition.language"), emptyList(), ManagementList("sensevoice-languages",
        senseVoiceLanguages.map { code -> ManagementItem(code, text("language.$code"), icon = PluginIcon.MIC,
            selected = language == code, primaryAction = "select-language/$code",
            selection = ManagementSelection("select-language/$code")) }))
    else -> null
}
