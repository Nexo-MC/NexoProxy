package com.nexomc.nexoproxy.glyphs

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoProxy
import com.velocitypowered.api.TextHolder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.scoreboard.ScoreboardEvent
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.scoreboard.ProxyScoreboard
import net.kyori.adventure.key.Key
import team.unnamed.creative.serialize.minecraft.font.FontSerializer

class GlyphListener(val plugin: NexoProxy) {

    @Subscribe
    fun PluginMessageEvent.onPluginMessage() {
        when (identifier.id) {
            GlyphStore.GLYPH_CHANNEL_NAME if (plugin.config.glyphs) -> {
                val json = JsonParser.parseString(data.decodeToString()).asJsonObject

                // Check for shift font override
                json.remove("__shift_font")?.asJsonObject?.let {
                    val key = Key.key(it["key"].asString!!)
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
                result = PluginMessageEvent.ForwardResult.handled()
                val serverName = (source as ServerConnection).serverInfo.name
                if (plugin.config.debug) plugin.logger.info("Registered $count glyph(s) from $serverName")
            }
        }
    }
}
