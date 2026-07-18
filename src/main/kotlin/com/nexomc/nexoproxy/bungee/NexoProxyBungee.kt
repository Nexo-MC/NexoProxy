package com.nexomc.nexoproxy.bungee

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoConfig
import com.nexomc.nexoproxy.glyphs.GlyphStore
import com.nexomc.nexoproxy.pack.NexoPackHelpers
import com.nexomc.nexoproxy.pack.ResourcePackInfo
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import org.bstats.bungeecord.Metrics
import java.nio.file.Files

/**
 * BungeeCord support for NexoProxy - see BUNGEE.md. Reuses NexoConfig/ResourcePackInfo/NexoPackHelpers/
 * GlyphStore/GlyphHandler/Shift as-is; none of them have Velocity-specific imports and all behave
 * identically here.
 */
class NexoProxyBungee : Plugin() {

    lateinit var config: NexoConfig
        internal set

    private val packsFile get() = dataFolder.toPath().resolve(".packs.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun onEnable() {
        Metrics(this, 30155)
        config = NexoConfig.loadConfig(dataFolder.toPath())
        loadPacks()

        proxy.pluginManager.registerListener(this, NexoProxyBungeeListener(this))
        proxy.pluginManager.registerCommand(this, NexoProxyBungeeCommand(this))
        proxy.registerChannel(NexoPackHelpers.PACK_HASH_CHANNEL_NAME)
        proxy.registerChannel(GlyphStore.GLYPH_CHANNEL_NAME)

        logger.info("NexoProxy enabled on BungeeCord - see BUNGEE.md")
    }

    override fun onDisable() {
        savePacks()
        proxy.unregisterChannel(NexoPackHelpers.PACK_HASH_CHANNEL_NAME)
        proxy.unregisterChannel(GlyphStore.GLYPH_CHANNEL_NAME)
    }

    fun reload(source: CommandSender) {
        savePacks()
        config = NexoConfig.loadConfig(dataFolder.toPath())
        source.sendMessage(TextComponent("[NexoProxy] Reloaded config and saved pack cache."))
        logger.info("Reloaded by ${source.name}")
    }

    private fun loadPacks() {
        if (Files.notExists(packsFile)) return
        runCatching {
            val array = JsonParser.parseReader(packsFile.toFile().reader()).asJsonArray
            for (element in array) {
                NexoPackHelpers.addMapping(ResourcePackInfo(element.asJsonObject))
            }
            logger.info("Loaded ${array.size()} cached pack mapping(s) from ${packsFile.fileName}")
        }.onFailure { logger.warning("Failed to load pack cache: ${it.message}") }
    }

    private fun savePacks() {
        runCatching {
            Files.createDirectories(dataFolder.toPath())
            val entries = NexoPackHelpers.allMappings.toList().takeLast(20)
            val array = JsonArray()
            entries.forEach { array.add(it.toJson()) }
            packsFile.toFile().writeText(gson.toJson(array))
            logger.info("Saved ${entries.size} pack mapping(s) to ${packsFile.fileName}")
        }.onFailure { logger.warning("Failed to save pack cache: ${it.message}") }
    }
}
