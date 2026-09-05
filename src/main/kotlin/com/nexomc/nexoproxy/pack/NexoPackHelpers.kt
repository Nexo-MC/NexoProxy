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

    internal val nexoServers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val PACK_HASH_CHANNEL: MinecraftChannelIdentifier = MinecraftChannelIdentifier.from("nexo:pack_hash")

    fun addMapping(pack: ResourcePackInfo) {
        byObfHash[pack.obfuscatedHash] = pack
        byObfUuid[pack.obfuscatedUuid] = pack
    }

    val allMappings: Collection<ResourcePackInfo> get() = byObfHash.values

    fun findMappingByHash(hash: String): ResourcePackInfo? =
        byObfHash[hash] ?: byObfHash.values.find { it.unobfuscatedHash == hash }

    fun findMappingByUUID(uuid: UUID): ResourcePackInfo? = byObfUuid[uuid]

    fun trackedPacks(player: PlayerUUID): MutableMap<String, AppliedPack> =
        packHashTracker.getOrPut(player) { ConcurrentHashMap() }
}
