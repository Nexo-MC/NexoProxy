package com.nexomc.nexoproxy

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.inject.Inject
import com.nexomc.nexoproxy.glyphs.GlyphListener
import com.nexomc.nexoproxy.pack.NexoPackHelpers
import com.nexomc.nexoproxy.pack.ResourcePackListener
import com.nexomc.nexoproxy.pack.ResourcePackInfo
import com.nexomc.nexoproxy.packets.DisconnectListener
import com.nexomc.nexoproxy.glyphs.GlyphStore
import com.nexomc.nexoproxy.glyphs.ScoreboardListener
import com.nexomc.nexoproxy.packets.LoginListener
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.jvm.java
import kotlin.jvm.optionals.getOrNull


@Plugin(
    id = "nexoproxy",
    name = "NexoProxy",
    version = BuildConstants.VERSION,
    authors = ["boy0000"],
    dependencies = [Dependency(id = "velocitab", optional = true)]
)
class NexoProxy @Inject constructor(
    val logger: Logger,
    val proxyServer: ProxyServer,
    @DataDirectory val dataDirectory: Path,
    val metricsFactory: Metrics.Factory,
) {

    lateinit var config: NexoConfig internal set
    var isVelocitabPresent: Boolean = false
    var isScoreboardApiPresent: Boolean = false
    internal set

    val HANDSHAKE_CHANNEL = MinecraftChannelIdentifier.from("nexo:proxy_handshake")
    private val packsFile get() = dataDirectory.resolve(".packs.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        metricsFactory.make(this, 30155)
        config = NexoConfig.loadConfig(dataDirectory)
        loadPacks()
        GlyphStore.enabled = config.glyphs

        isVelocitabPresent = proxyServer.pluginManager.getPlugin("velocitab").isPresent
        isScoreboardApiPresent = (proxyServer.pluginManager.getPlugin("velocity-scoreboard-api").getOrNull()
            ?.description?.version?.getOrNull()?.firstOrNull()?.digitToIntOrNull() ?: 0) >= 2

        proxyServer.eventManager.register(this, LoginEvent::class.java, LoginListener(this))
        proxyServer.eventManager.register(this, DisconnectEvent::class.java, -404, DisconnectListener(this))


        proxyServer.channelRegistrar.register(MinecraftChannelIdentifier.from(NexoPackHelpers.PACK_HASH_CHANNEL_NAME))
        proxyServer.eventManager.register(this, ResourcePackListener(this))

        proxyServer.channelRegistrar.register(MinecraftChannelIdentifier.from(GlyphStore.GLYPH_CHANNEL_NAME), HANDSHAKE_CHANNEL)
        proxyServer.eventManager.register(this, GlyphListener(this))

        if (isScoreboardApiPresent) proxyServer.eventManager.register(this, ScoreboardListener())

        proxyServer.commandManager.register(
            proxyServer.commandManager.metaBuilder("nexoproxy").aliases("nxp").plugin(this).build(),
            NexoProxyCommand(this)
        )
    }

    @Subscribe
    fun ServerPostConnectEvent.onServerPostConnect() {
        val server = player.currentServer.getOrNull() ?: return
        if (server.server.playersConnected.size != 1) return
        server.sendPluginMessage(HANDSHAKE_CHANNEL, byteArrayOf())
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        savePacks()
    }

    fun reload(source: net.kyori.adventure.audience.Audience) {
        savePacks()
        config = NexoConfig.loadConfig(dataDirectory)
        GlyphStore.enabled = config.glyphs
        source.sendMessage(net.kyori.adventure.text.Component.text("[NexoProxy] Reloaded config and saved pack cache."))
        logger.info("Reloaded by ${if (source is Player) source.username else "console"}")
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

