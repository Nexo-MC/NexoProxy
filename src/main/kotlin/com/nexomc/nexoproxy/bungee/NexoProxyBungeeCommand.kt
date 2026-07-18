package com.nexomc.nexoproxy.bungee

import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Command

class NexoProxyBungeeCommand(private val plugin: NexoProxyBungee) : Command("nexoproxy", "nexoproxy.admin", "nxp") {

    override fun execute(sender: CommandSender, args: Array<String>) {
        when (args.firstOrNull()) {
            "reload", "rl" -> plugin.reload(sender)
            "debug" -> {
                plugin.config = plugin.config.copy(debug = !plugin.config.debug)
                plugin.config.saveConfig(plugin.dataFolder.toPath())
                sender.sendMessage(TextComponent("[NexoProxy] Debug mode: ${if (plugin.config.debug) "ON" else "OFF"}"))
            }
            else -> sender.sendMessage(TextComponent("Usage: /nexoproxy reload|rl|debug"))
        }
    }
}
