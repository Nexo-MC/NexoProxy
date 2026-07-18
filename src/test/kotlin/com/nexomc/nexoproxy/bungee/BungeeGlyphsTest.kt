package com.nexomc.nexoproxy.bungee

import com.nexomc.nexoproxy.glyphs.GlyphStore
import com.nexomc.nexoproxy.glyphs.ProxyGlyph
import net.kyori.adventure.key.Key
import net.md_5.bungee.api.chat.TextComponent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the BaseComponent <-> Adventure round-trip (BungeeGlyphs.kt) end to end: a real
 * BaseComponent in, resolveGlyphs() through the shared Velocity logic, a real BaseComponent back out.
 * Can't test actual packet delivery without a live client (see BUNGEE.md), but this is the part most
 * likely to silently break - if the JSON round-trip between the two component models ever drifts, this
 * fails loudly instead of just quietly not resolving anything in-game.
 */
class BungeeGlyphsTest {

    @BeforeTest
    fun registerTestGlyph() {
        GlyphStore.enabled = true
        GlyphStore.glyphs["test"] = ProxyGlyph(
            id = "test",
            font = Key.key("nexo:default"),
            unicodes = listOf("A"),
            defaultColor = null,
            defaultShadowColor = null,
            permission = "",
            placeholders = emptyList(),
        )
    }

    @AfterTest
    fun cleanup() {
        GlyphStore.glyphs.clear()
        GlyphStore.enabled = true
    }

    @Test
    fun `resolves a glyph tag inside a real BaseComponent`() {
        val input = TextComponent("hello <glyph:test> world")

        val resolved = input.resolveGlyphs()

        assertTrue(resolved != null, "resolveGlyphs() should not drop the component entirely")
        assertTrue(
            "<glyph:test>" !in resolved!!.toPlainText(),
            "raw tag should be gone: ${resolved.toPlainText()}",
        )
    }

    @Test
    fun `a component with no glyph tags passes through untouched`() {
        val input = TextComponent("just plain text")

        val resolved = input.resolveGlyphs()

        assertEquals("just plain text", resolved!!.toPlainText())
    }

    @Test
    fun `does nothing when glyphs are globally disabled`() {
        GlyphStore.enabled = false
        val input = TextComponent("hello <glyph:test> world")

        val resolved = input.resolveGlyphs()

        assertTrue(resolved === input, "should return the exact same instance when disabled")
    }

    @Test
    fun `an unknown glyph id is left as literal text`() {
        val input = TextComponent("<glyph:does-not-exist>")

        val resolved = input.resolveGlyphs()

        assertTrue(
            "does-not-exist" in resolved!!.toPlainText(),
            "unknown glyph ids should stay as literal text, not vanish: ${resolved.toPlainText()}",
        )
    }
}
