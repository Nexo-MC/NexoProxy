# BungeeCord support

Fixes #1. Adds NexoProxy to BungeeCord/Waterfall as a second platform in the same jar - the build now
produces both `velocity-plugin.json` and `bungee.yml`, and whichever proxy loads the jar just uses its
own entry point (`NexoProxy` for Velocity, `com.nexomc.nexoproxy.bungee.NexoProxyBungee` for Bungee).
`NexoConfig`, `ResourcePackInfo` and `NexoPackHelpers` are reused as-is - they had no Velocity-specific
imports to begin with.

## Scope: resource pack dedup only

This does **not** port glyph tag resolution (tab list / scoreboard / title / bossbar / chat rewriting).
That's a separate, larger piece of work - Bungee's chat components aren't Adventure, so it needs its
own bridge, and it touches a lot more packet types. Happy to do it as a follow-up if there's interest,
but didn't want to bundle it into this PR.

## Why this needed more than porting the Velocity code

Velocity has typed events and packet classes for resource packs
(`ServerResourcePackSendEvent`/`ServerResourcePackRemoveEvent`, `ResourcePackRequestPacket`/
`RemoveResourcePackPacket`). BungeeCord has none of that - it forwards these as raw, unregistered
packets. So the Bungee side has to:

- Inject into the player's Netty pipeline directly (`BungeeChannelInjector`) - `Connection#unsafe()`
  only exposes `sendPacket`, nothing pipeline-level, so this reflects into
  `UserConnection#ch`/`ChannelWrapper#getHandle()`, verified against BungeeCord's current source. If
  that ever breaks upstream, injection just silently no-ops rather than throwing.
- Parse the packet bytes by hand (`BungeeResourcePackHandler`) to read the pack hash/uuid, since there's
  no typed packet to read a field off of.

The one thing worth flagging for review: **the packet id for both push and pop changes on almost every
Minecraft protocol version** (checked against Velocity's own `StateRegistry` - e.g. the play-state push
packet has been `0x48`, `0x42`, `0x46`, `0x4B`, `0x4F`, `0x51` across recent versions alone). A single
hardcoded id would silently misparse packets the moment a client's negotiated protocol version doesn't
match whatever was assumed. `ResourcePackPacketIds` mirrors Velocity's full version table instead of
guessing one id, with a test (`ResourcePackPacketIdsTest`) covering the documented thresholds, the gaps
between them, and the newest version PaperMC's own Velocity `dev` branch tracks.

## Testing

- `./gradlew test` - the packet-id version table above.
- Booted a real Waterfall 1.21 (build 615) server with the built jar: loads, `onEnable` completes
  (config generated, pack cache loaded, listener/command/channel all register), `/nexoproxy` works,
  clean shutdown. Log:
  ```
  [INFO]: Loaded plugin NexoProxy version 1.2.1 by boy0000
  [INFO] [NexoProxy]: NexoProxy enabled on BungeeCord (resource pack dedup only - see BUNGEE.md)
  [INFO]: Enabled plugin NexoProxy version 1.2.1 by boy0000
  ```
- Didn't have a way to connect a real Minecraft client through a full Bungee -> backend -> Nexo pack
  flow in the environment I built this in, so the actual packet interception hasn't been exercised
  against a live client/server exchange - only the pieces above. Worth someone confirming that end to
  end before merging, ideally against whatever version(s) of Minecraft you actually run.
