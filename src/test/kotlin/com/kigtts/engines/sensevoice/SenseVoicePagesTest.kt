package com.kigtts.engines.sensevoice

import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test

class SenseVoicePagesTest {
    @Test fun settingsKeepControlsInGroupedContentInsteadOfToolbarActions() {
        val page = senseVoicePage("settings", false, "auto")!!
        assertTrue(page.blocks.all { it is PageBlock.Group })
        val groups = page.blocks.filterIsInstance<PageBlock.Group>()
        assertEquals(listOf("识别设置", "识别资源", "检查"), groups.map { it.title })
        val inputs = groups.flatMap { it.children }.filterIsInstance<PageBlock.Input>()
        assertEquals(setOf("language", "import-zip", "import-file", "download", "verify-runtime"), inputs.map { it.actionId }.toSet())
        assertEquals("自动识别", inputs.first().value)
        assertFalse(page.blocks.any { it is PageBlock.Actions })
    }

    @Test fun languagesUseASingleChoiceListAndReadyStatusReflectsTheModel() {
        val choices = senseVoicePage("languages", true, "ja")!!.management!!.items
        assertEquals(6, choices.size)
        assertEquals("日语", choices.single { it.selected }.title)
        assertTrue(choices.all { it.selection?.action == it.primaryAction })
        val status = senseVoicePage("settings", true, "yue")!!.blocks.filterIsInstance<PageBlock.Group>()
            .flatMap { it.children }.filterIsInstance<PageBlock.Notice>().single()
        assertEquals("识别包已就绪", status.text)
        assertNull(senseVoicePage("missing", false, "auto"))
    }
}
