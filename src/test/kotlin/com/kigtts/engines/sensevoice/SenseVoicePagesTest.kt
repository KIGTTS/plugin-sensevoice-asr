package com.kigtts.engines.sensevoice

import com.kigtts.plugin.api.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SenseVoicePagesTest {
    private val strings = PluginStrings.fromAssets(File("src/main/assets"))
    private fun page(id: String, ready: Boolean, language: String, locale: String = "zh-CN") =
        senseVoicePage(id, ready, language) { strings.text(it, locale) }

    @Test fun settingsKeepControlsInGroupedContentInsteadOfToolbarActions() {
        val page = page("settings", false, "auto")!!
        assertTrue(page.blocks.all { it is PageBlock.Group })
        val groups = page.blocks.filterIsInstance<PageBlock.Group>()
        assertEquals(listOf("识别设置", "识别资源", "检查"), groups.map { it.title })
        val inputs = groups.flatMap { it.children }.filterIsInstance<PageBlock.Input>()
        assertEquals(setOf("language", "import-zip", "import-file", "download", "verify-runtime"), inputs.map { it.actionId }.toSet())
        assertEquals("自动识别", inputs.first().value)
        assertFalse(page.blocks.any { it is PageBlock.Actions })
    }

    @Test fun languagesUseASingleChoiceListAndReadyStatusReflectsTheModel() {
        val choices = page("languages", true, "ja")!!.management!!.items
        assertEquals(6, choices.size)
        assertEquals("日语", choices.single { it.selected }.title)
        assertTrue(choices.all { it.selection?.action == it.primaryAction })
        val status = page("settings", true, "yue")!!.blocks.filterIsInstance<PageBlock.Group>()
            .flatMap { it.children }.filterIsInstance<PageBlock.Notice>().single()
        assertEquals("识别包已就绪", status.text)
        assertNull(page("missing", false, "auto"))
    }

    @Test fun displayLanguageDoesNotChangeModelLanguageOrActions() {
        val expected = mapOf("en-US" to "Japanese", "ko-KR" to "일본어", "ja-JP" to "日本語")
        expected.forEach { (locale, label) ->
            val selected = page("languages", true, "ja", locale)!!.management!!.items.single { it.selected }
            assertEquals(label, selected.title)
            assertEquals("select-language/ja", selected.primaryAction)
        }
    }
}
