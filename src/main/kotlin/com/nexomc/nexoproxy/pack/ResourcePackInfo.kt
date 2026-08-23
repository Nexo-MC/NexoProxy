package com.nexomc.nexoproxy.pack

import com.google.gson.JsonObject
import java.util.UUID

data class ResourcePackInfo(
    val unobfuscatedHash: String,
    val obfuscatedHash: String,
    val obfuscatedUuid: UUID = UUID.nameUUIDFromBytes(obfuscatedHash.hexToByteArray()),
    val uuid: PackUUID,
    val isTemplate: Boolean = false,
) {
    // Servers without obfuscation report an empty unobfuscated hash, fall back to keep their packs apart
    val dedupHash: String get() = unobfuscatedHash.ifEmpty { obfuscatedHash }

    constructor(json: JsonObject) : this(
        uuid = UUID.fromString(json.get("uuid").asString),
        obfuscatedHash = json.get("obfuscated").asString,
        unobfuscatedHash = json.get("unobfuscated").asString,
        isTemplate = json.get("template")?.asBoolean == true
    )

    constructor(templateHash: String) : this(
        uuid = UUID.nameUUIDFromBytes(templateHash.hexToByteArray()),
        obfuscatedHash = templateHash,
        unobfuscatedHash = templateHash,
        isTemplate = true
    )

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("uuid", uuid.toString())
        addProperty("obfuscated", obfuscatedHash)
        addProperty("unobfuscated", unobfuscatedHash)
        addProperty("template", isTemplate)
    }
}
