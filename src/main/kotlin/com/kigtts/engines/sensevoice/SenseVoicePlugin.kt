// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.sensevoice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import org.json.JSONObject

class SenseVoicePlugin : KigPlugin {
    override val descriptor = PluginDescriptor("engine.sensevoice", "SenseVoice 语音识别",
        "将录音转为文字，也可在连续聆听时逐句显示字幕", version = "0.8.5", license = "GPL-3.0-only")
    override val localization = PluginLocalization("zh-CN", listOf("zh-CN", "zh-CN-x-casual", "en", "ko", "ja"))
    private var strings = PluginStrings(emptyMap())
    private lateinit var environment: PluginEnvironment
    private lateinit var engine: SenseVoiceEngine
    private var language = "auto"
    override val asr: AsrEngine get() = engine
    override suspend fun attach(environment: PluginEnvironment) {
        this.environment = environment
        strings = PluginStrings.fromAssets(environment.assets)
        val config = environment.storage.file("config/settings.json")
        if (config.exists()) language = runCatching { JSONObject(config.readText()).optString("language", "auto") }.getOrDefault("auto")
        engine = SenseVoiceEngine(environment.storage.models, { language }) { key, args ->
            strings.text(key, environment.locale, args)
        }
    }
    override val contributions = PluginContributions(drawerEntries = listOf(
        DrawerEntry("settings", "SenseVoice 资源与设置", "settings", PluginIcon.MIC, settingsPage = true)))
    override fun contributions(context: PluginContext) = PluginContributions(drawerEntries = listOf(
        DrawerEntry("settings", strings.text("settings.title", context.locale), "settings", PluginIcon.MIC, settingsPage = true)))

    override fun page(pageId: String, context: PluginContext) =
        senseVoicePage(pageId, engine.info.availability == EngineAvailability.READY, language) { strings.text(it, context.locale) }

    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        fun text(key: String) = strings.text(key, context.locale)
        return when (actionId) {
            "import-zip" -> PluginOutcome.ImportResource(text("model.import_zip"), "models", "imported", zip = true)
            "import-file" -> PluginOutcome.ImportResource(text("model.import_files"), "models", "imported")
            "imported" -> PluginOutcome.RefreshUi
            "verify-runtime" -> {
                try {
                    Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer", true, javaClass.classLoader)
                    PluginOutcome.Message(text("diagnostics.ready"))
                } catch (_: ReflectiveOperationException) {
                    PluginOutcome.Message(text("diagnostics.unavailable"))
                } catch (_: LinkageError) {
                    PluginOutcome.Message(text("diagnostics.unavailable"))
                }
            }
            "download" -> PluginOutcome.TextInput(text("download.url"), "download-url")
            "download-url" -> {
                val url = context.inputs["value"].orEmpty().trim()
                val uri = runCatching { URI(url) }.getOrNull()
                if (uri?.scheme?.equals("https", ignoreCase = true) != true || uri.host.isNullOrBlank()) {
                    return PluginOutcome.Message(text("download.invalid_url"))
                }
                environment.storage.configuration
                environment.storage.file("config/download-url.txt").writeText(url)
                PluginOutcome.TextInput(text("download.hash"), "download-start")
            }
            "download-start" -> withContext(Dispatchers.IO) {
                val hash = context.inputs["value"].orEmpty().trim().lowercase()
                if (!hash.matches(Regex("[a-f0-9]{64}"))) return@withContext PluginOutcome.Message(text("download.invalid_hash"))
                try {
                    val url = environment.storage.file("config/download-url.txt").readText()
                    val archive = environment.download(url, "downloads/model.zip", hash)
                    ModelArchive.install(archive, environment.storage)
                    PluginOutcome.RefreshUi
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IllegalArgumentException) {
                    PluginOutcome.Message(text("download.invalid_model"))
                } catch (_: Exception) {
                    PluginOutcome.Message(text("download.failed"))
                }
            }
            "language" -> PluginOutcome.OpenPage("languages")
            "save-language" -> saveLanguage(context.inputs["value"].orEmpty().trim(), ::text)
            else -> if (actionId.startsWith("select-language/")) saveLanguage(actionId.substringAfter('/'), ::text)
                else PluginOutcome.Message(text("action.unknown"))
        }
    }

    private fun saveLanguage(value: String, text: (String) -> String): PluginOutcome {
        if (value !in senseVoiceLanguages) return PluginOutcome.Message(text("language.unsupported"))
        environment.storage.configuration
        environment.storage.file("config/settings.json").writeText(JSONObject().put("language", value).toString())
        language = value
        return PluginOutcome.RefreshUi
    }

    override suspend fun close() { if (::engine.isInitialized) engine.close() }
}
