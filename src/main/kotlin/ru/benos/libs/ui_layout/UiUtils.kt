package ru.benos.libs.ui_layout

import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.nodes.box.UiBoxNode
import ru.benos.libs.ui_layout.nodes.UiNode

object UiUtils {
    val String.literal: Component
        get() = Component.literal(this@literal)

    fun ui(modifier: UiModifier = UiModifier, block: UiBuilder.() -> Unit): UiNode {
        val children = UiBuilder().apply(block).build()
        val box = UiBoxNode(children = children, enableScissor = false, modifier = modifier)

        return box
    }
}