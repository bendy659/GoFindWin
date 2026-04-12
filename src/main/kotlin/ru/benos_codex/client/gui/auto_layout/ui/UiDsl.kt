package ru.benos_codex.client.gui.auto_layout.ui

import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import ru.benos_codex.client.gui.auto_layout.Theme
import kotlin.jvm.JvmName
import kotlin.reflect.KMutableProperty0

@DslMarker
annotation class UiDsl

/** Grid-specific builder that places content into explicit row/column cells. */
@UiDsl
class UiGridBuilder {
    private val children: MutableList<UiGridChild> = mutableListOf()

    /**
     * Adds a subtree into the given grid cell.
     *
     * @param row Zero-based row index.
     * @param column Zero-based column index.
     * @param block Content rendered inside the cell.
     */
    fun cell(
        row: Int,
        column: Int,
        block: UiBuilder.() -> Unit
    ) {
        children.add(
            UiGridChild(
                row = row,
                column = column,
                node = UiBox(
                    UiModifier.None,
                    children = UiBuilder().apply(block).build()
                )
            )
        )
    }

    fun build(): List<UiGridChild> = children.toList()
}

/**
 * Creates a standard text tooltip node.
 *
 * @param text Tooltip text. Styled components are preserved.
 */
fun tooltip(text: Component): UiTooltip =
    UiTooltip(
        UiBox(
            modifier = UiModifier.None.padding(6),
            backgroundColor = Theme.tooltipBackground,
            outline = UiOutline(Theme.tooltipBorder, 1),
            children = listOf(UiLabel(text = text, wrap = true, modifier = UiModifier.None.wrapWidth()))
        )
    )

/**
 * Creates a custom tooltip from arbitrary UI content.
 *
 * @param block Tooltip body built with the regular UI DSL.
 */
fun tooltip(block: UiBuilder.() -> Unit): UiTooltip =
    UiTooltip(
        UiBox(
            modifier = UiModifier.None.padding(6),
            backgroundColor = Theme.tooltipBackground,
            outline = UiOutline(Theme.tooltipBorder, 1),
            children = listOf(
                UiColumn(
                    modifier = UiModifier.None.fillWidth(),
                    gap = 4,
                    children = UiBuilder().apply(block).build()
                )
            )
        )
    )

/** Primary DSL entrypoint used to build widget trees. */
@UiDsl
class UiBuilder {
    private val children: MutableList<UiNode> = mutableListOf()

    /**
     * Adds a vertical layout container.
     *
     * @param modifier Container sizing and padding rules.
     * @param gap Space between children in pixels.
     * @param block Nested content.
     */
    fun column(
        modifier: UiModifier = UiModifier.None,
        gap: Int = 0,
        block: UiBuilder.() -> Unit
    ) {
        children.add(UiColumn(
            modifier,
            gap,
            UiBuilder().apply(block).build()
        ))
    }

    /**
     * Adds a horizontal layout container.
     *
     * @param modifier Container sizing and padding rules.
     * @param gap Space between children in pixels.
     * @param block Nested content.
     */
    fun row(
        modifier: UiModifier = UiModifier.None,
        gap: Int = 0,
        block: UiBuilder.() -> Unit
    ) {
        children.add(UiRow(
            modifier,
            gap,
            UiBuilder().apply(block).build()
        ))
    }

    /**
     * Adds a weighted grid container.
     *
     * @param rows Total row count.
     * @param columns Total column count.
     * @param modifier Container sizing and padding rules.
     * @param horizontalGap Horizontal spacing between cells in pixels.
     * @param verticalGap Vertical spacing between cells in pixels.
     * @param columnWeights Relative width weights for fill tracks.
     * @param rowWeights Relative height weights for fill tracks.
     * @param block Grid cell definitions.
     */
    fun grid(
        rows: Int,
        columns: Int,
        modifier: UiModifier = UiModifier.None,
        horizontalGap: Int = 0,
        verticalGap: Int = 0,
        columnWeights: List<Float> = List(columns) { 1f },
        rowWeights: List<Float> = List(rows) { 1f },
        block: UiGridBuilder.() -> Unit
    ) {
        children.add(
            UiGrid(
                rows = rows,
                columns = columns,
                modifier = modifier,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                columnWeights = columnWeights,
                rowWeights = rowWeights,
                children = UiGridBuilder().apply(block).build()
            )
        )
    }

