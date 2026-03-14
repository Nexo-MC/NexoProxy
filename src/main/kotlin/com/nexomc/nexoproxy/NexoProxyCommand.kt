package com.nexomc.nexoproxy

import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component

class NexoProxyCommand(private val plugin: NexoProxy) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val args = invocation.arguments()
        if (args.firstOrNull() in setOf("reload", "rl")) {
            plugin.reload(invocation.source())
        } else {
            invocation.source().sendMessage(Component.text("Usage: /nexoproxy reload|rl"))
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        return if (invocation.arguments().size <= 1) listOf("reload", "rl") else emptyList()
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return invocation.source().hasPermission("nexoproxy.admin")
    }
}
