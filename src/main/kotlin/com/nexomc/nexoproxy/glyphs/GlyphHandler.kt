package com.nexomc.nexoproxy.glyphs

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor

object GlyphStore {
    @Volatile var enabled: Boolean = true
    val glyphs: MutableMap<String, ProxyGlyph> = mutableMapOf()
    val GLYPH_CHANNEL = MinecraftChannelIdentifier.from("nexo:glyph_info")
}

data class ProxyGlyph(
    val id: String,
    val font: Key,
    val unicodes: List<String>,
    val defaultColor: Int?,
    val defaultShadowColor: Int?,
    val permission: String,
    val placeholders: List<String>,
) {
    val chars by lazy { unicodes.flatMap { it.toList() }.toCharArray() }

    fun glyphComponent(
        colorable: Boolean = false,
        shadowColor: Int? = defaultShadowColor,
        bitmapIndexRange: IntRange = IntRange.EMPTY
    ): Component {
        return if (bitmapIndexRange == IntRange.EMPTY) {
            baseComponent(colorable, shadowColor)
        } else {
            bitmapComponent(bitmapIndexRange, colorable, shadowColor)
        }
    }

    private fun baseComponent(colorable: Boolean, shadowColor: Int?): Component {
        val component = Component.textOfChildren(*unicodes.flatMapIndexed { i, row ->
            val shifted = row.toList().joinToString(Shift.of(-1))
            listOfNotNull(
                Component.text(shifted).font(font) as Component,
                Component.newline().takeIf { unicodes.size != i + 1 }
            )
        }.toTypedArray())

        return component
            .run { if (!colorable && defaultColor != null) color(TextColor.color(defaultColor)) else this }
            .run { if (!colorable && defaultColor != null) children(children().map { it.color(TextColor.color(defaultColor)) }) else this }
            .applyShadow(shadowColor)
    }

    private fun bitmapComponent(indexRange: IntRange, colorable: Boolean, shadowColor: Int?): Component {
        return Component.textOfChildren(*indexRange.map { index ->
            val char = chars.elementAtOrNull(index - 1) ?: chars.first()
            val shift = if (indexRange.count() > 1) Shift.of(-1) else ""
            val comp = Component.text("$char$shift").font(font)
            if (!colorable && defaultColor != null) comp.color(TextColor.color(defaultColor)) else comp
        }.toTypedArray()).applyShadow(shadowColor)
    }

    private fun Component.applyShadow(shadowColor: Int?): Component {
        return if (shadowColor != null) shadowColor(ShadowColor.shadowColor(shadowColor)) else this
    }
}

// --- Tag patterns ---

private val GLYPH_TAG = Regex("""(?<!\\)<(?:glyph|g):([^>]+)>""")
private val ESCAPED_GLYPH_TAG = Regex("""\\<(?:glyph|g):([^>]+)>""")
private val SHIFT_TAG = Regex("""(?<!\\)<(?:shift|s):(-?\d+)>""")
private val ESCAPED_SHIFT_TAG = Regex("""\\<(?:shift|s):(-?\d+)>""")

private fun parseGlyphTag(argsString: String): Component? {
    val args = argsString.split(":")
    val glyphId = args.firstOrNull() ?: return null
    val glyph = GlyphStore.glyphs[glyphId] ?: return null

    val colorable = args.any { it == "colorable" || it == "c" }
    val shadowIdx = args.indexOfFirst { it == "shadow" || it == "s" }
    val shadowColor = args.elementAtOrNull(shadowIdx + 1)?.takeIf { shadowIdx >= 0 }?.parseColor()
    val bitmapIndexRange = args.drop(1).firstNotNullOfOrNull {
        it.toIntRangeOrNull() ?: it.toIntOrNull()?.let { i -> IntRange(i, i) }
    } ?: IntRange.EMPTY

    return glyph.glyphComponent(
        colorable = colorable,
        shadowColor = if (shadowIdx >= 0) shadowColor else glyph.defaultShadowColor,
        bitmapIndexRange = bitmapIndexRange
    )
}

private fun parseShiftTag(shiftValue: String): Component? {
    val shift = shiftValue.toIntOrNull() ?: return null
    return Component.text(Shift.of(shift)).font(Shift.shiftFont.key())
}

