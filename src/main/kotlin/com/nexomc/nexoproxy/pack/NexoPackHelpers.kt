package com.nexomc.nexoproxy.pack

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

typealias PlayerUUID = UUID
typealias PackUUID = UUID

object NexoPackHelpers {
    private val byObfHash: MutableMap<String, ResourcePackInfo> = ConcurrentHashMap()
    private val byObfUuid: MutableMap<UUID, ResourcePackInfo> = ConcurrentHashMap()

    // packUuid is what the client holds and what a removal must target, server is the last one to have sent it
    data class AppliedPack(val dedupHash: String, val packUuid: PackUUID, val server: String, val isTemplate: Boolean)

    internal val packHashTracker: MutableMap<PlayerUUID, MutableMap<String, AppliedPack>> = ConcurrentHashMap()

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
