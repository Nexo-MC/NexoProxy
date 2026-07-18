package com.nexomc.nexoproxy.bungee

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every value here is transcribed from Velocity's own StateRegistry (ResourcePackRequestPacket /
 * RemoveResourcePackPacket map() calls) - if this ever needs updating for a new Minecraft version,
 * that's the canonical source to re-check, not a guess.
 */
class ResourcePackPacketIdsTest {

    @Test
    fun `play push matches known protocol versions exactly`() {
        assertEquals(0x48, ResourcePackPacketIds.playPush(47))   // 1.8
        assertEquals(0x42, ResourcePackPacketIds.playPush(764))  // 1.20.2
        assertEquals(0x44, ResourcePackPacketIds.playPush(765))  // 1.20.3
        assertEquals(0x46, ResourcePackPacketIds.playPush(766))  // 1.20.5
        assertEquals(0x4B, ResourcePackPacketIds.playPush(768))  // 1.21.2
        assertEquals(0x4A, ResourcePackPacketIds.playPush(770))  // 1.21.5
        assertEquals(0x4F, ResourcePackPacketIds.playPush(773))  // 1.21.9
        assertEquals(0x51, ResourcePackPacketIds.playPush(775))  // 26.1
    }

    @Test
    fun `play pop matches known protocol versions exactly`() {
        assertEquals(0x43, ResourcePackPacketIds.playPop(765))
        assertEquals(0x45, ResourcePackPacketIds.playPop(766))
        assertEquals(0x4A, ResourcePackPacketIds.playPop(768))
        assertEquals(0x49, ResourcePackPacketIds.playPop(770))
        assertEquals(0x4E, ResourcePackPacketIds.playPop(773))
        assertEquals(0x50, ResourcePackPacketIds.playPop(775))
    }

    @Test
    fun `config push and pop match known protocol versions exactly`() {
        assertEquals(0x06, ResourcePackPacketIds.configPush(764))
        assertEquals(0x07, ResourcePackPacketIds.configPush(765))
        assertEquals(0x09, ResourcePackPacketIds.configPush(766))

        assertEquals(0x06, ResourcePackPacketIds.configPop(765))
        assertEquals(0x08, ResourcePackPacketIds.configPop(766))
    }

    @Test
    fun `a version between two thresholds resolves to the lower one, not the next`() {
        // 767 is 1.21/1.21.1 - no entry of its own, must fall back to 766's (1.20.5) id.
        assertEquals(0x46, ResourcePackPacketIds.playPush(767))
        assertEquals(0x45, ResourcePackPacketIds.playPop(767))
    }

    @Test
    fun `a version at or above the newest known threshold keeps using it`() {
        // 776 (26.2) has no bump of its own yet - must still resolve to 775's (26.1) id.
        assertEquals(0x51, ResourcePackPacketIds.playPush(776))
        assertEquals(0x50, ResourcePackPacketIds.playPop(776))
    }

    @Test
    fun `config phase has no mapping before it existed as a protocol state`() {
        // Protocol 763 is 1.20/1.20.1 - config-state resource pack packets didn't exist before 1.20.2 (764).
        assertNull(ResourcePackPacketIds.configPush(763))
        assertNull(ResourcePackPacketIds.configPop(763))
    }
}
