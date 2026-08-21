package com.nexomc.nexoproxy.glyphs

import com.google.gson.JsonParser
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.TranslationArgument
import org.geysermc.floodgate.api.FloodgateApi
import org.geysermc.geyser.api.GeyserApi
import team.unnamed.creative.font.Font
import team.unnamed.creative.font.SpaceFontProvider
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.jvm.optionals.getOrNull

object BedrockGlyphs {

    val TABLE_CHANNEL: MinecraftChannelIdentifier = MinecraftChannelIdentifier.from("scaffolding:glyph_remap")
    val HANDSHAKE_CHANNEL: MinecraftChannelIdentifier = MinecraftChannelIdentifier.from("scaffolding:proxy_handshake")

    const val VERSION = 1

    private const val DEFAULT_FONT = "minecraft:default"

    private const val MAX_TABLE_BYTES = 1024 * 1024

    // Java glyph codepoints mapped to the Bedrock ones a backend baked its pack with, per font
    class Table(private val byFont: Map<String, Map<Int, IntArray>>) {

        val default: Map<Int, IntArray> = byFont[DEFAULT_FONT].orEmpty()

        val isEmpty: Boolean get() = byFont.isEmpty()

        fun font(key: String): Map<Int, IntArray>? = byFont[key]

        companion object {
            fun parse(json: ByteArray): Table = Table(
                JsonParser.parseString(json.decodeToString()).asJsonObject.entrySet().associate { (font, table) ->
                    font to table.asJsonObject.entrySet().associate { (codepoint, mapped) ->
                        val array = mapped.asJsonArray
                        codepoint.toInt() to IntArray(array.size() - 1) { array[it + 1].asInt }
                    }
                }
            )
        }
    }

    private val tables = ConcurrentHashMap<String, Table>()

    fun accept(server: String, data: ByteArray): Boolean = runCatching {
        val input = DataInputStream(ByteArrayInputStream(data))
        if (input.readByte().toInt() != VERSION) return false
        if (input.readUTF().isEmpty()) return false
        val length = input.readInt()
        if (length !in 1..MAX_TABLE_BYTES) return false
        val table = Table.parse(GZIPInputStream(ByteArrayInputStream(input.readNBytes(length))).use { it.readBytes() })
        if (table.isEmpty) tables.remove(server) else tables[server] = table
        true
    }.getOrDefault(false)

    fun remapForBedrock(component: Component, player: Player): Component {
        if (tables.isEmpty() || !BedrockPlayers.isBedrock(player)) return component
        return rewrite(component, tableFor(player) ?: return component)
    }

    // Tablists can be built before currentServer is set, fall back to the only table when there is a single backend
    private fun tableFor(player: Player): Table? {
        val server = player.currentServer.getOrNull()?.serverInfo?.name
        return server?.let(tables::get) ?: tables.values.singleOrNull()
    }

    // inherited is the font a parent set, which a stripped parent no longer passes on to its children on the client
    private fun rewrite(component: Component, table: Table, inherited: String? = null): Component {
        var self = component
        val own = self.style().font()?.asString()
        val font = own ?: inherited
        if (self is TextComponent) {
            val fontTable = font?.let(table::font)
            self = when {
                // Bedrock fonts have no negative advance, drop <shift> spacers
                own == Shift.shiftFont.key().asString() -> self.content("")
                // Strip the font too, Bedrock uses its global table
                fontTable != null -> self.content(remap(self.content(), fontTable)).font(null)
                own == null -> self.content(remap(self.content(), table.default))
                else -> self
            }
        }
        if (self is TranslatableComponent && self.arguments().isNotEmpty()) self = self.arguments(self.arguments().map { argument ->
            (argument.value() as? Component)?.let { TranslationArgument.component(rewrite(it, table, font)) } ?: argument
        })
        val children = self.children()
        if (children.isNotEmpty()) self = self.children(children.map { rewrite(it, table, font) })
        return self
    }

    private fun remap(text: String, table: Map<Int, IntArray>): String {
        if (text.isEmpty() || table.isEmpty()) return text
        val out = StringBuilder(text.length)
        var i = 0
        var changed = false
        while (i < text.length) {
            val codepoint = text.codePointAt(i)
            i += Character.charCount(codepoint)
            val cells = table[codepoint]
            when {
                codepoint in shiftChars() -> changed = true
                cells != null -> { cells.forEach(out::appendCodePoint); changed = true }
                else -> out.appendCodePoint(codepoint)
            }
        }
        return if (changed) out.toString() else text
    }

    private var shiftCache: Pair<Font, Set<Int>>? = null

    private fun shiftChars(): Set<Int> {
        val font = Shift.shiftFont
        shiftCache?.let { (source, chars) -> if (source === font) return chars }
        val chars = runCatching {
            font.providers().asSequence().filterIsInstance<SpaceFontProvider>().flatMap { it.advances().keys }
                .mapNotNullTo(mutableSetOf()) { it.takeIf { it.isNotEmpty() }?.codePointAt(0) }
        }.getOrDefault(emptySet())
        shiftCache = font to chars
        return chars
    }
}

object BedrockPlayers {

    private val cache = ConcurrentHashMap<UUID, Boolean>()

    private var detector: ((UUID) -> Boolean)? = null

    fun init(proxyServer: ProxyServer) {
        detector = when {
            proxyServer.pluginManager.getPlugin("floodgate").isPresent -> { id -> FloodgateApi.getInstance().isFloodgatePlayer(id) }
            proxyServer.pluginManager.getPlugin("geyser").isPresent -> { id -> GeyserApi.api().isBedrockPlayer(id) }
            else -> null
        }
    }

    fun isBedrock(player: Player): Boolean =
        cache.getOrPut(player.uniqueId) { detector?.invoke(player.uniqueId) ?: (player.uniqueId.mostSignificantBits == 0L) }

    fun forget(id: UUID) {
        cache.remove(id)
    }
}