    /**
     * Adds a scroll area around nested content.
     *
     * @param key Stable state key used to persist scroll offset between frames.
     * @param modifier Viewport sizing and padding rules.
     * @param scrollBarWidth Width of the scrollbar thumb/track area in pixels.
     * @param scrollGap Gap between content and scrollbar in pixels.
     * @param block Scrollable content.
     */
    fun scrollArea(
        key: String,
        modifier: UiModifier = UiModifier.None,
        scrollBarWidth: Int = 4,
        scrollGap: Int = 4,
        block: UiBuilder.() -> Unit
    ) {
        children.add(UiScrollArea(
            scrollKey = key,
            modifier = modifier,
            child = UiBox(
                UiModifier.None,
                children = UiBuilder().apply(block).build()
            ),
            scrollBarWidth = scrollBarWidth,
            scrollGap = scrollGap
        ))
    }

    /**
     * Adds a split view with a draggable divider.
     *
     * [value] is interpreted on the scale defined by [range]. In typical usage this is a percentage
     * value, for example `25..75` or `10..90`.
     *
     * @param value Mutable split position.
     * @param range Allowed value range for [value].
     * @param modifier Container sizing and padding rules.
     * @param gap Space between left content, divider and right content.
     * @param dividerWidth Visual divider width in pixels.
     * @param minLeftWidth Minimum width of the left pane in pixels.
     * @param minRightWidth Minimum width of the right pane in pixels.
     * @param tooltip Optional tooltip attached to the divider region.
     * @param left Left pane content.
     * @param right Right pane content.
     */
    fun splitRow(
        value: KMutableProperty0<Int>,
        range: IntRange = 0..100,
        modifier: UiModifier = UiModifier.None,
        gap: Int = 8,
        dividerWidth: Int = 2,
        minLeftWidth: Int = 64,
        minRightWidth: Int = 64,
        tooltip: UiTooltip? = null,
        left: UiBuilder.() -> Unit,
        right: UiBuilder.() -> Unit
    ) {
        children.add(UiSplitRow(
            value = value,
            range = range,
            modifier = modifier,
            gap = gap,
            dividerWidth = dividerWidth,
            minLeftWidth = minLeftWidth,
            minRightWidth = minRightWidth,
            tooltip = tooltip,
            left = UiBox(
                UiModifier.None,
                children = UiBuilder().apply(left).build()
            ),
            right = UiBox(
                UiModifier.None,
                children = UiBuilder().apply(right).build()
            )
        ))
    }

    /**
     * Adds a vertical split view with a draggable divider.
     *
     * [value] is interpreted on the scale defined by [range]. In typical usage this is a percentage
     * value, for example `25..75` or `10..90`.
     *
     * @param value Mutable split position.
     * @param range Allowed value range for [value].
     * @param modifier Container sizing and padding rules.
     * @param gap Space between top content, divider and bottom content.
     * @param dividerHeight Visual divider height in pixels.
     * @param minTopHeight Minimum height of the top pane in pixels.
     * @param minBottomHeight Minimum height of the bottom pane in pixels.
     * @param tooltip Optional tooltip attached to the divider region.
     * @param top Top pane content.
     * @param bottom Bottom pane content.
     */
    fun splitColumn(
        value: KMutableProperty0<Int>,
        range: IntRange = 0..100,
        modifier: UiModifier = UiModifier.None,
        gap: Int = 8,
        dividerHeight: Int = 2,
        minTopHeight: Int = 64,
        minBottomHeight: Int = 64,
        tooltip: UiTooltip? = null,
        top: UiBuilder.() -> Unit,
        bottom: UiBuilder.() -> Unit
    ) {
        children.add(UiSplitColumn(
            value = value,
            range = range,
            modifier = modifier,
            gap = gap,
            dividerHeight = dividerHeight,
            minTopHeight = minTopHeight,
            minBottomHeight = minBottomHeight,
            tooltip = tooltip,
            top = UiBox(
                UiModifier.None,
                children = UiBuilder().apply(top).build()
            ),
            bottom = UiBox(
                UiModifier.None,
                children = UiBuilder().apply(bottom).build()
            )
        ))
    }

