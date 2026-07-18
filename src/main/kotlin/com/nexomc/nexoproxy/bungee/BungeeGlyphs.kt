package com.nexomc.nexoproxy.bungee

import com.nexomc.nexoproxy.glyphs.GlyphStore
import com.nexomc.nexoproxy.glyphs.resolveGlyphs
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.chat.ComponentSerializer
import net.md_5.bungee.protocol.data.NumberFormat
import net.md_5.bungee.protocol.util.Either

/**
 * BungeeCord's chat components (BaseComponent) aren't Adventure, so glyph tag resolution can't run on
 * them directly - resolveGlyphs() (com.nexomc.nexoproxy.glyphs.GlyphHandler, shared with Velocity) only
 * knows Adventure's Component tree. Round-tripping through the same JSON chat format both sides already
 * understand lets that logic stay exactly as-is instead of a second implementation for BaseComponent.
 * This never touches the wire - by the time these packets reach here they're already decoded Java
 * objects; Bungee's own encoder re-serializes them for whatever protocol version the client negotiated.
 */
private val gson = GsonComponentSerializer.gson()

fun BaseComponent?.resolveGlyphs(): BaseComponent? {
    if (this == null || !GlyphStore.enabled || GlyphStore.glyphs.isEmpty()) return this
    val resolved = gson.deserialize(ComponentSerializer.toString(this)).resolveGlyphs()
    return ComponentSerializer.parse(gson.serialize(resolved)).firstOrNull() ?: this
}

/** Team/ScoreboardObjective fields can be either a legacy string or a component - only the latter has glyphs. */
fun Either<String, BaseComponent>.resolveEitherGlyphs(): Either<String, BaseComponent> {
    if (!isRight) return this
    val resolved = right.resolveGlyphs() ?: return this
    return if (resolved === right) this else Either.right(resolved)
}

fun NumberFormat?.resolveGlyphs(): NumberFormat? {
    if (this == null || type != NumberFormat.Type.FIXED) return this
    val component = value as? BaseComponent ?: return this
    val resolved = component.resolveGlyphs() ?: return this
    return if (resolved === component) this else NumberFormat(NumberFormat.Type.FIXED, resolved)
}
