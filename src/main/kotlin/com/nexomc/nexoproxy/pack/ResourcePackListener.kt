package com.nexomc.nexoproxy.pack

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoConfig
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.player.ServerResourcePackRemoveEvent
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent
import com.velocitypowered.api.proxy.ServerConnection
import org.slf4j.Logger

class ResourcePackListener(val logger: Logger, var config: NexoConfig) {

    private fun debugLog(msg: String) {
        if (config.debug) logger.info(msg)
    }

    @Subscribe
    fun PluginMessageEvent.onPluginMessage() {
        when (identifier.id) {

            // Pack obfuscation mapping from a backend Nexo server
            NexoPackHelpers.PACK_HASH_CHANNEL.id if (config.resourcePacks) -> {
                val json = JsonParser.parseString(data.decodeToString()).asJsonObject
                val pack = ResourcePackInfo(json)
                NexoPackHelpers.addMapping(pack)
                result = PluginMessageEvent.ForwardResult.handled()
                val serverName = (source as ServerConnection).serverInfo.name
                debugLog("Registered pack mapping: ${pack.unobfuscatedHash} -> ${pack.obfuscatedHash} from $serverName")
            }
        }
    }

    // Clean up player state on disconnect
    @Subscribe
    fun DisconnectEvent.onDisconnect() {
        NexoPackHelpers.packHashTracker.remove(player.uniqueId)
    }

    // Intercept all resource pack removes.
    // We deny removes for any pack we recognise as a Nexo pack.
    // if a new *different* Nexo pack is incoming, the send event
    // will go through and the client handles the swap automatically.
    // If the same pack is incoming, we'd deny that too, so the remove is moot.
    // Non-Nexo packs (packId not in our mappings) are always allowed through.
    @Subscribe
    fun ServerResourcePackRemoveEvent.onPackRemove() {
        if (!config.resourcePacks) return
        if (packId == null) {
            if (NexoPackHelpers.packHashTracker[serverConnection.player.uniqueId] != null) {
                result = ResultedEvent.GenericResult.denied()
            }
            return
        }

        val mapping = NexoPackHelpers.findMappingByUUID(packId!!) ?: return

        result = ResultedEvent.GenericResult.denied()
        debugLog("Denied remove of Nexo pack ${mapping.unobfuscatedHash} for ${serverConnection.player.username}")
    }

    // Intercept all resource pack sends.
    // If the pack is a known Nexo pack:
    //   - Look up the canonical obfuscated version from our mappings
    //   - Check if the player already has this unobfuscated pack loaded
    //   - If yes: deny (duplicate, possibly different obfuscation)
    //   - If no: swap in the canonical obfuscated URL/hash, update tracker, allow
    // If the pack is not a Nexo pack: always allow through untouched.
    @Subscribe
    fun ServerResourcePackSendEvent.onPackSend() {
        if (!config.resourcePacks) return
        val player = serverConnection.player
        val incomingId = receivedResourcePack.hash?.toHexString()?.trim()!!

        val (unobf, obf) = NexoPackHelpers.findMappingByHash(incomingId)
            ?: return debugLog("Non NexoPack $incomingId for ${player.username}, allowing through")

        val currentUnobfId = NexoPackHelpers.packHashTracker[player.uniqueId]

        if (currentUnobfId == unobf) {
            result = ResultedEvent.GenericResult.denied()
            debugLog("Denied duplicate NexoPack-send for ${player.username}: unobfuscated=${unobf}, already loaded")
            return
        }

        NexoPackHelpers.packHashTracker[player.uniqueId] = unobf
        debugLog("Sending Nexo pack to ${player.username}: unobfuscated=${unobf}, obfuscated=${obf}")
    }
}