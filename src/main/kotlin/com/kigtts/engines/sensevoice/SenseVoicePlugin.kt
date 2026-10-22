// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.sensevoice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import org.json.JSONObject

class SenseVoicePlugin : KigPlugin {
    override val descriptor = PluginDescriptor("engine.sensevoice", "SenseVoice 语音识别",
        "从 KIGTTS 移植的离线识别，支持完整录音及分段流式输入", version = "0.8.0", license = "GPL-3.0-only")
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
        PageBlock.Notice(if (engine.info.availability == EngineAvailability.READY) "模型与 tokens.txt 已就绪" else "请导入 SenseVoice ONNX 与 tokens.txt，或包含两者的 ZIP"),
        PageBlock.Text("完整录音返回一次结果；分段流式根据声音端点切句后执行离线推理。它不是原生增量解码器。当前语言：$language"),
        PageBlock.Text("模型、下载、导入文件及配置均在 appdata/Kigtts/plugin/plugindata/${descriptor.id}/ 下。"),
        PageBlock.Actions(listOf(PageAction("import-zip", "导入模型 ZIP", PluginIcon.ADD),
            PageAction("import-file", "导入 ONNX / tokens.txt", PluginIcon.ADD), PageAction("download", "从 HTTPS 下载模型 ZIP", PluginIcon.ADD),
            PageAction("language", "识别语言", PluginIcon.SETTINGS), PageAction("verify-runtime", "检查推理运行库", PluginIcon.INFO))),
    )) else null
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = when (actionId) {
        "import-zip" -> PluginOutcome.ImportResource("导入 SenseVoice 模型 ZIP", "models", "imported", zip = true)
        "import-file" -> PluginOutcome.ImportResource("导入 SenseVoice ONNX 或 tokens.txt", "models", "imported")
        "imported" -> PluginOutcome.RefreshUi
        "verify-runtime" -> {
            try {
                Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer", true, javaClass.classLoader)
                PluginOutcome.Message("推理运行库已加载")
            } catch (failure: LinkageError) { PluginOutcome.Message("推理运行库加载失败：${failure.message}") }
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
