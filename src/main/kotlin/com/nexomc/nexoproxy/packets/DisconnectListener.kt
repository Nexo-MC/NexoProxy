package com.nexomc.nexoproxy.packets

import com.nexomc.nexoproxy.NexoProxy
import com.velocitypowered.api.event.AwaitingEventExecutor
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.proxy.connection.client.ConnectedPlayer

/**
 * Async logic here inspired by https://github.com/4drian3d/VPacketEvents
 */
class DisconnectListener(val plugin: NexoProxy) : AwaitingEventExecutor<DisconnectEvent> {

    override fun executeAsync(event: DisconnectEvent): EventTask? {
        if (!plugin.config.glyphs) return null
        return EventTask.async { uninjectPlayer(event.player) }
    }

    private fun uninjectPlayer(player: Player) {
        val channel = (player as? ConnectedPlayer)?.connection?.channel ?: return
        channel.eventLoop().submit {
            if (channel.pipeline().get(NexoChannelHandler.PACKET_KEY) != null) {
                channel.pipeline().remove(NexoChannelHandler.PACKET_KEY)
            }
            if (plugin.config.debug) plugin.logger.info("Uninjected ${player.username}")
        }
    }
}