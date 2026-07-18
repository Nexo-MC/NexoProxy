# BungeeCord support

Fixes #1. Adds NexoProxy to BungeeCord/Waterfall as a second platform in the same jar - the build now
produces both `velocity-plugin.json` and `bungee.yml`, and whichever proxy loads the jar just uses its
own entry point (`NexoProxy` for Velocity, `com.nexomc.nexoproxy.bungee.NexoProxyBungee` for Bungee).
`NexoConfig`, `ResourcePackInfo`, `NexoPackHelpers`, `GlyphStore`/`GlyphHandler`/`Shift` are all reused
as-is - none of them have Velocity-specific imports (see the fix below for the one that used to).

## Scope: resource pack dedup + glyph tag resolution (tab/scoreboard/title/bossbar/chat)

Both pieces of what NexoProxy does on Velocity are now covered on Bungee too. Not included: map-instance
stuff, anything beyond what the Velocity side already does - this is a straight port, not new features.

## A real bug this caught: NoClassDefFoundError just from touching the shared objects

While wiring up glyphs, a test that only calls `GlyphStore.enabled`/`.glyphs` blew up with
`NoClassDefFoundError: com/velocitypowered/api/proxy/messages/MinecraftChannelIdentifier`. Both
`GlyphStore` and `NexoPackHelpers` had a `MinecraftChannelIdentifier` field (`GLYPH_CHANNEL`/
`PACK_HASH_CHANNEL`), eagerly constructed in the class initializer. That's fine on Velocity, but these
objects are shared with Bungee now, and Bungee's runtime classpath has zero Velocity classes on it -
so the *instant* anything touches either object (adding a pack mapping, checking `packHashTracker`,
even just `savePacks()` on a normal shutdown), the whole class init throws and takes the plugin down.
Fixed both to hold a plain channel-name string instead; Velocity-only code builds its own
`MinecraftChannelIdentifier` from that string where it actually needs one. See the first commit.

## Why this needed more than porting the Velocity code

Velocity has typed events/packets for resource packs and typed packet classes for chat/tab/scoreboard.
BungeeCord's story is different for each:

- **Resource packs**: no typed classes at all - it forwards push/pop as raw, unregistered packets. So
  this pipeline-injects directly (`BungeeChannelInjector` - reflection into
  `UserConnection#ch`/`ChannelWrapper#getHandle()`, since `Connection#unsafe()` only exposes
  `sendPacket`; verified against BungeeCord's current source, no-ops rather than throws if that ever
  changes upstream) and parses the packet bytes by hand (`BungeeResourcePackHandler`).
  **Worth a second look:** the packet id for both push and pop changes on almost every Minecraft
  protocol version (checked against Velocity's own `StateRegistry` - the play-state push id alone has
  been `0x48`, `0x42`, `0x46`, `0x4B`, `0x4F`, `0x51` across recent versions). `ResourcePackPacketIds`
  mirrors Velocity's full version table instead of guessing one id, with a test covering the documented
  thresholds and the gaps between them.
- **Glyphs**: Bungee *does* have typed packet classes here (`Title`/`Subtitle`/`BossBar`/`SystemChat`/
  `Team`/`ScoreboardObjective`/`ScoreboardScore`/`PlayerListItem(Update)`/`PlayerListHeaderFooter` - all
  verified against current source, field names and all), so `BungeeGlyphPacketHandler` just mutates
  fields directly and forwards the same object - no raw parsing needed for this part. The catch is
  Bungee's chat components are `BaseComponent`, not Adventure, and `resolveGlyphs()` (the actual tag
  logic in `GlyphHandler.kt`, shared with Velocity) only knows Adventure's `Component`. `BungeeGlyphs.kt`
  bridges the two by round-tripping through the same JSON chat format both sides already understand -
  this never touches the wire, it's purely between two in-memory Java object models; Bungee's own
  encoder handles the actual wire format for whatever protocol version the client negotiated.
- Adventure itself isn't provided by BungeeCord at runtime (unlike Velocity, which does), so it's
  bundled here (`net.kyori:adventure-api`/`adventure-text-serializer-gson`, pinned to 4.26.1 - the exact
  version Velocity's own dependency chain already resolves to). Deliberately **not** relocated:
  relocating `net.kyori` would rewrite the shared `GlyphHandler.kt`'s own bytecode references too,
  breaking it on Velocity (whose platform-provided Adventure classes stay at `net.kyori.*`,
  unrelocated). Since Velocity's copy is `compileOnly`, nothing of it ever ends up bundled in the jar to
  begin with - only Bungee's copy does, so there's nothing to actually collide with.

## Testing

- `./gradlew test`: the packet-id version table, and the `BaseComponent`<->Adventure glyph bridge (a
  real `BaseComponent` in, through `resolveGlyphs()`, a real `BaseComponent` back out - registered/
  unknown/disabled cases).
- Booted a real Waterfall 1.21 (build 615) server with the built jar: loads, `onEnable` completes
  (config generated, pack cache loaded, both channels registered, listener/command registered),
  `/nexoproxy` works, clean shutdown - and since `onEnable` itself now touches both `NexoPackHelpers`
  and `GlyphStore` (registering their channels), this run is what actually caught the class-init bug
  above being fixed for real, not just in the test JVM. Log:
  ```
  [INFO]: Loaded plugin NexoProxy version 1.2.1 by boy0000
  [INFO] [NexoProxy]: NexoProxy enabled on BungeeCord - see BUNGEE.md
  [INFO]: Enabled plugin NexoProxy version 1.2.1 by boy0000
  ```
- Didn't have a way to connect a real Minecraft client through a full Bungee -> backend -> Nexo pack/
  glyph exchange in the environment I built this in, so the actual packet interception (both resource
  pack dedup and glyph tag rendering in tab/scoreboard/chat) hasn't been exercised against a live
  client/server connection - only the pieces above. Worth confirming end to end before merging, ideally
  against whatever Minecraft version(s) you actually run.
