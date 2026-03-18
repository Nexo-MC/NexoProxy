package com.nexomc.nexoproxy

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class NexoConfig(
    val debug: Boolean = false,
    val resourcePacks: Boolean = true,
    val glyphs: Boolean = true,
) {
    companion object {
        fun loadConfig(dataDirectory: Path): NexoConfig {
            Files.createDirectories(dataDirectory)
            val configFile = dataDirectory.resolve("config.yml")
            if (Files.notExists(configFile)) {
                NexoProxy::class.java.getResourceAsStream("/config.yml")!!.use { input ->
                    Files.copy(input, configFile)
                }
            }
            return Yaml.default.decodeFromString(configFile.toFile().readText())
        }
    }
}