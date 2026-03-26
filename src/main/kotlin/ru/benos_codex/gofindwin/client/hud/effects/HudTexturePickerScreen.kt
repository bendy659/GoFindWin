package ru.benos_codex.gofindwin.client.hud.effects

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class HudTexturePickerScreen(
    private val parent: Screen,
    private val initialTextureId: String,
    initialUvX: Int,
    initialUvY: Int,
    initialUvWidth: Int,
    initialUvHeight: Int,
    private val onApply: (String, Int, Int, Int, Int) -> Unit
) : Screen(Component.literal("Select texture")) {
    companion object {
        private const val GRID_COLUMNS = 6
        private const val GRID_ROWS = 10
        private const val TILE_SIZE = 34
        private const val TILE_GAP = 6
        private const val SCROLLBAR_WIDTH = 8
        private const val PREVIEW_LEFT = 320
        private const val PREVIEW_TOP = 68
        private const val PREVIEW_BOTTOM_SAFE = 86
    }

    private data class TextureMeta(
        val displayId: String,
        val resourceId: String,
        val width: Int,
        val height: Int
    )

    private val allTextures: List<TextureMeta> by lazy { loadTextures() }
    private var filteredTextures: List<TextureMeta> = emptyList()
    private var selectedTextureId = initialTextureId
    private var hoveredTexture: TextureMeta? = null
    private var firstVisibleRow = 0

    private var zoom = 1.5f
    private var zoomTarget = 1.5f
    private var panX = 0f
    private var panY = 0f
    private var draggingPan = false
    private var draggingScrollbar = false
    private var draggingSelection = false
    private var lastMouseX = 0.0
    private var lastMouseY = 0.0
    private var selectionStartU = initialUvX
    private var selectionStartV = initialUvY
    private var selectionEndU = initialUvX + initialUvWidth
    private var selectionEndV = initialUvY + initialUvHeight

    private lateinit var searchField: EditBox
    private lateinit var applyButton: Button
    private lateinit var closeButton: Button
    private val textureButtons = mutableListOf<Button>()

    override fun init() {
        clearWidgets()
        textureButtons.clear()

        searchField = EditBox(font, listLeft(), 20, 280, 20, Component.literal("Search texture"))
        searchField.setValue(selectedTextureId)
        searchField.setResponder { updateFilter(it) }
        addRenderableWidget(searchField)

        applyButton = addRenderableWidget(
            Button.builder(Component.literal("Apply")) {
                val selection = normalizedSelection()
                onApply(selectedTextureId, selection[0], selection[1], selection[2], selection[3])
                minecraft.setScreen(parent)
            }.bounds(width - 150, 20, 60, 20).build()
        )
        closeButton = addRenderableWidget(
            Button.builder(Component.literal("Close")) {
                onClose()
            }.bounds(width - 84, 20, 60, 20).build()
        )

        repeat(GRID_COLUMNS * GRID_ROWS) { index ->
            val button = addRenderableWidget(
                Button.builder(Component.empty()) {
                    visibleTextureAt(index)?.let { entry ->
                        selectedTextureId = entry.displayId
                        resetSelection()
                        searchField.setValue(entry.displayId)
                        refreshButtons()
                    }
                }.bounds(0, 0, TILE_SIZE, TILE_SIZE).build()
            )
            textureButtons += button
        }

        updateFilter(initialTextureId)
        setInitialFocus(searchField)
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xCC05070A.toInt())
        hoveredTexture = null
        zoom += (zoomTarget - zoom) * 0.2f
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.drawString(font, "Select texture", listLeft(), 4, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Texture preview", PREVIEW_LEFT, 46, 0xFFFFFFFF.toInt())
        renderTextureGrid(guiGraphics, mouseX, mouseY)
        renderScrollbar(guiGraphics)
        renderPreview(guiGraphics)
        renderFooter(guiGraphics)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isOverPreview(mouseX, mouseY)) {
            val step = if (scrollY > 0) 1.15f else 0.87f
            zoomTarget = (zoomTarget * step).coerceIn(0.5f, 24f)
            return true
        }
        if (isOverGrid(mouseX, mouseY)) {
            val previous = firstVisibleRow
            firstVisibleRow = (firstVisibleRow - scrollY.toInt().coerceIn(-1, 1)).coerceIn(0, maxFirstVisibleRow())
            if (previous != firstVisibleRow) {
                refreshButtons()
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()
        val button = mouseButtonEvent.button()
        lastMouseX = mouseX
        lastMouseY = mouseY

        if (button == 0 && isOverScrollbarThumb(mouseX, mouseY)) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY)
            refreshButtons()
            return true
        }
        if (button == 2 && isOverPreview(mouseX, mouseY)) {
            draggingPan = true
            return true
        }
        if (button == 0 && isOverPreview(mouseX, mouseY)) {
            previewToTexture(mouseX, mouseY)?.let { (u, v) ->
                selectionStartU = u
                selectionStartV = v
                selectionEndU = u + 1
                selectionEndV = v + 1
                draggingSelection = true
                return true
            }
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()
        val button = mouseButtonEvent.button()

        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(mouseY)
            refreshButtons()
            return true
        }
        if (draggingPan && button == 2) {
            panX += (mouseX - lastMouseX).toFloat()
            panY += (mouseY - lastMouseY).toFloat()
            lastMouseX = mouseX
            lastMouseY = mouseY
            return true
        }
        if (draggingSelection && button == 0) {
            previewToTexture(mouseX, mouseY)?.let { (u, v) ->
                selectionEndU = u + 1
                selectionEndV = v + 1
            }
            return true
        }
        return super.mouseDragged(mouseButtonEvent, d, e)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (mouseButtonEvent.button() == 0) {
            draggingScrollbar = false
            draggingSelection = false
        }
        if (mouseButtonEvent.button() == 2) {
            draggingPan = false
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    private fun renderTextureGrid(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val left = listLeft()
        val top = listTop()
        val right = left + listWidth()
        val bottom = top + listHeight()
        guiGraphics.fill(left - 8, top - 8, right + 18, bottom + 8, 0x66141A22)

        textureButtons.forEachIndexed { index, button ->
            val entry = visibleTextureAt(index) ?: return@forEachIndexed
            guiGraphics.fill(button.x, button.y, button.x + TILE_SIZE, button.y + TILE_SIZE, 0xFF2A313C.toInt())

            val thumbScale = min(
                (TILE_SIZE - 6).toFloat() / entry.width.toFloat(),
                (TILE_SIZE - 6).toFloat() / entry.height.toFloat()
            )
            val drawWidth = max(1, (entry.width * thumbScale).toInt())
            val drawHeight = max(1, (entry.height * thumbScale).toInt())
            val drawX = button.x + (TILE_SIZE - drawWidth) / 2
            val drawY = button.y + (TILE_SIZE - drawHeight) / 2
            renderTextureImage(guiGraphics, entry, drawX, drawY, thumbScale)

            val borderColor = when {
                entry.displayId == selectedTextureId -> 0xFFFFD166.toInt()
                button.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> 0xFFD7DEE6.toInt()
                else -> 0xFF4A5562.toInt()
            }
            guiGraphics.fill(button.x, button.y, button.x + TILE_SIZE, button.y + 1, borderColor)
            guiGraphics.fill(button.x, button.y, button.x + 1, button.y + TILE_SIZE, borderColor)
            guiGraphics.fill(button.x + TILE_SIZE - 1, button.y, button.x + TILE_SIZE, button.y + TILE_SIZE, borderColor)
            guiGraphics.fill(button.x, button.y + TILE_SIZE - 1, button.x + TILE_SIZE, button.y + TILE_SIZE, borderColor)

            if (button.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                hoveredTexture = entry
            }
        }
    }

    private fun renderPreview(guiGraphics: GuiGraphics) {
        val previewWidth = width - PREVIEW_LEFT - 28
        val previewHeight = height - PREVIEW_TOP - PREVIEW_BOTTOM_SAFE
        guiGraphics.fill(PREVIEW_LEFT, PREVIEW_TOP, PREVIEW_LEFT + previewWidth, PREVIEW_TOP + previewHeight, 0xFF141A22.toInt())
        drawCheckerboard(guiGraphics, PREVIEW_LEFT, PREVIEW_TOP, previewWidth, previewHeight, 16)

        val meta = selectedTextureMeta() ?: return
        val drawWidth = max(1, (meta.width * zoom).toInt())
        val drawHeight = max(1, (meta.height * zoom).toInt())
        val drawX = (PREVIEW_LEFT + previewWidth / 2f - drawWidth / 2f + panX).toInt()
        val drawY = (PREVIEW_TOP + previewHeight / 2f - drawHeight / 2f + panY).toInt()

        guiGraphics.enableScissor(PREVIEW_LEFT, PREVIEW_TOP, PREVIEW_LEFT + previewWidth, PREVIEW_TOP + previewHeight)
        renderTextureImage(guiGraphics, meta, drawX, drawY, zoom)

        val selection = normalizedSelection()
        val selX = drawX + (selection[0] * zoom).toInt()
        val selY = drawY + (selection[1] * zoom).toInt()
        val selW = max(1, (selection[2] * zoom).toInt())
        val selH = max(1, (selection[3] * zoom).toInt())
        guiGraphics.fill(selX, selY, selX + selW, selY + 1, 0xFFFFD166.toInt())
        guiGraphics.fill(selX, selY, selX + 1, selY + selH, 0xFFFFD166.toInt())
        guiGraphics.fill(selX + selW - 1, selY, selX + selW, selY + selH, 0xFFFFD166.toInt())
        guiGraphics.fill(selX, selY + selH - 1, selX + selW, selY + selH, 0xFFFFD166.toInt())
        guiGraphics.disableScissor()
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics) {
        val left = scrollbarLeft()
        val top = listTop()
        val bottom = top + listHeight()
        guiGraphics.fill(left, top, left + SCROLLBAR_WIDTH, bottom, 0x55343C46)
        val thumbTop = scrollbarThumbTop()
        guiGraphics.fill(left, thumbTop, left + SCROLLBAR_WIDTH, thumbTop + scrollbarThumbHeight(), 0xFFD7DEE6.toInt())
    }

    private fun renderFooter(guiGraphics: GuiGraphics) {
        val total = filteredTextures.size
        val from = min(total, firstVisibleRow * GRID_COLUMNS + 1)
        val to = min(total, (firstVisibleRow + GRID_ROWS) * GRID_COLUMNS)
        val counter = if (total == 0) "No textures" else "$from-$to / $total"
        guiGraphics.drawString(font, counter, listLeft(), listTop() + listHeight() + 12, 0xFF9AA7B4.toInt())

        val meta = hoveredTexture ?: selectedTextureMeta()
        if (meta != null) {
            val infoX = PREVIEW_LEFT
            val infoY = height - 52
            guiGraphics.drawString(font, meta.displayId, infoX, infoY, 0xFFFFFFFF.toInt())
            guiGraphics.drawString(font, "Size ${meta.width}x${meta.height}", infoX, infoY + 12, 0xFFB8C3D1.toInt())
            val selection = normalizedSelection()
            guiGraphics.drawString(font, "UV ${selection[0]}, ${selection[1]} | ${selection[2]}x${selection[3]}", infoX, infoY + 24, 0xFFB8C3D1.toInt())

            hoveredTexture?.let {
                val previewScale = min(96f / it.width.toFloat(), 96f / it.height.toFloat())
                val drawWidth = max(1, (it.width * previewScale).toInt())
                val drawHeight = max(1, (it.height * previewScale).toInt())
                val drawX = width - drawWidth - 24
                val drawY = height - drawHeight - 24
                guiGraphics.fill(drawX - 4, drawY - 4, drawX + drawWidth + 4, drawY + drawHeight + 4, 0xCC141A22.toInt())
                drawCheckerboard(guiGraphics, drawX, drawY, drawWidth, drawHeight, 8)
                renderTextureImage(guiGraphics, it, drawX, drawY, previewScale)
            }
        }
    }

    private fun updateFilter(query: String) {
        val normalized = query.trim().lowercase()
        filteredTextures = if (normalized.isBlank()) {
            allTextures
        } else {
            allTextures.filter { it.displayId.contains(normalized) }
        }
        if (filteredTextures.none { it.displayId == selectedTextureId }) {
            filteredTextures.firstOrNull()?.let {
                selectedTextureId = it.displayId
                resetSelection()
            }
        }
        firstVisibleRow = 0
        refreshButtons()
    }

    private fun refreshButtons() {
        textureButtons.forEachIndexed { index, button ->
            val entry = visibleTextureAt(index)
            button.visible = entry != null
            button.active = entry != null
            val column = index % GRID_COLUMNS
            val row = index / GRID_COLUMNS
            button.setPosition(
                listLeft() + column * (TILE_SIZE + TILE_GAP),
                listTop() + row * (TILE_SIZE + TILE_GAP)
            )
            button.setMessage(Component.empty())
        }
    }

    private fun loadTextures(): List<TextureMeta> {
        val manager = minecraft.resourceManager
        return manager.listResources("textures") { path -> path.path.endsWith(".png") }
            .keys
            .mapNotNull { fileId ->
                val strippedPath = fileId.path.removePrefix("textures/").removeSuffix(".png")
                val displayId = Identifier.fromNamespaceAndPath(fileId.namespace, strippedPath).toString()
                val resource = manager.getResource(fileId).orElse(null) ?: return@mapNotNull null
                resource.open().use { stream ->
                    NativeImage.read(stream).use { image ->
                        TextureMeta(displayId, fileId.toString(), image.width, image.height)
                    }
                }
            }
            .sortedBy { it.displayId }
    }

    private fun selectedTextureMeta(): TextureMeta? =
        allTextures.firstOrNull { it.displayId == selectedTextureId }

    private fun renderTextureImage(guiGraphics: GuiGraphics, meta: TextureMeta, drawX: Int, drawY: Int, scale: Float) {
        val texture = Identifier.tryParse(meta.resourceId) ?: return
        val drawWidth = max(1, (meta.width * scale).toInt())
        val drawHeight = max(1, (meta.height * scale).toInt())
        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            drawX,
            drawY,
            0f,
            0f,
            drawWidth,
            drawHeight,
            meta.width,
            meta.height,
            meta.width,
            meta.height
        )
    }

    private fun visibleTextureAt(index: Int): TextureMeta? =
        filteredTextures.getOrNull(firstVisibleRow * GRID_COLUMNS + index)

    private fun resetSelection() {
        selectedTextureMeta()?.let {
            selectionStartU = 0
            selectionStartV = 0
            selectionEndU = min(16, it.width)
            selectionEndV = min(16, it.height)
            panX = 0f
            panY = 0f
            zoom = 1.5f
            zoomTarget = 1.5f
        }
    }

    private fun normalizedSelection(): IntArray {
        val meta = selectedTextureMeta() ?: return intArrayOf(0, 0, 16, 16)
        val minU = min(selectionStartU, selectionEndU - 1).coerceIn(0, meta.width - 1)
        val minV = min(selectionStartV, selectionEndV - 1).coerceIn(0, meta.height - 1)
        val maxU = max(selectionStartU + 1, selectionEndU).coerceIn(minU + 1, meta.width)
        val maxV = max(selectionStartV + 1, selectionEndV).coerceIn(minV + 1, meta.height)
        return intArrayOf(minU, minV, maxU - minU, maxV - minV)
    }

    private fun previewToTexture(mouseX: Double, mouseY: Double): Pair<Int, Int>? {
        val meta = selectedTextureMeta() ?: return null
        val previewWidth = width - PREVIEW_LEFT - 28
        val previewHeight = height - PREVIEW_TOP - PREVIEW_BOTTOM_SAFE
        val drawWidth = max(1, (meta.width * zoom).toInt())
        val drawHeight = max(1, (meta.height * zoom).toInt())
        val drawX = PREVIEW_LEFT + previewWidth / 2f - drawWidth / 2f + panX
        val drawY = PREVIEW_TOP + previewHeight / 2f - drawHeight / 2f + panY
        val localX = ((mouseX - drawX) / zoom).toInt().coerceIn(0, meta.width - 1)
        val localY = ((mouseY - drawY) / zoom).toInt().coerceIn(0, meta.height - 1)
        return localX to localY
    }

    private fun updateScrollFromMouse(mouseY: Double) {
        val maxRow = maxFirstVisibleRow()
        if (maxRow <= 0) {
            firstVisibleRow = 0
            return
        }
        val trackTop = listTop()
        val thumbTravel = (listHeight() - scrollbarThumbHeight()).coerceAtLeast(1)
        val centeredY = (mouseY - trackTop - scrollbarThumbHeight() / 2f).toFloat().coerceIn(0f, thumbTravel.toFloat())
        val progress = centeredY / thumbTravel.toFloat()
        firstVisibleRow = (progress * maxRow).toInt().coerceIn(0, maxRow)
    }

    private fun totalRows(): Int = ceil(filteredTextures.size / GRID_COLUMNS.toDouble()).toInt()
    private fun maxFirstVisibleRow(): Int = (totalRows() - GRID_ROWS).coerceAtLeast(0)

    private fun scrollbarLeft(): Int = listLeft() + listWidth() + 10

    private fun scrollbarThumbHeight(): Int {
        val totalRows = totalRows().coerceAtLeast(1)
        val raw = listHeight().toFloat() * (GRID_ROWS.toFloat() / totalRows.toFloat())
        return raw.toInt().coerceIn(24, listHeight())
    }

    private fun scrollbarThumbTop(): Int {
        val maxRow = maxFirstVisibleRow()
        if (maxRow <= 0) return listTop()
        val travel = listHeight() - scrollbarThumbHeight()
        val progress = firstVisibleRow.toFloat() / maxRow.toFloat()
        return listTop() + (travel * progress).toInt()
    }

    private fun isOverScrollbarThumb(mouseX: Double, mouseY: Double): Boolean {
        val left = scrollbarLeft()
        val top = scrollbarThumbTop()
        val bottom = top + scrollbarThumbHeight()
        return mouseX >= left && mouseX <= left + SCROLLBAR_WIDTH && mouseY >= top && mouseY <= bottom
    }

    private fun isOverPreview(mouseX: Double, mouseY: Double): Boolean {
        val previewWidth = width - PREVIEW_LEFT - 28
        val previewHeight = height - PREVIEW_TOP - PREVIEW_BOTTOM_SAFE
        return mouseX >= PREVIEW_LEFT &&
            mouseX <= PREVIEW_LEFT + previewWidth &&
            mouseY >= PREVIEW_TOP &&
            mouseY <= PREVIEW_TOP + previewHeight
    }

    private fun isOverGrid(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= listLeft() &&
            mouseX <= listLeft() + listWidth() &&
            mouseY >= listTop() &&
            mouseY <= listTop() + listHeight()
    }

    private fun listLeft(): Int = 22
    private fun listTop(): Int = 58
    private fun listWidth(): Int = GRID_COLUMNS * (TILE_SIZE + TILE_GAP) - TILE_GAP
    private fun listHeight(): Int = GRID_ROWS * (TILE_SIZE + TILE_GAP) - TILE_GAP

    private fun drawCheckerboard(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, cell: Int) {
        val light = 0xFF3A4350.toInt()
        val dark = 0xFF222933.toInt()
        var row = 0
        var dy = 0
        while (dy < height) {
            var col = 0
            var dx = 0
            while (dx < width) {
                val color = if ((row + col) % 2 == 0) light else dark
                guiGraphics.fill(
                    x + dx,
                    y + dy,
                    x + min(dx + cell, width),
                    y + min(dy + cell, height),
                    color
                )
                dx += cell
                col += 1
            }
            dy += cell
            row += 1
        }
    }
}
