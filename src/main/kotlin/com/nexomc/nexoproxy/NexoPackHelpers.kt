package com.nexomc.nexoproxy

import com.google.gson.JsonObject
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.UUID

typealias PlayerUUID = UUID
typealias PackUUID = UUID

object NexoPackHelpers {
    val nexoObfuscationMappings: MutableMap<String, ObfuscatedResourcePack> = mutableMapOf()
    val packHashTracker: MutableMap<PlayerUUID, String> = mutableMapOf()
    val HASH_CHANNEL = MinecraftChannelIdentifier.from("nexo:pack_hash")

    fun findMappingByHash(hash: String): ObfuscatedResourcePack? =
        nexoObfuscationMappings[hash]
            ?: nexoObfuscationMappings.values.find { it.unobfuscatedHash == hash }

    fun findMappingByUUID(uuid: UUID): ObfuscatedResourcePack? =
        nexoObfuscationMappings.values.find { it.obfuscatedUuid == uuid }
}

data class ObfuscatedResourcePack(
    val uuid: PackUUID,
    val url: String,
    val obfuscatedHash: String,
    val unobfuscatedHash: String,
    val obfuscatedUuid: UUID = UUID.nameUUIDFromBytes(obfuscatedHash.hexToByteArray())
) {
    constructor(json: JsonObject) : this(
        uuid = UUID.fromString(json.get("uuid").asString),
        url = json.get("url").asString,
        obfuscatedHash = json.get("obfuscated").asString,
        unobfuscatedHash = json.get("unobfuscated").asString
    )
}