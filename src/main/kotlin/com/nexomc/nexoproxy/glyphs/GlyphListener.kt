package com.nexomc.nexoproxy.glyphs

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoConfig
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ServerConnection
import net.kyori.adventure.key.Key
import org.slf4j.Logger
import team.unnamed.creative.serialize.minecraft.font.FontSerializer

class GlyphListener(val logger: Logger, var config: NexoConfig) {

    @Subscribe
    fun PluginMessageEvent.onPluginMessage() {
        when (identifier.id) {
            GlyphStore.GLYPH_CHANNEL.id if (config.glyphs) -> {
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
                    )
                    count++
                }
                result = PluginMessageEvent.ForwardResult.handled()
                val serverName = (source as ServerConnection).serverInfo.name
                if (config.debug) logger.info("Registered $count glyph(s) from $serverName")
            }
        }
    }
}
