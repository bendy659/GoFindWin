package ru.benos.libs.ui_layout

import net.minecraft.network.chat.Component
import ru.benos.libs.helpers.ComponentHelper.literal
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.axis.UiAxis
import ru.benos.libs.ui_layout.data.axis.UiTextAlign
import ru.benos.libs.ui_layout.data.theme.UiBoxColorTheme
import ru.benos.libs.ui_layout.nodes.UiBoxNode
import ru.benos.libs.ui_layout.nodes.UiLabelNode
import ru.benos.libs.ui_layout.nodes.grid.UiLinearLayoutNode

@DslMarker
annotation class UiDsl

// Box //
fun UiBuilder.box(
    boxTheme: UiBoxColorTheme = UiBoxColorTheme.TRANSPARENT,
    enableScissor  : Boolean = false,
    modifier       : UiModifier = UiModifier,
    block: UiBuilder.() -> Unit = { }
) {
    val children = UiBuilder().apply(block).build()
    val node = UiBoxNode(boxTheme, enableScissor, children, modifier)

    this@box.addNode(node)
}

// Label //
fun UiBuilder.label(
    boxTheme: UiBoxColorTheme = UiBoxColorTheme.TRANSPARENT,
    enableScissor: Boolean = false,
    component: Component,
    textAlign: UiTextAlign = UiTextAlign.Left,
    wrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    enableLabelShadow: Boolean = false,
    modifier: UiModifier = UiModifier
) {
    val node = UiLabelNode(boxTheme, enableScissor, component, textAlign, wrap, maxLines, enableLabelShadow, modifier)

    this@label.addNode(node)
}

// private //
private fun UiBuilder.linearLayout(
    axis: UiAxis,
    boxTheme: UiBoxColorTheme,
    enableScissor: Boolean,
    gap: Int,
    modifier: UiModifier,
    block: UiBuilder.() -> Unit
) {
    val children = UiBuilder().apply(block).build()
    val node = UiLinearLayoutNode(axis, boxTheme, enableScissor, gap, children, modifier)

    this@linearLayout.addNode(node)
}
// ======= //

// Row //
fun UiBuilder.row(
    boxTheme: UiBoxColorTheme = UiBoxColorTheme.TRANSPARENT,
    enableScissor: Boolean = true,
    gap: Int = 0,
    modifier: UiModifier = UiModifier,
    block: UiBuilder.() -> Unit
) =
    linearLayout(UiAxis.Horizontal, boxTheme, enableScissor, gap, modifier, block)

// Column //
fun UiBuilder.column(
    boxTheme: UiBoxColorTheme = UiBoxColorTheme.TRANSPARENT,
    enableScissor: Boolean = true,
    gap: Int = 0,
    modifier: UiModifier = UiModifier,
    block: UiBuilder.() -> Unit
) =
    linearLayout(UiAxis.Vertical, boxTheme, enableScissor, gap, modifier, block)

// Label //
fun UiBuilder.label(
    boxTheme: UiBoxColorTheme = UiBoxColorTheme.TRANSPARENT,
    enableScissor: Boolean = false,
    text: String,
    textAlign: UiTextAlign = UiTextAlign.Left,
    wrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    enableLabelShadow: Boolean = false,
    modifier: UiModifier = UiModifier
) =
    this@label.label(boxTheme, enableScissor, text.literal, textAlign, wrap, maxLines, enableLabelShadow, modifier)