package com.nexomc.nexoproxy.glyphs

import net.kyori.adventure.key.Key
import team.unnamed.creative.font.Font
import team.unnamed.creative.font.FontProvider
import team.unnamed.creative.font.SpaceFontProvider
import kotlin.math.abs

object Shift {
    private val defaultAdvances = mapOf(
        "\uE101" to -1, "\uE102" to -2, "\uE103" to -4, "\uE104" to -8, "\uE105" to -16,
        "\uE106" to -32, "\uE107" to -64, "\uE108" to -128, "\uE109" to -256, "\uE110" to -512,

        "\uE112" to 1, "\uE113" to 2, "\uE114" to 4, "\uE115" to 8, "\uE116" to 16,
        "\uE117" to 32, "\uE118" to 64, "\uE119" to 128, "\uE120" to 256, "\uE121" to 512
    )

    @Volatile var shiftFont: Font = Font.font(Key.key("nexo:shift"), FontProvider.space(defaultAdvances))
        set(value) {
            field = value
            cachedPowers = value.providers().filterIsInstance<SpaceFontProvider>().flatMap { it.advances().toList() }
                .sortedByDescending { abs(it.second) }.filter { it.second != 0 }
        }

    private var cachedPowers = defaultAdvances.toList().sortedByDescending { abs(it.second) }.filter { it.second != 0 }

    fun of(shift: Int): String {
        var remaining = abs(shift)
        return buildString {
            for ((char, value) in cachedPowers) {
                if (shift < 0 && value > 0) continue
                if (shift > 0 && value < 0) continue
                val absValue = abs(value)
                if (remaining >= absValue) {
                    append(char)
                    remaining -= absValue
                }
            }
        }
    }
}