package com.nexomc.nexoproxy.packets

import com.nexomc.nexoproxy.NexoConfig
import com.velocitypowered.api.event.AwaitingEventExecutor
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import org.slf4j.Logger

/**
 * Async logic here inspired by https://github.com/4drian3d/VPacketEvents
 */
class DisconnectListener(val config: NexoConfig, val logger: Logger) : AwaitingEventExecutor<DisconnectEvent> {

    override fun executeAsync(event: DisconnectEvent): EventTask? {
        if (!config.glyphs) return null
        return EventTask.async { uninjectPlayer(event.player) }
    }

    private fun uninjectPlayer(player: Player) {
        val channel = (player as? ConnectedPlayer)?.connection?.channel ?: return
        channel.eventLoop().submit {
            channel.pipeline().remove(NexoChannelHandler.PACKET_KEY)
            if (config.debug) logger.info("Uninjected ${player.username}")
        }
    }
}