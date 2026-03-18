package com.nexomc.nexoproxy.glyphs

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoConfig
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ServerConnection
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.slf4j.Logger

class GlyphListener(val logger: Logger, var config: NexoConfig) {

    @Subscribe
    fun PluginMessageEvent.onPluginMessage() {
        // Glyph Component mappings from a backend Nexo server
        // Expected payload: {"heart": <adventure-gson-json>, "crown": <adventure-gson-json>, ...}
        when (identifier.id) {
            GlyphStore.GLYPH_CHANNEL.id if (config.glyphs) -> {
                val json = JsonParser.parseString(data.decodeToString()).asJsonObject
                json.entrySet().forEach { (id, componentEl) ->
                    GlyphStore.glyphComponents[id] = GsonComponentSerializer.gson().deserialize(componentEl.toString())
                }
                result = PluginMessageEvent.ForwardResult.handled()
                val serverName = (source as ServerConnection).serverInfo.name
                if (config.debug) logger.info("Registered ${json.size()} glyph(s) from $serverName")
            }
        }
    }
}