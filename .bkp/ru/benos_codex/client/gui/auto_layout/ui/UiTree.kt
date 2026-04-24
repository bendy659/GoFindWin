package ru.benos_codex.client.gui.auto_layout.ui

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.ARGB
import net.minecraft.util.CommonColors
import org.lwjgl.glfw.GLFW
import ru.benos_codex.client.gui.auto_layout.Theme
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

object UiDebugOverlay {
    var enabled: Boolean = false
    var showLabels: Boolean = true
    var showContentBoxes: Boolean = true
}

enum class UiDebugLayer(
    val label: String,
    val fillColor: Int,
    val outlineColor: Int
) {
    ScrollArea("ScrollArea", ARGB.color(32, 80, 220, 255), ARGB.color(220, 80, 220, 255)),
    Grid("Grid", ARGB.color(28, 255, 180, 80), ARGB.color(220, 255, 180, 80)),
    Column("Column", ARGB.color(28, 120, 255, 180), ARGB.color(220, 120, 255, 180)),
    Row("Row", ARGB.color(28, 120, 180, 255), ARGB.color(220, 120, 180, 255)),
    Box("Box", ARGB.color(24, 255, 120, 220), ARGB.color(220, 255, 120, 220)),
    Group("Group", ARGB.color(24, 255, 220, 120), ARGB.color(220, 255, 220, 120)),
    Label("Label", ARGB.color(18, 240, 240, 240), ARGB.color(190, 240, 240, 240)),
    ItemStack("ItemStack", ARGB.color(28, 180, 255, 120), ARGB.color(220, 180, 255, 120)),
    Render("Render", ARGB.color(28, 180, 180, 180), ARGB.color(220, 180, 180, 180)),
    Separator("Separator", ARGB.color(40, 255, 255, 255), ARGB.color(220, 255, 255, 255)),
    Dropdown("Dropdown", ARGB.color(28, 220, 120, 255), ARGB.color(220, 220, 120, 255)),
    Button("Button", ARGB.color(28, 255, 120, 160), ARGB.color(220, 255, 120, 160)),
    TextField("TextField", ARGB.color(28, 120, 255, 255), ARGB.color(220, 120, 255, 255)),
    Checkbox("Checkbox", ARGB.color(28, 140, 255, 120), ARGB.color(220, 140, 255, 120)),
    Multiline("Multiline", ARGB.color(28, 255, 160, 120), ARGB.color(220, 255, 160, 120)),
    Slider("Slider", ARGB.color(28, 255, 210, 120), ARGB.color(220, 255, 210, 120)),
    Progress("Progress", ARGB.color(28, 120, 255, 140), ARGB.color(220, 120, 255, 140)),
    Split("Split", ARGB.color(28, 255, 120, 120), ARGB.color(220, 255, 120, 120)),
    Gap("Gap", ARGB.color(56, 255, 80, 80), ARGB.color(190, 255, 80, 80)),
    Padding("Padding", ARGB.color(24, 255, 180, 40), ARGB.color(220, 255, 180, 40)),
    Content("Content", ARGB.color(18, 40, 255, 120), ARGB.color(220, 40, 255, 120))
}