    /**
     * Adds a generic box container.
     *
     * @param modifier Container sizing and padding rules.
     * @param backgroundColor Optional fill color.
     * @param outline Optional border.
     * @param block Nested content.
     */
    fun box(
        modifier: UiModifier = UiModifier.None,
        backgroundColor: Int? = null,
        outline: UiOutline? = null,
        block: UiBuilder.() -> Unit = {}
    ) {
        children.add(UiBox(
            modifier,
            backgroundColor,
            outline,
            UiBuilder().apply(block).build()
        ))
    }

    /**
     * Adds a collapsible group section.
     *
     * @param title Header text.
     * @param expanded Mutable expanded/collapsed state.
     * @param modifier Container sizing and padding rules.
     * @param contentIndent Left indent applied to body content.
     * @param contentTopGap Gap between header and body content.
     * @param tooltip Optional tooltip for the group header.
     * @param block Group body content.
     */
    fun group(
        title: Component,
        expanded: KMutableProperty0<Boolean>,
        modifier: UiModifier = UiModifier.None,
        contentIndent: Int = 12,
        contentTopGap: Int = 6,
        tooltip: UiTooltip? = null,
        block: UiBuilder.() -> Unit
    ) {
        children.add(
            UiGroup(
                title = title,
                expanded = expanded,
                modifier = modifier,
                contentIndent = contentIndent,
                contentTopGap = contentTopGap,
                tooltip = tooltip,
                child = UiBox(
                    UiModifier.None,
                    children = UiBuilder().apply(block).build()
                )
            )
        )
    }

    /**
     * Adds a text label with an explicit color.
     *
     * @param text Label text. Styled components are preserved.
     * @param color Text color override.
     * @param align Horizontal alignment inside the allocated bounds.
     * @param wrap Enables multi-line wrapping.
     * @param maxLines Maximum number of displayed lines when wrapping is enabled.
     * @param modifier Widget sizing and padding rules.
     * @param tooltip Optional hover tooltip.
     */
    fun label(
        text: Component,
        color: Int,
        align: UiTextAlign = UiTextAlign.Start,
        wrap: Boolean = false,
        maxLines: Int = Int.MAX_VALUE,
        modifier: UiModifier = UiModifier.None,
        tooltip: UiTooltip? = null
    ) {
        children.add(UiLabel(
            text,
            color,
            align,
            wrap,
            maxLines,
            modifier,
            tooltip
        ))
    }

    /**
     * Adds a text label using the widget default color.
     *
     * @param text Label text. Styled components are preserved.
     * @param align Horizontal alignment inside the allocated bounds.
     * @param wrap Enables multi-line wrapping.
     * @param maxLines Maximum number of displayed lines when wrapping is enabled.
     * @param modifier Widget sizing and padding rules.
     * @param tooltip Optional hover tooltip.
     */
    fun label(
        text: Component,
        align: UiTextAlign = UiTextAlign.Start,
        wrap: Boolean = false,
        maxLines: Int = Int.MAX_VALUE,
        modifier: UiModifier = UiModifier.None,
        tooltip: UiTooltip? = null
    ) {
        children.add(UiLabel(
            text,
            align = align,
            wrap = wrap,
            maxLines = maxLines,
            modifier = modifier,
            tooltip = tooltip
        ))
    }

    /**
     * Adds a clickable button.
     *
     * @param text Button caption.
     * @param modifier Widget sizing and padding rules.
     * @param tooltip Optional hover tooltip.
     * @param onClick Invoked when the mouse is released inside the button.
     */
    fun button(
        text: Component,
        modifier: UiModifier = UiModifier.None,
        tooltip: UiTooltip? = null,
        onClick: () -> Unit
    ) {
        children.add(UiButton(text, onClick, modifier, tooltip = tooltip))
    }

