// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.sensevoice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import org.json.JSONObject

class SenseVoicePlugin : KigPlugin {
    override val descriptor = PluginDescriptor("engine.sensevoice", "SenseVoice 语音识别",
        "将录音转为文字，也可在连续聆听时逐句显示字幕", version = "0.8.1", license = "GPL-3.0-only")
    private lateinit var environment: PluginEnvironment
    private lateinit var engine: SenseVoiceEngine
    private var language = "auto"
    override val asr: AsrEngine get() = engine
    override suspend fun attach(environment: PluginEnvironment) {
        this.environment = environment
        val config = environment.storage.file("config/settings.json")
        if (config.exists()) language = runCatching { JSONObject(config.readText()).optString("language", "auto") }.getOrDefault("auto")
        engine = SenseVoiceEngine(environment.storage.models) { language }
    }
    override val contributions = PluginContributions(drawerEntries = listOf(
        DrawerEntry("settings", "SenseVoice 资源与设置", "settings", PluginIcon.MIC, settingsPage = true)))
    override fun page(pageId: String, context: PluginContext) = if (pageId == "settings") PluginPage(pageId, "SenseVoice", listOf(
        PageBlock.Text("SenseVoice", heading = true),
        PageBlock.Notice(if (engine.info.availability == EngineAvailability.READY) "识别包已就绪" else "请导入 SenseVoice ONNX 与 tokens.txt，或包含两者的 ZIP"),
        PageBlock.Text("录音结束后显示结果，连续聆听时逐句显示字幕。当前语言：$language"),
        PageBlock.Actions(listOf(PageAction("import-zip", "导入模型 ZIP", PluginIcon.ADD),
            PageAction("import-file", "导入 ONNX / tokens.txt", PluginIcon.ADD), PageAction("download", "下载识别包", PluginIcon.ADD),
            PageAction("language", "识别语言", PluginIcon.SETTINGS), PageAction("verify-runtime", "检查识别引擎", PluginIcon.INFO))),
    )) else null
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = when (actionId) {
        "import-zip" -> PluginOutcome.ImportResource("导入 SenseVoice 模型 ZIP", "models", "imported", zip = true)
        "import-file" -> PluginOutcome.ImportResource("导入 SenseVoice ONNX 或 tokens.txt", "models", "imported")
        "imported" -> PluginOutcome.RefreshUi
        "verify-runtime" -> {
            try {
                Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer", true, javaClass.classLoader)
                PluginOutcome.Message("识别引擎已就绪")
            } catch (failure: LinkageError) { PluginOutcome.Message("识别引擎暂不可用：${failure.message}") }
        }
        "download" -> PluginOutcome.TextInput("模型 ZIP 的 HTTPS 地址", "download-url")
        "download-url" -> {
            val url = context.inputs["value"].orEmpty().trim()
            require(URI(url).scheme == "https") { "请使用 HTTPS 地址" }
            val config = environment.storage.file("config/download-url.txt"); environment.storage.configuration; config.writeText(url)
            PluginOutcome.TextInput("SHA-256（必填，来自模型发布者）", "download-start")
        }
        "download-start" -> withContext(Dispatchers.IO) {
            val hash = context.inputs["value"].orEmpty().trim().lowercase()
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "请输入 64 位 SHA-256" }
            val url = environment.storage.file("config/download-url.txt").readText()
            val archive = environment.download(url, "downloads/model.zip", hash)
            ModelArchive.install(archive, environment.storage)
            PluginOutcome.RefreshUi
        }
        "language" -> PluginOutcome.TextInput("auto / zh / en / ja / ko / yue", "save-language", language)
        "save-language" -> {
            val value = context.inputs["value"].orEmpty().trim()
            require(value in setOf("auto", "zh", "en", "ja", "ko", "yue")) { "不支持的语言代码" }
            language = value; environment.storage.configuration
            environment.storage.file("config/settings.json").writeText(JSONObject().put("language", language).toString())
            PluginOutcome.RefreshUi
        }
        else -> PluginOutcome.Message("未知 SenseVoice 操作")
    }

    override suspend fun close() { if (::engine.isInitialized) engine.close() }
}
