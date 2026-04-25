package ru.benos.libs.ui_layout.nodes

import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize
import ru.benos.libs.ui_layout.data.axis.UiTextAlign

open class UiLabelNode(
    private val component        : Component,
    private val textAlign        : UiTextAlign,
    private val wrap             : Boolean,
    private val maxLines         : Int,
    private val enableLabelShadow: Boolean,

    override val modifier: UiModifier
) : UiNode() {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val innerWidth = (maxWidth - modifier.padding.horizontal).coerceAtLeast(0)
        val lines = measureLines(runtime, innerWidth)

        val width = lines.maxOfOrNull(runtime.font::width) ?: 0
        val height = lines.size * runtime.font.lineHeight

        return UiSize(
            modifier.resolveWidth(width, maxWidth),
            modifier.resolveHeight(height, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        super.render(runtime, bounds)

        val inner = bounds.shrink(modifier.padding)
        val lines = measureLines(runtime, inner.width)

        lines.forEachIndexed { index, line ->
            val lineWidth = runtime.font.width(line)
            val drawX =
                when (textAlign) {
                    UiTextAlign.Left   -> inner.x
                    UiTextAlign.Center -> inner.x + ((inner.width - lineWidth) / 2).coerceAtLeast(0)
                    UiTextAlign.Right  -> inner.right - lineWidth
                    UiTextAlign.Fill   -> inner.x // Not realized
                }
            val drawY = inner.y + (index * runtime.font.lineHeight)

            runtime.guiGraphics.drawString(
                runtime.font,
                line,
                drawX, drawY,
                0xFFFFFFFF.toInt(),
                enableLabelShadow
            )
        }
    }

    fun measureLines(runtime: UiRuntime, availableWidth: Int): List<FormattedCharSequence> {
        if (!wrap || availableWidth <= 0)
            return listOf(component.visualOrderText)

        val wrapped = runtime.font.split(component, availableWidth)
            .take(maxLines.coerceAtLeast(1))

        return wrapped.ifEmpty { listOf(component.visualOrderText) }
    }
}