/** Per-frame UI context that stores input state, hover state and transient interaction regions. */
class UiRuntime(
    var guiGraphics: GuiGraphics,
    var font: Font,
    var mouseX: Int,
    var mouseY: Int
) {
    private val availableSpaceStack: MutableList<UiSize> = mutableListOf()
    private val overlayRenderers: MutableList<(screenWidth: Int, screenHeight: Int) -> Unit> = mutableListOf()
    private val clickRegions: MutableList<ClickRegion> = mutableListOf()
    private val dragRegions: MutableList<DragRegion> = mutableListOf()
    private val scrollRegions: MutableList<ScrollRegion> = mutableListOf()
    private var activeDragRegion: DragRegion? = null
    private var focusedTextInputKey: String? = null
    private val textInputStates: MutableMap<String, TextInputState> = mutableMapOf()
    private val scrollStates: MutableMap<String, ScrollState> = mutableMapOf()
    private val dropdownStates: MutableMap<String, DropdownState> = mutableMapOf()
    private val pressedStates: MutableMap<String, Boolean> = mutableMapOf()
    private val toggleStates: MutableMap<String, Boolean> = mutableMapOf()
    private val textInputRects: MutableMap<String, UiRect> = mutableMapOf()
    private val dropdownInputs: MutableMap<String, UiDropdownInput> = mutableMapOf()
    private val dropdownRegions: MutableMap<String, MutableList<UiRect>> = mutableMapOf()
    private val debugLabelSlots: MutableMap<Pair<Int, Int>, Int> = mutableMapOf()
    private var activeDropdownKey: String? = null
    private var passiveRenderDepth: Int = 0
    private var hoveredTooltip: UiTooltip? = null

    fun beginFrame(
        guiGraphics: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ) {
        this.guiGraphics = guiGraphics
        this.font = font
        this.mouseX = mouseX
        this.mouseY = mouseY
        currentRuntime = this
        availableSpaceStack.clear()
        overlayRenderers.clear()
        clickRegions.clear()
        dragRegions.clear()
        scrollRegions.clear()
        textInputRects.clear()
        dropdownInputs.clear()
        dropdownRegions.clear()
        debugLabelSlots.clear()
        hoveredTooltip = null
    }

    fun addClickRegion(rect: UiRect, priority: Boolean = false, onClick: (mouseX: Double, mouseY: Double) -> Unit) {
        if (passiveRenderDepth > 0) return
        clickRegions += ClickRegion(rect, priority, onClick)
    }

    fun addDragRegion(
        rect: UiRect,
        priority: Boolean = false,
        onStart: (mouseX: Double, mouseY: Double) -> Unit = { _, _ -> },
        onDrag: (mouseX: Double, mouseY: Double) -> Unit,
        onEnd: (mouseX: Double, mouseY: Double) -> Unit = { _, _ -> }
    ) {
        if (passiveRenderDepth > 0) return
        dragRegions += DragRegion(rect, priority, onStart, onDrag, onEnd)
    }

    private fun findTopDragRegion(mouseX: Double, mouseY: Double): DragRegion? =
        dragRegions.asReversed().firstOrNull { it.priority && it.rect.contains(mouseX, mouseY) }
            ?: dragRegions.asReversed().firstOrNull { !it.priority && it.rect.contains(mouseX, mouseY) }

    private fun findTopClickRegion(mouseX: Double, mouseY: Double): ClickRegion? =
        clickRegions.asReversed().firstOrNull { it.priority && it.rect.contains(mouseX, mouseY) }
            ?: clickRegions.asReversed().firstOrNull { !it.priority && it.rect.contains(mouseX, mouseY) }

    private fun findTopScrollRegion(mouseX: Double, mouseY: Double): ScrollRegion? =
        scrollRegions.asReversed().firstOrNull { it.priority && it.rect.contains(mouseX, mouseY) }
            ?: scrollRegions.asReversed().firstOrNull { !it.priority && it.rect.contains(mouseX, mouseY) }

    fun click(mouseX: Double, mouseY: Double): Boolean {
        activeDropdownKey
            ?.takeIf { isToggled(it) }
            ?.let { key ->
                val insideDropdown = dropdownRegions[key]?.any { it.contains(mouseX, mouseY) } == true
                if (!insideDropdown) {
                    closeDropdown(key)
                }
            }

        val dragHit = findTopDragRegion(mouseX, mouseY)
        if (dragHit != null) {
            activeDragRegion = dragHit
            dragHit.onStart(mouseX, mouseY)
            dragHit.onDrag(mouseX, mouseY)
            return true
        }

        val hit = findTopClickRegion(mouseX, mouseY) ?: return false
        hit.onClick(mouseX, mouseY)
        return true
    }

    fun drag(mouseX: Double, mouseY: Double): Boolean {
        val dragRegion = activeDragRegion ?: return false
        dragRegion.onDrag(mouseX, mouseY)
        return true
    }

    fun release(mouseX: Double, mouseY: Double) {
        activeDragRegion?.onEnd?.invoke(mouseX, mouseY)
        activeDragRegion = null
    }

    fun focus(textFieldKey: String?) {
        focusedTextInputKey = textFieldKey
    }

    fun isFocused(textFieldKey: String): Boolean = focusedTextInputKey == textFieldKey

    fun clearFocus() {
        focusedTextInputKey = null
    }

    fun charTyped(codePoint: Char): Boolean =
        focusedTextInputKey?.let { key -> textInputStates[key]?.input?.charTyped(codePoint) } == true

    fun keyPressed(keyCode: Int, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean =
        activeDropdownKey
            ?.takeIf { isToggled(it) }
            ?.let { key -> dropdownInputs[key]?.keyPressed(keyCode, isCtrlDown, isShiftDown) } == true ||
            focusedTextInputKey?.let { key -> textInputStates[key]?.input?.keyPressed(keyCode, isCtrlDown, isShiftDown) } == true

    fun mouseScrolled(scrollY: Double, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean =
        if (
            activeDropdownKey
                ?.takeIf { key ->
                    isToggled(key) && (dropdownRegions[key]?.any { it.contains(mouseX.toDouble(), mouseY.toDouble()) } == true)
                }
                ?.let { key -> dropdownInputs[key]?.mouseScrolled(scrollY, isCtrlDown, isShiftDown) } == true
        ) {
            true
        } else if (
            focusedTextInputKey
                ?.takeIf { key -> textInputRects[key]?.contains(mouseX.toDouble(), mouseY.toDouble()) == true }
                ?.let { key -> textInputStates[key]?.input?.mouseScrolled(scrollY, isCtrlDown, isShiftDown) } == true
        ) {
            true
        } else {
            val hovered = findTopScrollRegion(mouseX.toDouble(), mouseY.toDouble()) ?: return false
            hovered.onScroll(scrollY)
            true
        }

    fun textInputState(key: String, input: UiTextInput): TextInputState =
        textInputStates.getOrPut(key) { TextInputState(input = input) }.also { it.input = input }

    fun scrollState(key: String): ScrollState =
        scrollStates.getOrPut(key) { ScrollState() }

    fun dropdownState(key: String): DropdownState =
        dropdownStates.getOrPut(key) { DropdownState() }

    fun setPressed(key: String, pressed: Boolean) {
        pressedStates[key] = pressed
    }

    fun isPressed(key: String): Boolean = pressedStates[key] == true

    fun setToggled(key: String, toggled: Boolean) {
        toggleStates[key] = toggled
    }

    fun isToggled(key: String): Boolean = toggleStates[key] == true

    fun toggle(key: String): Boolean {
        val next = !isToggled(key)
        setToggled(key, next)
        return next
    }

    fun setTextInputRect(key: String, rect: UiRect) {
        textInputRects[key] = rect
    }

    fun registerDropdownInput(key: String, input: UiDropdownInput) {
        if (passiveRenderDepth > 0) return
        dropdownInputs[key] = input
    }

    fun registerDropdownArea(key: String, rect: UiRect) {
        if (passiveRenderDepth > 0) return
        dropdownRegions.getOrPut(key) { mutableListOf() } += rect
    }

    fun openDropdown(key: String) {
        val previous = activeDropdownKey
        if (previous != null && previous != key) {
            setToggled(previous, false)
        }
        activeDropdownKey = key
        setToggled(key, true)
    }

    fun closeDropdown(key: String) {
        setToggled(key, false)
        if (activeDropdownKey == key) {
            activeDropdownKey = null
        }
    }

    fun addScrollRegion(rect: UiRect, priority: Boolean = false, onScroll: (scrollY: Double) -> Unit) {
        if (passiveRenderDepth > 0) return
        scrollRegions += ScrollRegion(rect, priority, onScroll)
    }

    fun setTooltip(rect: UiRect, tooltip: UiTooltip?) {
        if (passiveRenderDepth > 0) return
        if (tooltip == null) return
        if (rect.contains(mouseX.toDouble(), mouseY.toDouble())) {
            hoveredTooltip = tooltip
        }
    }

    fun renderTooltipOverlay(screenWidth: Int, screenHeight: Int) {
        val tooltip = hoveredTooltip ?: return
        val maxWidth = (screenWidth / 2).coerceAtLeast(120)
        val measured = tooltip.content.measure(this, maxWidth, screenHeight / 2)
        val offsetX = 12
        val offsetY = 12
        val margin = 8
        val x = (mouseX + offsetX).coerceAtMost((screenWidth - measured.width - margin).coerceAtLeast(margin))
        val y = if (mouseY + offsetY + measured.height <= screenHeight - margin) {
            mouseY + offsetY
        } else {
            (mouseY - offsetY - measured.height).coerceAtLeast(margin)
        }
        tooltip.content.render(this, UiRect(x, y, measured.width, measured.height))
    }

    fun addOverlayRenderer(renderer: (screenWidth: Int, screenHeight: Int) -> Unit) {
        overlayRenderers += renderer
    }

    fun renderOverlays(screenWidth: Int, screenHeight: Int) {
        overlayRenderers.forEach { renderer ->
            renderer(screenWidth, screenHeight)
        }
    }

    fun renderPassive(block: () -> Unit) {
        val previousMouseX = mouseX
        val previousMouseY = mouseY
        passiveRenderDepth++
        mouseX = Int.MIN_VALUE / 4
        mouseY = Int.MIN_VALUE / 4
        try {
            block()
        } finally {
            passiveRenderDepth--
            mouseX = previousMouseX
            mouseY = previousMouseY
        }
    }

    fun debugRect(rect: UiRect, layer: UiDebugLayer, label: String = layer.label) {
        if (!UiDebugOverlay.enabled) return
        addOverlayRenderer { _, _ ->
            guiGraphics.nextStratum()
            guiGraphics.fill(rect.x, rect.y, rect.right, rect.bottom, layer.fillColor)
            guiGraphics.fill(rect.x, rect.y, rect.right, rect.y + 1, layer.outlineColor)
            guiGraphics.fill(rect.x, rect.bottom - 1, rect.right, rect.bottom, layer.outlineColor)
            guiGraphics.fill(rect.x, rect.y, rect.x + 1, rect.bottom, layer.outlineColor)
            guiGraphics.fill(rect.right - 1, rect.y, rect.right, rect.bottom, layer.outlineColor)
            if (UiDebugOverlay.showLabels && rect.width > 24 && rect.height > font.lineHeight + 4) {
                val anchorX = rect.x.coerceAtLeast(0)
                val anchorY = rect.y.coerceAtLeast(0)
                val slotKey = (anchorX / 12) to (anchorY / 12)
                val slotIndex = debugLabelSlots.getOrDefault(slotKey, 0)
                debugLabelSlots[slotKey] = slotIndex + 1

                val textWidth = font.width(label)
                val textHeight = font.lineHeight
                val labelX = rect.x + 2
                val labelY = rect.y + 2 + slotIndex * (textHeight + 3)
                val padX = 2
                val padY = 1
                val boxLeft = labelX - padX
                val boxTop = labelY - padY
                val boxRight = labelX + textWidth + padX
                val boxBottom = labelY + textHeight + padY

                guiGraphics.fill(boxLeft, boxTop, boxRight, boxBottom, ARGB.color(210, 12, 12, 16))
                guiGraphics.fill(boxLeft, boxTop, boxRight, boxTop + 1, layer.outlineColor)
                guiGraphics.fill(boxLeft, boxBottom - 1, boxRight, boxBottom, layer.outlineColor)
                guiGraphics.fill(boxLeft, boxTop, boxLeft + 1, boxBottom, layer.outlineColor)
                guiGraphics.fill(boxRight - 1, boxTop, boxRight, boxBottom, layer.outlineColor)
                guiGraphics.drawString(font, label, labelX, labelY, CommonColors.WHITE)
            }
        }
    }

    fun debugPaddedRect(bounds: UiRect, padding: UiInsets, label: String) {
        if (!UiDebugOverlay.enabled || !UiDebugOverlay.showContentBoxes) return
        if (padding.horizontal <= 0 && padding.vertical <= 0) {
            debugRect(bounds, UiDebugLayer.Content, "$label.Content")
            return
        }

        val inner = bounds.shrink(padding)
        if (inner.width <= 0 || inner.height <= 0) return

        if (inner.y > bounds.y) {
            debugRect(UiRect(bounds.x, bounds.y, bounds.width, inner.y - bounds.y), UiDebugLayer.Padding, "$label.PaddingTop")
        }
        if (inner.bottom < bounds.bottom) {
            debugRect(UiRect(bounds.x, inner.bottom, bounds.width, bounds.bottom - inner.bottom), UiDebugLayer.Padding, "$label.PaddingBottom")
        }
        if (inner.x > bounds.x) {
            debugRect(UiRect(bounds.x, inner.y, inner.x - bounds.x, inner.height), UiDebugLayer.Padding, "$label.PaddingLeft")
        }
        if (inner.right < bounds.right) {
            debugRect(UiRect(inner.right, inner.y, bounds.right - inner.right, inner.height), UiDebugLayer.Padding, "$label.PaddingRight")
        }

        debugRect(inner, UiDebugLayer.Content, "$label.Content")
    }

    fun pushAvailableSpace(width: Int, height: Int) {
        availableSpaceStack += UiSize(width, height)
    }

    fun popAvailableSpace() {
        if (availableSpaceStack.isNotEmpty()) {
            availableSpaceStack.removeAt(availableSpaceStack.lastIndex)
        }
    }

    fun currentAvailableWidth(): Int? = availableSpaceStack.lastOrNull()?.width

    fun currentAvailableHeight(): Int? = availableSpaceStack.lastOrNull()?.height

    private data class ClickRegion(
        val rect: UiRect,
        val priority: Boolean,
        val onClick: (mouseX: Double, mouseY: Double) -> Unit
    )
    private data class DragRegion(
        val rect: UiRect,
        val priority: Boolean,
        val onStart: (mouseX: Double, mouseY: Double) -> Unit,
        val onDrag: (mouseX: Double, mouseY: Double) -> Unit,
        val onEnd: (mouseX: Double, mouseY: Double) -> Unit
    )
    private data class ScrollRegion(
        val rect: UiRect,
        val priority: Boolean,
        val onScroll: (scrollY: Double) -> Unit
    )

    data class TextInputState(
        var input: UiTextInput,
        var caretIndex: Int = 0,
        var preferredColumn: Int = 0,
        var scrollOffset: Int = 0,
        var horizontalScrollOffset: Int = 0,
        var selectionAnchor: Int? = null,
        val undoStack: MutableList<TextSnapshot> = mutableListOf(),
        val redoStack: MutableList<TextSnapshot> = mutableListOf()
    )

    data class TextSnapshot(
        val text: String,
        val caretIndex: Int,
        val preferredColumn: Int,
        val scrollOffset: Int,
        val horizontalScrollOffset: Int,
        val selectionAnchor: Int?
    )

    data class ScrollState(
        var offset: Int = 0
    )

    data class DropdownState(
        var highlightedIndex: Int = 0,
        var scrollOffset: Int = 0
    )

    companion object {
        internal var currentRuntime: UiRuntime? = null
    }
}

/** Base node of the auto-layout tree. */
interface UiNode {
    val modifier: UiModifier

    fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize

    fun render(runtime: UiRuntime, bounds: UiRect)
}

/** Contract for text-input widgets that want keyboard and wheel events routed by [UiRuntime]. */
interface UiTextInput {
    fun charTyped(codePoint: Char): Boolean
    fun keyPressed(keyCode: Int, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean
    fun mouseScrolled(scrollY: Double, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean = false
}

interface UiDropdownInput {
    fun keyPressed(keyCode: Int, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean = false
    fun mouseScrolled(scrollY: Double, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean = false
}

/** Horizontal alignment for text inside [UiLabel]. */
enum class UiTextAlign {
    Start,
    Center,
    End
}

enum class UiTextVerticalAlign {
    Top,
    Center,
    Bottom
}

/** Item rendering mode used by [UiItemStackView] and [ItemStackRenderer]. */
enum class UiItemStackMode {
    Flat2D,
    Spin3D
}

/** Orientation used by [UiSeparator]. */
enum class UiSeparatorOrientation {
    Horizontal,
    Vertical
}

/** Per-frame timing context passed into item/item-stack transform callbacks. */
data class ItemRenderFrame(
    val deltaTracker: DeltaTracker,
    val partialTick: Float,
    val timeMs: Long,
    val tickCount: Long,
    val gameTimeWithPartial: Float
)

/** 2D transform applied to flat item rendering. */
data class ItemTransform2D(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

/** 3D transform applied to item/item-stack rendering. */
data class ItemTransform3D(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleZ: Float = 1f
)

typealias UiItemStackFrame = ItemRenderFrame
typealias UiItemStackTransform = ItemTransform3D

/** Single dropdown option entry. */
data class UiDropdownOption<T>(
    val value: T,
    val label: Component
)

data class UiTexture(
    val texture: Identifier,
    val textureWidth: Int,
    val textureHeight: Int,
    val u: Float = 0f,
    val v: Float = 0f,
    val regionWidth: Int = textureWidth,
    val regionHeight: Int = textureHeight,
    val tintColor: Int = -1
)

data class UiButtonTextures(
    val normal: UiTexture,
    val hovered: UiTexture = normal,
    val pressed: UiTexture = hovered
)

private fun UiButtonTextures.defaultKeyFragment(): String =
    buildString {
        append(normal.texture)
        append(':')
        append(normal.u)
        append(':')
        append(normal.v)
        append(':')
        append(normal.regionWidth)
        append('x')
        append(normal.regionHeight)
    }

/** Mutable zoom binding used by widgets such as [UiMultilineTextField]. */
class UiZoomBinding internal constructor(
    private val getter: () -> Float,
    private val setter: (Float) -> Unit
) {
    fun value(): Float = getter()
    fun set(value: Float) = setter(value)
}

/** Tooltip payload rendered by the overlay pass after the main UI tree. */
class UiTooltip internal constructor(
    internal val content: UiNode
)

/** Regex-based syntax highlight rule for [UiMultilineTextField]. */
data class UiTextHighlightRule(
    val pattern: Regex,
    val color: Int
)

/** Border description for boxed widgets such as [UiBox]. */
data class UiOutline(
    val color: Int,
    val size: Int = 1
)

/** Scrollable viewport that clips its child and renders a draggable scrollbar. */
class UiScrollArea(
    private val scrollKey: String,
    override val modifier: UiModifier = UiModifier.None,
    private val child: UiNode,
    private val scrollBarWidth: Int = 4,
    private val scrollGap: Int = 4
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        val contentWidth = (inner.width - scrollBarWidth - scrollGap).coerceAtLeast(0)
        runtime.pushAvailableSpace(contentWidth, inner.height)
        val childSize = child.measure(runtime, contentWidth, Int.MAX_VALUE / 8)
        runtime.popAvailableSpace()
        return UiSize(
            modifier.resolveWidth(childSize.width + scrollBarWidth + scrollGap, maxWidth),
            modifier.resolveHeight(childSize.height.coerceAtMost(maxHeight), maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.ScrollArea)
        runtime.debugPaddedRect(bounds, modifier.padding, "ScrollArea")
        val inner = bounds.shrink(modifier.padding)
        val contentWidth = (inner.width - scrollBarWidth - scrollGap).coerceAtLeast(0)
        runtime.pushAvailableSpace(contentWidth, inner.height)
        val childSize = child.measure(runtime, contentWidth, Int.MAX_VALUE / 8)
        val state = runtime.scrollState(scrollKey)
        val maxOffset = (childSize.height - inner.height).coerceAtLeast(0)
        state.offset = state.offset.coerceIn(0, maxOffset)

        val viewportRect = UiRect(inner.x, inner.y, contentWidth, inner.height)
        runtime.addScrollRegion(viewportRect) { scrollY ->
            state.offset = (state.offset - (scrollY * 14.0).toInt()).coerceIn(0, maxOffset)
        }

        runtime.guiGraphics.enableScissor(viewportRect.x, viewportRect.y, viewportRect.right, viewportRect.bottom)
        child.render(
            runtime,
            UiRect(
                viewportRect.x,
                viewportRect.y - state.offset,
                contentWidth,
                childSize.height
            )
        )
        runtime.guiGraphics.disableScissor()
        runtime.popAvailableSpace()

        if (maxOffset <= 0) return
        val barX = viewportRect.right + scrollGap
        val barRect = UiRect(barX, viewportRect.y, scrollBarWidth, viewportRect.height)
        val thumbHeight = ((viewportRect.height.toFloat() * viewportRect.height.toFloat()) / childSize.height.toFloat())
            .toInt()
            .coerceAtLeast(12)
            .coerceAtMost(viewportRect.height)
        val travel = (viewportRect.height - thumbHeight).coerceAtLeast(0)
        val thumbY = viewportRect.y + ((state.offset.toFloat() / maxOffset.toFloat()) * travel).toInt()
        val thumbRect = UiRect(barX, thumbY, scrollBarWidth, thumbHeight)
        val thumbHovered = thumbRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        runtime.guiGraphics.fill(barX, viewportRect.y, barX + scrollBarWidth, viewportRect.bottom, Theme.scrollTrack)
        runtime.guiGraphics.fill(
            barX,
            thumbY,
            barX + scrollBarWidth,
            thumbY + thumbHeight,
            if (thumbHovered) Theme.scrollThumbHover else Theme.scrollThumb
        )

        fun applyScrollFromThumb(mouseY: Double) {
            if (travel <= 0) {
                state.offset = 0
                return
            }
            val localY = (mouseY - viewportRect.y - thumbHeight / 2.0).coerceIn(0.0, travel.toDouble())
            val ratio = localY / travel.toDouble()
            state.offset = (maxOffset * ratio).toInt().coerceIn(0, maxOffset)
        }

        runtime.addClickRegion(barRect) { _, mouseY ->
            applyScrollFromThumb(mouseY)
        }
        runtime.addDragRegion(
            rect = thumbRect,
            onDrag = { _, mouseY -> applyScrollFromThumb(mouseY) }
        )
    }
}

private data class AxisChildMeasure(
    val node: UiNode,
    val size: UiSize
)

/** Cell entry used internally by [UiGrid]. */
data class UiGridChild(
    val row: Int,
    val column: Int,
    val node: UiNode
)

private fun UiNode.measureForColumn(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
    val widthRule = modifier.width
    val heightRule = modifier.height
    val probeWidth = when (widthRule) {
        is UiLength.Fixed -> widthRule.value
        is UiLength.Fill -> 0
        is UiLength.Available -> 0
        is UiLength.Expand -> maxWidth
        UiLength.Wrap -> maxWidth
    }.coerceAtLeast(modifier.minWidth)
    val probeHeight = when (heightRule) {
        is UiLength.Fixed -> heightRule.value
        is UiLength.Fill -> 0
        is UiLength.Available -> 0
        is UiLength.Expand -> maxHeight
        UiLength.Wrap -> maxHeight
    }.coerceAtLeast(modifier.minHeight)
    return measure(runtime, probeWidth, probeHeight)
}

private fun UiNode.measureForRow(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
    val widthRule = modifier.width
    val heightRule = modifier.height
    val probeWidth = when (widthRule) {
        is UiLength.Fixed -> widthRule.value
        is UiLength.Fill -> 0
        is UiLength.Available -> 0
        is UiLength.Expand -> maxWidth
        UiLength.Wrap -> maxWidth
    }.coerceAtLeast(modifier.minWidth)
    val probeHeight = when (heightRule) {
        is UiLength.Fixed -> heightRule.value
        is UiLength.Fill -> 0
        is UiLength.Available -> 0
        is UiLength.Expand -> maxHeight
        UiLength.Wrap -> maxHeight
    }.coerceAtLeast(modifier.minHeight)
    return measure(runtime, probeWidth, probeHeight)
}

/** Weighted grid container with explicit row and column addressing. */
class UiGrid(
    private val rows: Int,
    private val columns: Int,
    override val modifier: UiModifier = UiModifier.None,
    private val horizontalGap: Int = 0,
    private val verticalGap: Int = 0,
    private val columnWeights: List<Float> = List(columns) { 1f },
    private val rowWeights: List<Float> = List(rows) { 1f },
    children: List<UiGridChild>
) : UiNode {
    private val children: List<UiGridChild> = children

    init {
        require(rows > 0) { "Grid rows must be > 0" }
        require(columns > 0) { "Grid columns must be > 0" }
        require(columnWeights.size == columns) { "Column weights size must match columns" }
        require(rowWeights.size == rows) { "Row weights size must match rows" }
    }

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        val measured = measureGrid(runtime, inner.width, inner.height)
        val contentWidth = measured.columnWidths.sum() + horizontalGap * (columns - 1).coerceAtLeast(0)
        val contentHeight = measured.rowHeights.sum() + verticalGap * (rows - 1).coerceAtLeast(0)
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Grid)
        runtime.debugPaddedRect(bounds, modifier.padding, "Grid")
        val inner = bounds.shrink(modifier.padding)
        val measured = measureGrid(runtime, inner.width, inner.height)
        val columnX = IntArray(columns)
        val rowY = IntArray(rows)

        var cursorX = inner.x
        repeat(columns) { column ->
            columnX[column] = cursorX
            cursorX += measured.columnWidths[column] + horizontalGap
        }

        var cursorY = inner.y
        repeat(rows) { row ->
            rowY[row] = cursorY
            cursorY += measured.rowHeights[row] + verticalGap
        }

        children.forEach { child ->
            if (child.row !in 0 until rows || child.column !in 0 until columns) return@forEach
            child.node.render(
                runtime,
                UiRect(
                    columnX[child.column],
                    rowY[child.row],
                    measured.columnWidths[child.column],
                    measured.rowHeights[child.row]
                )
            )
        }
    }

    private fun measureGrid(runtime: UiRuntime, availableWidth: Int, availableHeight: Int): MeasuredGrid {
        val columnWidths = resolveTrackSizes(availableWidth, columns, horizontalGap, columnWeights)
        val rowHeights = IntArray(rows) { 0 }

        children.forEach { child ->
            if (child.row !in 0 until rows || child.column !in 0 until columns) return@forEach
            val cellWidth = columnWidths[child.column]
            val measured = child.node.measure(runtime, cellWidth, availableHeight)
            rowHeights[child.row] = max(rowHeights[child.row], measured.height.coerceAtLeast(child.node.modifier.minHeight))
        }

        rowHeights.indices.forEach { row ->
            if (rowHeights[row] == 0) {
                rowHeights[row] = when (val rule = modifier.height) {
                    is UiLength.Fixed, is UiLength.Fill, is UiLength.Available, is UiLength.Expand -> 0
                    UiLength.Wrap -> 0
                }
            }
        }

        val totalGapHeight = verticalGap * (rows - 1).coerceAtLeast(0)
        val contentHeight = rowHeights.sum() + totalGapHeight
        val referenceHeight = when (modifier.height) {
            is UiLength.Available, is UiLength.Expand -> runtime.currentAvailableHeight() ?: availableHeight
            else -> availableHeight
        }
        if ((modifier.height is UiLength.Fill || modifier.height is UiLength.Available || modifier.height is UiLength.Expand) && referenceHeight > contentHeight) {
            val extra = referenceHeight - contentHeight
            distributeExtra(rowHeights, extra, rowWeights)
        }

        return MeasuredGrid(columnWidths, rowHeights)
    }

    private data class MeasuredGrid(
        val columnWidths: IntArray,
        val rowHeights: IntArray
    )
}

private fun resolveTrackSizes(availableSize: Int, count: Int, gap: Int, weights: List<Float>): IntArray {
    if (count <= 0) return IntArray(0)
    val safeWeights = weights.map { it.takeIf { value -> value > 0f } ?: 1f }
    val trackSpace = (availableSize - gap * (count - 1).coerceAtLeast(0)).coerceAtLeast(0)
    val totalWeight = safeWeights.sum().takeIf { it > 0f } ?: count.toFloat()
    val sizes = IntArray(count)
    var used = 0
    repeat(count) { index ->
        val size = if (index == count - 1) {
            trackSpace - used
        } else {
            ((trackSpace * (safeWeights[index] / totalWeight)).toInt()).coerceAtLeast(0)
        }
        sizes[index] = size
        used += size
    }
    return sizes
}

private fun distributeExtra(target: IntArray, extra: Int, weights: List<Float>) {
    if (extra <= 0 || target.isEmpty()) return
    val safeWeights = weights.map { it.takeIf { value -> value > 0f } ?: 1f }
    val totalWeight = safeWeights.sum().takeIf { it > 0f } ?: target.size.toFloat()
    var used = 0
    target.indices.forEach { index ->
        val addition = if (index == target.lastIndex) {
            extra - used
        } else {
            ((extra * (safeWeights[index] / totalWeight)).toInt()).coerceAtLeast(0)
        }
        target[index] += addition
        used += addition
    }
}

open class UiContainer(
    override val modifier: UiModifier = UiModifier.None,
    protected val children: List<UiNode>
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        var contentWidth = 0
        var contentHeight = 0

        children.forEach { child ->
            val measured = child.measure(runtime, inner.width, inner.height)
            contentWidth = max(contentWidth, measured.width)
            contentHeight = max(contentHeight, measured.height)
        }

        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Column)
        runtime.debugPaddedRect(bounds, modifier.padding, "Column")
        val inner = bounds.shrink(modifier.padding)
        children.forEach { child ->
            val measured = child.measure(runtime, inner.width, inner.height)
            child.render(runtime,
                UiRect(
                    inner.x,
                    inner.y,
                    when (child.modifier.width) {
                        is UiLength.Fill -> inner.width
                        is UiLength.Available -> runtime.currentAvailableWidth() ?: inner.width
                        is UiLength.Expand -> measured.width
                        else -> measured.width.coerceAtMost(inner.width)
                    },
                    when (child.modifier.height) {
                        is UiLength.Fill -> inner.height
                        is UiLength.Available -> runtime.currentAvailableHeight() ?: inner.height
                        is UiLength.Expand -> measured.height
                        else -> measured.height.coerceAtMost(inner.height)
                    }
                )
            )
        }
    }
}

/** Vertical layout container. */
class UiColumn(
    override val modifier: UiModifier = UiModifier.None,
    private val gap: Int = 0,
    children: List<UiNode>
) : UiNode {
    private val children: List<UiNode> = children

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        val measuredChildren = children.map { child -> AxisChildMeasure(child, child.measureForColumn(runtime, inner.width, inner.height)) }
        val contentWidth = measuredChildren.maxOfOrNull { it.size.width } ?: 0
        val contentHeight = measuredChildren.sumOf { entry ->
            when (entry.node.modifier.height) {
                is UiLength.Fill -> 0
                is UiLength.Available -> 0
                else -> entry.size.height
            }
        } + (gap * (measuredChildren.size - 1).coerceAtLeast(0))
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Group)
        val inner = bounds.shrink(modifier.padding)
        val measuredChildren = children.map { child -> AxisChildMeasure(child, child.measureForColumn(runtime, inner.width, inner.height)) }
        val fixedHeight = measuredChildren.sumOf { entry ->
            when (entry.node.modifier.height) {
                is UiLength.Fill -> 0
                is UiLength.Available -> 0
                else -> entry.size.height
            }
        }
        val totalGap = gap * (children.size - 1).coerceAtLeast(0)
        val referenceHeight = if (measuredChildren.any { it.node.modifier.height is UiLength.Available }) {
            runtime.currentAvailableHeight() ?: inner.height
        } else {
            inner.height
        }
        val remainingHeight = (referenceHeight - fixedHeight - totalGap).coerceAtLeast(0)
        val fillWeight = measuredChildren.sumOf { entry ->
            when (val rule = entry.node.modifier.height) {
                is UiLength.Fill -> rule.weight.toDouble()
                is UiLength.Available -> rule.weight.toDouble()
                is UiLength.Expand -> 0.0
                else -> 0.0
            }
        }

        var cursorY = inner.y
        measuredChildren.forEach { entry ->
            val child = entry.node
            val measured = entry.size
            val childHeight = when (val rule = child.modifier.height) {
                is UiLength.Fill -> {
                    if (fillWeight <= 0.0) 0
                    else ((remainingHeight * (rule.weight / fillWeight)).toInt()).coerceAtLeast(child.modifier.minHeight)
                }
                is UiLength.Available -> {
                    if (fillWeight <= 0.0) 0
                    else ((remainingHeight * (rule.weight / fillWeight)).toInt()).coerceAtLeast(child.modifier.minHeight)
                }
                is UiLength.Expand -> measured.height
                else -> measured.height
            }.coerceAtLeast(child.modifier.minHeight)
            val childWidth = when (child.modifier.width) {
                is UiLength.Fill -> inner.width
                is UiLength.Available -> runtime.currentAvailableWidth() ?: inner.width
                is UiLength.Expand -> measured.width
                else -> measured.width.coerceAtMost(inner.width)
            }.coerceAtLeast(child.modifier.minWidth)

            child.render(runtime, UiRect(inner.x, cursorY, childWidth, childHeight))
            if (gap > 0 && entry != measuredChildren.last()) {
                runtime.debugRect(UiRect(inner.x, cursorY + childHeight, inner.width, gap), UiDebugLayer.Gap, "ColumnGap")
            }
            cursorY += childHeight + gap
        }
    }
}

/** Horizontal layout container. */
class UiRow(
    override val modifier: UiModifier = UiModifier.None,
    private val gap: Int = 0,
    private val children: List<UiNode>
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        val measuredChildren = children.map { child -> AxisChildMeasure(child, child.measureForRow(runtime, inner.width, inner.height)) }
        val contentWidth = measuredChildren.sumOf { entry ->
            when (entry.node.modifier.width) {
                is UiLength.Fill -> 0
                is UiLength.Available -> 0
                else -> entry.size.width
            }
        } + (gap * (measuredChildren.size - 1).coerceAtLeast(0))
        val contentHeight = measuredChildren.maxOfOrNull { it.size.height } ?: 0
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Row)
        runtime.debugPaddedRect(bounds, modifier.padding, "Row")
        val inner = bounds.shrink(modifier.padding)
        val measuredChildren = children.map { child -> AxisChildMeasure(child, child.measureForRow(runtime, inner.width, inner.height)) }
        val fixedWidth = measuredChildren.sumOf { entry ->
            when (entry.node.modifier.width) {
                is UiLength.Fill -> 0
                is UiLength.Available -> 0
                else -> entry.size.width
            }
        }
        val totalGap = gap * (children.size - 1).coerceAtLeast(0)
        val referenceWidth = if (measuredChildren.any { it.node.modifier.width is UiLength.Available }) {
            runtime.currentAvailableWidth() ?: inner.width
        } else {
            inner.width
        }
        val remainingWidth = (referenceWidth - fixedWidth - totalGap).coerceAtLeast(0)
        val fillWeight = measuredChildren.sumOf { entry ->
            when (val rule = entry.node.modifier.width) {
                is UiLength.Fill -> rule.weight.toDouble()
                is UiLength.Available -> rule.weight.toDouble()
                is UiLength.Expand -> 0.0
                else -> 0.0
            }
        }

        var cursorX = inner.x
        measuredChildren.forEach { entry ->
            val child = entry.node
            val measured = entry.size
            val childWidth = when (val rule = child.modifier.width) {
                is UiLength.Fill -> {
                    if (fillWeight <= 0.0) 0
                    else ((remainingWidth * (rule.weight / fillWeight)).toInt()).coerceAtLeast(child.modifier.minWidth)
                }
                is UiLength.Available -> {
                    if (fillWeight <= 0.0) 0
                    else ((remainingWidth * (rule.weight / fillWeight)).toInt()).coerceAtLeast(child.modifier.minWidth)
                }
                is UiLength.Expand -> measured.width
                else -> measured.width
            }.coerceAtLeast(child.modifier.minWidth)
            val childHeight = when (child.modifier.height) {
                is UiLength.Fill -> inner.height
                is UiLength.Available -> runtime.currentAvailableHeight() ?: inner.height
                is UiLength.Expand -> measured.height
                else -> measured.height.coerceAtMost(inner.height)
            }.coerceAtLeast(child.modifier.minHeight)

            child.render(runtime, UiRect(cursorX, inner.y, childWidth, childHeight))
            if (gap > 0 && entry != measuredChildren.last()) {
                runtime.debugRect(UiRect(cursorX + childWidth, inner.y, gap, inner.height), UiDebugLayer.Gap, "RowGap")
            }
            cursorX += childWidth + gap
        }
    }
}

