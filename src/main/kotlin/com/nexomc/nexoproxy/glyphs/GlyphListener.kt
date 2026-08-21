package com.nexomc.nexoproxy.glyphs

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoProxy
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ServerConnection
import net.kyori.adventure.key.Key
import team.unnamed.creative.serialize.minecraft.font.FontSerializer

class GlyphListener(val plugin: NexoProxy) {

    @Subscribe
    fun PluginMessageEvent.onPluginMessage() {
        when (identifier.id) {
            GlyphStore.GLYPH_CHANNEL.id if (plugin.config.glyphs) -> {
                val serverName = (source as? ServerConnection)?.serverInfo?.name ?: return
                result = PluginMessageEvent.ForwardResult.handled()
                val count = registerGlyphs(JsonParser.parseString(data.decodeToString()).asJsonObject)
                GlyphCache.store(plugin, GlyphCache.GLYPHS, serverName, data)
                if (plugin.config.debug) plugin.logger.info("Registered $count glyph(s) from $serverName")
            }
            BedrockGlyphs.TABLE_CHANNEL.id if (plugin.config.glyphs) -> {
                val serverName = (source as? ServerConnection)?.serverInfo?.name ?: return
                result = PluginMessageEvent.ForwardResult.handled()
                if (!BedrockGlyphs.accept(serverName, data)) return
                GlyphCache.store(plugin, GlyphCache.BEDROCK, serverName, data)
                if (plugin.config.debug) plugin.logger.info("Registered the Bedrock glyph table of $serverName")
            }
        }
    }

    companion object {

        fun registerGlyphs(json: JsonObject): Int {
            json.remove("__shift_font")?.asJsonObject?.let {
                val key = Key.key(it["key"].asString!!)
                val font = it["font"].asJsonObject.toString()
                Shift.shiftFont = FontSerializer.INSTANCE.deserializeFromJsonString(font, key)
            }

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
            }
            return json.size()
        }
    }
}
