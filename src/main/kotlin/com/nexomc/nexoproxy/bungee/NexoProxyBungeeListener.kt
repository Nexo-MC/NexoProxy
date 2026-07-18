package com.nexomc.nexoproxy.bungee

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.pack.NexoPackHelpers
import com.nexomc.nexoproxy.pack.ResourcePackInfo
import net.md_5.bungee.api.event.PluginMessageEvent
import net.md_5.bungee.api.event.PostLoginEvent
import net.md_5.bungee.api.event.PlayerDisconnectEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler

class NexoProxyBungeeListener(private val plugin: NexoProxyBungee) : Listener {

    private fun debugLog(msg: String) {
        if (plugin.config.debug) plugin.logger.info(msg)
    }

    /** Pack obfuscation mapping from a backend Nexo server. */
    @EventHandler
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.tag != NexoProxyBungee.PACK_HASH_CHANNEL || !plugin.config.resourcePacks) return
        val json = JsonParser.parseString(event.data.decodeToString()).asJsonObject
        val pack = ResourcePackInfo(json)
        NexoPackHelpers.addMapping(pack)
        event.isCancelled = true
        debugLog("Registered pack mapping: ${pack.unobfuscatedHash} -> ${pack.obfuscatedHash}")
    }

    @EventHandler
    fun onPostLogin(event: PostLoginEvent) {
        if (!plugin.config.resourcePacks) return
        BungeeChannelInjector.inject(event.player, BungeeResourcePackHandler(event.player, plugin))
    }

    @EventHandler
    fun onDisconnect(event: PlayerDisconnectEvent) {
        NexoPackHelpers.packHashTracker.remove(event.player.uniqueId)
        if (plugin.config.resourcePacks) BungeeChannelInjector.uninject(event.player)
    }
}