/** Generic box container with optional background and outline. */
class UiBox(
    override val modifier: UiModifier = UiModifier.None,
    private val backgroundTexture: UiTexture? = null,
    private val backgroundColor: Int? = null,
    private val outline: UiOutline? = null,
    private val children: List<UiNode>
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        var contentWidth = 0
        var contentHeight = 0
        children.forEach { child ->
            val measured = child.measure(runtime, inner.width, inner.height)
            contentWidth = max(contentWidth, measured.width)
            contentHeight = max(contentHeight, measured.height)
        }
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Box)
        runtime.debugPaddedRect(bounds, modifier.padding, "Box")
        backgroundTexture?.let { runtime.guiGraphics.drawUiTexture(it, bounds) }
        backgroundColor?.let { runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.bottom, it) }
        outline?.let { border ->
            val size = border.size.coerceAtLeast(1)
            runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + size, border.color)
            runtime.guiGraphics.fill(bounds.x, bounds.bottom - size, bounds.right, bounds.bottom, border.color)
            runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + size, bounds.bottom, border.color)
            runtime.guiGraphics.fill(bounds.right - size, bounds.y, bounds.right, bounds.bottom, border.color)
        }
        val inner = bounds.shrink(modifier.padding)
        runtime.guiGraphics.enableScissor(inner.x, inner.y, inner.right, inner.bottom)
        children.forEach { child ->
            val measured = child.measure(runtime, inner.width, inner.height)
            val childWidth = when (child.modifier.width) {
                is UiLength.Fill -> inner.width
                is UiLength.Available -> runtime.currentAvailableWidth() ?: inner.width
                is UiLength.Expand -> measured.width
                else -> measured.width.coerceAtMost(inner.width)
            }
            val childHeight = when (child.modifier.height) {
                is UiLength.Fill -> inner.height
                is UiLength.Available -> runtime.currentAvailableHeight() ?: inner.height
                is UiLength.Expand -> measured.height
                else -> measured.height.coerceAtMost(inner.height)
            }
            child.render(runtime, UiRect(inner.x, inner.y, childWidth, childHeight))
        }
        runtime.guiGraphics.disableScissor()
    }
}

/** Collapsible section with a clickable header and nested content. */
class UiGroup(
    private val title: Component,
    private val expanded: KMutableProperty0<Boolean>,
    override val modifier: UiModifier = UiModifier.None,
    private val contentIndent: Int = 12,
    private val contentTopGap: Int = 6,
    private val headerHorizontalPadding: Int = 4,
    private val headerVerticalPadding: Int = 3,
    private val arrowScale: Float = 1.35f,
    private val arrowRightPadding: Int = 6,
    private val tooltip: UiTooltip? = null,
    private val child: UiNode
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        val headerHeight = runtime.font.lineHeight + headerVerticalPadding * 2
        val arrowWidth = (runtime.font.width(">") * arrowScale).toInt().coerceAtLeast(runtime.font.width(">"))
        val headerWidth = runtime.font.width(title) + headerHorizontalPadding * 2 + 12 + arrowWidth + arrowRightPadding
        if (!expanded.get()) {
            return UiSize(
                modifier.resolveWidth(headerWidth, maxWidth),
                modifier.resolveHeight(headerHeight, maxHeight)
            )
        }

        val bodyMaxWidth = (inner.width - contentIndent).coerceAtLeast(0)
        val bodySize = child.measure(runtime, bodyMaxWidth, maxHeight)
        val contentWidth = max(headerWidth, contentIndent + bodySize.width)
        val contentHeight = headerHeight + contentTopGap + bodySize.height
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Label)
        val inner = bounds.shrink(modifier.padding)
        val headerHeight = runtime.font.lineHeight + headerVerticalPadding * 2
        val isExpanded = expanded.get()
        val headerRect = UiRect(inner.x, inner.y, inner.width, headerHeight)
        val hovered = headerRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val titleWidth = runtime.font.width(title)
        val arrowText = ">"
        val arrowBaseWidth = runtime.font.width(arrowText)
        val arrowWidth = (arrowBaseWidth * arrowScale).toInt().coerceAtLeast(arrowBaseWidth)
        val titleX = inner.x + headerHorizontalPadding
        val titleY = inner.y + headerVerticalPadding
        val arrowX = (inner.right - arrowWidth - arrowRightPadding - headerHorizontalPadding)
            .coerceAtLeast(titleX + titleWidth + 12)
        val lineStartX = (titleX + titleWidth + 8).coerceAtMost(arrowX - 8)
        val lineY = inner.y + headerHeight / 2

        if (hovered) {
            runtime.guiGraphics.fill(headerRect.x, headerRect.y, headerRect.right, headerRect.bottom, Theme.groupHeaderHoverFill)
            runtime.guiGraphics.fill(headerRect.x, headerRect.y, headerRect.right, headerRect.y + 1, Theme.groupHeaderHoverOutline)
            runtime.guiGraphics.fill(headerRect.x, headerRect.bottom - 1, headerRect.right, headerRect.bottom, Theme.groupHeaderHoverOutline)
            runtime.guiGraphics.fill(headerRect.x, headerRect.y, headerRect.x + 1, headerRect.bottom, Theme.groupHeaderHoverOutline)
            runtime.guiGraphics.fill(headerRect.right - 1, headerRect.y, headerRect.right, headerRect.bottom, Theme.groupHeaderHoverOutline)
        }
        runtime.setTooltip(headerRect, tooltip)
        runtime.guiGraphics.drawString(runtime.font, title, titleX, titleY, Theme.groupHeader)
        if (lineStartX < arrowX - 2) {
            runtime.guiGraphics.fill(lineStartX, lineY, arrowX - 4, lineY + 1, Theme.groupLine)
        }
        runtime.guiGraphics.pose().pushMatrix()
        runtime.guiGraphics.pose().translate(arrowX + arrowWidth / 2f, titleY + runtime.font.lineHeight / 2f)
        if (isExpanded) {
            runtime.guiGraphics.pose().rotate((PI.toFloat() / 2f))
        }
        runtime.guiGraphics.pose().translate(-arrowBaseWidth / 2f, -runtime.font.lineHeight / 2f)
        runtime.guiGraphics.pose().scale(arrowScale, arrowScale)
        runtime.guiGraphics.drawString(runtime.font, arrowText, 0, 0, Theme.groupArrow)
        runtime.guiGraphics.pose().popMatrix()

        runtime.addClickRegion(headerRect) { _, _ ->
            expanded.set(!expanded.get())
        }

        if (!isExpanded) return

        val bodyY = inner.y + headerHeight + contentTopGap
        val bodyRect = UiRect(
            inner.x + contentIndent,
            bodyY,
            (inner.width - contentIndent).coerceAtLeast(0),
            (inner.bottom - bodyY).coerceAtLeast(0)
        )
        val measured = child.measure(runtime, bodyRect.width, bodyRect.height)
        child.render(
            runtime,
            UiRect(
                bodyRect.x,
                bodyRect.y,
                measured.width.coerceAtMost(bodyRect.width),
                measured.height.coerceAtMost(bodyRect.height)
            )
        )
    }
}

private fun drawContainerHover(runtime: UiRuntime, bounds: UiRect) {
    if (!bounds.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())) return
    runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.bottom, Theme.containerHoverFill)
    runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + 1, Theme.containerHoverOutline)
    runtime.guiGraphics.fill(bounds.x, bounds.bottom - 1, bounds.right, bounds.bottom, Theme.containerHoverOutline)
    runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.bottom, Theme.containerHoverOutline)
    runtime.guiGraphics.fill(bounds.right - 1, bounds.y, bounds.right, bounds.bottom, Theme.containerHoverOutline)
}

private fun GuiGraphics.drawUiTexture(texture: UiTexture, bounds: UiRect) {
    if (bounds.width <= 0 || bounds.height <= 0) return
    blit(
        RenderPipelines.GUI_TEXTURED,
        texture.texture,
        bounds.x,
        bounds.y,
        texture.u,
        texture.v,
        bounds.width,
        bounds.height,
        texture.regionWidth,
        texture.regionHeight,
        texture.textureWidth,
        texture.textureHeight,
        texture.tintColor
    )
}

/** Empty layout node used for fixed gaps and spacing. */
class UiSpacer(
    override val modifier: UiModifier = UiModifier.None
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize =
        UiSize(
            modifier.resolveWidth(0, maxWidth),
            modifier.resolveHeight(0, maxHeight)
        )

    override fun render(runtime: UiRuntime, bounds: UiRect) = Unit
}

class UiPassiveNode(
    private val child: UiNode
) : UiNode {
    override val modifier: UiModifier
        get() = child.modifier

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize =
        child.measure(runtime, maxWidth, maxHeight)

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.renderPassive {
            child.render(runtime, bounds)
        }
    }
}

class UiCenterNode(
    override val modifier: UiModifier = UiModifier.None,
    private val horizontalCenterize: Boolean = true,
    private val verticalCenterize: Boolean = true,
    private val child: UiNode
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize =
        child.measure(runtime, maxWidth, maxHeight).let { measured ->
            UiSize(
                modifier.resolveWidth(measured.width, maxWidth),
                modifier.resolveHeight(measured.height, maxHeight)
            )
        }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Box, "Center")
        runtime.debugPaddedRect(bounds, modifier.padding, "Center")
        val inner = bounds.shrink(modifier.padding)
        val measured = child.measure(runtime, inner.width, inner.height)
        val childWidth = when (child.modifier.width) {
            is UiLength.Fill -> inner.width
            is UiLength.Available -> runtime.currentAvailableWidth() ?: inner.width
            is UiLength.Expand -> measured.width
            else -> measured.width.coerceAtMost(inner.width)
        }
        val childHeight = when (child.modifier.height) {
            is UiLength.Fill -> inner.height
            is UiLength.Available -> runtime.currentAvailableHeight() ?: inner.height
            is UiLength.Expand -> measured.height
            else -> measured.height.coerceAtMost(inner.height)
        }
        val childX = if (horizontalCenterize) {
            inner.x + ((inner.width - childWidth) / 2).coerceAtLeast(0)
        } else {
            inner.x
        }
        val childY = if (verticalCenterize) {
            inner.y + ((inner.height - childHeight) / 2).coerceAtLeast(0)
        } else {
            inner.y
        }
        child.render(runtime, UiRect(childX, childY, childWidth, childHeight))
    }
}

/** Text widget with optional wrapping, alignment and tooltip support. */
class UiLabel(
    private val text: Component,
    private val color: Int = CommonColors.WHITE,
    private val align: UiTextAlign = UiTextAlign.Start,
    private val verticalAlign: UiTextVerticalAlign = UiTextVerticalAlign.Top,
    private val wrap: Boolean = false,
    private val maxLines: Int = Int.MAX_VALUE,
    override val modifier: UiModifier = UiModifier.None,
    private val tooltip: UiTooltip? = null
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val innerWidth = (maxWidth - modifier.padding.horizontal).coerceAtLeast(0)
        val lines = measureLines(runtime, innerWidth)
        val width = lines.maxOfOrNull { runtime.font.width(it) } ?: 0
        val height = lines.size * runtime.font.lineHeight
        return UiSize(
            modifier.resolveWidth(width, maxWidth),
            modifier.resolveHeight(height, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Label)
        runtime.debugPaddedRect(bounds, modifier.padding, "Label")
        val inner = bounds.shrink(modifier.padding)
        runtime.setTooltip(bounds, tooltip)
        val lines = measureLines(runtime, inner.width)
        val textBlockHeight = lines.size * runtime.font.lineHeight
        val startY = when (verticalAlign) {
            UiTextVerticalAlign.Top -> inner.y
            UiTextVerticalAlign.Center -> inner.y + ((inner.height - textBlockHeight) / 2).coerceAtLeast(0)
            UiTextVerticalAlign.Bottom -> inner.bottom - textBlockHeight
        }
        lines.forEachIndexed { index, line ->
            val lineWidth = runtime.font.width(line)
            val drawX = when (align) {
                UiTextAlign.Start -> inner.x
                UiTextAlign.Center -> inner.x + ((inner.width - lineWidth) / 2).coerceAtLeast(0)
                UiTextAlign.End -> inner.right - lineWidth
            }
            val drawY = startY + index * runtime.font.lineHeight
            runtime.guiGraphics.drawString(runtime.font, line, drawX, drawY, color)
        }
    }

    private fun measureLines(runtime: UiRuntime, availableWidth: Int): List<FormattedCharSequence> {
        if (!wrap || availableWidth <= 0) {
            return listOf(text.visualOrderText)
        }

        val wrapped = runtime.font.split(text, availableWidth)
            .take(maxLines.coerceAtLeast(1))

        return if (wrapped.isEmpty()) listOf(text.visualOrderText) else wrapped
    }
}

