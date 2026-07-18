package com.nexomc.nexoproxy.bungee

import com.nexomc.nexoproxy.glyphs.GlyphStore
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import net.md_5.bungee.protocol.DefinedPacket
import net.md_5.bungee.protocol.packet.BossBar
import net.md_5.bungee.protocol.packet.PlayerListHeaderFooter
import net.md_5.bungee.protocol.packet.PlayerListItem
import net.md_5.bungee.protocol.packet.PlayerListItemUpdate
import net.md_5.bungee.protocol.packet.ScoreboardObjective
import net.md_5.bungee.protocol.packet.ScoreboardScore
import net.md_5.bungee.protocol.packet.Subtitle
import net.md_5.bungee.protocol.packet.SystemChat
import net.md_5.bungee.protocol.packet.Team
import net.md_5.bungee.protocol.packet.Title

/**
 * Resolves <glyph:id> / <shift:N> tags in tab-list, scoreboard, title, bossbar and chat packets on
 * their way to the client - the Bungee counterpart to NexoChannelHandler on Velocity. Unlike the
 * resource pack packets, these all have typed classes Bungee already decodes for us (see BUNGEE.md),
 * so this just mutates fields in place and forwards the same object; no raw byte parsing needed here.
 */
internal class BungeeGlyphPacketHandler(private val plugin: NexoProxyBungee) : ChannelOutboundHandlerAdapter() {

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is DefinedPacket || !plugin.config.glyphs || !GlyphStore.enabled || GlyphStore.glyphs.isEmpty()) {
            super.write(ctx, msg, promise)
            return
        }

        val transformed = runCatching { transform(msg) }
            .onFailure { if (plugin.config.debug) plugin.logger.info("Failed to transform ${msg.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(msg)

        super.write(ctx, transformed, promise)
    }

    private fun transform(packet: DefinedPacket): DefinedPacket {
        when (packet) {
            is PlayerListHeaderFooter -> {
                packet.header = packet.header.resolveGlyphs()
                packet.footer = packet.footer.resolveGlyphs()
            }
            is PlayerListItemUpdate -> {
                if (PlayerListItemUpdate.Action.UPDATE_DISPLAY_NAME in packet.actions ||
                    PlayerListItemUpdate.Action.ADD_PLAYER in packet.actions
                ) {
                    packet.items?.forEach { it.displayName = it.displayName.resolveGlyphs() }
                }
            }
            is PlayerListItem -> packet.items?.forEach { it.displayName = it.displayName.resolveGlyphs() }
            is Team -> {
                // 0 = ADD, 2 = UPDATE (removes/entry-only changes don't carry display text)
                if (packet.mode.toInt() == 0 || packet.mode.toInt() == 2) {
                    packet.displayName = packet.displayName.resolveEitherGlyphs()
                    packet.prefix = packet.prefix.resolveEitherGlyphs()
                    packet.suffix = packet.suffix.resolveEitherGlyphs()
                }
            }
            is ScoreboardObjective -> {
                // 0 = CREATE, 2 = UPDATE
                if (packet.action.toInt() == 0 || packet.action.toInt() == 2) {
                    packet.value = packet.value.resolveEitherGlyphs()
                    packet.numberFormat = packet.numberFormat.resolveGlyphs()
                }
            }
            is ScoreboardScore -> {
                packet.displayName = packet.displayName.resolveGlyphs()
                packet.numberFormat = packet.numberFormat.resolveGlyphs()
            }
            is Title -> packet.text = packet.text.resolveGlyphs()
            is Subtitle -> packet.text = packet.text.resolveGlyphs()
            is BossBar -> packet.title = packet.title.resolveGlyphs()
            is SystemChat -> packet.message = packet.message.resolveGlyphs()
            else -> {}
        }
        return packet
    }
}