/**
 * Recursively resolves `<glyph:id[:args]>` and `<shift:N>` tags in a Component tree,
 * replacing them with the real Components from GlyphStore.
 *
 * Handles both inline tags in a single TextComponent and tags fragmented across
 * single-character TextComponents (from gradient/rainbow formatting).
 */
fun Component.resolveGlyphs(): Component {
    val resolvedChildren = resolveChildrenGlyphs(children())

    return when (this) {
        is TextComponent -> {
            val content = content()
            val parts = resolveTextContent(content)

            if (parts == null) {
                if (resolvedChildren == children()) return this
                return children(resolvedChildren)
            }

            val allParts = parts + resolvedChildren
            Component.text("").style(style()).children(allParts)
        }
        else -> when (resolvedChildren) {
            children() -> this
            else -> children(resolvedChildren)
        }
    }
}

/**
 * Resolves all glyph and shift tags in a text string.
 * Returns null if no tags were found (no changes needed).
 */
private fun resolveTextContent(content: String): List<Component>? {
    val allMatches = (GLYPH_TAG.findAll(content) + SHIFT_TAG.findAll(content))
        .sortedBy { it.range.first }
        .toList()

    if (allMatches.isEmpty()) return null

    val parts = mutableListOf<Component>()
    var lastEnd = 0
    for (match in allMatches) {
        if (match.range.first > lastEnd)
            parts += Component.text(content.substring(lastEnd, match.range.first))

        val resolved = when {
            match.value.startsWith("<g:") || match.value.startsWith("<glyph:") ->
                parseGlyphTag(match.groupValues[1])
            else -> parseShiftTag(match.groupValues[1])
        }
        parts += resolved ?: Component.text(match.value)
        lastEnd = match.range.last + 1
    }
    if (lastEnd < content.length)
        parts += Component.text(content.substring(lastEnd))

    return parts
}

/**
 * Processes a list of sibling components, handling the gradient/rainbow case where
 * a tag is fragmented across consecutive single-character TextComponents.
 */
private fun resolveChildrenGlyphs(children: List<Component>): List<Component> {
    if (children.isEmpty()) return children

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
        val allMatches = (GLYPH_TAG.findAll(text) + SHIFT_TAG.findAll(text))
            .sortedBy { it.range.first }
            .toList()

        if (allMatches.isEmpty()) {
            result.addAll(charComps)
        } else {
            var pos = 0
            for (match in allMatches) {
                repeat(match.range.first - pos) { result.add(charComps[pos++]) }

                val resolved = when {
                    match.value.startsWith("<g:") || match.value.startsWith("<glyph:") ->
                        parseGlyphTag(match.groupValues[1])
                    else -> parseShiftTag(match.groupValues[1])
                }
                if (resolved != null) {
                    result.add(resolved)
                    pos += match.value.length
                } else {
                    repeat(match.value.length) { result.add(charComps[pos++]) }
                }
            }
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

// --- Utility parsing functions ---

private fun String.toIntRangeOrNull(): IntRange? {
    val first = substringBefore("..").toIntOrNull() ?: return null
    val last = substringAfter("..").toIntOrNull()?.coerceAtLeast(first) ?: return null
    return first..last
}

@OptIn(ExperimentalStdlibApi::class)
private fun String.parseColor(): Int? {
    return runCatching {
        when {
            startsWith("#") || startsWith("0x") -> {
                val hex = removePrefix("#").removePrefix("0x")
                hex.padStart(8, 'F').take(8).hexToInt(HexFormat.Default)
            }
            "," in this -> {
                val parts = replace(" ", "").split(",").mapNotNull(String::toIntOrNull)
                when (parts.size) {
                    3 -> (0xFF shl 24) or (parts[0] shl 16) or (parts[1] shl 8) or parts[2]
                    4 -> (parts[3] shl 24) or (parts[0] shl 16) or (parts[1] shl 8) or parts[2]
                    else -> null
                }
            }
            toIntOrNull() != null -> (0xFF shl 24) or toInt()
            else -> NamedTextColor.NAMES.value(this)?.value()?.let { (0xFF shl 24) or it }
        }
    }.getOrNull()
}
