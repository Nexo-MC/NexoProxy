package com.nexomc.nexoproxy

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.UUID

typealias PlayerUUID = UUID
typealias PackUUID = UUID

object NexoPackHelpers {
    internal val nexoObfuscationMappings: MutableMap<String, ResourcePackInfo> = mutableMapOf()
    internal val packHashTracker: MutableMap<PlayerUUID, String> = mutableMapOf()
    val PACK_HASH_CHANNEL: MinecraftChannelIdentifier = MinecraftChannelIdentifier.from("nexo:pack_hash")

    fun findMappingByHash(hash: String): ResourcePackInfo? =
        nexoObfuscationMappings[hash] ?: nexoObfuscationMappings.values.find { it.unobfuscatedHash == hash || it.obfuscatedHash == hash }

    fun findMappingByUUID(uuid: UUID): ResourcePackInfo? =
        nexoObfuscationMappings.values.find { it.obfuscatedUuid == uuid }
}

