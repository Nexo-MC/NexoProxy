package com.nexomc.nexoproxy;

import com.google.gson.JsonParser
import com.google.inject.Inject
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.player.ServerResourcePackRemoveEvent
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import org.slf4j.Logger


@Plugin(
    id = "nexoproxy",
    name = "NexoProxy",
    version = BuildConstants.VERSION,
    authors = ["boy0000"]
)
class NexoProxy @Inject constructor(val logger: Logger, val server: ProxyServer) {

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        server.channelRegistrar.register(NexoPackHelpers.PACK_HASH_CHANNEL)
        server.eventManager.register(this, ResourcePackListener(logger))
    }
}

class ResourcePackListener(val logger: Logger) {

    // Called when a backend Nexo server sends us obfuscation data for a pack
    @Subscribe
    fun PluginMessageEvent.onHashUpload() {
        if (identifier != NexoPackHelpers.PACK_HASH_CHANNEL) return

        val json = JsonParser.parseString(data.decodeToString()).asJsonObject
        val pack = ResourcePackInfo(json)

        NexoPackHelpers.nexoObfuscationMappings[pack.unobfuscatedHash] = pack
        result = PluginMessageEvent.ForwardResult.handled()

        val serverName = (source as ServerConnection).serverInfo.name
        logger.info("Registered obfuscation mapping: ${pack.unobfuscatedHash} -> ${pack.obfuscatedHash} from $serverName")
    }

    // Clean up player state on disconnect
    @Subscribe
    fun DisconnectEvent.onDisconnect() {
        NexoPackHelpers.packHashTracker.remove(player.uniqueId)
    }

    // Intercept all resource pack removes.
    // We deny removes for any pack we recognise as a Nexo pack.
    // Rationale: if a new *different* Nexo pack is incoming, the send event
    // will go through and the client handles the swap automatically.
    // If the same pack is incoming, we'd deny that too, so the remove is moot.
    // Non-Nexo packs (packId not in our mappings) are always allowed through.
    @Subscribe
    fun ServerResourcePackRemoveEvent.onPackRemove() {
        if (packId == null) {
            if (NexoPackHelpers.packHashTracker[serverConnection.player.uniqueId] != null) {
                result = ResultedEvent.GenericResult.denied()
            }
            return
        }

        val mapping = NexoPackHelpers.findMappingByUUID(packId!!) ?: return

        //TODO This should be a buffer. As even if the hash is stored and its a NexoPack we might want to remove it
        // for example if a new pack was sent up and given to players
        // as servers dont have one packet but one for clear and one for send
        // So this should store a ResourcePackInfo object with the packet, wait a second to see if any send event is triggered for it
        // and if so for this player ignore, otherwise send
        // not sure how to entirely do this without causing recursion or other issues

        result = ResultedEvent.GenericResult.denied()
        logger.info("Denied remove of Nexo pack ${mapping.unobfuscatedHash} for ${serverConnection.player.username}")
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
        val player = serverConnection.player
        val incomingId = receivedResourcePack.hash?.toHexString()?.trim()!!

        val (unobf, obf) = NexoPackHelpers.findMappingByHash(incomingId)
            ?: return logger.info("Non NexoPack $incomingId for ${player.username}, allowing through")

        val currentUnobfId = NexoPackHelpers.packHashTracker[player.uniqueId]

        if (currentUnobfId == unobf) {
            // Player already has this pack (possibly under a different obfuscated hash)
            result = ResultedEvent.GenericResult.denied()
            logger.info("Denied duplicate NexoPack-send for ${player.username}: unobfuscated=${unobf}, already loaded")
            return
        }

        // Player had no NexoPack or one bound to another UnobfHash, so we let this pass & reassign current
        NexoPackHelpers.packHashTracker[player.uniqueId] = unobf

        logger.info("Sending Nexo pack to ${player.username}: unobfuscated=${unobf}, obfuscated=${obf}")
    }
}