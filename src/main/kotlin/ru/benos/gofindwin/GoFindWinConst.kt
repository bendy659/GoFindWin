package ru.benos.gofindwin

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.CommonColors
import net.minecraft.util.RandomSource

object GoFindWinConst {
    const val MOD_ID  : String = "gofindwin"
    const val MOD_NAME: String = "GoFindWin"

    var DEV: Boolean = true

    interface IEnum { val id: String }

    enum class EffectsProfiles(override val id: String): IEnum {
        SELECT("select"),
        BAD("bad"),
        AVERAGE("average"),
        NEW_RECORD("new_record")
    }

    val String.k: Component
        get() = Component.literal(this@k)

    val String.translate: Component
        get() = Component.translatable(this@translate)

    val Iterable<Component>.component: Component
        get() {
            val component = Component.empty()
            this@component.forEach { component.append(it) }

            return component
        }

    val String.ident: Identifier get() =
        Identifier.parse(this@ident)

    val String.mident: Identifier get() =
        "$MOD_ID:${this@mident}".ident

    fun randColor(
        rnd: RandomSource = RandomSource.create(),
        withAlpha: Boolean = false
    ): Int {
        val r = rnd.nextInt(0, 255)
        val g = rnd.nextInt(0, 255)
        val b = rnd.nextInt(0, 255)
        val a = if (withAlpha) rnd.nextInt(0, 255) else 255

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun Component.style(
        color: Int = CommonColors.WHITE,
        bold: Boolean = false,
        italic: Boolean = false,
        underlined: Boolean = false
    ): Component {
        val mutable = this@style.copy()
            .withStyle(
                Style.EMPTY
                    .withColor(color)
                    .withBold(bold)
                    .withItalic(italic)
                    .withUnderlined(underlined)
            )
        return mutable
    }
}