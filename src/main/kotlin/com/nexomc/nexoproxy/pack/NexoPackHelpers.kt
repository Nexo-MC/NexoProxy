package com.nexomc.nexoproxy.pack

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

typealias PlayerUUID = UUID
typealias PackUUID = UUID

object NexoPackHelpers {
    private val byObfHash: MutableMap<String, ResourcePackInfo> = ConcurrentHashMap()
    private val byObfUuid: MutableMap<UUID, ResourcePackInfo> = ConcurrentHashMap()

    // packUuid is what the client holds and what a removal must target, server is the last one to have sent it
    data class AppliedPack(
        val dedupHash: String, val packUuid: PackUUID, val server: String, val isTemplate: Boolean,
        // Application order, the client stacks packs so a higher one sits on top and wins
        val seq: Long = nextSeq()
    )

    private val seqCounter = AtomicLong()

    fun nextSeq(): Long = seqCounter.incrementAndGet()

    internal val packHashTracker: MutableMap<PlayerUUID, MutableMap<String, AppliedPack>> = ConcurrentHashMap()

    // Packs let through before their server announced its hashes, keyed by the hash the client received
    // A backend dispatching during configuration beats its own plugin-message, so these are adopted once it arrives
    internal val unrecognisedPacks: MutableMap<PlayerUUID, MutableMap<String, UnrecognisedPack>> = ConcurrentHashMap()

    data class UnrecognisedPack(val packUuid: PackUUID, val server: String, val seq: Long)

    fun rememberUnrecognised(player: PlayerUUID, hash: String, packUuid: PackUUID, server: String) {
        unrecognisedPacks.getOrPut(player) { ConcurrentHashMap() }[hash] = UnrecognisedPack(packUuid, server, nextSeq())
    }

    // removesTemplates is set by Nexo servers that remove a player's disabled templates by id themselves
    data class Announcement(val main: ResourcePackInfo, val templates: List<ResourcePackInfo>, val removesTemplates: Boolean) {
        val mappings: List<ResourcePackInfo> get() = templates + main
        val templateHashes: Set<String> = templates.mapTo(HashSet()) { it.dedupHash }
    }

    private val announcements: MutableMap<String, Announcement> = ConcurrentHashMap()

    private val retired = HashSet<String>()

    val nexoServers: Set<String> get() = announcements.keys

    fun announcement(server: String): Announcement? = announcements[server]

    val PACK_HASH_CHANNEL: MinecraftChannelIdentifier = MinecraftChannelIdentifier.from("nexo:pack_hash")

    // Every regeneration announces new hashes, without retiring the previous ones the mappings grow forever
    @Synchronized
    fun announce(server: String, announcement: Announcement) {
        announcements.put(server, announcement)?.let { previous -> previous.mappings.mapTo(retired) { it.obfuscatedHash } }
        announcement.mappings.forEach(::addMapping)

        val announced = announcements.values.flatMapTo(HashSet()) { it.mappings.map(ResourcePackInfo::obfuscatedHash) }
        val held = packHashTracker.values.flatMapTo(HashSet()) { applied -> applied.values.map { it.packUuid } }
        retired.removeAll { hash ->
            if (hash in announced) return@removeAll true
            val mapping = byObfHash[hash] ?: return@removeAll true
            if (mapping.obfuscatedUuid in held) return@removeAll false
            byObfHash.remove(hash)
            byObfUuid.remove(mapping.obfuscatedUuid)
            true
        }
    }

    fun addMapping(pack: ResourcePackInfo) {
        byObfHash[pack.obfuscatedHash] = pack
        byObfUuid[pack.obfuscatedUuid] = pack
    }

    // Mappings servers currently announce come first, they are the ones a restarted proxy needs before those servers re-announce
    fun mappingsToCache(limit: Int): List<ResourcePackInfo> {
        val announced = announcements.values.flatMapTo(LinkedHashSet()) { it.mappings }
        return (announced + byObfHash.values.filterNot { it in announced }).take(limit)
    }

    fun findMappingByHash(hash: String): ResourcePackInfo? =
        byObfHash[hash] ?: byObfHash.values.find { it.unobfuscatedHash == hash }

    fun findMappingByUUID(uuid: UUID): ResourcePackInfo? = byObfUuid[uuid]

    fun trackedPacks(player: PlayerUUID): MutableMap<String, AppliedPack> =
        packHashTracker.getOrPut(player) { ConcurrentHashMap() }
}
