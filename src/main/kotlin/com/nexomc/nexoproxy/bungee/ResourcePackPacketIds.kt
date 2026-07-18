package com.nexomc.nexoproxy.bungee

/**
 * BungeeCord has no typed packet classes for the resource pack push/pop packets - it forwards them as
 * raw, unregistered packets (unlike Velocity, which has `ResourcePackRequestPacket`/
 * `RemoveResourcePackPacket`). The packet id shifts on almost every protocol bump, so a single fixed id
 * silently misparses packets for players on a different client version. This mirrors Velocity's own
 * mapping (`com.velocitypowered.proxy.protocol.StateRegistry`) instead of guessing one id.
 *
 * Each table is (protocol version, packet id) pairs in ascending version order; the id in effect for a
 * given connection is the one at the largest version threshold <= its negotiated protocol version.
 */
internal object ResourcePackPacketIds {

    private val CONFIG_PUSH = intArrayOf(
        764, 0x06,
        765, 0x07,
        766, 0x09,
    )

    private val CONFIG_POP = intArrayOf(
        765, 0x06,
        766, 0x08,
    )

    private val PLAY_PUSH = intArrayOf(
        47, 0x48,
        107, 0x32,
        335, 0x33,
        338, 0x34,
        393, 0x37,
        477, 0x39,
        573, 0x3A,
        735, 0x39,
        751, 0x38,
        755, 0x3C,
        759, 0x3A,
        760, 0x3D,
        761, 0x3C,
        762, 0x40,
        764, 0x42,
        765, 0x44,
        766, 0x46,
        768, 0x4B,
        770, 0x4A,
        773, 0x4F,
        775, 0x51,
    )

    private val PLAY_POP = intArrayOf(
        765, 0x43,
        766, 0x45,
        768, 0x4A,
        770, 0x49,
        773, 0x4E,
        775, 0x50,
    )

    fun configPush(protocolVersion: Int): Int? = lookup(CONFIG_PUSH, protocolVersion)
    fun configPop(protocolVersion: Int): Int? = lookup(CONFIG_POP, protocolVersion)
    fun playPush(protocolVersion: Int): Int? = lookup(PLAY_PUSH, protocolVersion)
    fun playPop(protocolVersion: Int): Int? = lookup(PLAY_POP, protocolVersion)

    private fun lookup(table: IntArray, protocolVersion: Int): Int? {
        var result: Int? = null
        var i = 0
        while (i < table.size && table[i] <= protocolVersion) {
            result = table[i + 1]
            i += 2
        }
        return result
    }
}