/** Item-stack widget that renders either a flat icon or a spinning 3D preview. */
class UiItemStackView(
    private val stack: ItemStack,
    private val mode: UiItemStackMode = UiItemStackMode.Flat2D,
    private val transform: ((UiItemStackFrame) -> UiItemStackTransform)? = null,
    private val transformWithPrevious: ((UiItemStackFrame, UiItemStackTransform) -> UiItemStackTransform)? = null,
    private val transformKey: String? = null,
    override val modifier: UiModifier = UiModifier.None,
    private val tooltip: UiTooltip? = null
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val size = when {
            transform != null || transformWithPrevious != null -> 26
            else -> when (mode) {
            UiItemStackMode.Flat2D -> 18
            UiItemStackMode.Spin3D -> 26
            }
        }
        return UiSize(
            modifier.resolveWidth(size, maxWidth),
            modifier.resolveHeight(size, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.ItemStack)
        runtime.setTooltip(bounds, tooltip)
        if (stack.isEmpty) return

        val drawSize = bounds.width.coerceAtMost(bounds.height).coerceAtLeast(16)
        val drawX = bounds.x + ((bounds.width - drawSize) / 2)
        val drawY = bounds.y + ((bounds.height - drawSize) / 2)

        val customTransform = transform
        val customTransformWithPrevious = transformWithPrevious
        if (customTransform != null || customTransformWithPrevious != null) {
            val frame = UiItemStackRenderer.currentFrame()
            val resolvedKey = transformKey ?: defaultTransformKey()
            val previousTransform = previousTransforms[resolvedKey] ?: UiItemStackTransform()
            val resolvedTransform = customTransformWithPrevious?.invoke(frame, previousTransform)
                ?: customTransform!!.invoke(frame)
            previousTransforms[resolvedKey] = resolvedTransform
            if (!UiItemStackRenderer.renderTransformed3D(
                    runtime.guiGraphics,
                    stack,
                    drawX,
                    drawY,
                    drawSize,
                    resolvedTransform
                )
            ) {
                runtime.guiGraphics.renderFakeItem(stack, drawX, drawY)
            }
            return
        }

        when (mode) {
            UiItemStackMode.Flat2D -> {
                UiItemStackRenderer.renderFlat2D(runtime.guiGraphics, stack, drawX, drawY, drawSize)
            }
            UiItemStackMode.Spin3D -> {
                if (!UiItemStackRenderer.renderSpin3D(runtime.guiGraphics, stack, drawX, drawY, drawSize)) {
                    runtime.guiGraphics.renderFakeItem(stack, drawX, drawY)
                }
            }
        }
    }

    companion object {
        private val previousTransforms: MutableMap<String, UiItemStackTransform> = mutableMapOf()
    }

    private fun defaultTransformKey(): String =
        "itemstack:${stack.item.descriptionId}:${mode.name}:${modifier.hashCode()}"
}

/** Escape-hatch widget that exposes raw rendering inside the layout tree. */
class UiRenderNode(
    override val modifier: UiModifier = UiModifier.None,
    private val tooltip: UiTooltip? = null,
    private val renderer: (guiGraphics: GuiGraphics, deltaTracker: DeltaTracker, bounds: UiRect) -> Unit
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize =
        UiSize(
            modifier.resolveWidth(0, maxWidth),
            modifier.resolveHeight(0, maxHeight)
        )

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Render)
        runtime.setTooltip(bounds, tooltip)
        renderer(runtime.guiGraphics, Minecraft.getInstance().deltaTracker, bounds)
    }
}

/** Thin horizontal or vertical divider line. */
class UiSeparator(
    private val orientation: UiSeparatorOrientation = UiSeparatorOrientation.Horizontal,
    private val color: Int = Theme.separator,
    private val thickness: Int = 1,
    override val modifier: UiModifier = UiModifier.None,
    private val tooltip: UiTooltip? = null
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val baseWidth = if (orientation == UiSeparatorOrientation.Horizontal) 0 else thickness
        val baseHeight = if (orientation == UiSeparatorOrientation.Horizontal) thickness else 0
        return UiSize(
            modifier.resolveWidth(baseWidth, maxWidth),
            modifier.resolveHeight(baseHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Separator)
        runtime.setTooltip(bounds, tooltip)
        if (bounds.width <= 0 || bounds.height <= 0) return

        when (orientation) {
            UiSeparatorOrientation.Horizontal -> {
                val lineHeight = thickness.coerceAtLeast(1).coerceAtMost(bounds.height)
                val y = bounds.y + ((bounds.height - lineHeight) / 2)
                runtime.guiGraphics.fill(bounds.x, y, bounds.right, y + lineHeight, color)
            }
            UiSeparatorOrientation.Vertical -> {
                val lineWidth = thickness.coerceAtLeast(1).coerceAtMost(bounds.width)
                val x = bounds.x + ((bounds.width - lineWidth) / 2)
                runtime.guiGraphics.fill(x, bounds.y, x + lineWidth, bounds.bottom, color)
            }
        }
    }
}

/** Select field that expands into a clickable list of options. */
class UiDropdown<T>(
    private val selected: T?,
    private val options: List<UiDropdownOption<T>>,
    private val onSelect: (UiDropdownOption<T>) -> Unit,
    override val modifier: UiModifier = UiModifier.None,
    private val key: String,
    private val tooltip: UiTooltip? = null
) : UiNode, UiDropdownInput {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val labelWidth = options.maxOfOrNull { runtime.font.width(it.label) } ?: 72
        val contentWidth = labelWidth + 28
        val contentHeight = runtime.font.lineHeight + 10
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Dropdown)
        runtime.registerDropdownInput(key, this)
        runtime.registerDropdownArea(key, bounds)
        val hovered = bounds.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val open = runtime.isToggled(key)
        val state = runtime.dropdownState(key)
        val borderColor = when {
            open -> Theme.focusBorder
            hovered -> Theme.hoverBorder
            else -> Theme.border
        }
        val currentOption = options.firstOrNull { it.value == selected }
        val currentLabel = currentOption?.label ?: selected?.let { Component.literal(it.toString()) } ?: Component.empty()
        val currentIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
        state.highlightedIndex = state.highlightedIndex.coerceIn(0, (options.lastIndex).coerceAtLeast(0))

        runtime.guiGraphics.fill(
            bounds.x,
            bounds.y,
            bounds.right,
            bounds.bottom,
            if (open) Theme.textFieldBackgroundFocused else Theme.textFieldBackground
        )
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + 1, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.bottom - 1, bounds.right, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.right - 1, bounds.y, bounds.right, bounds.bottom, borderColor)
        runtime.setTooltip(bounds, tooltip)

        val textX = bounds.x + 6
        val textY = bounds.y + (bounds.height - runtime.font.lineHeight) / 2
        runtime.guiGraphics.drawString(runtime.font, currentLabel, textX, textY, CommonColors.WHITE)
        runtime.guiGraphics.drawString(
            runtime.font,
            Component.literal(if (open) "▲" else "▼"),
            bounds.right - 10,
            textY,
            Theme.mutedText
        )

        runtime.addClickRegion(bounds) { _, _ ->
            if (runtime.isToggled(key)) {
                runtime.closeDropdown(key)
            } else {
                state.highlightedIndex = currentIndex
                state.scrollOffset = 0
                runtime.openDropdown(key)
            }
        }

        if (!open || options.isEmpty()) return

        val screenHeight = Minecraft.getInstance().window.guiScaledHeight
        val optionHeight = bounds.height
        val popupGap = 2
        val popupMargin = 8
        val scrollbarWidth = 4
        val scrollbarGap = 4
        val desiredPopupHeight = optionHeight * options.size
        val availableBelow = (screenHeight - bounds.bottom - popupGap - popupMargin).coerceAtLeast(0)
        val availableAbove = (bounds.y - popupGap - popupMargin).coerceAtLeast(0)
        val openAbove = availableBelow < desiredPopupHeight && availableAbove > availableBelow
        val maxPopupHeight = if (openAbove) availableAbove else availableBelow
        val popupHeight = desiredPopupHeight.coerceAtMost(maxPopupHeight.coerceAtLeast(optionHeight))
        val dropdownY = if (openAbove) {
            bounds.y - popupGap - popupHeight
        } else {
            bounds.bottom + popupGap
        }
        val dropdownRect = UiRect(bounds.x, dropdownY, bounds.width, popupHeight)
        val scrollable = desiredPopupHeight > dropdownRect.height
        val contentWidth = if (scrollable) {
            (dropdownRect.width - scrollbarWidth - scrollbarGap).coerceAtLeast(0)
        } else {
            dropdownRect.width
        }
        val contentRect = UiRect(dropdownRect.x, dropdownRect.y, contentWidth, dropdownRect.height)
        val maxScrollOffset = (desiredPopupHeight - contentRect.height).coerceAtLeast(0)
        state.scrollOffset = state.scrollOffset.coerceIn(0, maxScrollOffset)
        rememberPopupMetrics(optionHeight, contentRect.height)
        ensureDropdownOptionVisible(state.highlightedIndex, optionHeight, contentRect.height, state)

        runtime.registerDropdownArea(key, dropdownRect)
        runtime.addClickRegion(dropdownRect, priority = true) { _, _ -> }
        runtime.addScrollRegion(bounds) { scrollY ->
            handleDropdownScroll(scrollY, optionHeight, contentRect.height, state)
        }
        runtime.addScrollRegion(dropdownRect, priority = true) { scrollY ->
            handleDropdownScroll(scrollY, optionHeight, contentRect.height, state)
        }

        fun hoveredIndexAt(mouseX: Double, mouseY: Double): Int? {
            if (!contentRect.contains(mouseX, mouseY)) return null
            val localY = (mouseY - contentRect.y + state.scrollOffset).toInt().coerceAtLeast(0)
            val index = localY / optionHeight
            return index.takeIf { it in options.indices }
        }

        hoveredIndexAt(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())?.let { hoveredIndex ->
            state.highlightedIndex = hoveredIndex
        }

        runtime.addClickRegion(contentRect, priority = true) { mouseX, mouseY ->
            hoveredIndexAt(mouseX, mouseY)?.let { index ->
                state.highlightedIndex = index
                onSelect(options[index])
                runtime.closeDropdown(key)
            }
        }

        runtime.addDragRegion(
            rect = contentRect,
            priority = true,
            onStart = { mouseX, mouseY ->
                hoveredIndexAt(mouseX, mouseY)?.let { index ->
                    state.highlightedIndex = index
                }
            },
            onDrag = { mouseX, mouseY ->
                hoveredIndexAt(mouseX, mouseY)?.let { index ->
                    state.highlightedIndex = index
                }
            }
        )

        val optionRects = options.mapIndexed { index, option ->
            val optionRect = UiRect(contentRect.x, contentRect.y + index * optionHeight - state.scrollOffset, contentRect.width, optionHeight)
            option to optionRect
        }

        val scrollbarRect: UiRect?
        val thumbRect: UiRect?
        if (scrollable) {
            scrollbarRect = UiRect(contentRect.right + scrollbarGap, dropdownRect.y, scrollbarWidth, dropdownRect.height)
            val thumbHeight = ((contentRect.height.toFloat() * contentRect.height.toFloat()) / desiredPopupHeight.toFloat())
                .toInt()
                .coerceAtLeast(12)
                .coerceAtMost(contentRect.height)
            val travel = (contentRect.height - thumbHeight).coerceAtLeast(0)
            val thumbY = dropdownRect.y + if (maxScrollOffset == 0 || travel == 0) {
                0
            } else {
                ((state.scrollOffset.toFloat() / maxScrollOffset.toFloat()) * travel).toInt()
            }
            thumbRect = UiRect(scrollbarRect.x, thumbY, scrollbarWidth, thumbHeight)

            fun applyDropdownScrollFromThumb(mouseY: Double) {
                if (travel <= 0 || maxScrollOffset <= 0) {
                    state.scrollOffset = 0
                    return
                }
                val localY = (mouseY - dropdownRect.y - thumbHeight / 2.0).coerceIn(0.0, travel.toDouble())
                val ratio = localY / travel.toDouble()
                state.scrollOffset = (maxScrollOffset * ratio).toInt().coerceIn(0, maxScrollOffset)
                state.highlightedIndex = (state.scrollOffset / optionHeight).coerceIn(0, options.lastIndex)
            }

            runtime.addClickRegion(scrollbarRect, priority = true) { _, mouseY ->
                applyDropdownScrollFromThumb(mouseY)
            }
            runtime.addDragRegion(
                rect = thumbRect,
                priority = true,
                onDrag = { _, mouseY -> applyDropdownScrollFromThumb(mouseY) }
            )
        } else {
            scrollbarRect = null
            thumbRect = null
        }

        runtime.addOverlayRenderer { _, _ ->
            runtime.guiGraphics.nextStratum()
            runtime.guiGraphics.fill(dropdownRect.x, dropdownRect.y, dropdownRect.right, dropdownRect.bottom, Theme.tooltipBackground)
            runtime.guiGraphics.fill(dropdownRect.x, dropdownRect.y, dropdownRect.right, dropdownRect.y + 1, Theme.tooltipBorder)
            runtime.guiGraphics.fill(dropdownRect.x, dropdownRect.bottom - 1, dropdownRect.right, dropdownRect.bottom, Theme.tooltipBorder)
            runtime.guiGraphics.fill(dropdownRect.x, dropdownRect.y, dropdownRect.x + 1, dropdownRect.bottom, Theme.tooltipBorder)
            runtime.guiGraphics.fill(dropdownRect.right - 1, dropdownRect.y, dropdownRect.right, dropdownRect.bottom, Theme.tooltipBorder)

            runtime.guiGraphics.enableScissor(contentRect.x, contentRect.y, contentRect.right, contentRect.bottom)
            optionRects.forEachIndexed { index, (option, optionRect) ->
                if (optionRect.bottom <= contentRect.y || optionRect.y >= contentRect.bottom) return@forEachIndexed

                val optionHovered = hoveredIndexAt(runtime.mouseX.toDouble(), runtime.mouseY.toDouble()) == index
                val isSelected = option.value == selected
                val highlighted = state.highlightedIndex == index
                if (optionHovered || highlighted || isSelected) {
                    runtime.guiGraphics.fill(
                        optionRect.x + 1,
                        optionRect.y + 1,
                        optionRect.right - 1,
                        optionRect.bottom - 1,
                        when {
                            isSelected -> Theme.buttonFillPressed
                            else -> Theme.buttonFillHover
                        }
                    )
                }
                runtime.guiGraphics.drawString(
                    runtime.font,
                    option.label,
                    optionRect.x + 6,
                    optionRect.y + (optionRect.height - runtime.font.lineHeight) / 2,
                    CommonColors.WHITE
                )
            }
            runtime.guiGraphics.disableScissor()

            if (scrollbarRect == null || thumbRect == null) return@addOverlayRenderer
            val thumbHovered = thumbRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())

            runtime.guiGraphics.fill(scrollbarRect.x, scrollbarRect.y, scrollbarRect.right, scrollbarRect.bottom, Theme.scrollTrack)
            runtime.guiGraphics.fill(
                thumbRect.x,
                thumbRect.y,
                thumbRect.right,
                thumbRect.bottom,
                if (thumbHovered) Theme.scrollThumbHover else Theme.scrollThumb
            )
        }
    }

    override fun keyPressed(keyCode: Int, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        if (options.isEmpty()) return false
        val runtime = UiRuntime.currentRuntime ?: return false
        if (!runtime.isToggled(key)) return false

        val state = runtime.dropdownState(key)
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE -> {
                runtime.closeDropdown(key)
                return true
            }
            GLFW.GLFW_KEY_ENTER,
            GLFW.GLFW_KEY_KP_ENTER,
            GLFW.GLFW_KEY_SPACE -> {
                val index = state.highlightedIndex.coerceIn(0, options.lastIndex)
                onSelect(options[index])
                runtime.closeDropdown(key)
                return true
            }
            GLFW.GLFW_KEY_UP -> {
                state.highlightedIndex = (state.highlightedIndex - 1).coerceAtLeast(0)
                ensureDropdownOptionVisible(state.highlightedIndex, currentOptionHeight(), currentPopupViewportHeight(), state)
                return true
            }
            GLFW.GLFW_KEY_DOWN -> {
                state.highlightedIndex = (state.highlightedIndex + 1).coerceAtMost(options.lastIndex)
                ensureDropdownOptionVisible(state.highlightedIndex, currentOptionHeight(), currentPopupViewportHeight(), state)
                return true
            }
            GLFW.GLFW_KEY_HOME -> {
                state.highlightedIndex = 0
                state.scrollOffset = 0
                return true
            }
            GLFW.GLFW_KEY_END -> {
                state.highlightedIndex = options.lastIndex
                ensureDropdownOptionVisible(state.highlightedIndex, currentOptionHeight(), currentPopupViewportHeight(), state)
                return true
            }
        }
        return false
    }

    override fun mouseScrolled(scrollY: Double, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        if (options.isEmpty()) return false
        val runtime = UiRuntime.currentRuntime ?: return false
        if (!runtime.isToggled(key)) return false

        val state = runtime.dropdownState(key)
        handleDropdownScroll(scrollY, currentOptionHeight(), currentPopupViewportHeight(), state)
        return true
    }

    private fun handleDropdownScroll(
        scrollY: Double,
        optionHeight: Int,
        popupViewportHeight: Int,
        state: UiRuntime.DropdownState
    ) {
        if (options.isEmpty()) return
        val step = if (scrollY > 0.0) -1 else if (scrollY < 0.0) 1 else 0
        if (step == 0) return
        state.highlightedIndex = (state.highlightedIndex + step).coerceIn(0, options.lastIndex)
        ensureDropdownOptionVisible(state.highlightedIndex, optionHeight, popupViewportHeight, state)
    }

    private fun ensureDropdownOptionVisible(
        index: Int,
        optionHeight: Int,
        popupViewportHeight: Int,
        state: UiRuntime.DropdownState
    ) {
        if (optionHeight <= 0 || popupViewportHeight <= 0) return
        val maxScrollOffset = (options.size * optionHeight - popupViewportHeight).coerceAtLeast(0)
        val optionTop = index * optionHeight
        val optionBottom = optionTop + optionHeight
        if (optionTop < state.scrollOffset) {
            state.scrollOffset = optionTop
        } else if (optionBottom > state.scrollOffset + popupViewportHeight) {
            state.scrollOffset = optionBottom - popupViewportHeight
        }
        state.scrollOffset = state.scrollOffset.coerceIn(0, maxScrollOffset)
    }

    private fun currentOptionHeight(): Int = lastOptionHeight.coerceAtLeast(1)

    private fun currentPopupViewportHeight(): Int = lastPopupViewportHeight.coerceAtLeast(currentOptionHeight())

    private var lastOptionHeight: Int = 0
    private var lastPopupViewportHeight: Int = 0

    private fun rememberPopupMetrics(optionHeight: Int, popupViewportHeight: Int) {
        lastOptionHeight = optionHeight
        lastPopupViewportHeight = popupViewportHeight
    }
}

