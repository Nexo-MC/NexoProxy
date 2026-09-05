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
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.scheduler.ScheduledTask
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
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
                adoptUnrecognisedPacks()
            }
        }
    }

    // Clean up player state on disconnect
    @Subscribe
    fun DisconnectEvent.onDisconnect() {
        NexoPackHelpers.packHashTracker.remove(player.uniqueId)
        NexoPackHelpers.unrecognisedPacks.remove(player.uniqueId)
        pendingSweeps.remove(player.uniqueId)?.cancel()
        mainPackReplaced -= player.uniqueId
    }

    // Removes of the main Nexo pack are denied, it is only ever replaced by a different one
    // Template packs are toggled by the server itself, so those go through like any non-Nexo pack
    @Subscribe
    fun ServerResourcePackRemoveEvent.onPackRemove() {
        if (!plugin.config.resourcePacks) return
        val player = serverConnection.player
        val tracked = NexoPackHelpers.packHashTracker[player.uniqueId]
        if (packId == null) {
            // A clear-all opens a new set, whatever the previous one did to the pack order is settled
            mainPackReplaced -= player.uniqueId
            if (tracked.isNullOrEmpty()) return
            val serverName = serverConnection.serverInfo.name
            result = ResultedEvent.GenericResult.denied()
            dropTemplates(player, tracked, serverName)
            // It is also the only marker of a server declaring its whole set, sweep whatever it does not re-send
            scheduleSweep(player, serverName)
            return
        }

        val mapping = NexoPackHelpers.findMappingByUUID(packId!!) ?: return

        if (mapping.isTemplate) {
            tracked?.remove(mapping.dedupHash)
            debugLog("Allowed remove of Nexo template-pack ${mapping.dedupHash} for ${player.username}")
            return
        }

        result = ResultedEvent.GenericResult.denied()
        debugLog("Denied remove of Nexo pack ${mapping.dedupHash} for ${player.username}")
    }

    // Claims packs applied before their mapping arrived, a backend can dispatch before its own plugin-message
    // Nothing would ever replace such a pack and the player would end up holding two main packs
    private fun adoptUnrecognisedPacks() {
        plugin.proxyServer.allPlayers.forEach { player ->
            val pending = NexoPackHelpers.unrecognisedPacks[player.uniqueId] ?: return@forEach

            pending.entries.toList().forEach entry@{ (hash, unrecognised) ->
                val mapping = NexoPackHelpers.findMappingByHash(hash) ?: return@entry
                pending.remove(hash)
                val tracked = NexoPackHelpers.trackedPacks(player.uniqueId)
                val adopted = NexoPackHelpers.AppliedPack(
                    mapping.dedupHash, unrecognised.packUuid, unrecognised.server, mapping.isTemplate, unrecognised.seq
                )

                val existing = tracked[mapping.dedupHash]
                if (existing != null && existing.packUuid != adopted.packUuid) {
                    // Two servers handed the client the same pack under different pack-uuids, only the last one is in effect
                    val (kept, stale) = if (adopted.seq > existing.seq) adopted to existing else existing to adopted
                    tracked[mapping.dedupHash] = kept
                    player.removeResourcePacks(stale.packUuid)
                    debugLog("Removed duplicate copy of ${mapping.dedupHash} from ${player.username}")
                } else {
                    tracked[mapping.dedupHash] = adopted
                    debugLog("Adopted ${mapping.dedupHash} for ${player.username}, it arrived before ${unrecognised.server} announced it")
                }

                // Only the main pack handed over last is in effect, anything below it is dead weight
                if (mapping.isTemplate) return@entry
                val newest = tracked.values.filterNot { it.isTemplate }.maxOfOrNull { it.seq } ?: return@entry
                tracked.values.filter { !it.isTemplate && it.seq < newest }.forEach { stale ->
                    player.removeResourcePacks(stale.packUuid)
                    tracked.remove(stale.dedupHash)
                    debugLog("Removed superseded main pack ${stale.dedupHash} from ${player.username}")
                }
            }

            if (pending.isEmpty()) NexoPackHelpers.unrecognisedPacks.remove(player.uniqueId)
        }
    }

    // A clear-all is how a server re-dispatches its whole set, multipack toggles included
    // Its templates are dropped here so the sends that follow re-add the ones still enabled
    private fun dropTemplates(player: Player, tracked: MutableMap<String, NexoPackHelpers.AppliedPack>, serverName: String) {
        tracked.values.filter { it.isTemplate && it.server == serverName }.forEach { template ->
            player.removeResourcePacks(template.packUuid)
            tracked.remove(template.dedupHash)
            debugLog("Dropped Nexo template-pack ${template.dedupHash} from ${player.username} before $serverName re-sends its set")
        }
    }

    @Subscribe
    fun ServerResourcePackSendEvent.onPackSend() {
        if (!plugin.config.resourcePacks) return
        val player = serverConnection.player
        val serverName = serverConnection.serverInfo.name
        val incomingId = receivedResourcePack.hash?.toHexString()?.trim()
            ?: return debugLog("Received resource pack with no hash for ${player.username}, allowing through")

        val mapping = NexoPackHelpers.findMappingByHash(incomingId) ?: run {
            NexoPackHelpers.rememberUnrecognised(player.uniqueId, incomingId, receivedResourcePack.id, serverName)
            return debugLog("Non NexoPack $incomingId for ${player.username}, allowing through")
        }

        val tracked = NexoPackHelpers.trackedPacks(player.uniqueId)
        tracked[mapping.dedupHash]?.let { existing ->
            // A template only overrides the main pack while it sits above it, re-apply it when one was just stacked on top
            if (mapping.isTemplate && player.uniqueId in mainPackReplaced) {
                player.removeResourcePacks(existing.packUuid)
                tracked.remove(existing.dedupHash)
                debugLog("Re-applying template-pack ${mapping.dedupHash} for ${player.username}, it has to stay above the new main pack")
                return@let
            }

            tracked[mapping.dedupHash] = existing.copy(server = serverName)
            result = ResultedEvent.GenericResult.denied()
            debugLog("Denied duplicate NexoPack-send for ${player.username}: ${mapping.dedupHash}, already loaded")
            return
        }

        // The client stacks packs instead of swapping, so the previous main pack is removed here
        // Template packs coexist and are swept on server switch instead
        if (!mapping.isTemplate) {
            tracked.values.filterNot { it.isTemplate }.forEach { previous ->
                player.removeResourcePacks(previous.packUuid)
                tracked.remove(previous.dedupHash)
                debugLog("Removed previous Nexo pack ${previous.dedupHash} from ${player.username}")
            }
            mainPackReplaced += player.uniqueId
        }

        tracked[mapping.dedupHash] = NexoPackHelpers.AppliedPack(mapping.dedupHash, receivedResourcePack.id, serverName, mapping.isTemplate)
        debugLog("Sending Nexo pack to ${player.username}: ${mapping.dedupHash} (template=${mapping.isTemplate})")
    }

    @Subscribe
    fun ServerPostConnectEvent.onServerSwitch() {
        if (!plugin.config.resourcePacks) return
        val serverName = player.currentServer.getOrNull()?.serverInfo?.name ?: return
        scheduleSweep(player, serverName)
    }

    // The new server re-sends every pack it wants kept, so anything still owned by another server afterwards is stale
    // Delayed since a backend only dispatches once its pack finished generating, sweeping on connect raced that
    private fun scheduleSweep(player: Player, serverName: String) {
        pendingSweeps.remove(player.uniqueId)?.cancel()
        pendingSweeps[player.uniqueId] = plugin.proxyServer.scheduler.buildTask(plugin, Runnable {
            pendingSweeps.remove(player.uniqueId)
            sweepStalePacks(player, serverName)
        }).delay(SWEEP_DELAY).schedule()
    }

    private fun sweepStalePacks(player: Player, serverName: String) {
        mainPackReplaced -= player.uniqueId
        // Servers without pack data never re-send, their players keep whatever they hold
        if (serverName !in NexoPackHelpers.nexoServers) return
        if (player.currentServer.getOrNull()?.serverInfo?.name != serverName) return

        val tracked = NexoPackHelpers.packHashTracker[player.uniqueId] ?: return
        tracked.values.filter { it.server != serverName }.forEach { stale ->
            player.removeResourcePacks(stale.packUuid)
            tracked.remove(stale.dedupHash)
            debugLog("Removed stale Nexo pack ${stale.dedupHash} from ${player.username}, $serverName never re-sent it")
        }
    }

    companion object {
        private val SWEEP_DELAY: Duration = Duration.ofSeconds(3)
        private val pendingSweeps: MutableMap<PlayerUUID, ScheduledTask> = ConcurrentHashMap()

        // Players whose main pack was replaced during the set currently being dispatched
        private val mainPackReplaced: MutableSet<PlayerUUID> = ConcurrentHashMap.newKeySet()
    }
}
