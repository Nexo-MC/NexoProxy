package com.nexomc.nexoproxy

import com.google.gson.JsonObject
import java.util.UUID

data class ResourcePackInfo(
    val unobfuscatedHash: String,
    val obfuscatedHash: String,
    val obfuscatedUuid: UUID = UUID.nameUUIDFromBytes(obfuscatedHash.hexToByteArray()),
    val uuid: PackUUID,
    val url: String
) {
    constructor(json: JsonObject) : this(
        uuid = UUID.fromString(json.get("uuid").asString),
        url = json.get("url").asString,
        obfuscatedHash = json.get("obfuscated").asString,
        unobfuscatedHash = json.get("unobfuscated").asString
    )

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("uuid", uuid.toString())
        addProperty("url", url)
        addProperty("obfuscated", obfuscatedHash)
        addProperty("unobfuscated", unobfuscatedHash)
    }
}