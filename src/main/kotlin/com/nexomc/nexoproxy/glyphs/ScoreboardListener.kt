package com.nexomc.nexoproxy.glyphs

import com.velocitypowered.api.TextHolder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.scoreboard.ObjectiveEvent
import com.velocitypowered.api.event.scoreboard.ScoreEvent
import com.velocitypowered.api.event.scoreboard.TeamEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.scoreboard.NumberFormat
import net.kyori.adventure.text.Component

class ScoreboardListener {

    @Subscribe
    fun TeamEvent.Register.onScoreboardEvent() {
        if (!isMutable) return
        prefix = prefix.resolveGlyphs(player)
        suffix = suffix.resolveGlyphs(player)
        displayName = displayName.resolveGlyphs(player)
    }

    @Subscribe
    fun TeamEvent.Update.onScoreboardEvent() {
        if (!isMutable) return
        prefix = prefix.resolveGlyphs(player)
        suffix = suffix.resolveGlyphs(player)
        displayName = displayName.resolveGlyphs(player)
    }

    @Subscribe
    fun ScoreEvent.Set.onScore() {
        if (!isMutable) return
        displayName?.forBedrock(player)?.also(::setDisplayName)
        numberFormat?.resolveGlyphs(player)?.let(::setNumberFormat)
    }

    @Subscribe
    fun ObjectiveEvent.Register.onScoreboardEvent() {
        if (!isMutable) return
        title = title.resolveGlyphs(player)
        numberFormat?.resolveGlyphs(player)?.let(::setNumberFormat)
    }
    @Subscribe
    fun ObjectiveEvent.Update.onScoreboardEvent() {
        if (!isMutable) return
        title = title.resolveGlyphs(player)
        numberFormat?.resolveGlyphs(player)?.let(::setNumberFormat)
    }

    private fun NumberFormat.resolveGlyphs(player: Player): NumberFormat? {
        return (this as? NumberFormat.FixedFormat)?.component?.forBedrock(player)?.let(NumberFormat::fixed)
    }

    private fun TextHolder.resolveGlyphs(player: Player): TextHolder {
        return TextHolder.of(legacyText, modernText.forBedrock(player))
    }

    private fun Component.forBedrock(player: Player): Component =
        BedrockGlyphs.remapForBedrock(resolveGlyphs(), player)
}
