package ru.benos.libs.ui_layout.nodes

import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.UiUtils.literal
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize
import ru.benos.libs.ui_layout.data.UiAlign

class UiLabelNode(
    private val component          : Component,
    private val horizontalAlignment: UiAlign,
    private val verticalAlignment  : UiAlign,
    private val wrap               : Boolean,
    private val maxLines           : Int,
    private val enableLabelShadow  : Boolean,

    override val modifier          : UiModifier
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val innerWidth = (maxWidth - modifier.padding.horizontal).coerceAtLeast(0)
        val lines = measureLines(runtime, innerWidth)

        val width = lines.maxOfOrNull { sequence ->
            runtime.font
                .width(sequence)
        } ?: 0
        val height = lines.size * runtime.font.lineHeight

        return UiSize(
            modifier.resolveWidth(width, maxWidth),
            modifier.resolveHeight(height, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        val inner = bounds.shrink(modifier.padding)
        val lines = measureLines(runtime, inner.width)
        val textBlockHeight = lines.size * runtime.font.lineHeight
        val startY =
            when (verticalAlignment) {
                UiAlign.START  -> inner.y
                UiAlign.CENTER -> inner.y + ((inner.height - textBlockHeight) / 2).coerceAtLeast(0)
                UiAlign.END    -> inner.bottom - textBlockHeight
            }

        lines.forEachIndexed { index, line ->
            val lineWidth = runtime.font.width(line)
            val drawX =
                when (horizontalAlignment) {
                    UiAlign.START  -> inner.x
                    UiAlign.CENTER -> inner.x + ((inner.width - lineWidth) / 2).coerceAtLeast(0)
                    UiAlign.END    -> inner.right - lineWidth
                }
            val drawY = startY + (index * runtime.font.lineHeight)

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

fun UiBuilder.label(
    component: Component,
    horizontalAlignment: UiAlign = UiAlign.START,
    verticalAlignment  : UiAlign = UiAlign.START,
    wrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    enableLabelShadow: Boolean = false,
    modifier: UiModifier = UiModifier
) {
    val node = UiLabelNode(
        component,
        horizontalAlignment,
        verticalAlignment,
        wrap,
        maxLines,
        enableLabelShadow,
        modifier
    )

    this@label.addNode(node)
}

fun UiBuilder.label(
    label: String,
    horizontalAlignment: UiAlign = UiAlign.START,
    verticalAlignment  : UiAlign = UiAlign.START,
    wrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    enableLabelShadow: Boolean = false,
    modifier: UiModifier = UiModifier
) =
    this@label.label(
        label.literal,
        horizontalAlignment,
        verticalAlignment,
        wrap,
        maxLines,
        enableLabelShadow,
        modifier
    )