/** Clickable button with hover and pressed states. */
class UiButton(
    private val text: Component,
    private val onClick: () -> Unit,
    override val modifier: UiModifier = UiModifier.None,
    private val key: String? = null,
    private val textures: UiButtonTextures? = null,
    private val tooltip: UiTooltip? = null
) : UiNode {
    private val resolvedKey: String =
        key ?: "button:${text.string}:${textures?.defaultKeyFragment().orEmpty()}"

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val contentWidth = runtime.font.width(text) + 16
        val contentHeight = runtime.font.lineHeight + 10
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Button)
        runtime.debugPaddedRect(bounds, modifier.padding, "Button")
        val hovered = bounds.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val pressed = runtime.isPressed(resolvedKey)
        val background = when {
            pressed -> Theme.buttonFillPressed
            hovered -> Theme.buttonFillHover
            else -> Theme.buttonFill
        }
        val border = when {
            pressed -> Theme.buttonBorderPressed
            hovered -> Theme.buttonBorderHover
            else -> Theme.buttonBorder
        }
        val textOffsetY = if (pressed) 1 else 0
        val activeTexture = when {
            pressed -> textures?.pressed
            hovered -> textures?.hovered
            else -> textures?.normal
        }
        if (activeTexture != null) {
            runtime.guiGraphics.drawUiTexture(activeTexture, bounds)
        } else {
            runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.bottom, background)
            runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + 1, border)
            runtime.guiGraphics.fill(bounds.x, bounds.bottom - 1, bounds.right, bounds.bottom, border)
            runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.bottom, border)
            runtime.guiGraphics.fill(bounds.right - 1, bounds.y, bounds.right, bounds.bottom, border)
        }
        runtime.guiGraphics.drawString(
            runtime.font,
            text,
            bounds.x + ((bounds.width - runtime.font.width(text)) / 2).coerceAtLeast(6),
            bounds.y + (bounds.height - runtime.font.lineHeight) / 2 + textOffsetY,
            CommonColors.WHITE
        )
        runtime.setTooltip(bounds, tooltip)
        runtime.addDragRegion(
            rect = bounds,
            onStart = { _, _ -> runtime.setPressed(resolvedKey, true) },
            onDrag = { mouseX, mouseY -> runtime.setPressed(resolvedKey, bounds.contains(mouseX, mouseY)) },
            onEnd = { mouseX, mouseY ->
                val shouldClick = bounds.contains(mouseX, mouseY)
                runtime.setPressed(resolvedKey, false)
                if (shouldClick) onClick()
            }
        )
    }
}

/** Single-line editable text field. */
class UiTextField(
    private val value: KMutableProperty0<String>,
    private val placeholder: Component? = null,
    override val modifier: UiModifier = UiModifier.None,
    private val maxLength: Int = 256,
    private val key: String = "textfield:${value.name}",
    private val tooltip: UiTooltip? = null,
    private val onClick: (() -> Unit)? = null
) : UiNode, UiTextInput {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val contentWidth = 120
        val contentHeight = runtime.font.lineHeight + 10
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.TextField)
        runtime.debugPaddedRect(bounds, modifier.padding, "TextField")
        latestRuntime = runtime
        val state = runtime.textInputState(key, this)
        runtime.setTextInputRect(key, bounds)
        val inner = bounds.shrink(modifier.padding)
        val focused = runtime.isFocused(key)
        val current = value.get()
        state.caretIndex = state.caretIndex.coerceIn(0, current.length)
        val hovered = bounds.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val borderColor = when {
            focused -> Theme.focusBorder
            hovered -> Theme.hoverBorder
            else -> Theme.border
        }

        runtime.guiGraphics.fill(
            bounds.x,
            bounds.y,
            bounds.right,
            bounds.bottom,
            if (focused) Theme.textFieldBackgroundFocused else Theme.textFieldBackground
        )
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + 1, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.bottom - 1, bounds.right, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.right - 1, bounds.y, bounds.right, bounds.bottom, borderColor)
        runtime.setTooltip(bounds, tooltip)

        val textX = inner.x + 4
        val textY = inner.y + (inner.height - runtime.font.lineHeight) / 2
        val contentRect = UiRect(textX, inner.y + 1, (inner.right - textX - 4).coerceAtLeast(0), (inner.height - 2).coerceAtLeast(0))
        runtime.guiGraphics.enableScissor(contentRect.x, contentRect.y, contentRect.right, contentRect.bottom)
        if (current.isEmpty() && placeholder != null) {
            runtime.guiGraphics.drawString(runtime.font, placeholder, textX, textY, Theme.placeholder)
        } else {
            runtime.guiGraphics.drawString(runtime.font, current, textX, textY, CommonColors.WHITE)
        }

        if (focused && shouldRenderCaret()) {
            val caretText = current.take(state.caretIndex)
            val caretX = textX + runtime.font.width(caretText)
            runtime.guiGraphics.fill(caretX, textY - 1, caretX + 1, textY + runtime.font.lineHeight + 1, Theme.caret)
        }
        runtime.guiGraphics.disableScissor()

        runtime.addClickRegion(bounds) { mouseX, _ ->
            onClick?.invoke()
            runtime.focus(key)
            state.caretIndex = caretIndexForX(runtime, current, mouseX.toInt() - textX)
        }
    }

    override fun charTyped(codePoint: Char): Boolean {
        if (codePoint.code < 32 || codePoint == 127.toChar()) return false
        val current = value.get()
        if (current.length >= maxLength) return true
        val stateCaret = runtimeState()?.caretIndex ?: current.length
        val next = current.substring(0, stateCaret) + codePoint + current.substring(stateCaret)
        value.set(next)
        runtimeState()?.caretIndex = stateCaret + 1
        return true
    }

    override fun keyPressed(keyCode: Int, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        val current = value.get()
        val state = runtimeState() ?: return false
        when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (state.caretIndex <= 0 || current.isEmpty()) return true
                val next = current.removeRange(state.caretIndex - 1, state.caretIndex)
                value.set(next)
                state.caretIndex--
                return true
            }
            GLFW.GLFW_KEY_DELETE -> {
                if (state.caretIndex >= current.length) return true
                value.set(current.removeRange(state.caretIndex, state.caretIndex + 1))
                return true
            }
            GLFW.GLFW_KEY_LEFT -> {
                state.caretIndex = (state.caretIndex - 1).coerceAtLeast(0)
                return true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                state.caretIndex = (state.caretIndex + 1).coerceAtMost(current.length)
                return true
            }
            GLFW.GLFW_KEY_HOME -> {
                state.caretIndex = 0
                return true
            }
            GLFW.GLFW_KEY_END -> {
                state.caretIndex = current.length
                return true
            }
        }
        return false
    }

    private fun shouldRenderCaret(): Boolean = (System.currentTimeMillis() / 500L) % 2L == 0L

    private fun caretIndexForX(runtime: UiRuntime, text: String, localX: Int): Int {
        if (localX <= 0) return 0
        for (index in text.indices) {
            if (localX < runtime.font.width(text.take(index + 1))) {
                return index
            }
        }
        return text.length
    }

    private fun runtimeState(): UiRuntime.TextInputState? = latestRuntime?.textInputState(key, this)

    companion object {
        private var latestRuntime: UiRuntime? = null
    }

    init {
        latestRuntime = null
    }
}

/** Integer-only editable field with optional wheel-based step changes while hovered. */
class UiIntField(
    private val value: KMutableProperty0<Int>,
    private val range: IntRange,
    private val step: Int = 1,
    private val placeholder: Component? = null,
    override val modifier: UiModifier = UiModifier.None,
    private val key: String = "intfield:${value.name}",
    private val tooltip: UiTooltip? = null
) : UiNode, UiTextInput {
    init {
        require(!range.isEmpty()) { "IntField range must not be empty" }
        require(step > 0) { "IntField step must be positive" }
    }

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val contentWidth = 80
        val contentHeight = runtime.font.lineHeight + 10
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.TextField, "IntField")
        runtime.debugPaddedRect(bounds, modifier.padding, "IntField")
        latestRuntime = runtime
        val state = runtime.textInputState(key, this)
        runtime.setTextInputRect(key, bounds)
        val inner = bounds.shrink(modifier.padding)
        val focused = runtime.isFocused(key)
        val currentValue = value.get().coerceIn(range.first, range.last)
        if (currentValue != value.get()) value.set(currentValue)

        val displayText = if (focused) {
            rawInputByKey.getOrPut(key) { currentValue.toString() }
        } else {
            currentValue.toString()
        }
        state.caretIndex = state.caretIndex.coerceIn(0, displayText.length)
        val hovered = bounds.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val borderColor = when {
            focused -> Theme.focusBorder
            hovered -> Theme.hoverBorder
            else -> Theme.border
        }

        runtime.guiGraphics.fill(
            bounds.x,
            bounds.y,
            bounds.right,
            bounds.bottom,
            if (focused) Theme.textFieldBackgroundFocused else Theme.textFieldBackground
        )
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + 1, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.bottom - 1, bounds.right, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.right - 1, bounds.y, bounds.right, bounds.bottom, borderColor)
        runtime.setTooltip(bounds, tooltip)
        runtime.addScrollRegion(bounds) { scrollY -> applyWheelDelta(scrollY) }

        val textX = inner.x + 4
        val textY = inner.y + (inner.height - runtime.font.lineHeight) / 2
        val contentRect = UiRect(textX, inner.y + 1, (inner.right - textX - 4).coerceAtLeast(0), (inner.height - 2).coerceAtLeast(0))
        runtime.guiGraphics.enableScissor(contentRect.x, contentRect.y, contentRect.right, contentRect.bottom)
        if (displayText.isEmpty() && placeholder != null) {
            runtime.guiGraphics.drawString(runtime.font, placeholder, textX, textY, Theme.placeholder)
        } else {
            runtime.guiGraphics.drawString(runtime.font, displayText, textX, textY, CommonColors.WHITE)
        }

        if (focused && shouldRenderCaret()) {
            val caretText = displayText.take(state.caretIndex)
            val caretX = textX + runtime.font.width(caretText)
            runtime.guiGraphics.fill(caretX, textY - 1, caretX + 1, textY + runtime.font.lineHeight + 1, Theme.caret)
        }
        runtime.guiGraphics.disableScissor()

        runtime.addClickRegion(bounds) { mouseX, _ ->
            runtime.focus(key)
            val raw = rawInputByKey.getOrPut(key) { value.get().coerceIn(range.first, range.last).toString() }
            state.caretIndex = caretIndexForX(runtime, raw, mouseX.toInt() - textX)
        }
    }

    override fun charTyped(codePoint: Char): Boolean {
        if (!isAllowedIntChar(codePoint)) return false
        val state = runtimeState() ?: return false
        val current = rawInputByKey.getOrPut(key) { value.get().coerceIn(range.first, range.last).toString() }
        val next = current.substring(0, state.caretIndex) + codePoint + current.substring(state.caretIndex)
        if (!isPotentialInt(next)) return true
        rawInputByKey[key] = next
        state.caretIndex++
        next.toIntOrNull()?.let { parsed ->
            value.set(parsed.coerceIn(range.first, range.last))
        }
        return true
    }

    override fun keyPressed(keyCode: Int, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        val state = runtimeState() ?: return false
        val current = rawInputByKey.getOrPut(key) { value.get().coerceIn(range.first, range.last).toString() }
        when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (state.caretIndex <= 0) return true
                val next = current.removeRange(state.caretIndex - 1, state.caretIndex)
                if (!isPotentialInt(next)) return true
                rawInputByKey[key] = next
                state.caretIndex--
                next.toIntOrNull()?.let { parsed -> value.set(parsed.coerceIn(range.first, range.last)) }
                return true
            }
            GLFW.GLFW_KEY_DELETE -> {
                if (state.caretIndex >= current.length) return true
                val next = current.removeRange(state.caretIndex, state.caretIndex + 1)
                if (!isPotentialInt(next)) return true
                rawInputByKey[key] = next
                next.toIntOrNull()?.let { parsed -> value.set(parsed.coerceIn(range.first, range.last)) }
                return true
            }
            GLFW.GLFW_KEY_LEFT -> {
                state.caretIndex = (state.caretIndex - 1).coerceAtLeast(0)
                return true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                state.caretIndex = (state.caretIndex + 1).coerceAtMost(current.length)
                return true
            }
            GLFW.GLFW_KEY_HOME -> {
                state.caretIndex = 0
                return true
            }
            GLFW.GLFW_KEY_END -> {
                state.caretIndex = current.length
                return true
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitRawInput()
                return true
            }
            GLFW.GLFW_KEY_UP -> {
                applyWheelDelta(1.0)
                return true
            }
            GLFW.GLFW_KEY_DOWN -> {
                applyWheelDelta(-1.0)
                return true
            }
        }
        return false
    }

    override fun mouseScrolled(scrollY: Double, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        applyWheelDelta(scrollY)
        return true
    }

    private fun applyWheelDelta(scrollY: Double) {
        val direction = scrollY.compareTo(0.0)
        if (direction == 0) return
        val next = (value.get() + step * direction).coerceIn(range.first, range.last)
        value.set(next)
        rawInputByKey[key] = next.toString()
        runtimeState()?.caretIndex = rawInputByKey[key]?.length ?: 0
    }

    private fun commitRawInput() {
        val committed = rawInputByKey[key]?.toIntOrNull()?.coerceIn(range.first, range.last) ?: value.get().coerceIn(range.first, range.last)
        value.set(committed)
        rawInputByKey[key] = committed.toString()
        runtimeState()?.caretIndex = rawInputByKey[key]?.length ?: 0
    }

    private fun shouldRenderCaret(): Boolean = (System.currentTimeMillis() / 500L) % 2L == 0L

    private fun caretIndexForX(runtime: UiRuntime, text: String, localX: Int): Int {
        if (localX <= 0) return 0
        for (index in text.indices) {
            if (localX < runtime.font.width(text.take(index + 1))) return index
        }
        return text.length
    }

    private fun runtimeState(): UiRuntime.TextInputState? = latestRuntime?.textInputState(key, this)

    companion object {
        private var latestRuntime: UiRuntime? = null
        private val rawInputByKey: MutableMap<String, String> = mutableMapOf()

        private fun isAllowedIntChar(char: Char): Boolean = char.isDigit() || char == '-'
        private fun isPotentialInt(value: String): Boolean = value.isEmpty() || value == "-" || value.toIntOrNull() != null
    }
}

