package ru.benos.libs.ui_layout.nodes

import ru.benos.libs.ui_layout.UiDebugging
import ru.benos.libs.ui_layout.UiDsl
import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.data.*
import ru.benos.libs.ui_layout.data.axis.UiAlign
import ru.benos.libs.ui_layout.data.theme.UiBoxColorTheme
import ru.benos.libs.ui_layout.data.theme.UiCanvas
import kotlin.math.max

@UiDsl
open class UiBoxNode(
    private val boxTheme     : UiBoxColorTheme,
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
        registerEvents(runtime, bounds)

        val hasTransform = modifier.transform != UiTransform.DEFAULT
        if (hasTransform) {
            runtime.guiGraphics.pose().pushMatrix()

            val pivotX = bounds.x + bounds.width * modifier.transform.origin.x
            val pivotY = bounds.y + bounds.height * modifier.transform.origin.y

            val offset = modifier.transform.translate
            val rotation = modifier.transform.rotation
            val scale = modifier.transform.scale

            runtime.guiGraphics.pose().translate(offset.x, offset.y)
            runtime.guiGraphics.pose().rotateAbout(rotation, pivotX, pivotY)
            runtime.guiGraphics.pose().scaleAround(scale.x, scale.y, pivotX, pivotY)
        }

        renderBackground(runtime, bounds)
        if (UiDebugging.DEBUG_UI_BOUNDS) renderDebugBounds(runtime, bounds)

        val inner = bounds.shrink(modifier.padding)
        scissor(runtime, inner) { renderChildren(runtime, inner) }

        if (hasTransform)
            runtime.guiGraphics.pose().popMatrix()
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

                else -> boxTheme.backgroundNormal
            }
        val overlays =
            when {
                runtime.isClicked(bounds)  -> boxTheme.overlayClicked
                runtime.isReleased(bounds) -> boxTheme.overlayReleased
                runtime.isHovered(bounds)  -> boxTheme.overlayHovered
                //isFocused       -> boxTheme.outlineFocused

                else -> boxTheme.overlayNormal
            }

        // Background //
        backgroundColor.render(runtime.guiGraphics, bounds)

        // Overlays //
        if (!overlays.isNullOrEmpty())
            overlays.forEach { overlay -> overlay.render(runtime.guiGraphics, bounds) }
    }

    protected fun renderDebugBounds(runtime: UiRuntime, bounds: UiRect) {
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + 1, 0xFFFF0000.toInt()) // top
        runtime.guiGraphics.fill(bounds.x, bounds.bottom - 1, bounds.right, bounds.bottom, 0xFFFF0000.toInt()) // bottom
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.bottom, 0xFFFF0000.toInt()) // left
        runtime.guiGraphics.fill(bounds.right - 1, bounds.y, bounds.right, bounds.bottom, 0xFFFF0000.toInt()) // right
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