    /**
     * Adds a single-line text field.
     *
     * @param value Mutable text value.
     * @param modifier Widget sizing and padding rules.
     * @param placeholder Placeholder displayed while the value is empty.
     * @param maxLength Maximum stored text length.
     * @param tooltip Optional hover tooltip.
     * @param onClick Optional callback invoked when the field is clicked.
     */
    fun textField(
        value: KMutableProperty0<String>,
        modifier: UiModifier = UiModifier.None,
        placeholder: Component? = null,
        maxLength: Int = 256,
        tooltip: UiTooltip? = null,
        onClick: (() -> Unit)? = null
    ) {
        children.add(UiTextField(
            value,
            placeholder,
            modifier,
            maxLength,
            tooltip = tooltip,
            onClick = onClick
        ))
    }

    /**
     * Adds an integer-only text field.
     *
     * The mouse wheel changes the value while the field is hovered. Each wheel step changes the value
     * by [step].
     *
     * @param value Mutable integer value.
     * @param range Allowed value range.
     * @param step Increment/decrement step used by the mouse wheel and arrow keys.
     * @param modifier Widget sizing and padding rules.
     * @param placeholder Placeholder displayed while the field has no editable text.
     * @param tooltip Optional hover tooltip.
     */
    fun intField(
        value: KMutableProperty0<Int>,
        range: IntRange,
        step: Int = 1,
        modifier: UiModifier = UiModifier.None,
        placeholder: Component? = null,
        tooltip: UiTooltip? = null
    ) {
        children.add(
            UiIntField(
                value = value,
                range = range,
                step = step,
                placeholder = placeholder,
                modifier = modifier,
                tooltip = tooltip
            )
        )
    }

    /**
     * Adds a float-only text field.
     *
     * The mouse wheel changes the value while the field is hovered. Each wheel step changes the value
     * by [step].
     *
     * @param value Mutable float value.
     * @param range Allowed value range.
     * @param step Increment/decrement step used by the mouse wheel and arrow keys.
     * @param modifier Widget sizing and padding rules.
     * @param placeholder Placeholder displayed while the field has no editable text.
     * @param tooltip Optional hover tooltip.
     */
    fun floatField(
        value: KMutableProperty0<Float>,
        range: ClosedFloatingPointRange<Float>,
        step: Float = 0.1f,
        modifier: UiModifier = UiModifier.None,
        placeholder: Component? = null,
        tooltip: UiTooltip? = null
    ) {
        children.add(
            UiFloatField(
                value = value,
                range = range,
                step = step,
                placeholder = placeholder,
                modifier = modifier,
                tooltip = tooltip
            )
        )
    }

    /**
     * Adds a multi-line text field with an explicit zoom binding.
     *
     * @param value Mutable text value.
     * @param zoom External zoom binding used by `Ctrl + Wheel`.
     * @param modifier Widget sizing and padding rules.
     * @param placeholder Placeholder displayed while the value is empty.
     * @param maxLength Maximum stored text length.
     * @param showCharCount Enables the footer character counter.
     * @param maxChars Soft character limit displayed by the counter.
     * @param highlights Regex-based highlight rules.
     * @param tooltip Optional hover tooltip.
     */
    fun multilineTextField(
        value: KMutableProperty0<String>,
        zoom: UiZoomBinding,
        modifier: UiModifier = UiModifier.None,
        placeholder: Component? = null,
        maxLength: Int = 4096,
        showCharCount: Boolean = false,
        maxChars: Int = Int.MAX_VALUE,
        highlights: List<UiTextHighlightRule> = emptyList(),
        tooltip: UiTooltip? = null
    ) {
        children.add(UiMultilineTextField(
            value,
            placeholder,
            modifier,
            maxLength,
            showCharCount,
            maxChars,
            zoomBinding = zoom,
            highlights = highlights,
            tooltip = tooltip
        ))
    }

