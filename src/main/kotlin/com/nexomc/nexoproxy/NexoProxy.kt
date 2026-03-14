package com.nexomc.nexoproxy

import com.charleskorn.kaml.Yaml
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.inject.Inject
import com.velocitypowered.api.event.ResultedEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.player.ServerResourcePackRemoveEvent
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path


@Serializable
data class NexoConfig(val debug: Boolean = false)

@Plugin(
    id = "nexoproxy",
    name = "NexoProxy",
    version = BuildConstants.VERSION,
    authors = ["boy0000"]
)
class NexoProxy @Inject constructor(
    val logger: Logger,
    val server: ProxyServer,
    @DataDirectory val dataDirectory: Path
) {

    private val packsFile get() = dataDirectory.resolve(".packs.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var listener: ResourcePackListener

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        val config = loadConfig()
        loadPacks()

        listener = ResourcePackListener(logger, config.debug)
        server.channelRegistrar.register(NexoPackHelpers.PACK_HASH_CHANNEL)
        server.eventManager.register(this, listener)
        server.commandManager.register(
            server.commandManager.metaBuilder("nexoproxy").aliases("nxp").plugin(this).build(),
            NexoProxyCommand(this)
        )
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        savePacks()
    }

    fun reload(source: net.kyori.adventure.audience.Audience) {
        savePacks()
        val config = loadConfig()
        listener.debug = config.debug
        source.sendMessage(net.kyori.adventure.text.Component.text("[NexoProxy] Reloaded config and saved pack cache."))
        logger.info("Reloaded by ${if (source is com.velocitypowered.api.proxy.Player) source.username else "console"}")
    }

    private fun loadConfig(): NexoConfig {
        Files.createDirectories(dataDirectory)
        val configFile = dataDirectory.resolve("config.yml")
        if (Files.notExists(configFile)) {
            NexoProxy::class.java.getResourceAsStream("/config.yml")!!.use { input ->
                Files.copy(input, configFile)
            }
        }
        return Yaml.default.decodeFromString(configFile.toFile().readText())
    }

    private fun loadPacks() {
        if (Files.notExists(packsFile)) return
        runCatching {
            val array = JsonParser.parseReader(packsFile.toFile().reader()).asJsonArray
            for (element in array) {
                NexoPackHelpers.addMapping(ResourcePackInfo(element.asJsonObject))
            }
            logger.info("Loaded ${array.size()} cached pack mapping(s) from ${packsFile.fileName}")
        }.onFailure { logger.warn("Failed to load pack cache: ${it.message}") }
    }

    private fun savePacks() {
        runCatching {
            Files.createDirectories(dataDirectory)
            val entries = NexoPackHelpers.allMappings.toList().takeLast(20)
            val array = JsonArray()
            entries.forEach { array.add(it.toJson()) }
            packsFile.toFile().writeText(gson.toJson(array))
            logger.info("Saved ${entries.size} pack mapping(s) to ${packsFile.fileName}")
        }.onFailure { logger.warn("Failed to save pack cache: ${it.message}") }
    }
}

class ResourcePackListener(val logger: Logger, @Volatile var debug: Boolean) {

    private fun debugLog(msg: String) {
        if (debug) logger.info(msg)
    }

    // Called when a backend Nexo server sends us obfuscation data for a pack
    @Subscribe
    fun PluginMessageEvent.onHashUpload() {
        if (identifier != NexoPackHelpers.PACK_HASH_CHANNEL) return

        val json = JsonParser.parseString(data.decodeToString()).asJsonObject
        val pack = ResourcePackInfo(json)

        NexoPackHelpers.addMapping(pack)
        result = PluginMessageEvent.ForwardResult.handled()

        val serverName = (source as ServerConnection).serverInfo.name
        debugLog("Registered obfuscation mapping: ${pack.unobfuscatedHash} -> ${pack.obfuscatedHash} from $serverName")
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
