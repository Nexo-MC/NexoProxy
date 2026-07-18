package com.nexomc.nexoproxy

import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component

class NexoProxyCommand(private val plugin: NexoProxy) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val args = invocation.arguments().firstOrNull()
        when (args) {
            "reload", "rl" -> plugin.reload(invocation.source())
            "debug" -> {
                plugin.config = plugin.config.copy(debug = !plugin.config.debug)
                plugin.config.saveConfig(plugin.dataDirectory)
                invocation.source().sendMessage(Component.text("[NexoProxy] Debug mode: ${if (plugin.config.debug) "ON" else "OFF"}"))
            }
            else -> invocation.source().sendMessage(Component.text("Usage: /nexoproxy reload|rl|debug"))
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        return if (invocation.arguments().size <= 1) listOf("reload", "rl", "debug") else emptyList()
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return invocation.source().hasPermission("nexoproxy.admin")
    }
}