/** Float-only editable field with optional wheel-based step changes while hovered. */
class UiFloatField(
    private val value: KMutableProperty0<Float>,
    private val range: ClosedFloatingPointRange<Float>,
    private val step: Float = 0.1f,
    private val placeholder: Component? = null,
    override val modifier: UiModifier = UiModifier.None,
    private val key: String = "floatfield:${value.name}",
    private val tooltip: UiTooltip? = null
) : UiNode, UiTextInput {
    init {
        require(!range.isEmpty()) { "FloatField range must not be empty" }
        require(step > 0f) { "FloatField step must be positive" }
    }

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val contentWidth = 96
        val contentHeight = runtime.font.lineHeight + 10
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.TextField, "FloatField")
        runtime.debugPaddedRect(bounds, modifier.padding, "FloatField")
        latestRuntime = runtime
        val state = runtime.textInputState(key, this)
        runtime.setTextInputRect(key, bounds)
        val inner = bounds.shrink(modifier.padding)
        val focused = runtime.isFocused(key)
        val currentValue = value.get().coerceIn(range.start, range.endInclusive)
        if (currentValue != value.get()) value.set(currentValue)

        val displayText = if (focused) {
            rawInputByKey.getOrPut(key) { formatFloat(currentValue) }
        } else {
            formatFloat(currentValue)
        }
        state.caretIndex = state.caretIndex.coerceIn(0, displayText.length)
        val hovered = bounds.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val borderColor = when {
            focused -> Theme.focusBorder
            hovered -> Theme.hoverBorder
            else -> Theme.border
        }

        runtime.guiGraphics.fill(
            bounds.x,
            bounds.y,
            bounds.right,
            bounds.bottom,
            if (focused) Theme.textFieldBackgroundFocused else Theme.textFieldBackground
        )
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + 1, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.bottom - 1, bounds.right, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.right - 1, bounds.y, bounds.right, bounds.bottom, borderColor)
        runtime.setTooltip(bounds, tooltip)
        runtime.addScrollRegion(bounds) { scrollY -> applyWheelDelta(scrollY) }

        val textX = inner.x + 4
        val textY = inner.y + (inner.height - runtime.font.lineHeight) / 2
        val contentRect = UiRect(textX, inner.y + 1, (inner.right - textX - 4).coerceAtLeast(0), (inner.height - 2).coerceAtLeast(0))
        runtime.guiGraphics.enableScissor(contentRect.x, contentRect.y, contentRect.right, contentRect.bottom)
        if (displayText.isEmpty() && placeholder != null) {
            runtime.guiGraphics.drawString(runtime.font, placeholder, textX, textY, Theme.placeholder)
        } else {
            runtime.guiGraphics.drawString(runtime.font, displayText, textX, textY, CommonColors.WHITE)
        }

        if (focused && shouldRenderCaret()) {
            val caretText = displayText.take(state.caretIndex)
            val caretX = textX + runtime.font.width(caretText)
            runtime.guiGraphics.fill(caretX, textY - 1, caretX + 1, textY + runtime.font.lineHeight + 1, Theme.caret)
        }
        runtime.guiGraphics.disableScissor()

        runtime.addClickRegion(bounds) { mouseX, _ ->
            runtime.focus(key)
            val raw = rawInputByKey.getOrPut(key) { formatFloat(value.get().coerceIn(range.start, range.endInclusive)) }
            state.caretIndex = caretIndexForX(runtime, raw, mouseX.toInt() - textX)
        }
    }

    override fun charTyped(codePoint: Char): Boolean {
        if (!isAllowedFloatChar(codePoint)) return false
        val state = runtimeState() ?: return false
        val current = rawInputByKey.getOrPut(key) { formatFloat(value.get().coerceIn(range.start, range.endInclusive)) }
        val next = current.substring(0, state.caretIndex) + codePoint + current.substring(state.caretIndex)
        if (!isPotentialFloat(next)) return true
        rawInputByKey[key] = next
        state.caretIndex++
        next.toFloatOrNull()?.let { parsed ->
            value.set(parsed.coerceIn(range.start, range.endInclusive))
        }
        return true
    }

    override fun keyPressed(keyCode: Int, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        val state = runtimeState() ?: return false
        val current = rawInputByKey.getOrPut(key) { formatFloat(value.get().coerceIn(range.start, range.endInclusive)) }
        when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (state.caretIndex <= 0) return true
                val next = current.removeRange(state.caretIndex - 1, state.caretIndex)
                if (!isPotentialFloat(next)) return true
                rawInputByKey[key] = next
                state.caretIndex--
                next.toFloatOrNull()?.let { parsed -> value.set(parsed.coerceIn(range.start, range.endInclusive)) }
                return true
            }
            GLFW.GLFW_KEY_DELETE -> {
                if (state.caretIndex >= current.length) return true
                val next = current.removeRange(state.caretIndex, state.caretIndex + 1)
                if (!isPotentialFloat(next)) return true
                rawInputByKey[key] = next
                next.toFloatOrNull()?.let { parsed -> value.set(parsed.coerceIn(range.start, range.endInclusive)) }
                return true
            }
            GLFW.GLFW_KEY_LEFT -> {
                state.caretIndex = (state.caretIndex - 1).coerceAtLeast(0)
                return true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                state.caretIndex = (state.caretIndex + 1).coerceAtMost(current.length)
                return true
            }
            GLFW.GLFW_KEY_HOME -> {
                state.caretIndex = 0
                return true
            }
            GLFW.GLFW_KEY_END -> {
                state.caretIndex = current.length
                return true
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitRawInput()
                return true
            }
            GLFW.GLFW_KEY_UP -> {
                applyWheelDelta(1.0)
                return true
            }
            GLFW.GLFW_KEY_DOWN -> {
                applyWheelDelta(-1.0)
                return true
            }
        }
        return false
    }

    override fun mouseScrolled(scrollY: Double, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        applyWheelDelta(scrollY)
        return true
    }

    private fun applyWheelDelta(scrollY: Double) {
        val direction = scrollY.compareTo(0.0).toFloat()
        if (direction == 0f) return
        val next = (value.get() + step * direction).coerceIn(range.start, range.endInclusive)
        value.set(next)
        rawInputByKey[key] = formatFloat(next)
        runtimeState()?.caretIndex = rawInputByKey[key]?.length ?: 0
    }

    private fun commitRawInput() {
        val committed = rawInputByKey[key]?.toFloatOrNull()?.coerceIn(range.start, range.endInclusive)
            ?: value.get().coerceIn(range.start, range.endInclusive)
        value.set(committed)
        rawInputByKey[key] = formatFloat(committed)
        runtimeState()?.caretIndex = rawInputByKey[key]?.length ?: 0
    }

    private fun shouldRenderCaret(): Boolean = (System.currentTimeMillis() / 500L) % 2L == 0L

    private fun caretIndexForX(runtime: UiRuntime, text: String, localX: Int): Int {
        if (localX <= 0) return 0
        for (index in text.indices) {
            if (localX < runtime.font.width(text.take(index + 1))) return index
        }
        return text.length
    }

    private fun runtimeState(): UiRuntime.TextInputState? = latestRuntime?.textInputState(key, this)

    companion object {
        private var latestRuntime: UiRuntime? = null
        private val rawInputByKey: MutableMap<String, String> = mutableMapOf()

        private fun isAllowedFloatChar(char: Char): Boolean = char.isDigit() || char == '-' || char == '.'
        private fun isPotentialFloat(value: String): Boolean =
            value.isEmpty() || value == "-" || value == "." || value == "-." || value.toFloatOrNull() != null

        private fun formatFloat(value: Float): String =
            value.toString().trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }
}

/** Boolean toggle rendered as a checkbox plus optional label. */
class UiCheckbox(
    private val value: KMutableProperty0<Boolean>,
    private val text: Component? = null,
    override val modifier: UiModifier = UiModifier.None,
    private val tooltip: UiTooltip? = null
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val boxSize = runtime.font.lineHeight.coerceAtLeast(10)
        val labelWidth = text?.let(runtime.font::width)?.plus(6) ?: 0
        val contentWidth = boxSize + labelWidth
        val contentHeight = boxSize.coerceAtLeast(runtime.font.lineHeight)
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Checkbox)
        runtime.debugPaddedRect(bounds, modifier.padding, "Checkbox")
        val inner = bounds.shrink(modifier.padding)
        val hovered = bounds.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val checked = value.get()
        val boxSize = inner.height.coerceAtLeast(10).coerceAtMost(14)
        val boxY = inner.y + ((inner.height - boxSize) / 2)
        val boxRect = UiRect(inner.x, boxY, boxSize, boxSize)
        val borderColor = if (hovered) Theme.hoverBorder else Theme.border

        runtime.guiGraphics.fill(boxRect.x, boxRect.y, boxRect.right, boxRect.bottom, Theme.textFieldBackground)
        runtime.guiGraphics.fill(boxRect.x, boxRect.y, boxRect.right, boxRect.y + 1, borderColor)
        runtime.guiGraphics.fill(boxRect.x, boxRect.bottom - 1, boxRect.right, boxRect.bottom, borderColor)
        runtime.guiGraphics.fill(boxRect.x, boxRect.y, boxRect.x + 1, boxRect.bottom, borderColor)
        runtime.guiGraphics.fill(boxRect.right - 1, boxRect.y, boxRect.right, boxRect.bottom, borderColor)

        if (checked) {
            runtime.guiGraphics.fill(
                boxRect.x + 3,
                boxRect.y + 3,
                boxRect.right - 3,
                boxRect.bottom - 3,
                Theme.focusBorder
            )
        }

        text?.let {
            runtime.guiGraphics.drawString(
                runtime.font,
                it,
                boxRect.right + 6,
                inner.y + (inner.height - runtime.font.lineHeight) / 2,
                CommonColors.WHITE
            )
        }

        runtime.setTooltip(bounds, tooltip)
        runtime.addClickRegion(bounds) { _, _ -> value.set(!value.get()) }
    }
}

