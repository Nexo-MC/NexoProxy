package com.nexomc.nexoproxy.glyphs

import com.google.gson.JsonParser
import com.nexomc.nexoproxy.NexoProxy
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

// Backends only send their glyphs once a player connects, too late for their tablist and scoreboard
object GlyphCache {

    const val GLYPHS = "glyphs"
    const val BEDROCK = "bedrock-glyphs"

    fun store(plugin: NexoProxy, kind: String, server: String, data: ByteArray) {
        runCatching {
            plugin.dataDirectory.resolve(kind).createDirectories().resolve(fileName(server)).writeBytes(data)
        }.onFailure { plugin.logger.warn("Failed to cache $kind for $server: ${it.message}") }
    }

    // Call once at startup, before any player can connect
    fun load(plugin: NexoProxy) {
        val glyphs = read(plugin, GLYPHS).sumOf { (_, data) ->
            runCatching { GlyphListener.registerGlyphs(JsonParser.parseString(data.decodeToString()).asJsonObject) }.getOrDefault(0)
        }
        val tables = read(plugin, BEDROCK).count { (server, data) -> BedrockGlyphs.accept(server, data) }
        if (glyphs > 0 || tables > 0)
            plugin.logger.info("Loaded $glyphs cached glyph(s) and $tables cached Bedrock glyph table(s)")
    }

    private fun read(plugin: NexoProxy, kind: String): List<Pair<String, ByteArray>> = runCatching {
        plugin.dataDirectory.resolve(kind).listDirectoryEntries().filter { it.isRegularFile() }.mapNotNull { file ->
            runCatching { file.fileName.toString().removeSuffix(SUFFIX) to file.readBytes() }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun fileName(server: String) = server.replace(UNSAFE, "_") + SUFFIX

    private val UNSAFE = Regex("[^A-Za-z0-9._-]")
    private const val SUFFIX = ".bin"
}
