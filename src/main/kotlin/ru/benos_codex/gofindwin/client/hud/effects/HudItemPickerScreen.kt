package ru.benos_codex.gofindwin.client.hud.effects

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class HudItemPickerScreen(
    private val parent: Screen,
    private val initialQuery: String,
    private val onPick: (String) -> Unit
) : Screen(Component.literal("Select item")) {
    companion object {
        private const val COLUMNS = 8
        private const val VISIBLE_ROWS = 5
        private const val SLOT_SIZE = 20
        private const val SLOT_GAP = 6
        private const val GRID_TOP_OFFSET = 44
        private const val SCROLLBAR_WIDTH = 6
    }

    private data class ItemEntry(
        val id: String,
        val displayName: String,
        val stack: ItemStack
    )

    private val allItems = BuiltInRegistries.ITEM.keySet()
        .map { id ->
            val stack = ItemStack(BuiltInRegistries.ITEM.getValue(id))
            ItemEntry(
                id = id.toString(),
                displayName = stack.hoverName.string,
                stack = stack
            )
        }
        .sortedBy { it.id }

    private var filteredItems = allItems
    private var firstVisibleRow = 0
    private var hoveredItem: ItemEntry? = null
    private var draggingScrollbar = false

    private lateinit var searchField: EditBox
    private lateinit var closeButton: Button
    private val slotButtons = mutableListOf<Button>()

    override fun init() {
        clearWidgets()
        slotButtons.clear()

        val left = panelLeft()
        val top = panelTop()

        searchField = EditBox(font, left, top, 208, 20, Component.literal("Search item"))
        searchField.setValue(initialQuery)
        searchField.setResponder { updateFilter(it) }
        addRenderableWidget(searchField)

        closeButton = addRenderableWidget(
            Button.builder(Component.literal("Close")) {
                onClose()
            }.bounds(left + 220, top + panelHeight() + 18, 84, 20).build()
        )

        repeat(COLUMNS * VISIBLE_ROWS) { index ->
            val column = index % COLUMNS
            val row = index / COLUMNS
            val button = addRenderableWidget(
                Button.builder(Component.empty()) {
                    visibleEntryAt(index)?.let {
                        onPick(it.id)
                        minecraft.setScreen(parent)
                    }
                }.bounds(
                    left + column * (SLOT_SIZE + SLOT_GAP),
                    top + GRID_TOP_OFFSET + row * (SLOT_SIZE + SLOT_GAP),
                    SLOT_SIZE,
                    SLOT_SIZE
                ).build()
            )
            slotButtons += button
        }

        updateFilter(initialQuery)
        setInitialFocus(searchField)
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xB0000000.toInt())
        hoveredItem = null
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val left = panelLeft()
        val top = panelTop()
        val right = left + panelWidth()
        val bottom = top + panelHeight()
        guiGraphics.fill(left - 10, top - 18, right + 10, bottom + 12, 0x66141A22)

        guiGraphics.drawCenteredString(font, title, width / 2, top - 16, 0xFFFFFFFF.toInt())

        slotButtons.forEachIndexed { index, button ->
            val entry = visibleEntryAt(index) ?: return@forEachIndexed
            guiGraphics.renderItem(entry.stack, button.x + 2, button.y + 2)
            if (button.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                hoveredItem = entry
            }
        }

        renderScrollbar(guiGraphics)
        renderFooter(guiGraphics, left, top)
        hoveredItem?.let { renderTooltip(guiGraphics, mouseX, mouseY, it) }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (totalRows() <= VISIBLE_ROWS) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val previousRow = firstVisibleRow
        firstVisibleRow = (firstVisibleRow - scrollY.toInt().coerceIn(-1, 1)).coerceIn(0, maxFirstVisibleRow())
        if (firstVisibleRow != previousRow) {
            refreshButtons()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (mouseButtonEvent.button() == 0 && isOverScrollbarThumb(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseButtonEvent.y())
            refreshButtons()
            return true
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
        if (draggingScrollbar && mouseButtonEvent.button() == 0) {
            updateScrollFromMouse(mouseButtonEvent.y())
            refreshButtons()
            return true
        }
        return super.mouseDragged(mouseButtonEvent, d, e)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (mouseButtonEvent.button() == 0) {
            draggingScrollbar = false
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    private fun updateFilter(query: String) {
        val normalized = query.trim().lowercase()
        filteredItems = if (normalized.isBlank()) {
            allItems
        } else {
            allItems.filter { entry ->
                entry.id.lowercase().contains(normalized) || entry.displayName.lowercase().contains(normalized)
            }
        }
        firstVisibleRow = 0
        refreshButtons()
    }

    private fun refreshButtons() {
        slotButtons.forEachIndexed { index, button ->
            val entry = visibleEntryAt(index)
            button.visible = entry != null
            button.active = entry != null
            button.setMessage(Component.empty())
        }
    }

    private fun renderFooter(guiGraphics: GuiGraphics, left: Int, top: Int) {
        val total = filteredItems.size
        val from = min(total, firstVisibleRow * COLUMNS + 1)
        val to = min(total, (firstVisibleRow + VISIBLE_ROWS) * COLUMNS)
        val text = if (total == 0) "No items" else "$from-$to / $total"
        guiGraphics.drawCenteredString(font, text, width / 2, top + panelHeight() + 6, 0xFF9AA7B4.toInt())
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics) {
        val left = scrollbarLeft()
        val top = scrollbarTop()
        val bottom = scrollbarBottom()
        guiGraphics.fill(left, top, left + SCROLLBAR_WIDTH, bottom, 0x55343C46)

        val thumbTop = scrollbarThumbTop()
        val thumbBottom = thumbTop + scrollbarThumbHeight()
        guiGraphics.fill(left, thumbTop, left + SCROLLBAR_WIDTH, thumbBottom, 0xFFD7DEE6.toInt())
    }

    private fun renderTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, entry: ItemEntry) {
        val lines = listOf(entry.displayName, entry.id)
        val tooltipWidth = lines.maxOf { font.width(it) } + 10
        val tooltipHeight = lines.size * 12 + 6
        var tooltipX = mouseX + 10
        var tooltipY = mouseY + 6

        if (tooltipX + tooltipWidth > width - 8) {
            tooltipX = mouseX - tooltipWidth - 10
        }
        if (tooltipY + tooltipHeight > height - 8) {
            tooltipY = height - tooltipHeight - 8
        }

        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0100010.toInt())
        guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY, 0x905050FF.toInt())
        guiGraphics.fill(tooltipX - 1, tooltipY + tooltipHeight, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, 0x905050FF.toInt())
        guiGraphics.fill(tooltipX - 1, tooltipY, tooltipX, tooltipY + tooltipHeight, 0x905050FF.toInt())
        guiGraphics.fill(tooltipX + tooltipWidth, tooltipY, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight, 0x905050FF.toInt())

        guiGraphics.drawString(font, entry.displayName, tooltipX + 5, tooltipY + 4, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, entry.id, tooltipX + 5, tooltipY + 16, 0xFFB8C3D1.toInt())
    }

    private fun visibleEntryAt(index: Int): ItemEntry? {
        val absoluteIndex = firstVisibleRow * COLUMNS + index
        return filteredItems.getOrNull(absoluteIndex)
    }

    private fun updateScrollFromMouse(mouseY: Double) {
        val totalRows = totalRows()
        if (totalRows <= VISIBLE_ROWS) {
            firstVisibleRow = 0
            return
        }

        val trackTop = scrollbarTop()
        val trackHeight = scrollbarHeight()
        val thumbHeight = scrollbarThumbHeight()
        val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(1)
        val centeredY = (mouseY - trackTop - thumbHeight / 2f).toFloat().coerceIn(0f, thumbTravel.toFloat())
        val progress = centeredY / thumbTravel.toFloat()
        firstVisibleRow = (progress * maxFirstVisibleRow()).toInt().coerceIn(0, maxFirstVisibleRow())
    }

    private fun isOverScrollbarThumb(mouseX: Double, mouseY: Double): Boolean {
        val left = scrollbarLeft()
        val top = scrollbarThumbTop()
        val bottom = top + scrollbarThumbHeight()
        return mouseX >= left && mouseX <= left + SCROLLBAR_WIDTH && mouseY >= top && mouseY <= bottom
    }

    private fun scrollbarLeft(): Int = panelLeft() + COLUMNS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP + 8
    private fun scrollbarTop(): Int = panelTop() + GRID_TOP_OFFSET
    private fun scrollbarBottom(): Int = scrollbarTop() + scrollbarHeight()
    private fun scrollbarHeight(): Int = VISIBLE_ROWS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP

    private fun scrollbarThumbHeight(): Int {
        val totalRows = totalRows().coerceAtLeast(1)
        val raw = scrollbarHeight().toFloat() * (VISIBLE_ROWS.toFloat() / totalRows.toFloat())
        return raw.toInt().coerceIn(18, scrollbarHeight())
    }

    private fun scrollbarThumbTop(): Int {
        if (totalRows() <= VISIBLE_ROWS) return scrollbarTop()
        val travel = scrollbarHeight() - scrollbarThumbHeight()
        val progress = firstVisibleRow.toFloat() / maxFirstVisibleRow().coerceAtLeast(1).toFloat()
        return scrollbarTop() + (travel * progress).toInt()
    }

    private fun totalRows(): Int = ceil(filteredItems.size / COLUMNS.toDouble()).toInt()
    private fun maxFirstVisibleRow(): Int = (totalRows() - VISIBLE_ROWS).coerceAtLeast(0)

    private fun panelLeft(): Int = width / 2 - 116
    private fun panelTop(): Int = height / 2 - 108
    private fun panelWidth(): Int = 8 * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP + 18
    private fun panelHeight(): Int = GRID_TOP_OFFSET + VISIBLE_ROWS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP
}
