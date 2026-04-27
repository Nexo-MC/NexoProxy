package com.nexomc.nexoproxy.packets

import com.nexomc.nexoproxy.NexoProxy
import com.nexomc.nexoproxy.glyphs.resolveGlyphs
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.proxy.protocol.MinecraftPacket
import com.velocitypowered.proxy.protocol.packet.BossBarPacket
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket
import com.velocitypowered.proxy.protocol.packet.title.LegacyTitlePacket
import com.velocitypowered.proxy.protocol.packet.title.TitleActionbarPacket
import com.velocitypowered.proxy.protocol.packet.title.TitleSubtitlePacket
import com.velocitypowered.proxy.protocol.packet.title.TitleTextPacket
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import net.william278.velocitab.packet.UpdateTeamsPacket
import org.jspecify.annotations.NullMarked

@NullMarked
internal class NexoChannelHandler(
    private val player: Player,
    private val plugin: NexoProxy
) : ChannelDuplexHandler() {

    override fun channelRead(ctx: ChannelHandlerContext?, packet: Any?) {
        if (packet !is MinecraftPacket) return super.channelRead(ctx, packet)

        super.channelRead(ctx, transformPacket(packet) ?: return)
    }

    override fun write(ctx: ChannelHandlerContext, packet: Any, promise: ChannelPromise) {
        if (packet !is MinecraftPacket) return super.write(ctx, packet, promise)

        super.write(ctx, transformPacket(packet) ?: return, promise)
    }

    inline fun <reified T : MinecraftPacket> registerTransformer(
        condition: Boolean = true,
        noinline transformer: (T) -> MinecraftPacket?
    ) {
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
            if (plugin.config.debug) plugin.logger.info(it.stackTraceToString())
        }.getOrDefault(packet)
    }

    internal val packetTransformers = mutableMapOf<Class<out MinecraftPacket>, (MinecraftPacket) -> MinecraftPacket?>()

    init {
        registerTransformer<HeaderAndFooterPacket> { packet ->
            HeaderAndFooterPacket(packet.header.resolveGlyphs(), packet.footer.resolveGlyphs())
        }

        if (plugin.isVelocitabPresent) registerReader<UpdateTeamsPacket> { packet ->
            packet.displayName(packet.displayName()?.resolveGlyphs())
            packet.prefix(packet.prefix()?.resolveGlyphs())
            packet.suffix(packet.suffix()?.resolveGlyphs())
        }

        registerReader<UpsertPlayerInfoPacket> { packet ->
            if (packet.actions.any { it in actionsToHandle }) packet.entries.forEach { entry ->
                entry.displayName = entry.displayName?.resolveGlyphs()
            }
        }
        registerReader<TitleTextPacket> { packet ->
            packet.component = packet.component.resolveGlyphs()
        }
        registerReader<TitleSubtitlePacket> { packet ->
            packet.component = packet.component.resolveGlyphs()
        }
        registerReader<TitleActionbarPacket> { packet ->
            packet.component = packet.component.resolveGlyphs()
        }
        registerReader<LegacyTitlePacket> { packet ->
            packet.component = packet.component?.resolveGlyphs()
        }
        registerReader<BossBarPacket> { packet ->
            packet.name = packet.name?.resolveGlyphs()
        }
        registerReader<SystemChatPacket> { packet ->
            packet.component.resolveGlyphs()
        }
    }

    private fun ComponentHolder.resolveGlyphs(): ComponentHolder {
        return ComponentHolder(player.protocolVersion, component.resolveGlyphs())
    }

    companion object {
        const val PACKET_KEY = "nexoproxy"

        private val actionsToHandle = setOf(
            UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME,
            UpsertPlayerInfoPacket.Action.ADD_PLAYER,
            UpsertPlayerInfoPacket.Action.UPDATE_LISTED
        )

    }
}