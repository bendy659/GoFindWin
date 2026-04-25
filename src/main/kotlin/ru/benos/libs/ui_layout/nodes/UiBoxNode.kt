package ru.benos.libs.ui_layout.nodes

import ru.benos.libs.ui_layout.UiDsl
import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.data.UiLength
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize
import ru.benos.libs.ui_layout.data.axis.UiAlign
import ru.benos.libs.ui_layout.data.theme.UiBoxTheme
import kotlin.math.max

@UiDsl
open class UiBoxNode(
    private val boxTheme     : UiBoxTheme,
    private val enableScissor: Boolean,
    private val children     : List<UiNode>,

    override val modifier: UiModifier
) : UiNode() {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight)
            .shrink(modifier.padding)

        var contentWidth = 0
        var contentHeight = 0

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
        super.render(runtime, bounds)

        renderBackground(runtime, bounds)

        val inner = bounds.shrink(modifier.padding)
        scissor(runtime, inner) { renderChildren(runtime, inner) }
    }

    open fun renderChildren(runtime: UiRuntime, inner: UiRect) {
        children.forEach { node ->
            val measured = node.measure(runtime, inner.width, inner.height)

            val childWidth  = calcUiLength(node.modifier.width, inner.width, runtime.currentAvailableWidth, measured.width)
            val childHeight = calcUiLength(node.modifier.height, inner.height, runtime.currentAvailableHeight, measured.height)

            val hAlignOffset = calcAlign(modifier.hAlign, inner.width, childWidth)
            val vAlignOffset = calcAlign(modifier.vAlign, inner.height, childHeight)

            val childBounds = UiRect(inner.x + hAlignOffset, inner.y + vAlignOffset, measured.width, measured.height)
            node.render(runtime, childBounds)
        }
    }

    private fun renderBackground(runtime: UiRuntime, bounds: UiRect) {
        val backgroundColor =
            when {
                runtime.isClicked(bounds)  -> boxTheme.backgroundClicked
                runtime.isReleased(bounds) -> boxTheme.backgroundReleased
                runtime.isHovered(bounds)  -> boxTheme.backgroundHovered
                //isFocused       -> boxTheme.backgroundFocused

                else -> boxTheme.background
            }
        val outline =
            when {
                runtime.isClicked(bounds)  -> boxTheme.outlineClicked
                runtime.isReleased(bounds) -> boxTheme.outlineReleased
                runtime.isHovered(bounds)  -> boxTheme.outlineHovered
                //isFocused       -> boxTheme.outlineFocused

                else -> boxTheme.outline
            }

        // Background //
        runtime.guiGraphics.fill(
            bounds.x, bounds.y,
            bounds.right, bounds.bottom,
            backgroundColor
        )

        // Outline //
        if (outline != null && outline.width > 0) {
            runtime.guiGraphics.fill( // top //
                bounds.x, bounds.y,
                bounds.right, bounds.y + outline.width,
                outline.color
            )
            runtime.guiGraphics.fill( // bottom //
                bounds.x, bounds.bottom - outline.width,
                bounds.right, bounds.bottom,
                outline.color
            )
            runtime.guiGraphics.fill( // left //
                bounds.x, bounds.y + outline.width,
                bounds.x + outline.width, bounds.bottom - outline.width,
                outline.color
            )
            runtime.guiGraphics.fill( // right //
                bounds.right - outline.width, bounds.y + outline.width,
                bounds.right, bounds.bottom - outline.width,
                outline.color
            )
        }
    }

    protected fun calcUiLength(uiLength: UiLength, inner: Int, current: Int?, measure: Int): Int =
        when (uiLength) {
            is UiLength.Fill      -> inner
            is UiLength.Available -> current ?: inner
            is UiLength.Expand    -> measure
            else -> measure.coerceAtMost(inner)
        }

    protected fun calcAlign(align: UiAlign, inner: Int, size: Int): Int =
        when (align) {
            UiAlign.Start  -> 0
            UiAlign.Center -> (inner - size) / 2
            UiAlign.End    -> inner - size
        }

    protected fun scissor(runtime: UiRuntime, inner: UiRect, block: () -> Unit) {
        if (enableScissor)
            runtime.guiGraphics.enableScissor(inner.x, inner.y, inner.right, inner.bottom)

        block()

        if (enableScissor)
            runtime.guiGraphics.disableScissor()
    }
}
