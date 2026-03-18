package com.nexomc.nexoproxy.glyphs

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent

object GlyphStore {
    @Volatile var enabled: Boolean = true
    val glyphComponents: MutableMap<String, Component> = mutableMapOf()
    val GLYPH_CHANNEL = MinecraftChannelIdentifier.from("nexo:glyph_info")
}

private val GLYPH_TAG = Regex("<glyph:([^>]+)>")

/**
 * Recursively resolves <glyph:id> tags in a Component tree, replacing them
 * with the real Components from GlyphStore.
 *
 * Two cases are handled:
 *
 * 1. The tag appears as literal text inside a single TextComponent (the common case
 *    when the surrounding formatting is a simple colour like <red>).
 *
 * 2. The tag is split across multiple single-character TextComponent siblings
 *    (happens with <gradient:…> and <rainbow> because MiniMessage applies a
 *    per-character colour, producing one TextComponent per character). In this
 *    case the sibling list is accumulated into a buffer, the buffer text is
 *    scanned for complete tags, and matching character-spans are replaced.
 */
fun Component.resolveGlyphs(): Component {
    val resolvedChildren = resolveChildrenGlyphs(children())

    return when (this) {
        is TextComponent -> {
            val content = content()
            val matches = GLYPH_TAG.findAll(content).toList()

            if (matches.isEmpty()) {
                if (resolvedChildren == children()) return this
                return children(resolvedChildren)
            }

            // Tag(s) present in a single TextComponent — split around them
            val parts = mutableListOf<Component>()
            var lastEnd = 0
            for (match in matches) {
                if (match.range.first > lastEnd)
                    parts += Component.text(content.substring(lastEnd, match.range.first))
                parts += GlyphStore.glyphComponents[match.groupValues[1]] ?: Component.text(match.value)
                lastEnd = match.range.last + 1
            }
            if (lastEnd < content.length)
                parts += Component.text(content.substring(lastEnd))
            parts += resolvedChildren

            Component.text("").style(style()).children(parts)
        }
        else -> when (resolvedChildren) {
            children() -> this
            else -> children(resolvedChildren)
        }
    }
}

/**
 * Processes a list of sibling components, handling the gradient/rainbow case where
 * a glyph tag is fragmented across consecutive single-character TextComponents.
 *
 * Non-single-char components and components with children are never buffered;
 * they flush the current buffer and are recursed into normally.
 */
private fun resolveChildrenGlyphs(children: List<Component>): List<Component> {
    if (children.isEmpty()) return children

    // Fast path: no lone single-char nodes — normal recursive resolve is enough
    if (children.none { it is TextComponent && it.children().isEmpty() && it.content().length == 1 }) {
        val resolved = children.map { it.resolveGlyphs() }
        return if (resolved == children) children else resolved
    }

    val result = mutableListOf<Component>()
    val charBuffer = StringBuilder()
    val charComps = mutableListOf<TextComponent>()

    fun flushBuffer() {
        if (charComps.isEmpty()) return
        val text = charBuffer.toString()
        val matches = GLYPH_TAG.findAll(text).toList()

        if (matches.isEmpty()) {
            result.addAll(charComps)
        } else {
            var pos = 0
            for (match in matches) {
                // Keep chars before this tag
                repeat(match.range.first - pos) { result.add(charComps[pos++]) }

                val glyphComp = GlyphStore.glyphComponents[match.groupValues[1]]
                if (glyphComp != null) {
                    result.add(glyphComp)
                    pos += match.value.length
                } else {
                    // Unknown glyph id — preserve the literal chars unchanged
                    repeat(match.value.length) { result.add(charComps[pos++]) }
                }
            }
            // Remaining chars after the last tag
            while (pos < charComps.size) result.add(charComps[pos++])
        }

        charBuffer.clear()
        charComps.clear()
    }

    for (child in children) {
        if (child is TextComponent && child.children().isEmpty() && child.content().length == 1) {
            charBuffer.append(child.content())
            charComps.add(child)
        } else {
            flushBuffer()
            result.add(child.resolveGlyphs())
        }
    }
    flushBuffer()

    return if (result == children) children else result
}
