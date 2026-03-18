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
import com.nexomc.nexoproxy.packets.LoginListener
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.configuration.PlayerEnterConfigurationEvent
import com.velocitypowered.api.event.player.configuration.PlayerFinishConfigurationEvent
import com.velocitypowered.api.event.player.configuration.PlayerFinishedConfigurationEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.jvm.java


@Plugin(
    id = "nexoproxy",
    name = "NexoProxy",
    version = BuildConstants.VERSION,
    authors = ["boy0000"]
)
class NexoProxy @Inject constructor(
    val logger: Logger,
    val proxyServer: ProxyServer,
    @DataDirectory val dataDirectory: Path,
    val metricsFactory: Metrics.Factory,
) {

    private val packsFile get() = dataDirectory.resolve(".packs.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var packListener: ResourcePackListener
    private lateinit var glyphListener: GlyphListener

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        metricsFactory.make(this, 30155)
        val config = NexoConfig.loadConfig(dataDirectory)
        loadPacks()
        GlyphStore.enabled = config.glyphs

        proxyServer.eventManager.register(this, PlayerFinishedConfigurationEvent::class.java, LoginListener(config, logger))
        proxyServer.eventManager.register(this, DisconnectEvent::class.java, -404, DisconnectListener(config, logger))


        packListener = ResourcePackListener(logger, config)
        proxyServer.channelRegistrar.register(NexoPackHelpers.PACK_HASH_CHANNEL)
        proxyServer.eventManager.register(this, packListener)

        glyphListener = GlyphListener(logger, config)
        proxyServer.channelRegistrar.register(GlyphStore.GLYPH_CHANNEL)
        proxyServer.eventManager.register(this, glyphListener)

        proxyServer.commandManager.register(
            proxyServer.commandManager.metaBuilder("nexoproxy").aliases("nxp").plugin(this).build(),
            NexoProxyCommand(this)
        )
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        savePacks()
    }

    fun reload(source: net.kyori.adventure.audience.Audience) {
        savePacks()
        val config = NexoConfig.loadConfig(dataDirectory)
        packListener.config = config
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

