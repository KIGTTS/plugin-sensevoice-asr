// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.sensevoice

import com.kigtts.plugin.api.*

internal val senseVoiceLanguages = linkedMapOf("auto" to "自动识别", "zh" to "中文", "en" to "英语",
    "ja" to "日语", "ko" to "韩语", "yue" to "粤语")

internal fun senseVoicePage(id: String, ready: Boolean, language: String): PluginPage? = when (id) {
    "settings" -> PluginPage(id, "SenseVoice", listOf(
        PageBlock.Group("识别设置", listOf(
            PageBlock.Input("识别语言", senseVoiceLanguages[language] ?: "自动识别", "language", icon = PluginIcon.MIC))),
        PageBlock.Group("识别资源", listOf(
            PageBlock.Notice(if (ready) "识别包已就绪" else "添加识别包后即可使用"),
            PageBlock.Input("导入模型 ZIP", "包含 ONNX 模型和 tokens.txt", "import-zip", icon = PluginIcon.ADD, symbol = "folder_zip"),
            PageBlock.Input("导入模型文件", "ONNX 或 tokens.txt", "import-file", icon = PluginIcon.ADD, symbol = "file_open"),
            PageBlock.Input("下载识别包", "使用 HTTPS 地址与校验值", "download", icon = PluginIcon.ADD))),
        PageBlock.Group("检查", listOf(
            PageBlock.Input("检查识别引擎", "", "verify-runtime", icon = PluginIcon.INFO))),
    ))
    "languages" -> PluginPage(id, "识别语言", emptyList(), ManagementList("sensevoice-languages",
        senseVoiceLanguages.map { (code, label) -> ManagementItem(code, label, icon = PluginIcon.MIC,
            selected = language == code, primaryAction = "select-language/$code",
            selection = ManagementSelection("select-language/$code")) }))
    else -> null
}
