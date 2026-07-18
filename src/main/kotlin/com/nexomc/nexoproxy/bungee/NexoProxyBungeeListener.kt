package com.nexomc.nexoproxy.bungee

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.glyphs.GlyphStore
import com.nexomc.nexoproxy.glyphs.ProxyGlyph
import com.nexomc.nexoproxy.glyphs.Shift
import com.nexomc.nexoproxy.pack.NexoPackHelpers
import com.nexomc.nexoproxy.pack.ResourcePackInfo
import net.kyori.adventure.key.Key
import net.md_5.bungee.api.event.PlayerDisconnectEvent
import net.md_5.bungee.api.event.PluginMessageEvent
import net.md_5.bungee.api.event.PostLoginEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler
import team.unnamed.creative.serialize.minecraft.font.FontSerializer

class NexoProxyBungeeListener(private val plugin: NexoProxyBungee) : Listener {

    private fun debugLog(msg: String) {
        if (plugin.config.debug) plugin.logger.info(msg)
    }

    /** Pack obfuscation mapping from a backend Nexo server. */
    @EventHandler
    fun onPackHashMessage(event: PluginMessageEvent) {
        if (event.tag != NexoPackHelpers.PACK_HASH_CHANNEL_NAME || !plugin.config.resourcePacks) return
        val json = JsonParser.parseString(event.data.decodeToString()).asJsonObject
        val pack = ResourcePackInfo(json)
        NexoPackHelpers.addMapping(pack)
        event.isCancelled = true
        debugLog("Registered pack mapping: ${pack.unobfuscatedHash} -> ${pack.obfuscatedHash}")
    }

    /** Glyph metadata sync from a backend Nexo server - mirrors Velocity's GlyphListener. */
    @EventHandler
    fun onGlyphMessage(event: PluginMessageEvent) {
        if (event.tag != GlyphStore.GLYPH_CHANNEL_NAME || !plugin.config.glyphs) return
        val json = JsonParser.parseString(event.data.decodeToString()).asJsonObject

        json.remove("__shift_font")?.asJsonObject?.let {
            val key = Key.key(it["key"].asString)
            val font = it["font"].asJsonObject.toString()
            Shift.shiftFont = FontSerializer.INSTANCE.deserializeFromJsonString(font, key)
        }

        var count = 0
        json.entrySet().forEach { (id, glyphEl) ->
            val obj = glyphEl.asJsonObject
            GlyphStore.glyphs[id] = ProxyGlyph(
                id = id,
                font = Key.key(obj.get("font").asString),
                unicodes = obj.getAsJsonArray("unicodes").map { it.asString },
                defaultColor = obj.get("color")?.takeUnless { it.isJsonNull }?.asInt,
                defaultShadowColor = obj.get("shadow")?.takeUnless { it.isJsonNull }?.asInt,
                permission = obj.get("permission")?.asString ?: "",
                placeholders = obj.getAsJsonArray("placeholders")?.map { it.asString } ?: emptyList(),
                type = obj.get("type")?.takeUnless { it.isJsonNull }?.asString,
                texture = obj.get("texture")?.takeUnless { it.isJsonNull }?.asString,
                atlas = obj.get("atlas")?.takeUnless { it.isJsonNull }?.asString,
            )
            count++
        }
        event.isCancelled = true
        debugLog("Registered $count glyph(s)")
    }

    @EventHandler
    fun onPostLogin(event: PostLoginEvent) {
        if (plugin.config.resourcePacks) {
            BungeeChannelInjector.inject(
                event.player,
                BungeeChannelInjector.RESOURCE_PACK_HANDLER,
                BungeeResourcePackHandler(event.player, plugin),
            )
        }
        if (plugin.config.glyphs) {
            BungeeChannelInjector.inject(
                event.player,
                BungeeChannelInjector.GLYPH_HANDLER,
                BungeeGlyphPacketHandler(plugin),
            )
        }
    }

    @EventHandler
    fun onDisconnect(event: PlayerDisconnectEvent) {
        NexoPackHelpers.packHashTracker.remove(event.player.uniqueId)
        BungeeChannelInjector.uninject(event.player, BungeeChannelInjector.RESOURCE_PACK_HANDLER)
        BungeeChannelInjector.uninject(event.player, BungeeChannelInjector.GLYPH_HANDLER)
    }
}
