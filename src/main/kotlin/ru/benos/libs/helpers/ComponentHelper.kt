package ru.benos.libs.helpers

import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB

object ComponentHelper {
    val String.literal: Component
        get() = Component.literal(this@literal)

    val String.translate: Component
        get() = Component.translatable(this@translate)

    val Iterable<Component>.component: Component
        get() {
            val component = Component.empty()
            this@component.forEach { component.append(it) }

            return component
        }

    fun Component.style(
        textColor   : Int     = this@style.style.color?.value ?: ARGB.white(255),
        isBold      : Boolean = this@style.style.isBold,
        isItalic    : Boolean = this@style.style.isItalic,
        isUnderlined: Boolean = this@style.style.isUnderlined
    ): Component {
        val style = this@style.style
            .withColor(textColor)
            .withBold(isBold)
            .withItalic(isItalic)
            .withUnderlined(isUnderlined)

        return this@style.copy().setStyle(style)
    }
}