/** Multi-line editor-like text field with selection, zoom and internal scrolling. */
class UiMultilineTextField(
    private val value: KMutableProperty0<String>,
    private val placeholder: Component? = null,
    override val modifier: UiModifier = UiModifier.None,
    private val maxLength: Int = 4096,
    private val showCharCount: Boolean = false,
    private val maxChars: Int = Int.MAX_VALUE,
    private val zoomBinding: UiZoomBinding? = null,
    private val highlights: List<UiTextHighlightRule> = emptyList(),
    private val key: String = "multiline:${value.name}",
    private val tooltip: UiTooltip? = null
) : UiNode, UiTextInput {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val contentWidth = 220
        val contentHeight = runtime.font.lineHeight * 5 + 18
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Multiline)
        runtime.debugPaddedRect(bounds, modifier.padding, "Multiline")
        latestRuntime = runtime
        val state = runtime.textInputState(key, this)
        runtime.setTextInputRect(key, bounds)
        val inner = bounds.shrink(modifier.padding)
        val focused = runtime.isFocused(key)
        val hovered = bounds.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val current = value.get()
        state.caretIndex = state.caretIndex.coerceIn(0, current.length)
        val zoom = (zoomBinding?.value() ?: 1f).coerceIn(0.75f, 2f)
        val lineHeight = (runtime.font.lineHeight * zoom).toInt().coerceAtLeast(runtime.font.lineHeight)
        val borderColor = when {
            focused -> Theme.focusBorder
            hovered -> Theme.hoverBorder
            else -> Theme.border
        }

        runtime.guiGraphics.fill(
            bounds.x,
            bounds.y,
            bounds.right,
            bounds.bottom,
            if (focused) Theme.textFieldBackgroundFocused else Theme.textFieldBackgroundAlt
        )
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.right, bounds.y + 1, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.bottom - 1, bounds.right, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.bottom, borderColor)
        runtime.guiGraphics.fill(bounds.right - 1, bounds.y, bounds.right, bounds.bottom, borderColor)
        runtime.setTooltip(bounds, tooltip)

        val footerHeight = if (showCharCount) runtime.font.lineHeight + 4 else 0
        val horizontalScrollBarHeight = 6
        val horizontalViewportPadding = 4
        val horizontalClipTolerance = 2
        val editorRect = UiRect(
            inner.x + 4,
            inner.y + 4,
            (inner.width - 8).coerceAtLeast(0),
            (inner.height - 8 - footerHeight).coerceAtLeast(0)
        )
        val digitWidth = runtime.font.width("0")
        val lineNumberWidth = (((digitWidth * lineCount(current).toString().length) * zoom).toInt() + 4).coerceAtLeast(10)
        val lines = current.split('\n')
        val longestLineWidth = linesMaxWidth(runtime, lines, zoom)
        val verticalScrollBarReserved = 10
        val separatorX = editorRect.x + lineNumberWidth + 3
        val baseTextRect = UiRect(
            separatorX + 6,
            editorRect.y,
            (editorRect.right - (separatorX + 6) - verticalScrollBarReserved).coerceAtLeast(0),
            editorRect.height
        )
        val visibleTextWidth = (baseTextRect.width - horizontalViewportPadding - horizontalClipTolerance).coerceAtLeast(0)
        val horizontalScrollNeeded = longestLineWidth > visibleTextWidth
        val viewportHeight = (editorRect.height - if (horizontalScrollNeeded) horizontalScrollBarHeight else 0).coerceAtLeast(0)
        val gutterRect = UiRect(editorRect.x, editorRect.y, lineNumberWidth, viewportHeight)
        val finalSeparatorX = gutterRect.right + 3
        val textRect = UiRect(
            finalSeparatorX + 6,
            editorRect.y,
            (editorRect.right - (finalSeparatorX + 6) - verticalScrollBarReserved).coerceAtLeast(0),
            viewportHeight
        )
        latestTextRect = textRect
        latestBounds = bounds
        runtime.guiGraphics.fill(finalSeparatorX, editorRect.y, finalSeparatorX + 1, editorRect.bottom, Theme.separator)

        val caretLine = caretLine(current, state.caretIndex)
        val caretColumn = caretColumn(current, state.caretIndex)
        val contentHeight = lines.size * lineHeight
        val maxScroll = (contentHeight - textRect.height).coerceAtLeast(0)
        state.scrollOffset = state.scrollOffset.coerceIn(0, maxScroll)
        val finalVisibleTextWidth = (textRect.width - horizontalViewportPadding - horizontalClipTolerance).coerceAtLeast(0)
        val maxHorizontalScroll = (longestLineWidth - finalVisibleTextWidth).coerceAtLeast(0)
        state.horizontalScrollOffset = state.horizontalScrollOffset.coerceIn(0, maxHorizontalScroll)

        runtime.guiGraphics.enableScissor(textRect.x, textRect.y, textRect.right, textRect.bottom)
        if (current.isEmpty() && placeholder != null) {
            runtime.guiGraphics.pose().pushMatrix()
            runtime.guiGraphics.pose().translate((textRect.x - state.horizontalScrollOffset).toFloat(), (textRect.y - state.scrollOffset).toFloat())
            runtime.guiGraphics.pose().scale(zoom, zoom)
            runtime.guiGraphics.drawString(runtime.font, placeholder, 0, 0, Theme.placeholder)
            runtime.guiGraphics.pose().popMatrix()
        } else {
            drawSelection(runtime, current, lines, textRect, lineHeight, zoom, state)
            lines.forEachIndexed { index, line ->
                val y = textRect.y + index * lineHeight - state.scrollOffset
                if (y + lineHeight > textRect.bottom) return@forEachIndexed
                if (y < textRect.y - lineHeight) return@forEachIndexed
                drawHighlightedLine(runtime, line, textRect.x - state.horizontalScrollOffset, y, zoom)
            }
        }
        runtime.guiGraphics.disableScissor()

        runtime.guiGraphics.enableScissor(gutterRect.x, gutterRect.y, gutterRect.right, gutterRect.bottom)
        lines.forEachIndexed { index, _ ->
            val y = gutterRect.y + index * lineHeight - state.scrollOffset
            if (y + lineHeight > gutterRect.bottom) return@forEachIndexed
            if (y < gutterRect.y - lineHeight) return@forEachIndexed
            val lineNo = (index + 1).toString()
            val scaledWidth = (runtime.font.width(lineNo) * zoom).toInt()
            val numberX = gutterRect.right - 2 - scaledWidth
            runtime.guiGraphics.pose().pushMatrix()
            runtime.guiGraphics.pose().translate(numberX.toFloat(), y.toFloat())
            runtime.guiGraphics.pose().scale(zoom, zoom)
            runtime.guiGraphics.drawString(runtime.font, lineNo, 0, 0, Theme.lineNumber)
            runtime.guiGraphics.pose().popMatrix()
        }
        runtime.guiGraphics.disableScissor()

        if (focused) {
            runtime.guiGraphics.enableScissor(textRect.x, textRect.y, textRect.right, textRect.bottom)
            val safeLine = lines.getOrElse(caretLine) { "" }
            val caretPrefix = safeLine.take(caretColumn.coerceAtMost(safeLine.length))
            val caretX = textRect.x + visualWidth(runtime, caretPrefix, zoom) - state.horizontalScrollOffset
            val caretY = textRect.y + caretLine * lineHeight - state.scrollOffset
            if (caretX < textRect.right && caretY < textRect.bottom && caretY + lineHeight > textRect.y) {
                runtime.guiGraphics.fill(caretX, caretY - 1, caretX + 1, caretY + lineHeight - 1, Theme.caret)
            }
            runtime.guiGraphics.disableScissor()
        }

        runtime.addClickRegion(bounds) { mouseX, mouseY ->
            runtime.focus(key)
            state.caretIndex = caretIndexForPoint(runtime, value.get(), textRect, mouseX.toInt(), mouseY.toInt(), state.scrollOffset, state.horizontalScrollOffset)
            state.preferredColumn = caretColumn(value.get(), state.caretIndex)
            state.selectionAnchor = null
        }
        runtime.addDragRegion(
            rect = UiRect(editorRect.x, editorRect.y, (textRect.right - editorRect.x).coerceAtLeast(0), editorRect.height),
            onStart = { mouseX, mouseY ->
                runtime.focus(key)
                val caret = caretIndexForPoint(runtime, value.get(), textRect, mouseX.toInt(), mouseY.toInt(), state.scrollOffset, state.horizontalScrollOffset)
                state.caretIndex = caret
                state.preferredColumn = caretColumn(value.get(), caret)
                state.selectionAnchor = caret
            },
            onDrag = { mouseX, mouseY ->
                val caret = caretIndexForPoint(runtime, value.get(), textRect, mouseX.toInt(), mouseY.toInt(), state.scrollOffset, state.horizontalScrollOffset)
                state.caretIndex = caret
                state.preferredColumn = caretColumn(value.get(), caret)
                if (state.selectionAnchor == null) state.selectionAnchor = caret
            }
        )

        if (maxScroll > 0) {
            val scrollBarWidth = 4
            val scrollBarX = bounds.right - 6
            val thumbHeight = ((textRect.height.toFloat() * textRect.height.toFloat()) / contentHeight.toFloat())
                .toInt()
                .coerceAtLeast(10)
                .coerceAtMost(textRect.height)
            val travel = (textRect.height - thumbHeight).coerceAtLeast(0)
            val thumbY = textRect.y + ((state.scrollOffset.toFloat() / maxScroll.toFloat()) * travel).toInt()
            val trackRect = UiRect(scrollBarX, textRect.y, scrollBarWidth, textRect.height)
            val thumbRect = UiRect(scrollBarX, thumbY, scrollBarWidth, thumbHeight)
            val thumbHovered = thumbRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
            runtime.guiGraphics.fill(scrollBarX, textRect.y, scrollBarX + scrollBarWidth, textRect.bottom, Theme.scrollTrack)
            runtime.guiGraphics.fill(
                scrollBarX,
                thumbY,
                scrollBarX + scrollBarWidth,
                thumbY + thumbHeight,
                if (thumbHovered) Theme.scrollThumbHover else Theme.scrollThumb
            )

            fun applyScrollFromThumb(mouseY: Double) {
                if (travel <= 0) {
                    state.scrollOffset = 0
                    return
                }
                val localY = (mouseY - textRect.y - thumbHeight / 2.0).coerceIn(0.0, travel.toDouble())
                val ratio = localY / travel.toDouble()
                state.scrollOffset = (maxScroll * ratio).toInt().coerceIn(0, maxScroll)
            }

            runtime.addClickRegion(trackRect) { _, mouseY -> applyScrollFromThumb(mouseY) }
            runtime.addDragRegion(
                rect = thumbRect,
                onDrag = { _, mouseY -> applyScrollFromThumb(mouseY) }
            )
        }

        if (horizontalScrollNeeded && maxHorizontalScroll > 0) {
            val scrollBarY = textRect.bottom + 2
            val trackRect = UiRect(textRect.x, scrollBarY, textRect.width, 4)
            val thumbWidth = ((textRect.width.toFloat() * textRect.width.toFloat()) / longestLineWidth.toFloat())
                .toInt()
                .coerceAtLeast(10)
                .coerceAtMost(textRect.width)
            val travel = (textRect.width - thumbWidth).coerceAtLeast(0)
            val thumbX = textRect.x + if (travel == 0) 0 else ((state.horizontalScrollOffset.toFloat() / maxHorizontalScroll.toFloat()) * travel).toInt()
            val thumbRect = UiRect(thumbX, scrollBarY, thumbWidth, 4)
            val thumbHovered = thumbRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
            runtime.guiGraphics.fill(trackRect.x, trackRect.y, trackRect.right, trackRect.bottom, Theme.scrollTrack)
            runtime.guiGraphics.fill(
                thumbRect.x,
                thumbRect.y,
                thumbRect.right,
                thumbRect.bottom,
                if (thumbHovered) Theme.scrollThumbHover else Theme.scrollThumb
            )

            fun applyHorizontalScrollFromThumb(mouseX: Double) {
                if (travel <= 0) {
                    state.horizontalScrollOffset = 0
                    return
                }
                val localX = (mouseX - textRect.x - thumbWidth / 2.0).coerceIn(0.0, travel.toDouble())
                val ratio = localX / travel.toDouble()
                state.horizontalScrollOffset = (maxHorizontalScroll * ratio).toInt().coerceIn(0, maxHorizontalScroll)
            }

            runtime.addClickRegion(trackRect) { mouseX, _ -> applyHorizontalScrollFromThumb(mouseX) }
            runtime.addDragRegion(
                rect = thumbRect,
                onDrag = { mouseX, _ -> applyHorizontalScrollFromThumb(mouseX) }
            )
        }

        if (showCharCount) {
            val chars = current.length
            val footer = "$chars/${if (maxChars == Int.MAX_VALUE) "∞" else maxChars} chars"
            val footerY = inner.bottom - runtime.font.lineHeight - 2
            runtime.guiGraphics.drawString(runtime.font, footer, inner.x + 4, footerY, if (chars > maxChars) CommonColors.RED else ARGB.color(180, 180, 180, 180))
        }
    }

    override fun charTyped(codePoint: Char): Boolean {
        if (codePoint.code < 32 && codePoint != '\n' && codePoint != '\t') return false
        val current = value.get()
        if (current.length >= maxLength && codePoint != '\n' && codePoint != '\t') return true
        if (codePoint != '\n' && codePoint != '\t' && codePoint == 127.toChar()) return false
        val state = runtimeState() ?: return false
        val (selectionStart, selectionEnd) = selectedRange(state)
        val base = if (selectionStart != null && selectionEnd != null) {
            current.removeRange(selectionStart, selectionEnd)
        } else {
            current
        }
        val insertIndex = selectionStart ?: state.caretIndex
        val inserted = codePoint.toString()
        val candidate = base.substring(0, insertIndex) + inserted + base.substring(insertIndex)
        if (candidate.length > maxLength || candidate.length > maxChars) return true
        saveUndoSnapshot(current, state)
        value.set(candidate)
        state.redoStack.clear()
        state.caretIndex = insertIndex + inserted.length
        state.preferredColumn = caretColumn(candidate, state.caretIndex)
        state.selectionAnchor = null
        return true
    }

    override fun keyPressed(keyCode: Int, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        val current = value.get()
        val state = runtimeState() ?: return false
        if (isCtrlDown) {
            when (keyCode) {
                GLFW.GLFW_KEY_Z -> {
                    if (state.undoStack.isEmpty()) return true
                    state.redoStack += snapshot(current, state)
                    applySnapshot(state, state.undoStack.removeLast())
                    return true
                }
                GLFW.GLFW_KEY_Y -> {
                    if (state.redoStack.isEmpty()) return true
                    state.undoStack += snapshot(current, state)
                    applySnapshot(state, state.redoStack.removeLast())
                    return true
                }
                GLFW.GLFW_KEY_A -> {
                    state.selectionAnchor = 0
                    state.caretIndex = current.length
                    state.preferredColumn = caretColumn(current, state.caretIndex)
                    return true
                }
                GLFW.GLFW_KEY_C -> {
                    selectedText(current, state)?.let(::writeClipboard)
                    return true
                }
                GLFW.GLFW_KEY_X -> {
                    val (selectionStart, selectionEnd) = selectedRange(state)
                    if (selectionStart != null && selectionEnd != null) {
                        writeClipboard(current.substring(selectionStart, selectionEnd))
                        saveUndoSnapshot(current, state)
                        value.set(current.removeRange(selectionStart, selectionEnd))
                        state.redoStack.clear()
                        state.caretIndex = selectionStart
                        state.preferredColumn = caretColumn(value.get(), state.caretIndex)
                        state.selectionAnchor = null
                    }
                    return true
                }
                GLFW.GLFW_KEY_V -> {
                    val paste = readClipboard().replace("\r\n", "\n")
                    if (paste.isEmpty()) return true
                    val (selectionStart, selectionEnd) = selectedRange(state)
                    val base = if (selectionStart != null && selectionEnd != null) {
                        current.removeRange(selectionStart, selectionEnd)
                    } else {
                        current
                    }
                    val insertIndex = selectionStart ?: state.caretIndex
                    val candidate = base.substring(0, insertIndex) + paste + base.substring(insertIndex)
                    if (candidate.length > maxLength || candidate.length > maxChars) return true
                    saveUndoSnapshot(current, state)
                    value.set(candidate)
                    state.redoStack.clear()
                    state.caretIndex = insertIndex + paste.length
                    state.preferredColumn = caretColumn(candidate, state.caretIndex)
                    state.selectionAnchor = null
                    return true
                }
            }
        }
        when (keyCode) {
            GLFW.GLFW_KEY_TAB -> {
                val (selectionStart, selectionEnd) = selectedRange(state)
                val base = if (selectionStart != null && selectionEnd != null) current.removeRange(selectionStart, selectionEnd) else current
                val insertIndex = selectionStart ?: state.caretIndex
                val candidate = base.substring(0, insertIndex) + '\t' + base.substring(insertIndex)
                if (candidate.length > maxLength || candidate.length > maxChars) return true
                saveUndoSnapshot(current, state)
                value.set(candidate)
                state.redoStack.clear()
                state.caretIndex = insertIndex + 1
                state.preferredColumn = caretColumn(candidate, state.caretIndex)
                state.selectionAnchor = null
                return true
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                val (selectionStart, selectionEnd) = selectedRange(state)
                val base = if (selectionStart != null && selectionEnd != null) current.removeRange(selectionStart, selectionEnd) else current
                val insertIndex = selectionStart ?: state.caretIndex
                if (base.length >= maxLength) return true
                val candidate = base.substring(0, insertIndex) + '\n' + base.substring(insertIndex)
                if (candidate.length > maxChars) return true
                saveUndoSnapshot(current, state)
                value.set(candidate)
                state.redoStack.clear()
                state.caretIndex = insertIndex + 1
                state.preferredColumn = 0
                state.selectionAnchor = null
                return true
            }
            GLFW.GLFW_KEY_BACKSPACE -> {
                val (selectionStart, selectionEnd) = selectedRange(state)
                if (selectionStart != null && selectionEnd != null) {
                    saveUndoSnapshot(current, state)
                    value.set(current.removeRange(selectionStart, selectionEnd))
                    state.redoStack.clear()
                    state.caretIndex = selectionStart
                } else {
                    if (state.caretIndex <= 0 || current.isEmpty()) return true
                    saveUndoSnapshot(current, state)
                    value.set(current.removeRange(state.caretIndex - 1, state.caretIndex))
                    state.redoStack.clear()
                    state.caretIndex--
                }
                state.preferredColumn = caretColumn(value.get(), state.caretIndex)
                state.selectionAnchor = null
                return true
            }
            GLFW.GLFW_KEY_DELETE -> {
                val (selectionStart, selectionEnd) = selectedRange(state)
                if (selectionStart != null && selectionEnd != null) {
                    saveUndoSnapshot(current, state)
                    value.set(current.removeRange(selectionStart, selectionEnd))
                    state.redoStack.clear()
                    state.caretIndex = selectionStart
                } else {
                    if (state.caretIndex >= current.length) return true
                    saveUndoSnapshot(current, state)
                    value.set(current.removeRange(state.caretIndex, state.caretIndex + 1))
                    state.redoStack.clear()
                }
                state.preferredColumn = caretColumn(value.get(), state.caretIndex)
                state.selectionAnchor = null
                return true
            }
            GLFW.GLFW_KEY_LEFT -> {
                moveCaretHorizontal(current, state, -1, isShiftDown)
                return true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                moveCaretHorizontal(current, state, 1, isShiftDown)
                return true
            }
            GLFW.GLFW_KEY_HOME -> {
                val next = lineStartIndex(current, caretLine(current, state.caretIndex))
                updateSelectionAfterMove(state, next, isShiftDown)
                state.preferredColumn = 0
                return true
            }
            GLFW.GLFW_KEY_END -> {
                val line = caretLine(current, state.caretIndex)
                val next = lineEndIndex(current, line)
                updateSelectionAfterMove(state, next, isShiftDown)
                state.preferredColumn = caretColumn(current, state.caretIndex)
                return true
            }
            GLFW.GLFW_KEY_UP -> {
                moveVertical(current, state, -1, isShiftDown)
                return true
            }
            GLFW.GLFW_KEY_DOWN -> {
                moveVertical(current, state, 1, isShiftDown)
                return true
            }
        }
        return false
    }

    override fun mouseScrolled(scrollY: Double, isCtrlDown: Boolean, isShiftDown: Boolean): Boolean {
        val state = runtimeState() ?: return false
        return if (isCtrlDown) {
            val binding = zoomBinding ?: return false
            binding.set((binding.value() + scrollY.toFloat() * 0.05f).coerceIn(0.75f, 2f))
            true
        } else if (isShiftDown) {
            val runtime = latestRuntime ?: return false
            val textRect = latestTextRect ?: return false
            val zoom = (zoomBinding?.value() ?: 1f).coerceIn(0.75f, 2f)
            val maxScroll = (linesMaxWidth(runtime, value.get().split('\n'), zoom) - textRect.width).coerceAtLeast(0)
            state.horizontalScrollOffset = (state.horizontalScrollOffset - (scrollY * 16.0).toInt()).coerceIn(0, maxScroll)
            true
        } else {
            val zoom = (zoomBinding?.value() ?: 1f).coerceIn(0.75f, 2f)
            val lineHeight = (latestRuntime?.font?.lineHeight?.times(zoom)?.toInt() ?: 12).coerceAtLeast(latestRuntime?.font?.lineHeight ?: 9)
            val lineCount = value.get().split('\n').size
            val contentHeight = lineCount * lineHeight
            val availableHeight = latestTextRect?.height ?: return false
            val maxScroll = (contentHeight - availableHeight).coerceAtLeast(0)
            state.scrollOffset = (state.scrollOffset - (scrollY * 16.0).toInt()).coerceIn(0, maxScroll)
            true
        }
    }

    private fun moveVertical(text: String, state: UiRuntime.TextInputState, direction: Int, isShiftDown: Boolean) {
        val line = caretLine(text, state.caretIndex)
        val column = state.preferredColumn.takeIf { it > 0 } ?: caretColumn(text, state.caretIndex)
        val targetLine = (line + direction).coerceIn(0, lineCount(text) - 1)
        val targetStart = lineStartIndex(text, targetLine)
        val targetEnd = lineEndIndex(text, targetLine)
        val next = (targetStart + column).coerceAtMost(targetEnd)
        updateSelectionAfterMove(state, next, isShiftDown)
        state.preferredColumn = column
    }

    private fun moveCaretHorizontal(text: String, state: UiRuntime.TextInputState, direction: Int, isShiftDown: Boolean) {
        val next = (state.caretIndex + direction).coerceIn(0, text.length)
        updateSelectionAfterMove(state, next, isShiftDown)
        state.preferredColumn = caretColumn(text, state.caretIndex)
    }

    private fun updateSelectionAfterMove(state: UiRuntime.TextInputState, nextCaret: Int, isShiftDown: Boolean) {
        val oldCaret = state.caretIndex
        if (isShiftDown) {
            if (state.selectionAnchor == null) state.selectionAnchor = oldCaret
        } else {
            state.selectionAnchor = null
        }
        state.caretIndex = nextCaret
    }

    private fun caretLine(text: String, caretIndex: Int): Int = text.take(caretIndex).count { it == '\n' }

    private fun caretColumn(text: String, caretIndex: Int): Int {
        val start = lineStartIndex(text, caretLine(text, caretIndex))
        return caretIndex - start
    }

    private fun lineCount(text: String): Int = text.count { it == '\n' } + 1

    private fun lineStartIndex(text: String, line: Int): Int {
        if (line <= 0) return 0
        var currentLine = 0
        text.forEachIndexed { index, char ->
            if (char == '\n') {
                currentLine++
                if (currentLine == line) return index + 1
            }
        }
        return text.length
    }

    private fun lineEndIndex(text: String, line: Int): Int {
        val start = lineStartIndex(text, line)
        val end = text.indexOf('\n', start)
        return if (end == -1) text.length else end
    }

    private fun shouldRenderCaret(): Boolean = (System.currentTimeMillis() / 500L) % 2L == 0L

    private fun caretIndexForPoint(runtime: UiRuntime, text: String, textRect: UiRect, mouseX: Int, mouseY: Int, scrollOffset: Int, horizontalScrollOffset: Int): Int {
        val lines = text.split('\n')
        val zoom = (zoomBinding?.value() ?: 1f).coerceIn(0.75f, 2f)
        val lineHeight = (runtime.font.lineHeight * zoom).toInt().coerceAtLeast(runtime.font.lineHeight)
        val line = ((mouseY - textRect.y + scrollOffset) / lineHeight).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val targetLine = lines.getOrElse(line) { "" }
        val localX = (mouseX - textRect.x + horizontalScrollOffset).coerceAtLeast(0)
        var column = targetLine.length
        for (index in targetLine.indices) {
            val previousWidth = visualWidth(runtime, targetLine.take(index), zoom)
            val nextWidth = visualWidth(runtime, targetLine.take(index + 1), zoom)
            if (localX < previousWidth + ((nextWidth - previousWidth) / 2)) {
                column = index
                break
            }
        }
        return lineStartIndex(text, line) + column
    }

    private fun selectedRange(state: UiRuntime.TextInputState): Pair<Int?, Int?> {
        val anchor = state.selectionAnchor ?: return null to null
        if (anchor == state.caretIndex) return null to null
        return minOf(anchor, state.caretIndex) to maxOf(anchor, state.caretIndex)
    }

    private fun selectedText(text: String, state: UiRuntime.TextInputState): String? {
        val (start, end) = selectedRange(state)
        return if (start != null && end != null) text.substring(start, end) else null
    }

    private fun drawSelection(
        runtime: UiRuntime,
        text: String,
        lines: List<String>,
        textRect: UiRect,
        lineHeight: Int,
        zoom: Float,
        state: UiRuntime.TextInputState
    ) {
        val (selectionStart, selectionEnd) = selectedRange(state)
        if (selectionStart == null || selectionEnd == null) return
        lines.indices.forEach { lineIndex ->
            val lineStart = lineStartIndex(text, lineIndex)
            val lineEnd = lineEndIndex(text, lineIndex)
            val highlightStart = max(selectionStart, lineStart)
            val highlightEnd = minOf(selectionEnd, lineEnd)
            if (highlightStart > lineEnd || highlightEnd < lineStart) return@forEach
            val startColumn = (highlightStart - lineStart).coerceAtLeast(0)
            val endColumnExclusive = when {
                highlightEnd > lineEnd -> lines[lineIndex].length
                else -> (highlightEnd - lineStart).coerceAtLeast(startColumn)
            }
            if (highlightStart == highlightEnd && highlightEnd <= lineEnd) return@forEach
            val startX = textRect.x + visualWidth(runtime, lines[lineIndex].take(startColumn), zoom)
            val endX = textRect.x + visualWidth(runtime, lines[lineIndex].take(endColumnExclusive), zoom)
            val y = textRect.y + lineIndex * lineHeight - state.scrollOffset
            runtime.guiGraphics.fill(
                startX - state.horizontalScrollOffset,
                y,
                maxOf(startX - state.horizontalScrollOffset + 1, endX - state.horizontalScrollOffset),
                y + lineHeight,
                ARGB.color(90, 120, 170, 255)
            )
        }
    }

    private fun linesMaxWidth(runtime: UiRuntime, lines: List<String>, zoom: Float): Int =
        lines.maxOfOrNull { visualWidth(runtime, it, zoom) } ?: 0

    private fun visualWidth(runtime: UiRuntime, text: String, zoom: Float): Int =
        (text.sumOf { runtime.font.width(displayText(it)) } * zoom).roundToInt()

    private fun displayText(char: Char): String =
        if (char == '\t') TAB_DISPLAY else char.toString()

    private fun snapshot(text: String, state: UiRuntime.TextInputState): UiRuntime.TextSnapshot =
        UiRuntime.TextSnapshot(
            text = text,
            caretIndex = state.caretIndex,
            preferredColumn = state.preferredColumn,
            scrollOffset = state.scrollOffset,
            horizontalScrollOffset = state.horizontalScrollOffset,
            selectionAnchor = state.selectionAnchor
        )

    private fun saveUndoSnapshot(text: String, state: UiRuntime.TextInputState) {
        val snapshot = snapshot(text, state)
        val last = state.undoStack.lastOrNull()
        if (last == snapshot) return
        state.undoStack += snapshot
        if (state.undoStack.size > 100) {
            state.undoStack.removeAt(0)
        }
    }

    private fun applySnapshot(state: UiRuntime.TextInputState, snapshot: UiRuntime.TextSnapshot) {
        value.set(snapshot.text)
        state.caretIndex = snapshot.caretIndex.coerceIn(0, snapshot.text.length)
        state.preferredColumn = snapshot.preferredColumn
        state.scrollOffset = snapshot.scrollOffset
        state.horizontalScrollOffset = snapshot.horizontalScrollOffset
        state.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, snapshot.text.length)
    }

    private fun writeClipboard(text: String) {
        runCatching {
            Minecraft.getInstance().keyboardHandler.clipboard = text
        }
    }

    private fun readClipboard(): String =
        runCatching {
            Minecraft.getInstance().keyboardHandler.clipboard
        }.getOrNull().orEmpty()

    private fun drawHighlightedLine(runtime: UiRuntime, line: String, x: Int, y: Int, zoom: Float) {
        val colors = IntArray(line.length) { CommonColors.WHITE }
        highlights.forEach { rule ->
            rule.pattern.findAll(line).forEach { match ->
                for (index in match.range) {
                    if (index in colors.indices) colors[index] = rule.color
                }
            }
        }
        if (line.isEmpty()) return

        runtime.guiGraphics.pose().pushMatrix()
        runtime.guiGraphics.pose().translate(x.toFloat(), y.toFloat())
        runtime.guiGraphics.pose().scale(zoom, zoom)

        var segmentStart = 0
        var currentX = 0
        while (segmentStart < line.length) {
            val color = colors[segmentStart]
            var segmentEnd = segmentStart + 1
            while (segmentEnd < line.length && colors[segmentEnd] == color) segmentEnd++
            val segment = buildString {
                for (index in segmentStart until segmentEnd) {
                    append(displayText(line[index]))
                }
            }
            runtime.guiGraphics.drawString(runtime.font, segment, currentX, 0, color)
            currentX += runtime.font.width(segment)
            segmentStart = segmentEnd
        }

        runtime.guiGraphics.pose().popMatrix()
    }

    private fun runtimeState(): UiRuntime.TextInputState? = latestRuntime?.textInputState(key, this)

    companion object {
        private const val TAB_SIZE = 4
        private val TAB_DISPLAY: String = " ".repeat(TAB_SIZE)
        private var latestRuntime: UiRuntime? = null
        private var latestTextRect: UiRect? = null
        private var latestBounds: UiRect? = null
    }
}

