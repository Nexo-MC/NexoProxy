package com.nexomc.nexoproxy.pack

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoProxy
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.player.ServerResourcePackRemoveEvent
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent
import com.velocitypowered.api.proxy.ServerConnection
import kotlin.jvm.optionals.getOrNull

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
                val templates = json.getAsJsonArray("templates")?.map { it.asString }.orEmpty()
                templates.forEach { hash -> NexoPackHelpers.addMapping(ResourcePackInfo(hash)) }
                result = PluginMessageEvent.ForwardResult.handled()
                val serverName = (source as ServerConnection).serverInfo.name
                NexoPackHelpers.nexoServers += serverName
                debugLog("Registered pack mapping: ${pack.unobfuscatedHash} -> ${pack.obfuscatedHash} (+${templates.size} template(s)) from $serverName")
            }
        }
    }

    // Clean up player state on disconnect
    @Subscribe
    fun DisconnectEvent.onDisconnect() {
        NexoPackHelpers.packHashTracker.remove(player.uniqueId)
    }

    // Intercept all resource pack removes, denying any pack we recognise as a Nexo pack
    // The proxy removes stale ones itself, main packs when a different one is sent, templates on server switch
    @Subscribe
    fun ServerResourcePackRemoveEvent.onPackRemove() {
        if (!plugin.config.resourcePacks) return
        if (packId == null) {
            if (!NexoPackHelpers.packHashTracker[serverConnection.player.uniqueId].isNullOrEmpty()) {
                result = ResultedEvent.GenericResult.denied()
            }
            return
        }

        val mapping = NexoPackHelpers.findMappingByUUID(packId!!) ?: return

        result = ResultedEvent.GenericResult.denied()
        debugLog("Denied remove of Nexo pack ${mapping.dedupHash} for ${serverConnection.player.username}")
    }

    @Subscribe
    fun ServerResourcePackSendEvent.onPackSend() {
        if (!plugin.config.resourcePacks) return
        val player = serverConnection.player
        val serverName = serverConnection.serverInfo.name
        val incomingId = receivedResourcePack.hash?.toHexString()?.trim()
            ?: return debugLog("Received resource pack with no hash for ${player.username}, allowing through")

        val mapping = NexoPackHelpers.findMappingByHash(incomingId)
            ?: return debugLog("Non NexoPack $incomingId for ${player.username}, allowing through")

        val tracked = NexoPackHelpers.trackedPacks(player.uniqueId)
        tracked[mapping.dedupHash]?.let { existing ->
            tracked[mapping.dedupHash] = existing.copy(server = serverName)
            result = ResultedEvent.GenericResult.denied()
            debugLog("Denied duplicate NexoPack-send for ${player.username}: ${mapping.dedupHash}, already loaded")
            return
        }

        // The client stacks packs instead of swapping, so the previous main pack is removed here
        // Template packs coexist and are swept on server switch instead
        if (!mapping.isTemplate) tracked.values.filterNot { it.isTemplate }.forEach { previous ->
            player.removeResourcePacks(previous.packUuid)
            tracked.remove(previous.dedupHash)
            debugLog("Removed previous Nexo pack ${previous.dedupHash} from ${player.username}")
        }

        tracked[mapping.dedupHash] = NexoPackHelpers.AppliedPack(mapping.dedupHash, receivedResourcePack.id, serverName, mapping.isTemplate)
        debugLog("Sending Nexo pack to ${player.username}: ${mapping.dedupHash} (template=${mapping.isTemplate})")
    }

    // The new server has re-sent every pack it wants kept by now, so anything still owned by another server is stale
    // Servers without pack data never re-send, their players keep whatever they hold
    @Subscribe
    fun ServerPostConnectEvent.onServerSwitch() {
        if (!plugin.config.resourcePacks) return
        val serverName = player.currentServer.getOrNull()?.serverInfo?.name ?: return
        if (serverName !in NexoPackHelpers.nexoServers) return

        val tracked = NexoPackHelpers.packHashTracker[player.uniqueId] ?: return
        tracked.values.filter { it.server != serverName }.forEach { stale ->
            player.removeResourcePacks(stale.packUuid)
            tracked.remove(stale.dedupHash)
            debugLog("Removed stale Nexo pack ${stale.dedupHash} from ${player.username} after switching to $serverName")
        }
    }
}
