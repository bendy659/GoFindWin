package ru.benos.libs.ui_layout.nodes.box

import ru.benos.libs.ui_layout.UiDsl
import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.*
import ru.benos.libs.ui_layout.nodes.UiNode
import kotlin.math.max

@UiDsl
open class UiBoxNode(
    private val enableScissor: Boolean,
    private val children     : List<UiNode>,

    override val modifier: UiModifier
) : UiNode {
    companion object {
        fun UiBuilder.box(
            enableScissor: Boolean = false,
            modifier: UiModifier = UiModifier,
            block: UiBuilder.() -> Unit
        ) {
            val children = UiBuilder().apply(block).build()
            val node = UiBoxNode(enableScissor, children, modifier)

            this@box.addNode(node)
        }
    }

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight)
            .shrink(modifier.padding)

        var contentWidth = 0; var contentHeight = 0

        children.forEach { child ->
            val measured = child.measure(runtime, inner.width, inner.height)

            contentWidth  = max(contentWidth, measured.width)
            contentHeight = max(contentHeight, measured.height)
        }

        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        renderBackground(runtime, bounds)

        val inner = bounds.shrink(modifier.padding)

        if (enableScissor)
            runtime.guiGraphics.enableScissor(inner.x, inner.y, inner.right, inner.bottom)

        children.forEach { child ->
            val measured = child.measure(runtime, inner.width, inner.height)

            val childWidth  = calcUiLength(child.modifier.width, inner.width, runtime.currentAvailableWidth, measured.width)
            val childHeight = calcUiLength(child.modifier.height, inner.height, runtime.currentAvailableHeight, measured.height)

            val hAlignOffset = calcAlign(modifier.hAlign, inner.width, childWidth)
            val vAlignOffset = calcAlign(modifier.vAlign, inner.height, childHeight)

            val childBounds = UiRect(inner.x + hAlignOffset, inner.y + vAlignOffset, measured.width, measured.height)
            child.render(runtime, childBounds)
        }

        if (enableScissor)
            runtime.guiGraphics.disableScissor()
    }

    open fun renderBackground(runtime: UiRuntime, bounds: UiRect) { /* Implementation */ }

    protected fun calcUiLength(uiLength: UiLength, inner: Int, current: Int?, measure: Int): Int =
        when (uiLength) {
            is UiLength.Fill      -> inner
            is UiLength.Available -> current ?: inner
            is UiLength.Expand    -> measure
            else -> measure.coerceAtMost(inner)
        }

    protected fun calcAlign(align: UiAlign, inner: Int, size: Int): Int =
        when (align) {
            UiAlign.START  -> 0
            UiAlign.CENTER -> (inner - size) / 2
            UiAlign.END    -> inner - size
        }
}