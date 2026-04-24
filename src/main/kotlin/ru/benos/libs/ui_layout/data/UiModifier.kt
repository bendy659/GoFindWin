package ru.benos.libs.ui_layout.data

import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.UiTooltip
import ru.benos.libs.ui_layout.builder.UiBuilder

open class UiModifier(
    val minWidth : Int         = 0,
    val minHeight: Int         = 0,
    val padding  : UiInsets    = UiInsets.ZERO,
    val width    : UiLength    = UiLength.Wrap,
    val height   : UiLength    = UiLength.Wrap,
    val hAlign   : UiAlign = UiAlign.START,
    val vAlign   : UiAlign = UiAlign.START,
    val tooltip  : UiTooltip?  = null
) {
    companion object: UiModifier()

    fun copy(
        minWidth  : Int      = this.minWidth,
        minHeight : Int      = this.minHeight,
        padding   : UiInsets = this.padding,
        width     : UiLength = this.width,
        height    : UiLength = this.height,
        hAlign: UiAlign  = this.hAlign,
        vAlign: UiAlign  = this.vAlign,
        tooltip   : UiTooltip?  = this.tooltip
    ): UiModifier =
        UiModifier(
            minWidth, minHeight,
            padding,
            width, height,
            hAlign, vAlign,
            tooltip
        )

    fun minSize(width: Int = minWidth, height: Int = minHeight) =
        this.copy(minWidth = width, minHeight = height)

    // Padding //
    fun padding(all: Int = 0): UiModifier =
        this.copy(padding = UiInsets(all))
    fun padding(horizontal: Int = 0, vertical: Int = 0): UiModifier =
        this.copy(padding = UiInsets(horizontal, vertical))
    fun padding(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0): UiModifier =
        this.copy(padding = UiInsets(left, top, right, bottom))

    // Width ///
    fun fixedWidth(width: Int): UiModifier =
        this.copy(width = UiLength.Fixed(width))
    fun fillWidth(weight: Float = 1f): UiModifier =
        this.copy(width = UiLength.Fill(weight))
    fun availableWidth(weight: Float = 1f): UiModifier =
        this.copy(width = UiLength.Available(weight))
    fun expandWidth(weight: Float = 1f): UiModifier =
        this.copy(width = UiLength.Expand(weight))
    fun wrapWidth(): UiModifier =
        this.copy(width = UiLength.Wrap)

    // Height ///
    fun fixedHeight(height: Int): UiModifier =
        this.copy(height = UiLength.Fixed(height))
    fun fillHeight(weight: Float = 1f): UiModifier =
        this.copy(height = UiLength.Fill(weight))
    fun availableHeight(weight: Float = 1f): UiModifier =
        this.copy(height = UiLength.Available(weight))
    fun expandHeight(weight: Float = 1f): UiModifier =
        this.copy(height = UiLength.Expand(weight))
    fun wrapHeight(): UiModifier =
        this.copy(height = UiLength.Wrap)

    // h/v Alignment //
    fun hAlignment(alignment: UiAlign): UiModifier =
        this.copy(hAlign = alignment)
    fun vAlignment(alignment: UiAlign): UiModifier =
        this.copy(vAlign = alignment)
    fun alignment(hAlign: UiAlign = UiAlign.START, vAlign: UiAlign = UiAlign.START): UiModifier =
        this.copy(hAlign = hAlign, vAlign = vAlign)

    // Tooltip //
    fun tooltip(content: UiBuilder.() -> Unit): UiModifier {
        val content = UiBuilder().apply(content).build()
        val uiTooltip = UiTooltip(content)

        return this.copy(tooltip = uiTooltip)
    }

    // Resolve width //
    internal fun resolveWidth(contentWidth: Int, availableWidth: Int): Int {
        val currentAvailable = UiRuntime.currentRuntime?.currentAvailableWidth

        return width.resolve(minWidth, padding.horizontal, currentAvailable, contentWidth, availableWidth)
    }

    // Resolve width //
    internal fun resolveHeight(contentHeight: Int, availableHeight: Int): Int {
        val currentAvailable = UiRuntime.currentRuntime?.currentAvailableHeight

        return height.resolve(minHeight, padding.vertical, currentAvailable, contentHeight, availableHeight)
    }

    // Resolve //
    private fun UiLength.resolve(
        min: Int, padding: Int, currentAvailable: Int?,
        content: Int, available: Int
    ): Int {
        val b =
            when (this) {
                is UiLength.Fixed     -> this.value
                is UiLength.Fill      -> available
                is UiLength.Available -> currentAvailable ?: available
                is UiLength.Expand    -> {
                    val c = content + padding
                    val d = currentAvailable ?: available

                    kotlin.math.max(c, d)
                }
                UiLength.Wrap -> content + padding
            }

        return kotlin.math.max(min, b)
    }
}