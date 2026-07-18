package com.nexomc.nexoproxy.pack

import com.nexomc.nexoproxy.pack.ResourcePackInfo
import java.util.UUID

typealias PlayerUUID = UUID
typealias PackUUID = UUID

object NexoPackHelpers {
    private val byObfHash: MutableMap<String, ResourcePackInfo> = mutableMapOf()
    private val byUnobfHash: MutableMap<String, ResourcePackInfo> = mutableMapOf()
    private val byObfUuid: MutableMap<UUID, ResourcePackInfo> = mutableMapOf()
    private val byPackUuid: MutableMap<PackUUID, ResourcePackInfo> = mutableMapOf()

    internal val packHashTracker: MutableMap<PlayerUUID, String> = mutableMapOf()
    // Plain string, not a MinecraftChannelIdentifier - this object is shared with the Bungee side too
    // now, and Bungee's runtime classpath has no Velocity classes on it at all. A Velocity-typed eager
    // field here would throw NoClassDefFoundError the instant anything touches this object on Bungee.
    const val PACK_HASH_CHANNEL_NAME = "nexo:pack_hash"

    // A pack gets re-registered with a new hash every time it's rebuilt, same uuid.
    // Drop the old hash/uuid entries for that uuid first or these maps just grow forever.
    fun addMapping(pack: ResourcePackInfo) {
        byPackUuid.remove(pack.uuid)?.let { stale ->
            byObfHash.remove(stale.obfuscatedHash)
            byUnobfHash.remove(stale.unobfuscatedHash)
            byObfUuid.remove(stale.obfuscatedUuid)
        }
        byObfHash[pack.obfuscatedHash] = pack
        byUnobfHash[pack.unobfuscatedHash] = pack
        byObfUuid[pack.obfuscatedUuid] = pack
        byPackUuid[pack.uuid] = pack
    }

    val allMappings: Collection<ResourcePackInfo> get() = byPackUuid.values

    fun findMappingByHash(hash: String): ResourcePackInfo? = byObfHash[hash] ?: byUnobfHash[hash]

    fun findMappingByUUID(uuid: UUID): ResourcePackInfo? = byObfUuid[uuid]
}