/** Integer slider with drag interaction and optional label formatter. */
class UiSlider(
    private val value: KMutableProperty0<Int>,
    private val range: IntRange,
    override val modifier: UiModifier = UiModifier.None,
    private val label: ((Int) -> Component)? = null,
    private val tooltip: UiTooltip? = null
) : UiNode {
    init {
        require(!range.isEmpty()) { "Slider range must not be empty" }
    }

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val trackWidth = 120
        val trackHeight = 20
        val labelHeight = if (label == null) 0 else runtime.font.lineHeight + 4
        return UiSize(
            modifier.resolveWidth(trackWidth, maxWidth),
            modifier.resolveHeight(trackHeight + labelHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugPaddedRect(bounds, modifier.padding, "Slider")
        val inner = bounds.shrink(modifier.padding)
        val currentValue = value.get().coerceIn(range.first, range.last)
        if (currentValue != value.get()) value.set(currentValue)

        val labelHeight = if (label == null) 0 else runtime.font.lineHeight + 4
        val trackRect = UiRect(
            x = inner.x,
            y = inner.y + labelHeight,
            width = inner.width.coerceAtLeast(24),
            height = (inner.height - labelHeight).coerceAtLeast(12)
        )
        val trackCenterY = trackRect.y + trackRect.height / 2
        val handleWidth = 8
        val usableWidth = (trackRect.width - handleWidth).coerceAtLeast(1)
        val ratio = if (range.first == range.last) 0f else
            (currentValue - range.first).toFloat() / (range.last - range.first).toFloat()
        val handleX = trackRect.x + (usableWidth * ratio).toInt()
        val handleRect = UiRect(handleX, trackRect.y, handleWidth, trackRect.height)

        label?.invoke(currentValue)?.let { text ->
            runtime.guiGraphics.drawString(runtime.font, text, inner.x, inner.y, CommonColors.WHITE)
        }

        runtime.guiGraphics.fill(
            trackRect.x,
            trackCenterY - 1,
            trackRect.right,
            trackCenterY + 1,
            ARGB.color(180, 160, 160, 160)
        )
        runtime.guiGraphics.fill(
            trackRect.x,
            trackCenterY - 1,
            handleRect.x + handleRect.width / 2,
            trackCenterY + 1,
            ARGB.color(220, 255, 220, 96)
        )

        val hovered = handleRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble()) ||
            trackRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        runtime.setTooltip(trackRect, tooltip)
        runtime.guiGraphics.fill(
            handleRect.x,
            handleRect.y,
            handleRect.right,
            handleRect.bottom,
            if (hovered) ARGB.color(220, 255, 240, 160) else ARGB.color(200, 220, 220, 220)
        )

        fun applySliderValue(mouseX: Double) {
            val clamped = (mouseX - trackRect.x.toDouble()).coerceIn(0.0, usableWidth.toDouble())
            val rawRatio = clamped / usableWidth.toDouble()
            val nextValue = (range.first + ((range.last - range.first) * rawRatio).toInt()).coerceIn(range.first, range.last)
            value.set(nextValue)
        }

        runtime.addDragRegion(
            rect = trackRect,
            onDrag = { mouseX, _ -> applySliderValue(mouseX) }
        )
    }
}

class UiProgressBar(
    private val progress: Float,
    override val modifier: UiModifier = UiModifier.None,
    private val label: ((Float) -> Component)? = null,
    private val tooltip: UiTooltip? = null
) : UiNode {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val barWidth = 120
        val barHeight = 14
        val labelHeight = if (label == null) 0 else runtime.font.lineHeight + 4
        return UiSize(
            modifier.resolveWidth(barWidth, maxWidth),
            modifier.resolveHeight(barHeight + labelHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Progress)
        runtime.debugPaddedRect(bounds, modifier.padding, "Progress")
        val clampedProgress = progress.coerceIn(0f, 1f)
        val inner = bounds.shrink(modifier.padding)
        val labelHeight = if (label == null) 0 else runtime.font.lineHeight + 4
        val barRect = UiRect(
            x = inner.x,
            y = inner.y + labelHeight,
            width = inner.width.coerceAtLeast(12),
            height = (inner.height - labelHeight).coerceAtLeast(8)
        )
        val hovered = barRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val borderColor = if (hovered) Theme.hoverBorder else Theme.border
        val fillWidth = ((barRect.width - 2).coerceAtLeast(0) * clampedProgress).toInt()

        label?.invoke(clampedProgress)?.let { text ->
            runtime.guiGraphics.drawString(runtime.font, text, inner.x, inner.y, CommonColors.WHITE)
        }

        runtime.setTooltip(barRect, tooltip)
        runtime.guiGraphics.fill(barRect.x, barRect.y, barRect.right, barRect.bottom, Theme.progressTrack)
        runtime.guiGraphics.fill(barRect.x, barRect.y, barRect.right, barRect.y + 1, borderColor)
        runtime.guiGraphics.fill(barRect.x, barRect.bottom - 1, barRect.right, barRect.bottom, borderColor)
        runtime.guiGraphics.fill(barRect.x, barRect.y, barRect.x + 1, barRect.bottom, borderColor)
        runtime.guiGraphics.fill(barRect.right - 1, barRect.y, barRect.right, barRect.bottom, borderColor)

        if (fillWidth > 0) {
            runtime.guiGraphics.fill(
                barRect.x + 1,
                barRect.y + 1,
                barRect.x + 1 + fillWidth,
                barRect.bottom - 1,
                if (clampedProgress >= 1f) Theme.progressFillComplete else Theme.progressFill
            )
        }
    }
}

/** Two-pane horizontal splitter controlled by an integer range, typically percent-based. */
class UiSplitRow(
    private val value: KMutableProperty0<Int>,
    private val range: IntRange,
    override val modifier: UiModifier = UiModifier.None,
    private val gap: Int = 8,
    private val dividerWidth: Int = 2,
    private val minLeftWidth: Int = 64,
    private val minRightWidth: Int = 64,
    private val tooltip: UiTooltip? = null,
    private val left: UiNode,
    private val right: UiNode
) : UiNode {
    private val interactionKey: String = "split-row:${System.identityHashCode(this)}"

    init {
        require(!range.isEmpty()) { "SplitRow range must not be empty" }
    }

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        val leftSize = left.measureForRow(runtime, inner.width, inner.height)
        val rightSize = right.measureForRow(runtime, inner.width, inner.height)
        val contentWidth = max(leftSize.width, minLeftWidth) + gap + max(rightSize.width, minRightWidth)
        val contentHeight = max(leftSize.height, rightSize.height)
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Split, "SplitRow")
        runtime.debugPaddedRect(bounds, modifier.padding, "SplitRow")
        val inner = bounds.shrink(modifier.padding)
        val currentValue = value.get().coerceIn(range.first, range.last)
        if (currentValue != value.get()) value.set(currentValue)

        val totalGap = gap.coerceAtLeast(dividerWidth)
        val availableWidth = (inner.width - totalGap).coerceAtLeast(0)
        var safeMinLeft = minLeftWidth.coerceAtLeast(0)
        var safeMinRight = minRightWidth.coerceAtLeast(0)
        val minSum = safeMinLeft + safeMinRight
        if (minSum > availableWidth && minSum > 0) {
            val scale = availableWidth.toFloat() / minSum.toFloat()
            safeMinLeft = (safeMinLeft * scale).toInt().coerceAtLeast(0)
            safeMinRight = (availableWidth - safeMinLeft).coerceAtLeast(0)
        }
        val percentMinLeft = ((availableWidth * (range.first.coerceIn(0, 100) / 100f)).toInt()).coerceAtLeast(0)
        val percentMaxLeft = ((availableWidth * (range.last.coerceIn(0, 100) / 100f)).toInt()).coerceAtMost(availableWidth)
        val clampMaxLeft = minOf((availableWidth - safeMinRight).coerceAtLeast(0), percentMaxLeft)
        val clampMinLeft = maxOf(safeMinLeft.coerceAtMost(availableWidth), percentMinLeft).coerceAtMost(clampMaxLeft)
        val ratio = currentValue.coerceIn(0, 100) / 100f
        val leftWidth = (availableWidth * ratio).toInt().coerceIn(clampMinLeft, clampMaxLeft)
        val rightWidth = (availableWidth - leftWidth).coerceAtLeast(0)

        val leftRect = UiRect(inner.x, inner.y, leftWidth, inner.height)
        val dividerAreaRect = UiRect(inner.x + leftWidth, inner.y, totalGap, inner.height)
        val dividerRect = UiRect(
            dividerAreaRect.x + (dividerAreaRect.width - dividerWidth) / 2,
            dividerAreaRect.y,
            dividerWidth,
            dividerAreaRect.height
        )
        val rightRect = UiRect(dividerAreaRect.right, inner.y, rightWidth, inner.height)
        runtime.debugRect(dividerAreaRect, UiDebugLayer.Gap, "SplitGap")

        left.render(runtime, leftRect)
        right.render(runtime, rightRect)

        val hovered = dividerAreaRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val active = runtime.isPressed(interactionKey)
        runtime.setTooltip(dividerAreaRect, tooltip)
        val lineHeight = (dividerRect.height / 5).coerceIn(24, 64)
        val lineY = dividerRect.y + (dividerRect.height - lineHeight) / 2
        if (hovered || active) {
            runtime.guiGraphics.fill(
                dividerAreaRect.x,
                lineY - 8,
                dividerAreaRect.right,
                lineY + lineHeight + 8,
                Theme.splitDividerGlow
            )
        }
        runtime.guiGraphics.fill(
            dividerRect.x,
            lineY,
            dividerRect.right,
            lineY + lineHeight,
            if (hovered || active) Theme.splitDividerHover else Theme.splitDivider
        )

        fun applySplit(mouseX: Double) {
            val rawLeft = (mouseX - inner.x.toDouble() - totalGap / 2.0).toInt()
            val clampedLeft = rawLeft.coerceIn(clampMinLeft, clampMaxLeft)
            val rawRatio = if (availableWidth == 0) 0.0 else clampedLeft.toDouble() / availableWidth.toDouble()
            val next = (rawRatio * 100.0).toInt().coerceIn(range.first, range.last)
            value.set(next)
        }

        runtime.addDragRegion(
            rect = dividerAreaRect,
            onStart = { _, _ -> runtime.setPressed(interactionKey, true) },
            onDrag = { mouseX, _ -> applySplit(mouseX) },
            onEnd = { _, _ -> runtime.setPressed(interactionKey, false) }
        )
    }
}

class UiSplitColumn(
    private val value: KMutableProperty0<Int>,
    private val range: IntRange,
    override val modifier: UiModifier = UiModifier.None,
    private val gap: Int = 8,
    private val dividerHeight: Int = 2,
    private val minTopHeight: Int = 64,
    private val minBottomHeight: Int = 64,
    private val tooltip: UiTooltip? = null,
    private val top: UiNode,
    private val bottom: UiNode
) : UiNode {
    private val interactionKey: String = "split-column:${System.identityHashCode(this)}"

    init {
        require(!range.isEmpty()) { "SplitColumn range must not be empty" }
    }

    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        val topSize = top.measureForColumn(runtime, inner.width, inner.height)
        val bottomSize = bottom.measureForColumn(runtime, inner.width, inner.height)
        val contentWidth = max(topSize.width, bottomSize.width)
        val contentHeight = max(topSize.height, minTopHeight) + gap + max(bottomSize.height, minBottomHeight)
        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun render(runtime: UiRuntime, bounds: UiRect) {
        runtime.debugRect(bounds, UiDebugLayer.Split, "SplitColumn")
        runtime.debugPaddedRect(bounds, modifier.padding, "SplitColumn")
        val inner = bounds.shrink(modifier.padding)
        val currentValue = value.get().coerceIn(range.first, range.last)
        if (currentValue != value.get()) value.set(currentValue)

        val totalGap = gap.coerceAtLeast(dividerHeight)
        val availableHeight = (inner.height - totalGap).coerceAtLeast(0)
        var safeMinTop = minTopHeight.coerceAtLeast(0)
        var safeMinBottom = minBottomHeight.coerceAtLeast(0)
        val minSum = safeMinTop + safeMinBottom
        if (minSum > availableHeight && minSum > 0) {
            val scale = availableHeight.toFloat() / minSum.toFloat()
            safeMinTop = (safeMinTop * scale).toInt().coerceAtLeast(0)
            safeMinBottom = (availableHeight - safeMinTop).coerceAtLeast(0)
        }
        val percentMinTop = ((availableHeight * (range.first.coerceIn(0, 100) / 100f)).toInt()).coerceAtLeast(0)
        val percentMaxTop = ((availableHeight * (range.last.coerceIn(0, 100) / 100f)).toInt()).coerceAtMost(availableHeight)
        val clampMaxTop = minOf((availableHeight - safeMinBottom).coerceAtLeast(0), percentMaxTop)
        val clampMinTop = maxOf(safeMinTop.coerceAtMost(availableHeight), percentMinTop).coerceAtMost(clampMaxTop)
        val ratio = currentValue.coerceIn(0, 100) / 100f
        val topHeight = (availableHeight * ratio).toInt().coerceIn(clampMinTop, clampMaxTop)
        val bottomHeight = (availableHeight - topHeight).coerceAtLeast(0)

        val topRect = UiRect(inner.x, inner.y, inner.width, topHeight)
        val dividerAreaRect = UiRect(inner.x, inner.y + topHeight, inner.width, totalGap)
        val dividerRect = UiRect(
            dividerAreaRect.x,
            dividerAreaRect.y + (dividerAreaRect.height - dividerHeight) / 2,
            dividerAreaRect.width,
            dividerHeight
        )
        val bottomRect = UiRect(inner.x, dividerAreaRect.bottom, inner.width, bottomHeight)
        runtime.debugRect(dividerAreaRect, UiDebugLayer.Gap, "SplitGap")

        top.render(runtime, topRect)
        bottom.render(runtime, bottomRect)

        val hovered = dividerAreaRect.contains(runtime.mouseX.toDouble(), runtime.mouseY.toDouble())
        val active = runtime.isPressed(interactionKey)
        runtime.setTooltip(dividerAreaRect, tooltip)
        val lineWidth = (dividerRect.width / 5).coerceIn(24, 64)
        val lineX = dividerRect.x + (dividerRect.width - lineWidth) / 2
        if (hovered || active) {
            runtime.guiGraphics.fill(
                lineX - 8,
                dividerAreaRect.y,
                lineX + lineWidth + 8,
                dividerAreaRect.bottom,
                Theme.splitDividerGlow
            )
        }
        runtime.guiGraphics.fill(
            lineX,
            dividerRect.y,
            lineX + lineWidth,
            dividerRect.bottom,
            if (hovered || active) Theme.splitDividerHover else Theme.splitDivider
        )

        fun applySplit(mouseY: Double) {
            val rawTop = (mouseY - inner.y.toDouble() - totalGap / 2.0).toInt()
            val clampedTop = rawTop.coerceIn(clampMinTop, clampMaxTop)
            val rawRatio = if (availableHeight == 0) 0.0 else clampedTop.toDouble() / availableHeight.toDouble()
            val next = (rawRatio * 100.0).toInt().coerceIn(range.first, range.last)
            value.set(next)
        }

        runtime.addDragRegion(
            rect = dividerAreaRect,
            onStart = { _, _ -> runtime.setPressed(interactionKey, true) },
            onDrag = { _, mouseY -> applySplit(mouseY) },
            onEnd = { _, _ -> runtime.setPressed(interactionKey, false) }
        )
    }
}
