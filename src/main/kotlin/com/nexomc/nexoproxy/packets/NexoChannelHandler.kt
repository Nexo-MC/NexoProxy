package com.nexomc.nexoproxy.packets

import com.nexomc.nexoproxy.NexoConfig
import com.nexomc.nexoproxy.glyphs.resolveGlyphs
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.proxy.protocol.MinecraftPacket
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger

@NullMarked
internal class NexoChannelHandler(
    private val player: Player,
    private val config: NexoConfig,
    private val logger: Logger
) : ChannelDuplexHandler() {

    override fun channelRead(ctx: ChannelHandlerContext?, packet: Any?) {
        if (packet !is MinecraftPacket) return super.channelRead(ctx, packet)

        super.channelRead(ctx, transformPacket(packet) ?: return)
    }

    override fun write(ctx: ChannelHandlerContext, packet: Any, promise: ChannelPromise) {
        if (packet !is MinecraftPacket) return super.write(ctx, packet, promise)

        super.write(ctx, transformPacket(packet) ?: return, promise)
    }

    inline fun <reified T : MinecraftPacket> registerTransformer(condition: Boolean = true, noinline transformer: (T) -> MinecraftPacket?) {
        if (condition) packetTransformers[T::class.java] = { packet ->
            if (packet is T) transformer(packet)
            else packet
        }
    }

    inline fun <reified T : MinecraftPacket> registerReader(condition: Boolean = true, noinline reader: (T) -> Unit) {
        if (condition) packetTransformers[T::class.java] = { packet ->
            if (packet.javaClass == T::class.java) reader(packet as T)
            packet // Return the original packet unchanged
        }
    }

    inline fun <reified T : MinecraftPacket> registerIgnored(condition: Boolean = true) {
        if (condition) packetTransformers[T::class.java] = { packet -> packet }
    }


    fun transformPacket(packet: MinecraftPacket): MinecraftPacket? {
        val entry = packetTransformers[packet::class.java] ?: return packet
        return runCatching {
            entry.invoke(packet)
        }.onFailure {
            if (config.debug) logger.info(it.stackTraceToString())
        }.getOrDefault(packet)
    }

    internal val packetTransformers: MutableMap<Class<out MinecraftPacket>, (MinecraftPacket) -> MinecraftPacket?> = mutableMapOf()

    init {
        registerTransformer<HeaderAndFooterPacket> { packet ->
            HeaderAndFooterPacket(packet.header.resolveGlyphs(), packet.footer.resolveGlyphs())
        }
        registerReader<UpsertPlayerInfoPacket> { packet ->
            packet.entries.forEach { entry ->
                entry.displayName = entry.displayName?.resolveGlyphs()
            }
        }
    }

    private fun ComponentHolder.resolveGlyphs(): ComponentHolder {
        return ComponentHolder(player.protocolVersion, component.resolveGlyphs())
    }

    companion object {
        const val PACKET_KEY = "nexoproxy"

        init {

        }


    }
}