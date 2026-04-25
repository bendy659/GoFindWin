package ru.benos.libs.ui_layout.data

import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.axis.UiAlign
import ru.benos.libs.ui_layout.nodes.UiTooltip

open class UiModifier(
    // Basic //
    val minWidth : Int         = 0,
    val minHeight: Int         = 0,
    val padding  : UiInsets    = UiInsets.ZERO,
    val width    : UiLength    = UiLength.Wrap,
    val height   : UiLength    = UiLength.Wrap,
    val hAlign   : UiAlign = UiAlign.Start,
    val vAlign   : UiAlign = UiAlign.Start,
    val tooltip  : UiTooltip?  = null,

    // Events //
    val onMouseEnter  : (()         -> Unit)? = null,
    val onMouseHovered: ((Int, Int) -> Unit)? = null,
    val onMouseExit   : (()         -> Unit)? = null,

    val onMouseClicked : ((Int, Int, Int) -> Boolean)? = null,
    val onMouseReleased: ((Int, Int, Int) -> Boolean)? = null,

    val onMouseDragged : ((Int, Int, Int) -> Boolean)? = null,
    val onMouseScrolled: ((Double) -> Boolean)? = null,

    val onKeyReleased: ((Int)  -> Boolean)? = null,
    val onKeyPressed : ((Int)  -> Boolean)? = null,
    val onCharTyped    : ((Char) -> Boolean)? = null,

    val onFocused: (() -> Unit)? = null
) {
    companion object: UiModifier()

    fun copy(
        // Basic //
        minWidth  : Int      = this.minWidth,
        minHeight : Int      = this.minHeight,
        padding   : UiInsets = this.padding,
        width     : UiLength = this.width,
        height    : UiLength = this.height,
        hAlign: UiAlign = this.hAlign,
        vAlign: UiAlign = this.vAlign,
        tooltip   : UiTooltip?  = this.tooltip,

        // Events //
        onMouseEnter  : (()         -> Unit)? = this.onMouseEnter,
        onMouseHovered: ((Int, Int) -> Unit)? = this.onMouseHovered,
        onMouseExit   : (()         -> Unit)? = this.onMouseExit,

        onMouseClicked : ((Int, Int, Int) -> Boolean)? = this.onMouseClicked,
        onMouseReleased: ((Int, Int, Int) -> Boolean)? = this.onMouseReleased,

        onMouseDragged : ((Int, Int, Int) -> Boolean)? = this.onMouseDragged,
        onMouseScrolled: ((Double) -> Boolean)? = this.onMouseScrolled,

        onKeyPressed : ((Int)  -> Boolean)? = this.onKeyPressed,
        onKeyReleased: ((Int)  -> Boolean)? = this.onKeyReleased,
        onCharTyped  : ((Char) -> Boolean)? = this.onCharTyped,

        onFocused: (() -> Unit)? = this.onFocused,
    ): UiModifier =
        UiModifier(
            // Basic //
            minWidth, minHeight,
            padding,
            width, height,
            hAlign, vAlign,
            tooltip,

            // Events //
            onMouseEnter, onMouseHovered, onMouseExit,
            onMouseClicked, onMouseReleased,
            onMouseDragged, onMouseScrolled,
            onKeyReleased, onKeyPressed, onCharTyped,
            onFocused
        )

    //// Builder ////

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
    fun hAlign(alignment: UiAlign): UiModifier =
        this.copy(hAlign = alignment)
    fun vAlign(alignment: UiAlign): UiModifier =
        this.copy(vAlign = alignment)
    fun align(hAlign: UiAlign = UiAlign.Start, vAlign: UiAlign = UiAlign.Start): UiModifier =
        this.copy(hAlign = hAlign, vAlign = vAlign)

    // Tooltip //
    fun tooltip(content: UiBuilder.() -> Unit): UiModifier {
        val content = UiBuilder().apply(content).build()
        val uiTooltip = UiTooltip(content)

        return this.copy(tooltip = uiTooltip)
    }

    /// Events ///

    // Mouse //
    fun onMouseEnter(block: () -> Unit) =
        this.copy(onMouseEnter = block)

    fun onMouseHovered(block: (mouseX: Int, mouseY: Int) -> Unit) =
        this.copy(onMouseHovered = block)

    fun onMouseExit(block: () -> Unit) =
        this.copy(onMouseExit = block)

    fun onMouseClicked(block: (key: Int, mouseX: Int, mouseY: Int) -> Boolean) =
        this.copy(onMouseClicked = block)

    fun onMouseReleased(block: (key: Int, mouseX: Int, mouseY: Int) -> Boolean) =
        this.copy(onMouseReleased = block)

    fun onMouseDragged(block: (key: Int, mouseX: Int, mouseY: Int) -> Boolean) =
        this.copy(onMouseDragged = block)

    fun onMouseScrolled(block: (factor: Double) -> Boolean) =
        this.copy(onMouseScrolled = block)

    // Key input //
    fun onKeyPressed(block: (Int) -> Boolean) =
        this.copy(onKeyPressed = block)

    fun onKeyReleased(block: (Int) -> Boolean) =
        this.copy(onKeyReleased = block)

    fun onCharTyped(block: (Char) -> Boolean) =
        this.copy(onCharTyped = block)

    // Other //
    fun onFocused(block: () -> Unit) =
        this.copy(onFocused = block)

    //// Units ////

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