    /**
     * Adds a multi-line text field with internal zoom state.
     *
     * @param value Mutable text value.
     * @param modifier Widget sizing and padding rules.
     * @param placeholder Placeholder displayed while the value is empty.
     * @param maxLength Maximum stored text length.
     * @param showCharCount Enables the footer character counter.
     * @param maxChars Soft character limit displayed by the counter.
     * @param highlights Regex-based highlight rules.
     * @param tooltip Optional hover tooltip.
     */
    fun multilineTextField(
        value: KMutableProperty0<String>,
        modifier: UiModifier = UiModifier.None,
        placeholder: Component? = null,
        maxLength: Int = 4096,
        showCharCount: Boolean = false,
        maxChars: Int = Int.MAX_VALUE,
        highlights: List<UiTextHighlightRule> = emptyList(),
        tooltip: UiTooltip? = null
    ) {
        children.add(UiMultilineTextField(
            value,
            placeholder,
            modifier,
            maxLength,
            showCharCount,
            maxChars,
            highlights = highlights,
            tooltip = tooltip
        ))
    }

    /**
     * Adds an integer slider.
     *
     * @param value Mutable integer value.
     * @param range Allowed value range.
     * @param modifier Widget sizing and padding rules.
     * @param label Optional formatter for the current value.
     * @param tooltip Optional hover tooltip.
     */
    fun slider(
        value: KMutableProperty0<Int>,
        range: IntRange,
        modifier: UiModifier = UiModifier.None,
        label: ((Int) -> Component)? = null,
        tooltip: UiTooltip? = null
    ) {
        children.add(UiSlider(value, range, modifier, label, tooltip))
    }

    /**
     * Adds a read-only progress bar.
     *
     * @param progress Current progress in the `0f..1f` range. Values outside the range are clamped.
     * @param modifier Widget sizing and padding rules.
     * @param label Optional formatter displayed above the bar.
     * @param tooltip Optional hover tooltip.
     */
    fun progressBar(
        progress: Float,
        modifier: UiModifier = UiModifier.None,
        label: ((Float) -> Component)? = null,
        tooltip: UiTooltip? = null
    ) {
        children.add(UiProgressBar(progress, modifier, label, tooltip))
    }

    /**
     * Adds a dropdown select field.
     *
     * @param value Mutable selected value.
     * @param options Available options shown in the popup list.
     * @param modifier Widget sizing and padding rules.
     * @param key Stable toggle-state key used to keep the popup open between frames.
     * @param tooltip Optional hover tooltip.
     */
    fun <T> dropdown(
        value: KMutableProperty0<T>,
        options: List<UiDropdownOption<T>>,
        modifier: UiModifier = UiModifier.None,
        key: String = "dropdown:${value.name}",
        tooltip: UiTooltip? = null
    ) {
        children.add(UiDropdown(value = value, options = options, modifier = modifier, key = key, tooltip = tooltip))
    }

    /**
     * Adds a checkbox.
     *
     * @param value Mutable boolean state.
     * @param text Optional label shown to the right of the checkbox.
     * @param modifier Widget sizing and padding rules.
     * @param tooltip Optional hover tooltip.
     */
    fun checkbox(
        value: KMutableProperty0<Boolean>,
        text: Component? = null,
        modifier: UiModifier = UiModifier.None,
        tooltip: UiTooltip? = null
    ) {
        children.add(UiCheckbox(value = value, text = text, modifier = modifier, tooltip = tooltip))
    }

    /**
     * Adds an item-stack widget.
     *
     * @param stack Item stack to render.
     * @param mode Flat inventory-like rendering or spinning 3D preview.
     * @param transform Optional 3D transform callback. When provided, the widget renders in 3D using
     * this transform instead of the default mode behavior.
     * @param transformKey Stable key used to persist previous transform between frames.
     * @param modifier Widget sizing and padding rules.
     * @param tooltip Optional hover tooltip.
     */
    fun itemStack(
        stack: ItemStack,
        mode: UiItemStackMode = UiItemStackMode.Flat2D,
        transform: ((UiItemStackFrame) -> UiItemStackTransform)? = null,
        transformKey: String? = null,
        modifier: UiModifier = UiModifier.None,
        tooltip: UiTooltip? = null
    ) {
        children.add(
            UiItemStackView(
                stack = stack,
                mode = mode,
                transform = transform,
                transformKey = transformKey,
                modifier = modifier,
                tooltip = tooltip
            )
        )
    }

