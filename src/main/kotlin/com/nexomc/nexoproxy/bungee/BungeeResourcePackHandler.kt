package com.nexomc.nexoproxy.bungee

import com.nexomc.nexoproxy.pack.NexoPackHelpers
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.protocol.DefinedPacket
import net.md_5.bungee.protocol.PacketWrapper
import net.md_5.bungee.protocol.Protocol

/**
 * Intercepts the resource pack push/pop packets on their way to the client and applies the same dedup
 * NexoProxy does on Velocity: deny a re-send of a pack the player already has loaded (avoids a pointless
 * reload/flicker switching backends), deny removal of a pack recognised as Nexo's (the client handles the
 * swap when the replacement send comes through right after). Everything else passes through untouched -
 * only these two packet ids, in the two phases they can appear in, are ever inspected.
 */
internal class BungeeResourcePackHandler(
    private val player: ProxiedPlayer,
    private val plugin: NexoProxyBungee,
) : ChannelOutboundHandlerAdapter() {

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is PacketWrapper || !plugin.config.resourcePacks) {
            super.write(ctx, msg, promise)
            return
        }

        val version = player.pendingConnection.version
        val pushId = when (msg.protocol) {
            Protocol.CONFIGURATION -> ResourcePackPacketIds.configPush(version)
            Protocol.GAME -> ResourcePackPacketIds.playPush(version)
            else -> null
        }
        val popId = when (msg.protocol) {
            Protocol.CONFIGURATION -> ResourcePackPacketIds.configPop(version)
            Protocol.GAME -> ResourcePackPacketIds.playPop(version)
            else -> null
        }
        if (pushId == null && popId == null) {
            super.write(ctx, msg, promise)
            return
        }

        val deny = when (readPacketId(msg)) {
            pushId -> handlePush(msg)
            popId -> handlePop(msg)
            else -> false
        }

        if (deny) {
            msg.trySingleRelease()
            promise.setSuccess()
            return
        }
        super.write(ctx, msg, promise)
    }

    private fun readPacketId(msg: PacketWrapper): Int {
        val readerIndex = msg.buf.readerIndex()
        return try {
            DefinedPacket.readVarInt(msg.buf)
        } finally {
            msg.buf.readerIndex(readerIndex)
        }
    }

    /** Wire shape: varint packet id, uuid, string url, string(40) hash, bool force, optional prompt. */
    private fun handlePush(msg: PacketWrapper): Boolean {
        val buf = msg.buf
        val readerIndex = buf.readerIndex()
        return try {
            DefinedPacket.readVarInt(buf) // packet id, already matched
            DefinedPacket.readUUID(buf)
            DefinedPacket.readString(buf) // url
            val hash = DefinedPacket.readString(buf, 40)

            val mapping = NexoPackHelpers.findMappingByHash(hash)
            if (mapping == null) {
                debug("Non NexoPack $hash for ${player.name}, allowing through")
                return false
            }

            val current = NexoPackHelpers.packHashTracker[player.uniqueId]
            if (current == mapping.unobfuscatedHash) {
                debug("Denied duplicate NexoPack-send for ${player.name}: unobfuscated=${mapping.unobfuscatedHash}, already loaded")
                return true
            }
            NexoPackHelpers.packHashTracker[player.uniqueId] = mapping.unobfuscatedHash
            debug("Sending Nexo pack to ${player.name}: unobfuscated=${mapping.unobfuscatedHash}, obfuscated=${mapping.obfuscatedHash}")
            false
        } finally {
            buf.readerIndex(readerIndex)
        }
    }

    /** Wire shape: varint packet id, bool hasUuid, optional uuid. */
    private fun handlePop(msg: PacketWrapper): Boolean {
        val buf = msg.buf
        val readerIndex = buf.readerIndex()
        return try {
            DefinedPacket.readVarInt(buf) // packet id, already matched
            val hasUuid = buf.readBoolean()
            if (!hasUuid) {
                val denying = NexoPackHelpers.packHashTracker[player.uniqueId] != null
                if (denying) debug("Denied remove-all of Nexo pack(s) for ${player.name}")
                return denying
            }
            val packId = DefinedPacket.readUUID(buf)
            val mapping = NexoPackHelpers.findMappingByUUID(packId) ?: return false
            debug("Denied remove of Nexo pack ${mapping.unobfuscatedHash} for ${player.name}")
            true
        } finally {
            buf.readerIndex(readerIndex)
        }
    }

    private fun debug(message: String) {
        if (plugin.config.debug) plugin.logger.info(message)
    }
}
