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
        server.channelRegistrar.register(NexoPackHelpers.HASH_CHANNEL)
        server.eventManager.register(this, ResourcePackListener(logger))
    }
}

class ResourcePackListener(val logger: Logger) {

    // Called when a backend Nexo server sends us obfuscation data for a pack
    @Subscribe
    fun PluginMessageEvent.onHashUpload() {
        if (identifier != NexoPackHelpers.HASH_CHANNEL) return

        val raw = data.decodeToString()
        logger.info("Received raw data: $raw")  // temporary, remove after debugging

        val json = JsonParser.parseString(raw).asJsonObject
        val pack = ObfuscatedResourcePack(json)

        // Store keyed by obfuscated UUID for O(1) lookup in onPackSend
        NexoPackHelpers.nexoObfuscationMappings[pack.unobfuscatedHash] = pack
        result = PluginMessageEvent.ForwardResult.handled()

        logger.info("Registered obfuscation mapping: ${pack.unobfuscatedHash} -> ${pack.uuid} from $source")
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
        val incomingId = receivedResourcePack.hash?.toHexString()!!

        val mapping = NexoPackHelpers.findMappingByHash(incomingId) ?: run {
            logger.info("Non-Nexo pack $incomingId for ${player.username}, allowing through")
            return
        }

        val currentUnobfId = NexoPackHelpers.packHashTracker[player.uniqueId]

        if (currentUnobfId == mapping.unobfuscatedHash) {
            // Player already has this pack (possibly under a different obfuscated hash)
            result = ResultedEvent.GenericResult.denied()
            logger.info(
                "Denied duplicate Nexo pack send for ${player.username}: " +
                        "unobfuscated=${mapping.unobfuscatedHash}, already loaded"
            )
            return
        }

        // New or different pack — swap to the canonical obfuscated version and allow
        providedResourcePack = providedResourcePack
            .asBuilder()
            .setId(mapping.uuid)
            //.setUrl(mapping.url)
            .setHash(mapping.obfuscatedHash.hexToByteArray())
            .build()

        NexoPackHelpers.packHashTracker[player.uniqueId] = mapping.unobfuscatedHash

        logger.info(
            "Sending Nexo pack to ${player.username}: " +
                    "unobfuscated=${mapping.unobfuscatedHash}, obfuscated=${mapping.uuid}"
        )
    }
}