    /**
     * Adds an item-stack widget with a transform callback that also receives the previous transform.
     *
     * @param stack Item stack to render.
     * @param mode Flat inventory-like rendering or spinning 3D preview.
     * @param transformKey Stable key used to persist previous transform between frames.
     * @param transform Callback receiving the current frame context and previous transform.
     * @param modifier Widget sizing and padding rules.
     * @param tooltip Optional hover tooltip.
     */
    fun itemStack(
        stack: ItemStack,
        mode: UiItemStackMode = UiItemStackMode.Flat2D,
        transformKey: String,
        transform: (UiItemStackFrame, UiItemStackTransform) -> UiItemStackTransform,
        modifier: UiModifier = UiModifier.None,
        tooltip: UiTooltip? = null
    ) {
        children.add(
            UiItemStackView(
                stack = stack,
                mode = mode,
                transformWithPrevious = transform,
                transformKey = transformKey,
                modifier = modifier,
                tooltip = tooltip
            )
        )
    }

    /**
     * Adds a custom render widget exposing [GuiGraphics], [DeltaTracker] and layout bounds.
     *
     * Use this as an escape hatch for drawing custom content that still participates in auto-layout.
     *
     * @param modifier Widget sizing and padding rules.
     * @param tooltip Optional hover tooltip.
     * @param block Render callback receiving raw drawing APIs and resolved bounds.
     */
    fun render(
        modifier: UiModifier = UiModifier.None,
        tooltip: UiTooltip? = null,
        block: (guiGraphics: GuiGraphics, deltaTracker: DeltaTracker, bounds: UiRect) -> Unit
    ) {
        children.add(UiRenderNode(modifier = modifier, tooltip = tooltip, renderer = block))
    }

    /**
     * Adds a horizontal separator line.
     *
     * Best used inside [column].
     *
     * @param modifier Widget sizing rules. Usually `fillWidth()` plus a small fixed height.
     * @param color Line color.
     * @param thickness Visual thickness in pixels.
     * @param tooltip Optional hover tooltip.
     */
    fun separator(
        modifier: UiModifier = UiModifier.None,
        color: Int = Theme.separator,
        thickness: Int = 1,
        tooltip: UiTooltip? = null
    ) {
        children.add(
            UiSeparator(
                orientation = UiSeparatorOrientation.Horizontal,
                color = color,
                thickness = thickness,
                modifier = modifier,
                tooltip = tooltip
            )
        )
    }

    /**
     * Adds a vertical separator line.
     *
     * Best used inside [row].
     *
     * @param modifier Widget sizing rules. Usually `fillHeight()` plus a small fixed width.
     * @param color Line color.
     * @param thickness Visual thickness in pixels.
     * @param tooltip Optional hover tooltip.
     */
    fun vSeparator(
        modifier: UiModifier = UiModifier.None,
        color: Int = Theme.separator,
        thickness: Int = 1,
        tooltip: UiTooltip? = null
    ) {
        children.add(
            UiSeparator(
                orientation = UiSeparatorOrientation.Vertical,
                color = color,
                thickness = thickness,
                modifier = modifier,
                tooltip = tooltip
            )
        )
    }

    /** Adds an empty spacing node. */
    fun spacer(modifier: UiModifier = UiModifier.None) {
        children.add(UiSpacer(modifier))
    }

    /** Adds an already-constructed node to the current builder. */
    fun node(node: UiNode) {
        children += node
    }

    fun build(): List<UiNode> = children.toList()
}

/**
 * Builds a root UI node from the DSL block.
 *
 * The returned node is a root [UiBox] containing the DSL children.
 */
fun ui(block: UiBuilder.() -> Unit): UiNode =
    UiBox(
        UiModifier.None,
        children = UiBuilder().apply(block).build()
    )

/**
 * Creates a float-backed zoom binding.
 *
 * @param property Mutable property storing the current zoom value.
 */
@JvmName("zoomFloat")
fun zoom(property: KMutableProperty0<Float>): UiZoomBinding =
    UiZoomBinding(
        getter = { property.get() },
        setter = { property.set(it) })

/**
 * Creates an int-backed zoom binding.
 *
 * @param property Mutable property storing the current zoom value.
 */
@JvmName("zoomInt")
fun zoom(property: KMutableProperty0<Int>): UiZoomBinding =
    UiZoomBinding(
        getter = { property.get().toFloat() },
        setter = { property.set(it.toInt()) })
