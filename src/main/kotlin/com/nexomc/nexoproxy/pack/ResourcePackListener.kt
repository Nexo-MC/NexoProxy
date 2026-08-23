package com.nexomc.nexoproxy.pack

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoProxy
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.player.ServerResourcePackRemoveEvent
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent
import com.velocitypowered.api.proxy.ServerConnection

class ResourcePackListener(val plugin: NexoProxy) {

    private fun debugLog(msg: String) {
        if (plugin.config.debug) plugin.logger.info(msg)
    }

    @Subscribe
    fun PluginMessageEvent.onPluginMessage() {
        when (identifier.id) {

            // Pack obfuscation mapping from a backend Nexo server
            NexoPackHelpers.PACK_HASH_CHANNEL.id if (plugin.config.resourcePacks) -> {
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
    // If a different Nexo pack is incoming, the send event removes the previous one before letting it through.
    // If the same pack is incoming, we'd deny that too, so the remove is moot.
    // Non-Nexo packs (packId not in our mappings) are always allowed through.
    @Subscribe
    fun ServerResourcePackRemoveEvent.onPackRemove() {
        if (!plugin.config.resourcePacks) return
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
    //   - If no: remove their previous Nexo pack, update tracker, allow
    // If the pack is not a Nexo pack: always allow through untouched.
    @Subscribe
    fun ServerResourcePackSendEvent.onPackSend() {
        if (!plugin.config.resourcePacks) return
        val player = serverConnection.player
        val incomingId = receivedResourcePack.hash?.toHexString()?.trim()
            ?: return debugLog("Received resource pack with no hash for ${player.username}, allowing through")

        val (unobf, obf) = NexoPackHelpers.findMappingByHash(incomingId)
            ?: return debugLog("Non NexoPack $incomingId for ${player.username}, allowing through")

        val current = NexoPackHelpers.packHashTracker[player.uniqueId]

        if (current?.unobfuscatedHash == unobf) {
            result = ResultedEvent.GenericResult.denied()
            debugLog("Denied duplicate NexoPack-send for ${player.username}: unobfuscated=${unobf}, already loaded")
            return
        }

        // The client stacks packs instead of swapping, remove the previous one so the incoming pack is the only Nexo pack
        current?.let { previous ->
            player.removeResourcePacks(previous.packUuid)
            debugLog("Removed previous Nexo pack ${previous.unobfuscatedHash} from ${player.username}")
        }

        NexoPackHelpers.packHashTracker[player.uniqueId] = NexoPackHelpers.AppliedPack(unobf, receivedResourcePack.id)
        debugLog("Sending Nexo pack to ${player.username}: unobfuscated=${unobf}, obfuscated=${obf}")
    }
}