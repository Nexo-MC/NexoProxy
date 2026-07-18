package com.nexomc.nexoproxy.bungee

import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import net.md_5.bungee.api.connection.ProxiedPlayer

/**
 * BungeeCord's public API has no way to reach a player's raw Netty channel (Connection#unsafe() only
 * exposes sendPacket, nothing pipeline-level) - net.md_5.bungee.UserConnection#ch is private with no
 * getter, so getting at it at all requires reflection. net.md_5.bungee.netty (PipelineUtils,
 * ChannelWrapper) isn't part of bungeecord-api either, so that's reflection too, not just the field.
 * Verified against the current BungeeCord source; if any of this ever breaks, inject()/uninject() just
 * silently no-op instead of throwing.
 */
internal object BungeeChannelInjector {
    const val RESOURCE_PACK_HANDLER = "nexoproxy-resourcepack"
    const val GLYPH_HANDLER = "nexoproxy-glyphs"

    // net.md_5.bungee.netty.PipelineUtils.PACKET_ENCODER - stable, long-lived internal constant name.
    private const val PACKET_ENCODER = "packet-encoder"

    private val chField = runCatching {
        Class.forName("net.md_5.bungee.UserConnection").getDeclaredField("ch").apply { isAccessible = true }
    }.getOrNull()

    private val getHandle = runCatching {
        Class.forName("net.md_5.bungee.netty.ChannelWrapper").getMethod("getHandle")
    }.getOrNull()

    private fun channel(player: ProxiedPlayer): Channel? {
        val wrapper = chField?.get(player) ?: return null
        return getHandle?.invoke(wrapper) as? Channel
    }

    fun inject(player: ProxiedPlayer, name: String, handler: ChannelHandler) {
        val channel = channel(player) ?: return
        channel.eventLoop().execute {
            if (channel.pipeline()[name] == null) {
                channel.pipeline().addBefore(PACKET_ENCODER, name, handler)
            }
        }
    }

    fun uninject(player: ProxiedPlayer, name: String) {
        val channel = channel(player) ?: return
        channel.eventLoop().execute {
            if (channel.pipeline()[name] != null) {
                channel.pipeline().remove(name)
            }
        }
    }
}
