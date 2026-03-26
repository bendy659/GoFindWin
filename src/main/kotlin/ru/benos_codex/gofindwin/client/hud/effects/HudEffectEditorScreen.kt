package ru.benos_codex.gofindwin.client.hud.effects

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import ru.benos_codex.gofindwin.client.hud.TimerHudConfigManager
import kotlin.math.max

class HudEffectEditorScreen(private val parent: Screen?) : Screen(Component.literal("GoFindWin Effect Editor")) {
    companion object {
        private const val SCROLL_STEP = 18
        private const val SCREEN_MARGIN_PX = 64
        private const val BODY_INNER_MARGIN_PX = 16
        private const val PANEL_GAP_PX = 16
    }

    private val rebuildFromScratch = true

    private data class Rect(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right get() = left + width
        val bottom get() = top + height
        val centerX get() = left + width / 2
        val centerY get() = top + height / 2
        fun inset(v: Int) = Rect(left + v, top + v, (width - v * 2).coerceAtLeast(1), (height - v * 2).coerceAtLeast(1))
        fun contains(x: Double, y: Double) = x >= left && x <= right && y >= top && y <= bottom
    }

    private class StyledEditField(font: net.minecraft.client.gui.Font, x: Int, y: Int, width: Int, height: Int, hint: Component) :
        EditBox(font, x, y, width, height, hint) {
        init {
            setBordered(true)
            setTextColor(0xFFF7F8FA.toInt())
            setTextColorUneditable(0xFFD4D9E0.toInt())
        }

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val fill = if (isFocused) 0x2612161D else 0x180E1117
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, fill)
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick)
        }
    }

    private class OverlayTextArea(
        private val textFont: net.minecraft.client.gui.Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) : AbstractWidget(x, y, width, height, Component.empty()) {
        private var value = ""
        private var cursor = 0
        private var scrollLine = 0
        private var maxLength = 4096
        private var responder: (String) -> Unit = {}

        fun setResponder(responder: (String) -> Unit) {
            this.responder = responder
        }

        fun setMaxLength(maxLength: Int) {
            this.maxLength = maxLength
        }

        fun value(): String = value

        fun setValue(value: String) {
            this.value = value.take(maxLength)
            cursor = cursor.coerceIn(0, this.value.length)
            ensureCursorVisible()
        }

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) = Unit

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val fill = if (isFocused) 0xD010141B.toInt() else 0xC80D1117.toInt()
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, fill)
            val textLeft = getX() + 10
            val textTop = getY() + 10
            val availableLines = ((height - 20) / textFont.lineHeight).coerceAtLeast(1)
            val lines = value.split('\n')
            val visibleLines = lines.drop(scrollLine).take(availableLines)
            visibleLines.forEachIndexed { index, line ->
                guiGraphics.drawString(textFont, line, textLeft, textTop + index * textFont.lineHeight, 0xFFFFFFFF.toInt())
            }
            if (isFocused) {
                val cursorPos = cursorPosition()
                val visibleLine = cursorPos.first - scrollLine
                if (visibleLine in 0 until availableLines) {
                    val lineText = lines.getOrElse(cursorPos.first) { "" }
                    val beforeCursor = lineText.take(cursorPos.second.coerceAtMost(lineText.length))
                    val cursorX = textLeft + textFont.width(beforeCursor)
                    val cursorY = textTop + visibleLine * textFont.lineHeight
                    guiGraphics.fill(cursorX, cursorY, cursorX + 1, cursorY + textFont.lineHeight, 0xFFFFFFFF.toInt())
                }
            }
            val border = if (isFocused) 0x88FFFFFF.toInt() else 0x44FFFFFF
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, border)
            guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border)
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, border)
            guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border)
        }

        override fun onClick(mouseButtonEvent: MouseButtonEvent, bl: Boolean) {
            setFocused(true)
            cursor = nearestCursor(mouseButtonEvent.x(), mouseButtonEvent.y())
            ensureCursorVisible()
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            if (!isMouseOver(mouseX, mouseY)) return false
            val lines = value.split('\n').size
            val availableLines = ((height - 20) / textFont.lineHeight).coerceAtLeast(1)
            val maxScroll = (lines - availableLines).coerceAtLeast(0)
            scrollLine = (scrollLine - scrollY.toInt()).coerceIn(0, maxScroll)
            return true
        }

        override fun charTyped(characterEvent: CharacterEvent): Boolean {
            if (!isFocused) return false
            val ch = characterEvent.codepoint()
            if (Character.isISOControl(ch)) return false
            insert(String(Character.toChars(ch)))
            return true
        }

        override fun keyPressed(keyEvent: KeyEvent): Boolean {
            if (!isFocused) return false
            when (keyEvent.key()) {
                257, 335 -> {
                    insert("\n")
                    return true
                }
                259 -> {
                    if (cursor > 0) {
                        value = value.removeRange(cursor - 1, cursor)
                        cursor -= 1
                        notifyResponder()
                    }
                    return true
                }
                261 -> {
                    if (cursor < value.length) {
                        value = value.removeRange(cursor, cursor + 1)
                        notifyResponder()
                    }
                    return true
                }
                262 -> {
                    cursor = (cursor + 1).coerceAtMost(value.length)
                    ensureCursorVisible()
                    return true
                }
                263 -> {
                    cursor = (cursor - 1).coerceAtLeast(0)
                    ensureCursorVisible()
                    return true
                }
                264 -> {
                    moveVertical(1)
                    return true
                }
                265 -> {
                    moveVertical(-1)
                    return true
                }
                268 -> {
                    cursor = lineStart(cursor)
                    ensureCursorVisible()
                    return true
                }
                269 -> {
                    cursor = lineEnd(cursor)
                    ensureCursorVisible()
                    return true
                }
            }
            return false
        }

        private fun insert(text: String) {
            if (text.isEmpty() || value.length + text.length > maxLength) return
            value = value.substring(0, cursor) + text + value.substring(cursor)
            cursor += text.length
            notifyResponder()
        }

        private fun notifyResponder() {
            ensureCursorVisible()
            responder(value)
        }

        private fun moveVertical(direction: Int) {
            val (line, column) = cursorPosition()
            val lines = value.split('\n')
            val newLine = (line + direction).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            var index = 0
            for (i in 0 until newLine) index += lines[i].length + 1
            cursor = index + column.coerceAtMost(lines.getOrElse(newLine) { "" }.length)
            ensureCursorVisible()
        }

        private fun lineStart(index: Int): Int {
            val safe = index.coerceIn(0, value.length)
            return value.lastIndexOf('\n', (safe - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        }

        private fun lineEnd(index: Int): Int {
            val safe = index.coerceIn(0, value.length)
            return value.indexOf('\n', safe).let { if (it == -1) value.length else it }
        }

        private fun cursorPosition(): Pair<Int, Int> {
            val before = value.take(cursor.coerceIn(0, value.length))
            val line = before.count { it == '\n' }
            val lineStart = before.lastIndexOf('\n').let { if (it == -1) 0 else it + 1 }
            return line to (before.length - lineStart)
        }

        private fun nearestCursor(mouseX: Double, mouseY: Double): Int {
            val lines = value.split('\n')
            val relativeY = (mouseY.toInt() - getY() - 10).coerceAtLeast(0)
            val targetLine = (relativeY / textFont.lineHeight + scrollLine).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            val lineText = lines.getOrElse(targetLine) { "" }
            val relativeX = (mouseX.toInt() - getX() - 10).coerceAtLeast(0)
            var bestColumn = 0
            for (column in 0..lineText.length) {
                if (textFont.width(lineText.take(column)) > relativeX) break
                bestColumn = column
            }
            var index = 0
            for (i in 0 until targetLine) index += lines[i].length + 1
            return index + bestColumn
        }

        private fun ensureCursorVisible() {
            val line = cursorPosition().first
            val availableLines = ((height - 20) / textFont.lineHeight).coerceAtLeast(1)
            val maxScroll = (value.split('\n').size - availableLines).coerceAtLeast(0)
            when {
                line < scrollLine -> scrollLine = line
                line >= scrollLine + availableLines -> scrollLine = line - availableLines + 1
            }
            scrollLine = scrollLine.coerceIn(0, maxScroll)
        }
    }

    private class StyledActionButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        private var text: Component,
        private val onPressAction: () -> Unit
    ) : AbstractWidget(x, y, width, height, text) {
        override fun onClick(mouseButtonEvent: MouseButtonEvent, bl: Boolean) = onPressAction()

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) = Unit

        fun setLabel(text: Component) {
            this.text = text
            this.message = text
        }

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val hovered = isHovered
            val fill = when {
                !active -> 0x180E1117
                hovered -> 0x32171D26
                else -> 0x240F141B
            }
            val border = when {
                !active -> 0x33FFFFFF
                hovered -> 0xA0FFFFFF.toInt()
                else -> 0x55FFFFFF
            }
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, fill)
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, border)
            guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border)
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, border)
            guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border)
            guiGraphics.drawCenteredString(
                Minecraft.getInstance().font,
                text,
                getX() + width / 2,
                getY() + (height - Minecraft.getInstance().font.lineHeight) / 2,
                if (active) 0xFFFFFFFF.toInt() else 0x88FFFFFF.toInt()
            )
        }
    }

    private data class BlankLayout(
        val props: Rect,
        val preview: Rect,
        val labelX: Int,
        val controlX: Int,
        val controlWidth: Int,
        val rowHeight: Int,
        val rowGap: Int,
        val sectionGap: Int
    ) {
        fun sectionTitleY(top: Int) = top
        fun rowY(afterSectionTop: Int, index: Int) = afterSectionTop + 18 + index * (rowHeight + rowGap)
    }

    private var selectedCategory = FinishEffectCategory.AVERAGE
    private val workingPresets = mutableMapOf<FinishEffectCategory, HudCelebrationConfig>()
    private val numericFields = mutableListOf<NumericField>()
    private var autoPreviewIntervalMs = 1200L
    private var nextAutoPreviewAtMs = 0L
    private var syncingFields = false
    private var scrollOffset = 0
    private var previewPanX = 0f
    private var previewPanY = 0f
    private var fullPreviewMode = false
    private var draggingPreview = false
    private var draggingScrollbar = false
    private var blankScrollOffset = 0
    private var draggingBlankScrollbar = false
    private var draggingBodySplit = false
    private var bodySplitRatio = 0.56f
    private val blankTooltipTargets = mutableMapOf<String, Rect>()
    private val blankOverlayInfoTargets = mutableMapOf<String, Rect>()
    private var blankOverlayOpen = false
    private var blankOverlayTarget: StyledEditField? = null
    private lateinit var blankOverlayField: OverlayTextArea
    private var blankOverlayLeftRect = Rect(0, 0, 0, 0)
    private var blankOverlayRightRect = Rect(0, 0, 0, 0)

    private lateinit var blankCategoryButton: StyledActionButton
    private lateinit var blankSpawnModeButton: StyledActionButton
    private lateinit var blankRenderTypeButton: StyledActionButton
    private lateinit var blankTextureModeButton: StyledActionButton
    private lateinit var blankTextureInterpolateButton: StyledActionButton
    private lateinit var blankCountField: StyledEditField
    private lateinit var blankRateField: StyledEditField
    private lateinit var blankLifetimeField: StyledEditField
    private lateinit var blankColorField: StyledEditField
    private lateinit var blankTextureIdField: StyledEditField
    private lateinit var blankPickTextureButton: StyledActionButton
    private lateinit var blankUvXField: StyledEditField
    private lateinit var blankUvYField: StyledEditField
    private lateinit var blankUvWidthField: StyledEditField
    private lateinit var blankUvHeightField: StyledEditField
    private lateinit var blankFramesXField: StyledEditField
    private lateinit var blankFramesYField: StyledEditField
    private lateinit var blankFramesField: StyledEditField

    private val scaledMargin: Int
        get() {
            val scale = (minecraft?.window?.guiScale?.toDouble() ?: 1.0).coerceAtLeast(1.0)
            return (SCREEN_MARGIN_PX / scale).toInt().coerceAtLeast(16)
        }

    private val scaledBodyInnerMargin: Int
        get() {
            val scale = (minecraft?.window?.guiScale?.toDouble() ?: 1.0).coerceAtLeast(1.0)
            return (BODY_INNER_MARGIN_PX / scale).toInt().coerceAtLeast(6)
        }

    private val scaledPanelGap: Int
        get() {
            val scale = (minecraft?.window?.guiScale?.toDouble() ?: 1.0).coerceAtLeast(1.0)
            return (PANEL_GAP_PX / scale).toInt().coerceAtLeast(4)
        }

    private val topSafe get() = scaledMargin
    private val bottomSafe get() = scaledMargin
    private val outerMargin get() = scaledMargin
    private val panelGap get() = max(14, width / 64)
    private val labelWidth get() = max(96, minOf(150, width / 12))
    private val fieldHeight get() = max(20, font.lineHeight + 8)
    private val scrollbarWidth get() = max(6, width / 320)
    private val scrollbarMargin get() = max(8, width / 120)

    private lateinit var categoryButton: Button
    private lateinit var renderTypeButton: Button
    private lateinit var originButton: Button
    private lateinit var spawnModeButton: Button
    private lateinit var textureModeButton: Button
    private lateinit var texturePlaybackModeButton: Button
    private lateinit var textureInterpolateButton: Button
    private lateinit var saveButton: Button
    private lateinit var closeButton: Button
    private lateinit var fullPreviewButton: Button
    private lateinit var pickItemButton: Button
    private lateinit var pickTextureButton: Button
    private lateinit var useTargetItemButton: Button
    private lateinit var autoPreviewField: EditBox
    private lateinit var textureIdField: EditBox
    private lateinit var textureUvXField: EditBox
    private lateinit var textureUvYField: EditBox
    private lateinit var textureUvWidthField: EditBox
    private lateinit var textureUvHeightField: EditBox
    private lateinit var textureTintColorField: EditBox
    private lateinit var textureFramesXField: EditBox
    private lateinit var textureFramesYField: EditBox
    private lateinit var textureFrameTimeField: EditBox
    private lateinit var textureFrameOrderField: EditBox
    private lateinit var itemIdField: EditBox
    private lateinit var colorField: EditBox
    private lateinit var textContentField: EditBox
    private lateinit var textColorField: EditBox
    private lateinit var textSizeField: EditBox
    private lateinit var textShadowButton: Button
    private lateinit var textShadowColorField: EditBox
    private lateinit var gravityField: EditBox
    private lateinit var offsetXField: EditBox
    private lateinit var offsetYField: EditBox
    private lateinit var initialVelocityXField: EditBox
    private lateinit var initialVelocityYField: EditBox
    private lateinit var positionXField: EditBox
    private lateinit var positionYField: EditBox
    private lateinit var rotationField: EditBox
    private lateinit var sizeField: EditBox
    private lateinit var alphaField: EditBox
    private lateinit var countField: EditBox
    private lateinit var spawnPeriodField: EditBox
    private lateinit var lifetimeField: EditBox

    override fun init() {
        clearWidgets()
        numericFields.clear()
        if (rebuildFromScratch) {
            initBlankMode()
            return
        }
        val s = settingsRect()
        val fx = s.left + 14 + labelWidth
        val fw = s.width - 28 - labelWidth - scrollbarWidth - scrollbarMargin
        val pw = (fw - 8) / 2
        val sx = fx + pw + 8
        val top = contentTop()

        categoryButton = addRenderableWidget(Button.builder(Component.literal(categoryLabel())) { cycleCategory() }.bounds(fx, top + 28, fw, fieldHeight).build())
        renderTypeButton = addRenderableWidget(Button.builder(Component.literal(renderTypeLabel())) { cycleRenderType() }.bounds(fx, top + 56, fw, fieldHeight).build())
        autoPreviewField = makeLongField(fx, top + 112, fw, autoPreviewText()) { raw ->
            if (!syncingFields) parseLongOrNull(raw)?.let { autoPreviewIntervalMs = it.coerceAtLeast(0L); queuePreview() }
        }
        originButton = addRenderableWidget(Button.builder(Component.literal(originLabel())) { cycleOrigin() }.bounds(fx, top + 140, fw, fieldHeight).build())
        spawnModeButton = addRenderableWidget(Button.builder(Component.literal(spawnModeLabel())) { cycleSpawnMode() }.bounds(fx, top + 168, fw, fieldHeight).build())

        textureModeButton = addRenderableWidget(Button.builder(Component.literal(textureModeLabel())) { cycleTextureMode() }.bounds(fx, top + 196, fw, fieldHeight).build())
        textureIdField = makeTextField(fx, top + 224, fw - 44, currentEmitter().textureId, "minecraft:texture or pattern_%d") { mutateEmitter { e -> e.copy(textureId = it) } }
        pickTextureButton = addRenderableWidget(Button.builder(Component.literal("...")) {
            val e = currentEmitter()
            minecraft.setScreen(HudTexturePickerScreen(this, e.textureId, e.textureUvX, e.textureUvY, e.textureUvWidth, e.textureUvHeight) { id, ux, uy, uw, uh ->
                mutateEmitter {
                    it.copy(
                        textureId = id,
                        textureUvX = ux,
                        textureUvXExpression = ux.toString(),
                        textureUvY = uy,
                        textureUvYExpression = uy.toString(),
                        textureUvWidth = uw,
                        textureUvWidthExpression = uw.toString(),
                        textureUvHeight = uh,
                        textureUvHeightExpression = uh.toString()
                    )
                }
                syncFieldsFromPreset(); queuePreview(forceNow = true)
            })
        }.bounds(fx + fw - 38, top + 224, 38, fieldHeight).build())
        textureUvXField = makeOptionalIntField(fx, top + 252, pw, { currentEmitter().textureUvXExpression }) { e, ex, v -> e.copy(textureUvXExpression = ex, textureUvX = v.coerceAtLeast(0)) }
        textureUvYField = makeOptionalIntField(sx, top + 252, pw, { currentEmitter().textureUvYExpression }) { e, ex, v -> e.copy(textureUvYExpression = ex, textureUvY = v.coerceAtLeast(0)) }
        textureUvWidthField = makeOptionalIntField(fx, top + 280, pw, { currentEmitter().textureUvWidthExpression }) { e, ex, v -> e.copy(textureUvWidthExpression = ex, textureUvWidth = v.coerceAtLeast(0)) }
        textureUvHeightField = makeOptionalIntField(sx, top + 280, pw, { currentEmitter().textureUvHeightExpression }) { e, ex, v -> e.copy(textureUvHeightExpression = ex, textureUvHeight = v.coerceAtLeast(0)) }
        textureTintColorField = makeTextField(fx, top + 308, fw, currentEmitter().textureTintColor, "#FFFFFF") { mutateEmitter { e -> e.copy(textureTintColor = if (it.isBlank()) "#FFFFFF" else it) } }
        textureFramesXField = makeOptionalIntField(fx, top + 336, pw, { currentEmitter().textureFramesXExpression }) { e, ex, v -> e.copy(textureFramesXExpression = ex, textureFramesX = v.coerceAtLeast(0)) }
        textureFramesYField = makeOptionalIntField(sx, top + 336, pw, { currentEmitter().textureFramesYExpression }) { e, ex, v -> e.copy(textureFramesYExpression = ex, textureFramesY = v.coerceAtLeast(0)) }
        textureFrameTimeField = makeOptionalFloatField(fx, top + 364, fw, { currentEmitter().textureFrameTimeMsExpression }) { e, ex, v -> e.copy(textureFrameTimeMsExpression = ex, textureFrameTimeMs = v.coerceAtLeast(0f)) }
        texturePlaybackModeButton = addRenderableWidget(Button.builder(Component.literal(texturePlaybackModeLabel())) { cycleTexturePlaybackMode() }.bounds(fx, top + 392, fw, fieldHeight).build())
        textureInterpolateButton = addRenderableWidget(Button.builder(Component.literal(textureInterpolateLabel())) { toggleTextureInterpolate() }.bounds(fx, top + 420, fw, fieldHeight).build())
        textureFrameOrderField = makeTextField(fx, top + 448, fw, currentEmitter().textureFrameOrder, "0,1,2,3 or 3,2,1,0") { mutateEmitter { e -> e.copy(textureFrameOrder = it) } }

        itemIdField = makeTextField(fx, top + 196, fw - 44, currentEmitter().itemId, "minecraft:diamond") { mutateEmitter { e -> e.copy(itemId = it) } }
        pickItemButton = addRenderableWidget(Button.builder(Component.literal("...")) {
            minecraft.setScreen(HudItemPickerScreen(this, itemIdField.value) { itemId ->
                mutateEmitter { it.copy(itemId = itemId, useTargetItem = false) }
                syncFieldsFromPreset(); queuePreview(forceNow = true)
            })
        }.bounds(fx + fw - 38, top + 196, 38, fieldHeight).build())
        useTargetItemButton = addRenderableWidget(Button.builder(Component.literal(useTargetItemLabel())) { toggleUseTargetItem() }.bounds(fx, top + 224, fw, fieldHeight).build())

        colorField = makeTextField(fx, top + 196, fw, currentColor(), "#F6D84E") {
            mutateEmitter { e -> e.copy(colorPalette = listOf(if (it.isBlank()) "#FFFFFF" else it)) }; queuePreview(forceNow = true)
        }
        textContentField = makeTextField(fx, top + 196, fw, currentEmitter().textContent, "Line1\\nLine2") { mutateEmitter { e -> e.copy(textContent = it) } }
        textColorField = makeTextField(fx, top + 224, pw, currentEmitter().textColor, "#FFFFFF") { mutateEmitter { e -> e.copy(textColor = if (it.isBlank()) "#FFFFFF" else it) } }
        textSizeField = makeFloatField(sx, top + 224, pw, { currentEmitter().textScaleExpression }) { e, ex, v -> e.copy(textScaleExpression = ex, textScale = v.coerceAtLeast(0.25f)) }
        textShadowButton = addRenderableWidget(Button.builder(Component.literal(textShadowLabel())) { toggleTextShadow() }.bounds(fx, top + 252, pw, fieldHeight).build())
        textShadowColorField = makeTextField(sx, top + 252, pw, currentEmitter().textShadowColor, "#202020") { mutateEmitter { e -> e.copy(textShadowColor = if (it.isBlank()) "#202020" else it) } }

        offsetXField = makeFloatField(fx, top + 474, pw, { currentEmitter().randomOffsetXExpression }) { e, ex, v -> e.copy(randomOffsetXExpression = ex, randomOffsetX = v.coerceAtLeast(0f)) }
        offsetYField = makeFloatField(sx, top + 474, pw, { currentEmitter().randomOffsetYExpression }) { e, ex, v -> e.copy(randomOffsetYExpression = ex, randomOffsetY = v.coerceAtLeast(0f)) }
        gravityField = makeFloatField(fx, top + 502, fw, { currentEmitter().gravityExpression }) { e, ex, v -> e.copy(gravityExpression = ex, gravity = v.coerceAtLeast(0f)) }
        initialVelocityXField = makeFloatField(fx, top + 558, pw, { currentEmitter().initialVelocityXExpression }) { e, ex, v -> e.copy(initialVelocityXExpression = ex, initialVelocityX = v) }
        initialVelocityYField = makeFloatField(sx, top + 558, pw, { currentEmitter().initialVelocityYExpression }) { e, ex, v -> e.copy(initialVelocityYExpression = ex, initialVelocityY = v) }
        positionXField = makeFloatField(fx, top + 614, pw, { currentEmitter().positionXExpression }) { e, ex, v -> e.copy(positionXExpression = ex, positionX = v) }
        positionYField = makeFloatField(sx, top + 614, pw, { currentEmitter().positionYExpression }) { e, ex, v -> e.copy(positionYExpression = ex, positionY = v) }
        rotationField = makeFloatField(fx, top + 670, fw, { currentEmitter().initialRotationExpression }) { e, ex, v -> e.copy(initialRotationExpression = ex, initialRotation = v) }
        sizeField = makeFloatField(fx, top + 726, pw, { currentEmitter().sizeExpression }) { e, ex, v -> e.copy(sizeExpression = ex, size = v.coerceAtLeast(1f)) }
        alphaField = makeFloatField(sx, top + 726, pw, { currentEmitter().alphaExpression }) { e, ex, v -> e.copy(alphaExpression = ex, alpha = v.coerceIn(0f, 1f)) }
        countField = makeIntField(fx, top + 782, pw, { currentEmitter().countExpression }) { e, ex, v -> e.copy(countExpression = ex, count = v.coerceAtLeast(1)) }
        spawnPeriodField = makeFloatField(sx, top + 782, pw, { currentEmitter().spawnPeriodExpression }) { e, ex, v -> e.copy(spawnPeriodExpression = ex, spawnPeriodMs = v.coerceAtLeast(10f)) }
        lifetimeField = makeIntField(fx, top + 838, fw, { currentEmitter().lifetimeExpression }) { e, ex, v -> e.copy(lifetimeExpression = ex, lifetimeTicks = v.coerceAtLeast(0)) }

        saveButton = addRenderableWidget(Button.builder(Component.literal("Save")) { HudCelebrationConfigManager.savePreset(currentPresetId(), currentPreset()) }.bounds(fx, top + 892, pw, fieldHeight).build())
        closeButton = addRenderableWidget(Button.builder(Component.literal("Close")) { onClose() }.bounds(sx, top + 892, pw, fieldHeight).build())
        fullPreviewButton = addRenderableWidget(Button.builder(Component.literal("Full Preview")) {
            fullPreviewMode = !fullPreviewMode
            fullPreviewButton.setMessage(Component.literal(if (fullPreviewMode) "Back To Editor" else "Full Preview"))
            updateEditorWidgetVisibility()
            layoutWidgets()
            queuePreview(forceNow = true)
        }.bounds(0, 0, 110, fieldHeight).build())

        syncFieldsFromPreset()
        refreshTypeVisibility()
        updateEditorWidgetVisibility()
        layoutWidgets()
        queuePreview(forceNow = true)
    }

    override fun isPauseScreen() = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0x55000000)
        if (rebuildFromScratch) {
            val frame = bodyRect()
            val innerMargin = scaledBodyInnerMargin
            val panels = bodyPanels()
            val divider = splitHandleRect()
            val dividerHovered = divider.contains(mouseX.toDouble(), mouseY.toDouble())
            val layout = blankLayout()

            guiGraphics.fill(frame.left, frame.top, frame.right, frame.bottom, 0x80000000.toInt())
            drawOutline(guiGraphics, frame, 0x55FFFFFF)
            guiGraphics.drawString(
                font,
                "GoFindWin settings > effects",
                frame.left + innerMargin,
                frame.top + innerMargin,
                0xFFFFFFFF.toInt()
            )
            guiGraphics.fill(panels.first.left, panels.first.top, panels.first.right, panels.first.bottom, 0x1AFFFFFF)
            guiGraphics.fill(panels.second.left, panels.second.top, panels.second.right, panels.second.bottom, 0x1AFFFFFF)
            drawOutline(guiGraphics, panels.first, 0x40FFFFFF)
            drawOutline(guiGraphics, panels.second, 0x40FFFFFF)
            guiGraphics.fill(
                divider.left,
                divider.top,
                divider.right,
                divider.bottom,
                when {
                    draggingBodySplit -> 0xCCFFFFFF.toInt()
                    dividerHovered -> 0xAAFFFFFF.toInt()
                    else -> 0x66FFFFFF.toInt()
                }
            )
            val blankContent = blankContentRect(layout)
            guiGraphics.enableScissor(blankContent.left, blankContent.top, blankContent.right, blankContent.bottom)
            super.render(guiGraphics, mouseX, mouseY, partialTick)
            renderBlankProps(guiGraphics, layout, mouseX, mouseY)
            guiGraphics.disableScissor()
            renderBlankScrollbar(guiGraphics, layout)
            if (blankOverlayOpen) renderBlankOverlay(guiGraphics, mouseX, mouseY)
            return
        }
        maybeSpawnAutoPreview()
        if (fullPreviewMode) {
            renderFullPreview(guiGraphics, mouseX, mouseY)
            super.render(guiGraphics, mouseX, mouseY, partialTick)
            return
        }
        renderPanels(guiGraphics)
        renderPreviewClipped(guiGraphics)
        numericFields.forEach { it.refreshPresentation() }
        val s = settingsRect()
        val p = previewRect()
        val lx = s.left + 14
        val top = contentTop() + scrollOffset
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Settings", s.left + 14, topSafe - 18, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Preview", p.left + 14, topSafe - 18, 0xFFFFFFFF.toInt())
        val helpX = p.right - 18
        val helpY = topSafe - 18
        guiGraphics.drawString(font, "?", helpX, helpY, 0xFFFFE28A.toInt())
        guiGraphics.enableScissor(s.left, s.top, s.right, s.bottom)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        drawSection(guiGraphics, "General", lx, top)
        guiGraphics.drawString(font, "Category", lx, top + 34, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Particle kind", lx, top + 62, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Spawn time ms", lx, top + 118, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Origin", lx, top + 146, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Spawn mode", lx, top + 174, 0xFFFFFFFF.toInt())
        drawSection(guiGraphics, "Visual", lx, visualSectionTop(top))
        when (currentEmitter().renderType) {
            HudCelebrationRenderType.TEXTURE -> {
                val textureMode = currentEmitter().textureMode
                val y0 = visualFieldTop(top)
                guiGraphics.drawString(font, "Texture mode", lx, y0 + 6, 0xFFFFFFFF.toInt())
                guiGraphics.drawString(font, "Texture id", lx, y0 + rowStep() + 6, 0xFFFFFFFF.toInt())
                guiGraphics.drawString(font, "UV X / Y", lx, y0 + rowStep() * 2 + 6, 0xFFFFFFFF.toInt())
                guiGraphics.drawString(font, "UV W / H", lx, y0 + rowStep() * 3 + 6, 0xFFFFFFFF.toInt())
                guiGraphics.drawString(font, "Tint color", lx, y0 + rowStep() * 4 + 6, 0xFFFFFFFF.toInt())
                if (textureMode == HudCelebrationTextureMode.SHEET) {
                    guiGraphics.drawString(font, "Frames X / Y", lx, y0 + rowStep() * 5 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Frame time ms", lx, y0 + rowStep() * 6 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Playback mode", lx, y0 + rowStep() * 7 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Interpolate", lx, y0 + rowStep() * 8 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Frame order", lx, y0 + rowStep() * 9 + 6, 0xFFFFFFFF.toInt())
                } else if (textureMode == HudCelebrationTextureMode.SEQUENCE) {
                    guiGraphics.drawString(font, "Frames count", lx, y0 + rowStep() * 5 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Frame time ms", lx, y0 + rowStep() * 6 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Playback mode", lx, y0 + rowStep() * 7 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Interpolate", lx, y0 + rowStep() * 8 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Frame order", lx, y0 + rowStep() * 9 + 6, 0xFFFFFFFF.toInt())
                    guiGraphics.drawString(font, "Use %d in texture id", lx, y0 + rowStep() * 9 + 18, 0xFFC8D0D8.toInt())
                }
            }
            HudCelebrationRenderType.ITEMSTACK -> {
                val y0 = visualFieldTop(top)
                guiGraphics.drawString(font, "Item id", lx, y0 + 6, 0xFFFFFFFF.toInt())
                guiGraphics.drawString(font, "Use target item", lx, y0 + rowStep() + 6, 0xFFFFFFFF.toInt())
            }
            HudCelebrationRenderType.PARTICLE -> guiGraphics.drawString(font, "Color", lx, visualFieldTop(top) + 6, 0xFFFFFFFF.toInt())
            HudCelebrationRenderType.TEXT -> {
                val y0 = visualFieldTop(top)
                guiGraphics.drawString(font, "Text", lx, y0 + 6, 0xFFFFFFFF.toInt())
                guiGraphics.drawString(font, "Text color / Size", lx, y0 + rowStep() + 6, 0xFFFFFFFF.toInt())
                guiGraphics.drawString(font, "Shadow / Shadow color", lx, y0 + rowStep() * 2 + 6, 0xFFFFFFFF.toInt())
            }
            else -> Unit
        }
        val motionTop = motionSectionTop(top)
        val my = motionFieldTop(top)
        drawSection(guiGraphics, "Motion", lx, motionTop)
        guiGraphics.drawString(font, "Offset X / Y", lx, my + 6, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Gravity", lx, my + rowStep() + 6, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Velocity X / Y", lx, my + rowStep() * 2 + 6, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Position X / Y", lx, my + rowStep() * 3 + 6, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Rotation", lx, my + rowStep() * 4 + 6, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Size / Alpha", lx, my + rowStep() * 5 + 6, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Count / Period ms", lx, my + rowStep() * 6 + 6, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Lifetime", lx, my + rowStep() * 7 + 6, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Alpha range: 0..1", lx, my + rowStep() * 8 + 8, 0xFFC8D0D8.toInt())
        guiGraphics.drawString(font, "Vars: rand, rand_x, rand_y, pos_x, pos_y, life_time", lx, my + rowStep() * 9 + 6, 0xFFC8D0D8.toInt())
        guiGraphics.drawString(font, "Fns: sin cos sin_deg cos_deg pow lerp int mod pos", lx, my + rowStep() * 10 + 4, 0xFFC8D0D8.toInt())
        if (mouseX in helpX..(helpX + 6) && mouseY in helpY..(helpY + font.lineHeight)) {
            val tooltipX = mouseX + 12
            val tooltipY = mouseY + 12
            val tooltipWidth = 124
            guiGraphics.fill(tooltipX - 4, tooltipY - 4, tooltipX + tooltipWidth, tooltipY + 32, 0xD010161D.toInt())
            guiGraphics.drawString(font, "Middle drag: move preview", tooltipX, tooltipY, 0xFFFFFFFF.toInt())
            guiGraphics.drawString(font, "Full Preview: hide UI", tooltipX, tooltipY + 10, 0xFFFFFFFF.toInt())
            guiGraphics.drawString(font, "Wheel: scroll settings", tooltipX, tooltipY + 20, 0xFFFFFFFF.toInt())
        }
        guiGraphics.disableScissor()
        renderScrollbar(guiGraphics)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (rebuildFromScratch) {
            if (blankOverlayOpen) {
                val handled = blankOverlayField.mouseClicked(mouseButtonEvent, bl)
                if (!handled && !blankOverlayLeftRect.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) && !blankOverlayRightRect.contains(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                    closeBlankOverlay()
                    return true
                }
                return handled
            }
            if (mouseButtonEvent.button() == 1) {
                hoveredBlankEditableField(mouseButtonEvent.x(), mouseButtonEvent.y())?.let {
                    openBlankOverlay(it)
                    return true
                }
            }
            if (mouseButtonEvent.button() == 0) {
                if (splitHandleRect().contains(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                    draggingBodySplit = true
                    return true
                }
                if (blankScrollbarThumbRect(blankLayout()).contains(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                    draggingBlankScrollbar = true
                    updateBlankScrollFromMouse(mouseButtonEvent.y())
                    layoutBlankWidgets()
                    return true
                }

                val handled = super.mouseClicked(mouseButtonEvent, bl)
                val x = mouseButtonEvent.x()
                val y = mouseButtonEvent.y()
                val clickedInteractive =
                    blankTooltipTargets.values.any { it.contains(x, y) } ||
                        blankCategoryButton.isMouseOver(x, y) ||
                        blankSpawnModeButton.isMouseOver(x, y) ||
                        blankRenderTypeButton.isMouseOver(x, y) ||
                        blankTextureModeButton.isMouseOver(x, y) ||
                        blankTextureInterpolateButton.isMouseOver(x, y) ||
                        blankCountField.isMouseOver(x, y) ||
                        blankRateField.isMouseOver(x, y) ||
                        blankLifetimeField.isMouseOver(x, y) ||
                        blankColorField.isMouseOver(x, y) ||
                        blankTextureIdField.isMouseOver(x, y) ||
                        blankPickTextureButton.isMouseOver(x, y) ||
                        blankUvXField.isMouseOver(x, y) ||
                        blankUvYField.isMouseOver(x, y) ||
                        blankUvWidthField.isMouseOver(x, y) ||
                        blankUvHeightField.isMouseOver(x, y) ||
                        blankFramesXField.isMouseOver(x, y) ||
                        blankFramesYField.isMouseOver(x, y) ||
                        blankFramesField.isMouseOver(x, y)

                if (!clickedInteractive) {
                    listOf(
                        blankCountField, blankRateField, blankLifetimeField, blankColorField, blankTextureIdField,
                        blankUvXField, blankUvYField, blankUvWidthField, blankUvHeightField, blankFramesXField, blankFramesYField, blankFramesField
                    ).forEach { it.setFocused(false) }
                    setFocused(null)
                }
                return handled
            }
            return super.mouseClicked(mouseButtonEvent, bl)
        }
        val x = mouseButtonEvent.x()
        val y = mouseButtonEvent.y()
        val button = mouseButtonEvent.button()
        if (button == 1) {
            (hoveredEditableField(x, y) ?: focusedEditableField())?.let { field ->
                minecraft.setScreen(HudExpandedFieldEditorScreen(this, field.value, "Edit value") { value ->
                    field.setValue(value)
                    field.setFocused(true)
                })
                return true
            }
        }
        if (button == 2 && previewRect().contains(x, y)) {
            draggingPreview = true
            HudCelebrationEffectsClient.clear()
            queuePreview(forceNow = true)
            return true
        }
        if (button == 0 && isOverScrollbarThumb(x, y)) {
            draggingScrollbar = true
            updateScrollFromMouse(y)
            layoutWidgets()
            return true
        }
        val handled = super.mouseClicked(mouseButtonEvent, bl)
        if (button == 0) {
            hoveredEditableField(x, y)?.let { field ->
                field.setFocused(true)
                setFocused(field)
                return true
            }
        }
        val clickedInteractive = expandableFields().any { it.visible && it.isMouseOver(x, y) } ||
            listOf(categoryButton, renderTypeButton, originButton, spawnModeButton, textureModeButton, texturePlaybackModeButton, textureInterpolateButton, pickItemButton, pickTextureButton, useTargetItemButton, textShadowButton, saveButton, closeButton)
                .filter { it.visible }
                .any { it.isMouseOver(x, y) }
        if (!clickedInteractive && !previewRect().contains(x, y)) clearFieldFocus()
        return handled
    }

    private fun hoveredEditableField(mouseX: Double, mouseY: Double): EditBox? {
        if (rebuildFromScratch) return null
        return expandableFields().firstOrNull { it.visible && it.isMouseOver(mouseX, mouseY) }
    }

    private fun focusedEditableField(): EditBox? {
        if (rebuildFromScratch) return null
        return expandableFields().firstOrNull { it.visible && it.isFocused }
    }

    private fun expandableFields(): List<EditBox> {
        if (rebuildFromScratch) return emptyList()
        return listOf(
            autoPreviewField, textureIdField, textureUvXField, textureUvYField, textureUvWidthField, textureUvHeightField, textureTintColorField, textureFramesXField, textureFramesYField, textureFrameTimeField, textureFrameOrderField,
            itemIdField, colorField, textContentField, textColorField, textSizeField, textShadowColorField,
            gravityField, offsetXField, offsetYField, initialVelocityXField, initialVelocityYField,
            positionXField, positionYField, rotationField, sizeField, alphaField, countField, spawnPeriodField, lifetimeField
        )
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
        if (rebuildFromScratch) {
            if (blankOverlayOpen) return blankOverlayField.mouseDragged(mouseButtonEvent, d, e)
            if (draggingBlankScrollbar && mouseButtonEvent.button() == 0) {
                updateBlankScrollFromMouse(mouseButtonEvent.y())
                layoutBlankWidgets()
                return true
            }
            if (draggingBodySplit && mouseButtonEvent.button() == 0) {
                updateBodySplit(mouseButtonEvent.x())
                return true
            }
            return super.mouseDragged(mouseButtonEvent, d, e)
        }
        when (mouseButtonEvent.button()) {
            0 -> if (draggingScrollbar) {
                updateScrollFromMouse(mouseButtonEvent.y())
                layoutWidgets()
                return true
            }
            2 -> if (draggingPreview) {
                previewPanX += d.toFloat()
                previewPanY += e.toFloat()
                HudCelebrationEffectsClient.clear()
                queuePreview(forceNow = true)
                return true
            }
        }
        return super.mouseDragged(mouseButtonEvent, d, e)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (rebuildFromScratch) {
            if (blankOverlayOpen) blankOverlayField.mouseReleased(mouseButtonEvent)
            if (mouseButtonEvent.button() == 0) draggingBodySplit = false
            if (mouseButtonEvent.button() == 0) draggingBlankScrollbar = false
            return super.mouseReleased(mouseButtonEvent)
        }
        if (mouseButtonEvent.button() == 0) draggingScrollbar = false
        if (mouseButtonEvent.button() == 2) draggingPreview = false
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (rebuildFromScratch && blankOverlayOpen) return true
        if (rebuildFromScratch) {
            val layout = blankLayout()
            if (blankContentRect(layout).contains(mouseX, mouseY) || layout.props.contains(mouseX, mouseY)) {
                val prev = blankScrollOffset
                blankScrollOffset = (blankScrollOffset + scrollY.toInt() * SCROLL_STEP).coerceIn(blankMinScrollOffset(layout), 0)
                if (blankScrollOffset != prev) {
                    layoutBlankWidgets()
                    return true
                }
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        val prev = scrollOffset
        scrollOffset = (scrollOffset + scrollY.toInt() * SCROLL_STEP).coerceIn(minScrollOffset(), 0)
        if (scrollOffset != prev) {
            layoutWidgets()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun onClose() {
        HudCelebrationEffectsClient.clear()
        minecraft?.setScreen(parent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (rebuildFromScratch && blankOverlayOpen) {
            if (keyEvent.key() == 256) {
                closeBlankOverlay()
                return true
            }
            if (blankOverlayField.keyPressed(keyEvent)) return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (rebuildFromScratch && blankOverlayOpen) {
            if (blankOverlayField.charTyped(characterEvent)) return true
        }
        return super.charTyped(characterEvent)
    }

    private fun cycleCategory() {
        selectedCategory = when (selectedCategory) {
            FinishEffectCategory.NEW_RECORD -> FinishEffectCategory.AVERAGE
            FinishEffectCategory.AVERAGE -> FinishEffectCategory.WORSE
            FinishEffectCategory.WORSE -> FinishEffectCategory.NEW_RECORD
        }
        categoryButton.setMessage(Component.literal(categoryLabel()))
        syncFieldsFromPreset()
        clearFieldFocus()
        queuePreview(forceNow = true)
    }

    private fun cycleRenderType() {
        val next = when (currentEmitter().renderType) {
            HudCelebrationRenderType.PARTICLE -> HudCelebrationRenderType.TEXTURE
            HudCelebrationRenderType.TEXTURE -> HudCelebrationRenderType.ITEMSTACK
            HudCelebrationRenderType.ITEMSTACK -> HudCelebrationRenderType.TEXT
            HudCelebrationRenderType.TEXT -> HudCelebrationRenderType.PARTICLE
            HudCelebrationRenderType.MOB -> HudCelebrationRenderType.PARTICLE
        }
        mutateEmitter { it.copy(renderType = next) }
        renderTypeButton.setMessage(Component.literal(renderTypeLabel()))
        clearFieldFocus()
        updateEditorWidgetVisibility()
        layoutWidgets()
        queuePreview(forceNow = true)
    }

    private fun cycleOrigin() {
        val values = HudCelebrationOrigin.entries
        val next = values[(values.indexOf(currentEmitter().origin) + 1) % values.size]
        mutateEmitter { it.copy(origin = next) }
        originButton.setMessage(Component.literal(originLabel()))
        queuePreview(forceNow = true)
    }

    private fun cycleSpawnMode() {
        val next = when (currentEmitter().spawnMode) {
            HudCelebrationSpawnMode.SINGLE -> HudCelebrationSpawnMode.CONTINUOUS
            HudCelebrationSpawnMode.CONTINUOUS -> HudCelebrationSpawnMode.SINGLE
        }
        mutateEmitter { it.copy(spawnMode = next) }
        spawnModeButton.setMessage(Component.literal(spawnModeLabel()))
        clearFieldFocus()
        updateEditorWidgetVisibility()
        layoutWidgets()
        queuePreview(forceNow = true)
    }

    private fun cycleTextureMode() {
        val next = when (currentEmitter().textureMode) {
            HudCelebrationTextureMode.SINGLE -> HudCelebrationTextureMode.SHEET
            HudCelebrationTextureMode.SHEET -> HudCelebrationTextureMode.SEQUENCE
            HudCelebrationTextureMode.SEQUENCE -> HudCelebrationTextureMode.SINGLE
        }
        mutateEmitter { it.copy(textureMode = next) }
        textureModeButton.setMessage(Component.literal(textureModeLabel()))
        clearFieldFocus()
        updateEditorWidgetVisibility()
        layoutWidgets()
        queuePreview(forceNow = true)
    }

    private fun cycleTexturePlaybackMode() {
        val next = when (currentEmitter().texturePlaybackMode) {
            HudCelebrationTexturePlaybackMode.ONCE -> HudCelebrationTexturePlaybackMode.LOOP
            HudCelebrationTexturePlaybackMode.LOOP -> HudCelebrationTexturePlaybackMode.PING_PONG
            HudCelebrationTexturePlaybackMode.PING_PONG -> HudCelebrationTexturePlaybackMode.ONCE
        }
        mutateEmitter { it.copy(texturePlaybackMode = next) }
        texturePlaybackModeButton.setMessage(Component.literal(texturePlaybackModeLabel()))
        queuePreview(forceNow = true)
    }

    private fun toggleTextureInterpolate() {
        mutateEmitter { it.copy(textureInterpolate = !it.textureInterpolate) }
        textureInterpolateButton.setMessage(Component.literal(textureInterpolateLabel()))
        queuePreview(forceNow = true)
    }

    private fun toggleUseTargetItem() {
        mutateEmitter { it.copy(useTargetItem = !it.useTargetItem) }
        useTargetItemButton.setMessage(Component.literal(useTargetItemLabel()))
        queuePreview(forceNow = true)
    }

    private fun toggleTextShadow() {
        mutateEmitter { it.copy(textShadowEnabled = !it.textShadowEnabled) }
        textShadowButton.setMessage(Component.literal(textShadowLabel()))
        queuePreview(forceNow = true)
    }

    private fun clearFieldFocus() {
        expandableFields().forEach { it.setFocused(false) }
        setFocused(null)
    }

    private fun refreshTypeVisibility() {
        val editorVisible = !fullPreviewMode
        val type = currentEmitter().renderType
        val textureMode = currentEmitter().textureMode
        setWidgetState(textureIdField, editorVisible && type == HudCelebrationRenderType.TEXTURE)
        setWidgetState(textureModeButton, editorVisible && type == HudCelebrationRenderType.TEXTURE)
        setWidgetState(pickTextureButton, editorVisible && type == HudCelebrationRenderType.TEXTURE)
        setWidgetState(textureUvXField, editorVisible && type == HudCelebrationRenderType.TEXTURE)
        setWidgetState(textureUvYField, editorVisible && type == HudCelebrationRenderType.TEXTURE)
        setWidgetState(textureUvWidthField, editorVisible && type == HudCelebrationRenderType.TEXTURE)
        setWidgetState(textureUvHeightField, editorVisible && type == HudCelebrationRenderType.TEXTURE)
        setWidgetState(textureTintColorField, editorVisible && type == HudCelebrationRenderType.TEXTURE)
        setWidgetState(textureFramesXField, editorVisible && type == HudCelebrationRenderType.TEXTURE && textureMode != HudCelebrationTextureMode.SINGLE)
        setWidgetState(textureFramesYField, editorVisible && type == HudCelebrationRenderType.TEXTURE && textureMode == HudCelebrationTextureMode.SHEET)
        setWidgetState(textureFrameTimeField, editorVisible && type == HudCelebrationRenderType.TEXTURE && textureMode != HudCelebrationTextureMode.SINGLE)
        setWidgetState(texturePlaybackModeButton, editorVisible && type == HudCelebrationRenderType.TEXTURE && textureMode != HudCelebrationTextureMode.SINGLE)
        setWidgetState(textureInterpolateButton, editorVisible && type == HudCelebrationRenderType.TEXTURE && textureMode != HudCelebrationTextureMode.SINGLE)
        setWidgetState(textureFrameOrderField, editorVisible && type == HudCelebrationRenderType.TEXTURE && textureMode != HudCelebrationTextureMode.SINGLE)
        setWidgetState(itemIdField, editorVisible && type == HudCelebrationRenderType.ITEMSTACK)
        setWidgetState(pickItemButton, editorVisible && type == HudCelebrationRenderType.ITEMSTACK)
        setWidgetState(useTargetItemButton, editorVisible && type == HudCelebrationRenderType.ITEMSTACK)
        setWidgetState(colorField, editorVisible && type == HudCelebrationRenderType.PARTICLE)
        setWidgetState(textContentField, editorVisible && type == HudCelebrationRenderType.TEXT)
        setWidgetState(textColorField, editorVisible && type == HudCelebrationRenderType.TEXT)
        setWidgetState(textSizeField, editorVisible && type == HudCelebrationRenderType.TEXT)
        setWidgetState(textShadowButton, editorVisible && type == HudCelebrationRenderType.TEXT)
        setWidgetState(textShadowColorField, editorVisible && type == HudCelebrationRenderType.TEXT)
        setWidgetState(spawnPeriodField, editorVisible && currentEmitter().spawnMode == HudCelebrationSpawnMode.CONTINUOUS)
    }

    private fun updateEditorWidgetVisibility() {
        val editorVisible = !fullPreviewMode
        listOf(
            categoryButton, renderTypeButton, originButton, spawnModeButton, textureModeButton, texturePlaybackModeButton, textureInterpolateButton, saveButton, closeButton, pickItemButton, pickTextureButton, useTargetItemButton,
            autoPreviewField, textureIdField, textureUvXField, textureUvYField, textureUvWidthField, textureUvHeightField, textureTintColorField, textureFramesXField, textureFramesYField, textureFrameTimeField, textureFrameOrderField,
            itemIdField, colorField, textContentField, textColorField, textSizeField, textShadowButton, textShadowColorField,
            gravityField, offsetXField, offsetYField, initialVelocityXField, initialVelocityYField, positionXField, positionYField, rotationField,
            sizeField, alphaField, countField, spawnPeriodField, lifetimeField
        ).forEach { widget ->
            if (widget in listOf(textureModeButton, texturePlaybackModeButton, textureInterpolateButton, textureIdField, textureUvXField, textureUvYField, textureUvWidthField, textureUvHeightField, textureTintColorField, textureFramesXField, textureFramesYField, textureFrameTimeField, textureFrameOrderField, itemIdField, colorField, textContentField, textColorField, textSizeField, textShadowButton, textShadowColorField, pickItemButton, pickTextureButton, useTargetItemButton)) {
                return@forEach
            }
            setWidgetState(widget, editorVisible)
        }
        setWidgetState(fullPreviewButton, true)
        refreshTypeVisibility()
    }

    private fun setWidgetState(widget: net.minecraft.client.gui.components.AbstractWidget, visible: Boolean) {
        widget.visible = visible
        widget.active = visible
    }

    private fun syncFieldsFromPreset() {
        syncingFields = true
        val e = currentEmitter()
        autoPreviewField.setValue(autoPreviewText())
        renderTypeButton.setMessage(Component.literal(renderTypeLabel()))
        originButton.setMessage(Component.literal(originLabel()))
        spawnModeButton.setMessage(Component.literal(spawnModeLabel()))
        textureModeButton.setMessage(Component.literal(textureModeLabel()))
        texturePlaybackModeButton.setMessage(Component.literal(texturePlaybackModeLabel()))
        textureInterpolateButton.setMessage(Component.literal(textureInterpolateLabel()))
        textureIdField.setValue(e.textureId)
        textureTintColorField.setValue(e.textureTintColor)
        textureFrameOrderField.setValue(e.textureFrameOrder)
        itemIdField.setValue(e.itemId)
        useTargetItemButton.setMessage(Component.literal(useTargetItemLabel()))
        colorField.setValue(currentColor())
        textContentField.setValue(e.textContent)
        textColorField.setValue(e.textColor)
        textShadowButton.setMessage(Component.literal(textShadowLabel()))
        textShadowColorField.setValue(e.textShadowColor)
        syncingFields = false
        numericFields.forEach { it.refreshPresentation() }
        refreshTypeVisibility()
        layoutWidgets()
    }

    private fun queuePreview(forceNow: Boolean = false) {
        val now = System.currentTimeMillis()
        if (forceNow || autoPreviewIntervalMs <= 0L || now >= nextAutoPreviewAtMs) {
            HudCelebrationEffectsClient.requestPreview(currentPreset())
            nextAutoPreviewAtMs = now + autoPreviewIntervalMs.coerceAtLeast(0L)
        }
    }

    private fun maybeSpawnAutoPreview() {
        if (autoPreviewIntervalMs > 0L && System.currentTimeMillis() >= nextAutoPreviewAtMs) {
            HudCelebrationEffectsClient.requestPreview(currentPreset())
            nextAutoPreviewAtMs = System.currentTimeMillis() + autoPreviewIntervalMs
        }
    }

    private fun currentPreset(): HudCelebrationConfig = workingPresets.getOrPut(selectedCategory) {
        val p = HudCelebrationConfigManager.getPreset(currentPresetId())
        if (p.emitters.isEmpty()) p.copy(emitters = listOf(HudCelebrationEmitterConfig())) else p
    }

    private fun currentEmitter(): HudCelebrationEmitterConfig = currentPreset().emitters.firstOrNull() ?: HudCelebrationEmitterConfig()

    private fun mutateEmitter(transform: (HudCelebrationEmitterConfig) -> HudCelebrationEmitterConfig) {
        val preset = currentPreset()
        val emitters = preset.emitters.toMutableList()
        val base = emitters.firstOrNull() ?: HudCelebrationEmitterConfig()
        if (emitters.isEmpty()) emitters += transform(base) else emitters[0] = transform(base)
        workingPresets[selectedCategory] = preset.copy(emitters = emitters)
    }

    private fun currentPresetId(): String = TimerHudConfigManager.config.finishEffects.profileId(selectedCategory)
    private fun categoryLabel(): String = selectedCategory.name.lowercase()
    private fun originLabel(): String = currentEmitter().origin.name.lowercase()
    private fun useTargetItemLabel(): String = if (currentEmitter().useTargetItem) "[x] Use target item" else "[ ] Use target item"
    private fun textShadowLabel(): String = if (currentEmitter().textShadowEnabled) "[x] Shadow" else "[ ] Shadow"
    private fun currentColor(): String = currentEmitter().colorPalette.firstOrNull() ?: "#FFFFFF"

    private fun renderTypeLabel(): String = when (currentEmitter().renderType) {
        HudCelebrationRenderType.PARTICLE -> "Color"
        HudCelebrationRenderType.TEXTURE -> "Texture"
        HudCelebrationRenderType.ITEMSTACK -> "ItemStack"
        HudCelebrationRenderType.TEXT -> "Text"
        HudCelebrationRenderType.MOB -> "Mob"
    }

    private fun spawnModeLabel(): String = when (currentEmitter().spawnMode) {
        HudCelebrationSpawnMode.SINGLE -> "Single"
        HudCelebrationSpawnMode.CONTINUOUS -> "Continuous"
    }

    private fun textureModeLabel(): String = when (currentEmitter().textureMode) {
        HudCelebrationTextureMode.SINGLE -> "Single"
        HudCelebrationTextureMode.SHEET -> "Spritesheet"
        HudCelebrationTextureMode.SEQUENCE -> "Sequence"
    }

    private fun texturePlaybackModeLabel(): String = when (currentEmitter().texturePlaybackMode) {
        HudCelebrationTexturePlaybackMode.ONCE -> "One-way"
        HudCelebrationTexturePlaybackMode.LOOP -> "Loop"
        HudCelebrationTexturePlaybackMode.PING_PONG -> "Ping-pong"
    }

    private fun textureInterpolateLabel(): String = if (currentEmitter().textureInterpolate) "[x] Interpolate" else "[ ] Interpolate"

    private fun rowStep() = fieldHeight + 2
    private fun visualSectionTop(top: Int) = top + 198
    private fun visualFieldTop(top: Int) = visualSectionTop(top) + 14
    private fun visualBottom(top: Int): Int {
        val first = visualFieldTop(top)
        return when (currentEmitter().renderType) {
            HudCelebrationRenderType.TEXTURE -> when (currentEmitter().textureMode) {
                HudCelebrationTextureMode.SINGLE -> first + rowStep() * 5 + fieldHeight
                HudCelebrationTextureMode.SHEET -> first + rowStep() * 10 + fieldHeight
                HudCelebrationTextureMode.SEQUENCE -> first + rowStep() * 10 + fieldHeight + 12
            }
            HudCelebrationRenderType.ITEMSTACK -> first + rowStep() * 2 + fieldHeight
            HudCelebrationRenderType.PARTICLE -> first + rowStep() + fieldHeight
            HudCelebrationRenderType.TEXT -> first + rowStep() * 3 + fieldHeight
            HudCelebrationRenderType.MOB -> first + rowStep() + fieldHeight
        }
    }
    private fun motionSectionTop(top: Int) = visualBottom(top) + 18
    private fun motionFieldTop(top: Int) = motionSectionTop(top) + 20
    private fun contentBottom(top: Int): Int = motionFieldTop(top) + rowStep() * 13 + fieldHeight

    private fun settingsRect(): Rect {
        if (rebuildFromScratch) return bodyPanels().first
        val availableWidth = width - outerMargin * 2 - panelGap
        val minPreviewWidth = max(260, width / 3)
        val desiredLeftWidth = (availableWidth * 0.58f).toInt()
        val maxLeftWidth = (availableWidth - minPreviewWidth).coerceAtLeast(360)
        val leftWidth = desiredLeftWidth.coerceIn(360, maxLeftWidth)
        return Rect(outerMargin, topSafe, leftWidth, height - topSafe - bottomSafe)
    }

    private fun previewRect(): Rect {
        if (rebuildFromScratch) return bodyPanels().second
        val s = settingsRect()
        return Rect(s.right + panelGap, topSafe, width - s.right - panelGap - outerMargin, height - topSafe - bottomSafe)
    }

    private fun bodyRect(): Rect {
        val margin = scaledMargin
        return Rect(margin, margin, width - margin * 2, height - margin * 2)
    }

    private fun bodyPanels(): Pair<Rect, Rect> {
        val body = bodyRect()
        val innerMargin = scaledBodyInnerMargin
        val gap = scaledPanelGap
        val titleHeight = font.lineHeight + innerMargin + 6
        val contentTop = body.top + titleHeight + innerMargin
        val contentLeft = body.left + innerMargin
        val contentRight = body.right - innerMargin
        val contentBottom = body.bottom - innerMargin
        val totalWidth = (contentRight - contentLeft).coerceAtLeast(120)
        val splitX = (contentLeft + (totalWidth * bodySplitRatio).toInt()).coerceIn(contentLeft + 80, contentRight - 80 - gap)
        val props = Rect(contentLeft, contentTop, splitX - contentLeft, contentBottom - contentTop)
        val previewLeft = splitX + gap
        val preview = Rect(previewLeft, contentTop, contentRight - previewLeft, contentBottom - contentTop)
        return props to preview
    }

    private fun splitHandleRect(): Rect {
        val panels = bodyPanels()
        val gap = scaledPanelGap
        val centerX = panels.first.right + gap / 2
        val handleWidth = 4
        val handleHeight = 12
        val top = panels.first.centerY - handleHeight / 2
        return Rect(centerX - handleWidth / 2, top, handleWidth, handleHeight)
    }

    private fun updateBodySplit(mouseX: Double) {
        val body = bodyRect()
        val innerMargin = scaledBodyInnerMargin
        val contentLeft = body.left + innerMargin
        val contentRight = body.right - innerMargin
        val totalWidth = (contentRight - contentLeft).coerceAtLeast(120)
        val ratio = ((mouseX - contentLeft) / totalWidth.toDouble()).toFloat()
        bodySplitRatio = ratio.coerceIn(0.25f, 0.75f)
        layoutBlankWidgets()
    }

    private fun drawOutline(guiGraphics: GuiGraphics, rect: Rect, color: Int) {
        guiGraphics.fill(rect.left, rect.top, rect.right, rect.top + 1, color)
        guiGraphics.fill(rect.left, rect.bottom - 1, rect.right, rect.bottom, color)
        guiGraphics.fill(rect.left, rect.top, rect.left + 1, rect.bottom, color)
        guiGraphics.fill(rect.right - 1, rect.top, rect.right, rect.bottom, color)
    }

    private fun initBlankMode() {
        blankCategoryButton = addRenderableWidget(
            StyledActionButton(0, 0, 100, fieldHeight, Component.literal(blankCategoryLabel())) { cycleBlankCategory() }
        )
        blankSpawnModeButton = addRenderableWidget(
            StyledActionButton(0, 0, 100, fieldHeight, Component.literal(blankSpawnModeLabel())) { cycleBlankSpawnMode() }
        )
        blankRenderTypeButton = addRenderableWidget(
            StyledActionButton(0, 0, 100, fieldHeight, Component.literal(blankRenderTypeLabel())) { cycleBlankRenderType() }
        )
        blankTextureModeButton = addRenderableWidget(
            StyledActionButton(0, 0, 100, fieldHeight, Component.literal(blankTextureModeLabel())) { cycleBlankTextureMode() }
        )
        blankTextureInterpolateButton = addRenderableWidget(
            StyledActionButton(0, 0, 100, fieldHeight, Component.literal(blankTextureInterpolateLabel())) { toggleBlankTextureInterpolate() }
        )
        blankCountField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0")))
        blankRateField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0")))
        blankLifetimeField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0")))
        blankColorField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("#FFFFFF")))
        blankTextureIdField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("minecraft:texture")))
        blankPickTextureButton = addRenderableWidget(StyledActionButton(0, 0, 36, fieldHeight, Component.literal("...")) {
            val e = blankEmitter()
            minecraft?.setScreen(
                HudTexturePickerScreen(this, e.textureId, e.textureUvX, e.textureUvY, e.textureUvWidth, e.textureUvHeight) { id, ux, uy, uw, uh ->
                    mutateEmitter {
                        it.copy(
                            textureId = id,
                            textureUvX = ux,
                            textureUvY = uy,
                            textureUvWidth = uw,
                            textureUvHeight = uh,
                            textureUvXExpression = ux.toString(),
                            textureUvYExpression = uy.toString(),
                            textureUvWidthExpression = uw.toString(),
                            textureUvHeightExpression = uh.toString()
                        )
                    }
                    blankTextureIdField.setValue(id)
                    blankUvXField.setValue(ux.toString())
                    blankUvYField.setValue(uy.toString())
                    blankUvWidthField.setValue(uw.toString())
                    blankUvHeightField.setValue(uh.toString())
                }
            )
        })
        blankUvXField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0")))
        blankUvYField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0")))
        blankUvWidthField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0")))
        blankUvHeightField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0")))
        blankFramesXField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("auto")))
        blankFramesYField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("auto")))
        blankFramesField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0,1,2,3")))

        blankCountField.setValue(blankEmitter().countExpression.takeIf { it.isNotBlank() } ?: "")
        blankRateField.setValue(blankEmitter().spawnPeriodExpression.takeIf { it.isNotBlank() } ?: "")
        blankLifetimeField.setValue(normalizedBlankLifetimeText())
        blankColorField.setValue(blankEmitter().colorPalette.firstOrNull().takeIf { !it.isNullOrBlank() } ?: "#FFFFFF")
        blankTextureIdField.setValue(blankEmitter().textureId)
        blankUvXField.setValue(blankEmitter().textureUvXExpression.takeIf { it.isNotBlank() && it != "var" } ?: blankEmitter().textureUvX.toString())
        blankUvYField.setValue(blankEmitter().textureUvYExpression.takeIf { it.isNotBlank() && it != "var" } ?: blankEmitter().textureUvY.toString())
        blankUvWidthField.setValue(blankEmitter().textureUvWidthExpression.takeIf { it.isNotBlank() && it != "var" } ?: blankEmitter().textureUvWidth.toString())
        blankUvHeightField.setValue(blankEmitter().textureUvHeightExpression.takeIf { it.isNotBlank() && it != "var" } ?: blankEmitter().textureUvHeight.toString())
        blankFramesXField.setValue(blankEmitter().textureFramesXExpression.takeIf { it.isNotBlank() } ?: "")
        blankFramesYField.setValue(blankEmitter().textureFramesYExpression.takeIf { it.isNotBlank() } ?: "")
        blankFramesField.setValue(blankEmitter().textureFrameOrder)

        blankCountField.setResponder { raw ->
            mutateEmitter { it.copy(countExpression = raw, count = raw.toIntOrNull()?.coerceAtLeast(0) ?: 0) }
        }
        blankRateField.setResponder { raw ->
            mutateEmitter { it.copy(spawnPeriodExpression = raw, spawnPeriodMs = raw.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f) }
        }
        blankLifetimeField.setResponder { raw ->
            mutateEmitter { it.copy(lifetimeExpression = raw, lifetimeTicks = raw.toIntOrNull()?.coerceAtLeast(0) ?: 0) }
        }
        blankColorField.setResponder { raw ->
            mutateEmitter { it.copy(colorPalette = listOf(raw.ifBlank { "#FFFFFF" })) }
        }
        blankTextureIdField.setResponder { raw ->
            mutateEmitter { it.copy(textureId = raw.ifBlank { "missingno" }) }
        }
        blankUvXField.setResponder { raw ->
            mutateEmitter { it.copy(textureUvXExpression = raw, textureUvX = raw.toIntOrNull() ?: 0) }
        }
        blankUvYField.setResponder { raw ->
            mutateEmitter { it.copy(textureUvYExpression = raw, textureUvY = raw.toIntOrNull() ?: 0) }
        }
        blankUvWidthField.setResponder { raw ->
            mutateEmitter { it.copy(textureUvWidthExpression = raw, textureUvWidth = raw.toIntOrNull() ?: 0) }
        }
        blankUvHeightField.setResponder { raw ->
            mutateEmitter { it.copy(textureUvHeightExpression = raw, textureUvHeight = raw.toIntOrNull() ?: 0) }
        }
        blankFramesXField.setResponder { raw ->
            mutateEmitter { it.copy(textureFramesXExpression = raw, textureFramesX = raw.toIntOrNull() ?: 0) }
        }
        blankFramesYField.setResponder { raw ->
            mutateEmitter { it.copy(textureFramesYExpression = raw, textureFramesY = raw.toIntOrNull() ?: 0) }
        }
        blankFramesField.setResponder { raw ->
            mutateEmitter { it.copy(textureFrameOrder = raw) }
        }
        blankOverlayField = addRenderableWidget(OverlayTextArea(font, 0, 0, 100, 180))
        blankOverlayField.visible = false
        blankOverlayField.active = false
        blankOverlayField.setMaxLength(4096)
        blankOverlayField.setResponder { value -> blankOverlayTarget?.setValue(value) }

        layoutBlankWidgets()
        refreshBlankGeneralVisibility()
    }

    private fun blankLayout(): BlankLayout {
        val props = bodyPanels().first
        val preview = bodyPanels().second
        val panelInset = scaledBodyInnerMargin
        val labelX = props.left + panelInset
        val controlX = props.left + max(148, props.width / 3)
        val contentRight = props.right - panelInset - scrollbarWidth - 8
        val controlWidth = (contentRight - controlX).coerceAtLeast(120)
        return BlankLayout(
            props = props,
            preview = preview,
            labelX = labelX,
            controlX = controlX,
            controlWidth = controlWidth,
            rowHeight = fieldHeight,
            rowGap = 10,
            sectionGap = 24
        )
    }

    private fun blankContentRect(layout: BlankLayout): Rect {
        val top = layout.props.top + scaledBodyInnerMargin + font.lineHeight + 10
        val bottom = layout.props.bottom - scaledBodyInnerMargin
        val right = layout.props.right - scaledBodyInnerMargin - scrollbarWidth - 8
        return Rect(layout.props.left + 1, top, (right - layout.props.left - 1).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
    }

    private fun layoutBlankWidgets() {
        if (!rebuildFromScratch || !::blankCategoryButton.isInitialized) return
        val layout = blankLayout()
        val sectionTop = blankContentRect(layout).top + blankScrollOffset + font.lineHeight + 10
        val visualTop = blankVisualSectionTop(layout)
        val row0 = layout.rowY(sectionTop, 0)
        val row1 = layout.rowY(sectionTop, 1)
        val row2 = layout.rowY(sectionTop, 2)
        val row3 = layout.rowY(sectionTop, 3)
        val columnGap = 8
        val pairWidth = ((layout.controlWidth - columnGap) / 2).coerceAtLeast(64)
        val secondX = layout.controlX + pairWidth + columnGap

        blankCategoryButton.setPosition(layout.controlX, row0 - 2)
        blankCategoryButton.setWidth(layout.controlWidth)
        blankSpawnModeButton.setPosition(layout.controlX, row1 - 2)
        blankSpawnModeButton.setWidth(layout.controlWidth)
        blankCountField.setPosition(layout.controlX, row2 - 2)
        blankCountField.setWidth(layout.controlWidth)
        blankRateField.setPosition(layout.controlX, row2 - 2)
        blankRateField.setWidth(layout.controlWidth)
        blankLifetimeField.setPosition(layout.controlX, row3 - 2)
        blankLifetimeField.setWidth(layout.controlWidth)

        var visualRow = blankSectionRowY(visualTop, 0)
        blankRenderTypeButton.setPosition(layout.controlX, visualRow - 2)
        blankRenderTypeButton.setWidth(layout.controlWidth)
        visualRow = blankSectionRowY(visualTop, 1)
        when (blankEmitter().renderType) {
            HudCelebrationRenderType.PARTICLE -> {
                blankColorField.setPosition(layout.controlX, visualRow - 2)
                blankColorField.setWidth(layout.controlWidth)
            }
            HudCelebrationRenderType.TEXTURE -> {
                blankUvXField.setPosition(layout.controlX, visualRow - 2)
                blankUvXField.setWidth(pairWidth)
                blankUvYField.setPosition(secondX, visualRow - 2)
                blankUvYField.setWidth(pairWidth)
                visualRow = blankSectionRowY(visualTop, 2)
                blankUvWidthField.setPosition(layout.controlX, visualRow - 2)
                blankUvWidthField.setWidth(pairWidth)
                blankUvHeightField.setPosition(secondX, visualRow - 2)
                blankUvHeightField.setWidth(pairWidth)
                visualRow = blankSectionRowY(visualTop, 3)
                blankTextureModeButton.setPosition(layout.controlX, visualRow - 2)
                blankTextureModeButton.setWidth(layout.controlWidth)
                visualRow = blankSectionRowY(visualTop, 4)
                blankTextureIdField.setPosition(layout.controlX, visualRow - 2)
                blankTextureIdField.setWidth(layout.controlWidth - 44)
                blankPickTextureButton.setPosition(layout.controlX + layout.controlWidth - 36, visualRow - 2)
                when (blankEmitter().textureMode) {
                    HudCelebrationTextureMode.SHEET -> {
                        visualRow = blankSectionRowY(visualTop, 5)
                        blankFramesXField.setPosition(layout.controlX, visualRow - 2)
                        blankFramesXField.setWidth(pairWidth)
                        blankFramesYField.setPosition(secondX, visualRow - 2)
                        blankFramesYField.setWidth(pairWidth)
                        visualRow = blankSectionRowY(visualTop, 6)
                        blankFramesField.setPosition(layout.controlX, visualRow - 2)
                        blankFramesField.setWidth(layout.controlWidth)
                        visualRow = blankSectionRowY(visualTop, 7)
                        blankTextureInterpolateButton.setPosition(layout.controlX, visualRow - 2)
                        blankTextureInterpolateButton.setWidth(layout.controlWidth)
                    }
                    HudCelebrationTextureMode.SEQUENCE -> {
                        visualRow = blankSectionRowY(visualTop, 5)
                        blankFramesField.setPosition(layout.controlX, visualRow - 2)
                        blankFramesField.setWidth(layout.controlWidth)
                        visualRow = blankSectionRowY(visualTop, 6)
                        blankTextureInterpolateButton.setPosition(layout.controlX, visualRow - 2)
                        blankTextureInterpolateButton.setWidth(layout.controlWidth)
                    }
                    HudCelebrationTextureMode.SINGLE -> Unit
                }
            }
            else -> Unit
        }
        refreshBlankVisualVisibility()
    }

    private fun blankContentBottom(layout: BlankLayout): Int {
        val visualTop = blankVisualSectionTop(layout)
        val visualRows = when (blankEmitter().renderType) {
            HudCelebrationRenderType.PARTICLE -> 2
            HudCelebrationRenderType.TEXTURE -> when (blankEmitter().textureMode) {
                HudCelebrationTextureMode.SINGLE -> 5
                HudCelebrationTextureMode.SHEET -> 8
                HudCelebrationTextureMode.SEQUENCE -> 7
            }
            else -> 2
        }
        return blankSectionRowY(visualTop, visualRows - 1) + fieldHeight + scaledBodyInnerMargin
    }

    private fun blankMinScrollOffset(layout: BlankLayout): Int {
        val overflow = blankContentBottom(layout) - blankContentRect(layout).bottom
        return -overflow.coerceAtLeast(0)
    }

    private fun blankScrollbarThumbRect(layout: BlankLayout): Rect {
        val contentRect = blankContentRect(layout)
        val trackLeft = layout.props.right - scaledBodyInnerMargin - scrollbarWidth
        val trackTop = contentRect.top
        val trackHeight = contentRect.height
        val contentHeight = (blankContentBottom(layout) - contentRect.top).coerceAtLeast(contentRect.height)
        val thumbHeight = ((contentRect.height.toFloat() / contentHeight.toFloat()) * trackHeight).toInt().coerceIn(24, trackHeight)
        val minOffset = blankMinScrollOffset(layout)
        val progress = if (minOffset == 0) 0f else (-blankScrollOffset).toFloat() / (-minOffset).toFloat()
        val travel = (trackHeight - thumbHeight).coerceAtLeast(0)
        val thumbTop = trackTop + (travel * progress).toInt()
        return Rect(trackLeft, thumbTop, scrollbarWidth, thumbHeight)
    }

    private fun renderBlankScrollbar(guiGraphics: GuiGraphics, layout: BlankLayout) {
        val contentRect = blankContentRect(layout)
        val trackLeft = layout.props.right - scaledBodyInnerMargin - scrollbarWidth
        guiGraphics.fill(trackLeft, contentRect.top, trackLeft + scrollbarWidth, contentRect.bottom, 0x30343C46)
        val thumb = blankScrollbarThumbRect(layout)
        guiGraphics.fill(thumb.left, thumb.top, thumb.right, thumb.bottom, if (draggingBlankScrollbar) 0xE0FFFFFF.toInt() else 0xB8FFFFFF.toInt())
    }

    private fun updateBlankScrollFromMouse(mouseY: Double) {
        val layout = blankLayout()
        val contentRect = blankContentRect(layout)
        val thumb = blankScrollbarThumbRect(layout)
        val thumbHeight = thumb.height
        val travel = (contentRect.height - thumbHeight).coerceAtLeast(0)
        val thumbCenter = mouseY.toFloat() - thumbHeight / 2f
        val normalized = ((thumbCenter - contentRect.top) / max(1f, travel.toFloat())).coerceIn(0f, 1f)
        val minOffset = blankMinScrollOffset(layout)
        blankScrollOffset = -((-minOffset) * normalized).toInt()
    }

    private fun renderBlankProps(guiGraphics: GuiGraphics, layout: BlankLayout, mouseX: Int, mouseY: Int) {
        blankTooltipTargets.clear()
        val top = blankContentRect(layout).top + blankScrollOffset
        val sectionTop = top + font.lineHeight + 10
        val lineLeft = layout.labelX + font.width("General") + 8
        val lineRight = blankContentRect(layout).right - 8

        guiGraphics.drawString(font, "General", layout.labelX, sectionTop, 0xFFFFE28A.toInt())
        if (lineRight > lineLeft) guiGraphics.fill(lineLeft, sectionTop + 5, lineRight, sectionTop + 6, 0x55D8E0EA)

        renderBlankLabel(guiGraphics, "Тип эмитора", layout.labelX, layout.rowY(sectionTop, 0) + 6, "emitter_type")
        renderBlankLabel(guiGraphics, "Режим спавна частиц", layout.labelX, layout.rowY(sectionTop, 1) + 6, "spawn_mode")
        renderBlankLabel(
            guiGraphics,
            if (blankEmitter().spawnMode == HudCelebrationSpawnMode.SINGLE) "Кол-во частиц" else "Частиц в мин",
            layout.labelX,
            layout.rowY(sectionTop, 2) + 6,
            "spawn_amount"
        )
        renderBlankLabel(guiGraphics, "Время жизни частицы", layout.labelX, layout.rowY(sectionTop, 3) + 6, "lifetime")
        val visualTop = blankVisualSectionTop(layout)
        val visualLineLeft = layout.labelX + font.width("Visual") + 8
        guiGraphics.drawString(font, "Visual", layout.labelX, visualTop, 0xFFFFE28A.toInt())
        if (lineRight > visualLineLeft) guiGraphics.fill(visualLineLeft, visualTop + 5, lineRight, visualTop + 6, 0x55D8E0EA)
        renderBlankLabel(guiGraphics, "Type", layout.labelX, blankSectionRowY(visualTop, 0) + 6, "visual_type")
        when (blankEmitter().renderType) {
            HudCelebrationRenderType.PARTICLE -> {
                renderBlankLabel(guiGraphics, "Color", layout.labelX, blankSectionRowY(visualTop, 1) + 6, "visual_color")
            }
            HudCelebrationRenderType.TEXTURE -> {
                renderBlankLabel(guiGraphics, "UV X / Y", layout.labelX, blankSectionRowY(visualTop, 1) + 6, "visual_uv_xy")
                renderBlankLabel(guiGraphics, "UV W / H", layout.labelX, blankSectionRowY(visualTop, 2) + 6, "visual_uv_wh")
                renderBlankLabel(guiGraphics, "Mode", layout.labelX, blankSectionRowY(visualTop, 3) + 6, "visual_texture_mode")
                renderBlankLabel(guiGraphics, "Texture ID", layout.labelX, blankSectionRowY(visualTop, 4) + 6, "visual_texture_id")
                when (blankEmitter().textureMode) {
                    HudCelebrationTextureMode.SHEET -> {
                        renderBlankLabel(guiGraphics, "Frames X / Y", layout.labelX, blankSectionRowY(visualTop, 5) + 6, "visual_frames_xy")
                        renderBlankLabel(guiGraphics, "Frames", layout.labelX, blankSectionRowY(visualTop, 6) + 6, "visual_frames")
                        renderBlankLabel(guiGraphics, "Interpolate", layout.labelX, blankSectionRowY(visualTop, 7) + 6, "visual_interpolate")
                    }
                    HudCelebrationTextureMode.SEQUENCE -> {
                        renderBlankLabel(guiGraphics, "Frames", layout.labelX, blankSectionRowY(visualTop, 5) + 6, "visual_frames")
                        renderBlankLabel(guiGraphics, "Interpolate", layout.labelX, blankSectionRowY(visualTop, 6) + 6, "visual_interpolate")
                    }
                    HudCelebrationTextureMode.SINGLE -> Unit
                }
            }
            HudCelebrationRenderType.ITEMSTACK -> {
                renderBlankLabel(guiGraphics, "Type settings soon", layout.labelX, blankSectionRowY(visualTop, 1) + 6, "visual_stub")
            }
            HudCelebrationRenderType.TEXT -> {
                renderBlankLabel(guiGraphics, "Type settings soon", layout.labelX, blankSectionRowY(visualTop, 1) + 6, "visual_stub")
            }
            else -> Unit
        }

        val hoveredTooltip = blankTooltipTargets.entries.firstOrNull { it.value.contains(mouseX.toDouble(), mouseY.toDouble()) }?.key
        if (hoveredTooltip != null) {
            renderBlankTooltip(guiGraphics, mouseX + 12, mouseY + 12, blankTooltipText(hoveredTooltip))
        }
    }

    private fun renderBlankLabel(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, tooltipKey: String) {
        guiGraphics.drawString(font, text, x, y, 0xFFFFFFFF.toInt())
        val qx = x + font.width(text) + 6
        guiGraphics.drawString(font, "?", qx, y, 0xFFFFE28A.toInt())
        blankTooltipTargets[tooltipKey] = Rect(qx - 1, y - 1, font.width("?") + 2, font.lineHeight + 2)
    }

    private fun renderBlankTooltip(guiGraphics: GuiGraphics, x: Int, y: Int, text: String) {
        val padding = 6
        val width = font.width(text) + padding * 2
        val height = font.lineHeight + padding * 2
        val rect = Rect(x, y, width, height)
        guiGraphics.fill(rect.left, rect.top, rect.right, rect.bottom, 0xBF0C1016.toInt())
        drawOutline(guiGraphics, rect, 0x66FFFFFF)
        guiGraphics.drawString(font, text, rect.left + padding, rect.top + padding, 0xFFFFFFFF.toInt())
    }

    private fun renderBlankOverlay(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val body = bodyRect()
        val inset = max(28, scaledBodyInnerMargin * 2)
        val overlay = body.inset(inset)
        val titleX = overlay.left + scaledBodyInnerMargin
        val titleY = overlay.top + scaledBodyInnerMargin
        val contentTop = titleY + font.lineHeight + 12
        val leftWidth = (overlay.width * 0.24f).toInt().coerceIn(120, 220)
        val gap = scaledPanelGap

        blankOverlayLeftRect = Rect(overlay.left + scaledBodyInnerMargin, contentTop, leftWidth, overlay.bottom - contentTop - scaledBodyInnerMargin)
        val rightLeft = blankOverlayLeftRect.right + gap
        blankOverlayRightRect = Rect(rightLeft, contentTop, overlay.right - rightLeft - scaledBodyInnerMargin, overlay.bottom - contentTop - scaledBodyInnerMargin)

        guiGraphics.fill(body.left, body.top, body.right, body.bottom, 0xAA000000.toInt())
        guiGraphics.fill(overlay.left, overlay.top, overlay.right, overlay.bottom, 0xE010141B.toInt())
        drawOutline(guiGraphics, overlay, 0x66FFFFFF)
        guiGraphics.drawString(font, "GoFindWin settings > effects > edit variable", titleX, titleY, 0xFFFFFFFF.toInt())

        guiGraphics.fill(blankOverlayLeftRect.left, blankOverlayLeftRect.top, blankOverlayLeftRect.right, blankOverlayLeftRect.bottom, 0x1AFFFFFF)
        guiGraphics.fill(blankOverlayRightRect.left, blankOverlayRightRect.top, blankOverlayRightRect.right, blankOverlayRightRect.bottom, 0x1AFFFFFF)
        drawOutline(guiGraphics, blankOverlayLeftRect, 0x40FFFFFF)
        drawOutline(guiGraphics, blankOverlayRightRect, 0x40FFFFFF)

        blankOverlayInfoTargets.clear()
        val infoX = blankOverlayLeftRect.left + 10
        var y = blankOverlayLeftRect.top + 10
        guiGraphics.drawString(font, "Variables", infoX, y, 0xFFFFE28A.toInt()); y += font.lineHeight + 8
        val variableEntries = listOf(
            "rand" to "rand : float, -1..1",
            "rand_x" to "rand_x : float, -1..1",
            "rand_y" to "rand_y : float, -1..1",
            "life_time" to "life_time : int, lifetime in ticks",
            "life_factor" to "life_factor : float, 0..1",
            "pos_x" to "pos_x : float, current local x",
            "pos_y" to "pos_y : float, current local y",
            "start_pos_x" to "start_pos_x : float, initial local x",
            "start_pos_y" to "start_pos_y : float, initial local y"
        )
        variableEntries.forEach { (label, tooltip) ->
            guiGraphics.drawString(font, label, infoX, y, 0xFFFFFFFF.toInt())
            blankOverlayInfoTargets[tooltip] = Rect(infoX - 2, y - 1, font.width(label) + 4, font.lineHeight + 2)
            y += font.lineHeight + 4
        }
        y += 8
        guiGraphics.drawString(font, "Functions", infoX, y, 0xFFFFE28A.toInt()); y += font.lineHeight + 8
        val functionEntries = listOf(
            "sin(angle: float)" to "sin(angle: float) -> float",
            "cos(angle: float)" to "cos(angle: float) -> float",
            "pow(a: float, b: float)" to "pow(a: float, b: float) -> float",
            "lerp(a: float, b: float, t: float)" to "lerp(a: float, b: float, t: float) -> float",
            "int(value: float)" to "int(value: float) -> int",
            "mod(a: float, b: float)" to "mod(a: float, b: float) -> float",
            "pos(value: float)" to "pos(value: float) -> float",
            "randi(from: int, to: int)" to "randi(from: int, to: int) -> int",
            "randf(from: float, to: float)" to "randf(from: float, to: float) -> float"
        )
        functionEntries.forEach { (label, tooltip) ->
            guiGraphics.drawString(font, label, infoX, y, 0xFFFFFFFF.toInt())
            blankOverlayInfoTargets[tooltip] = Rect(infoX - 2, y - 1, font.width(label) + 4, font.lineHeight + 2)
            y += font.lineHeight + 4
        }
        y += 8
        guiGraphics.drawString(font, "Info", infoX, y, 0xFFFFE28A.toInt()); y += font.lineHeight + 8
        guiGraphics.drawString(font, "a = rand_x; b = age_tick;", infoX, y, 0xFFFFFFFF.toInt()); y += font.lineHeight + 4
        guiGraphics.drawString(font, "sin(a * 360) * b;", infoX, y, 0xFFFFFFFF.toInt())

        blankOverlayField.visible = true
        blankOverlayField.active = true
        blankOverlayField.setPosition(blankOverlayRightRect.left + 10, blankOverlayRightRect.top + 10)
        blankOverlayField.setWidth(blankOverlayRightRect.width - 20)
        blankOverlayField.render(guiGraphics, mouseX, mouseY, 0f)

        val hoveredInfo = blankOverlayInfoTargets.entries.firstOrNull { it.value.contains(mouseX.toDouble(), mouseY.toDouble()) }?.key
        if (hoveredInfo != null) {
            renderBlankTooltip(guiGraphics, mouseX + 12, mouseY + 12, hoveredInfo)
        }
    }

    private fun cycleBlankCategory() {
        selectedCategory = when (selectedCategory) {
            FinishEffectCategory.NEW_RECORD -> FinishEffectCategory.AVERAGE
            FinishEffectCategory.AVERAGE -> FinishEffectCategory.WORSE
            FinishEffectCategory.WORSE -> FinishEffectCategory.NEW_RECORD
        }
        blankCategoryButton.setLabel(Component.literal(blankCategoryLabel()))
    }

    private fun cycleBlankSpawnMode() {
        val next = when (blankEmitter().spawnMode) {
            HudCelebrationSpawnMode.SINGLE -> HudCelebrationSpawnMode.CONTINUOUS
            HudCelebrationSpawnMode.CONTINUOUS -> HudCelebrationSpawnMode.SINGLE
        }
        mutateEmitter { it.copy(spawnMode = next) }
        blankSpawnModeButton.setLabel(Component.literal(blankSpawnModeLabel()))
        refreshBlankGeneralVisibility()
    }

    private fun cycleBlankRenderType() {
        val next = when (blankEmitter().renderType) {
            HudCelebrationRenderType.PARTICLE -> HudCelebrationRenderType.TEXTURE
            HudCelebrationRenderType.TEXTURE -> HudCelebrationRenderType.ITEMSTACK
            HudCelebrationRenderType.ITEMSTACK -> HudCelebrationRenderType.TEXT
            HudCelebrationRenderType.TEXT -> HudCelebrationRenderType.PARTICLE
            else -> HudCelebrationRenderType.PARTICLE
        }
        mutateEmitter { it.copy(renderType = next) }
        blankRenderTypeButton.setLabel(Component.literal(blankRenderTypeLabel()))
        refreshBlankVisualVisibility()
        layoutBlankWidgets()
    }

    private fun cycleBlankTextureMode() {
        val next = when (blankEmitter().textureMode) {
            HudCelebrationTextureMode.SINGLE -> HudCelebrationTextureMode.SHEET
            HudCelebrationTextureMode.SHEET -> HudCelebrationTextureMode.SEQUENCE
            HudCelebrationTextureMode.SEQUENCE -> HudCelebrationTextureMode.SINGLE
        }
        mutateEmitter { it.copy(textureMode = next) }
        blankTextureModeButton.setLabel(Component.literal(blankTextureModeLabel()))
        refreshBlankVisualVisibility()
        layoutBlankWidgets()
    }

    private fun toggleBlankTextureInterpolate() {
        mutateEmitter { it.copy(textureInterpolate = !it.textureInterpolate) }
        blankTextureInterpolateButton.setLabel(Component.literal(blankTextureInterpolateLabel()))
    }

    private fun refreshBlankGeneralVisibility() {
        if (!rebuildFromScratch || !::blankCountField.isInitialized) return
        val single = blankEmitter().spawnMode == HudCelebrationSpawnMode.SINGLE
        blankCountField.visible = single
        blankCountField.active = single
        blankRateField.visible = !single
        blankRateField.active = !single
    }

    private fun refreshBlankVisualVisibility() {
        if (!rebuildFromScratch || !::blankRenderTypeButton.isInitialized) return
        val texture = blankEmitter().renderType == HudCelebrationRenderType.TEXTURE
        val color = blankEmitter().renderType == HudCelebrationRenderType.PARTICLE
        val sheet = texture && blankEmitter().textureMode == HudCelebrationTextureMode.SHEET
        val sequence = texture && blankEmitter().textureMode == HudCelebrationTextureMode.SEQUENCE
        blankRenderTypeButton.visible = true
        blankRenderTypeButton.active = true
        blankColorField.visible = color
        blankColorField.active = color
        listOf(blankTextureModeButton, blankTextureIdField, blankPickTextureButton, blankUvXField, blankUvYField, blankUvWidthField, blankUvHeightField).forEach {
            it.visible = texture
            it.active = texture
        }
        blankFramesXField.visible = sheet
        blankFramesXField.active = sheet
        blankFramesYField.visible = sheet
        blankFramesYField.active = sheet
        blankFramesField.visible = sheet || sequence
        blankFramesField.active = sheet || sequence
        blankTextureInterpolateButton.visible = sheet || sequence
        blankTextureInterpolateButton.active = sheet || sequence
    }

    private fun hoveredBlankEditableField(mouseX: Double, mouseY: Double): StyledEditField? {
        return listOf(
            blankCountField,
            blankRateField,
            blankLifetimeField,
            blankColorField,
            blankTextureIdField,
            blankUvXField,
            blankUvYField,
            blankUvWidthField,
            blankUvHeightField,
            blankFramesXField,
            blankFramesYField,
            blankFramesField
        ).firstOrNull { it.visible && it.isMouseOver(mouseX, mouseY) }
    }

    private fun openBlankOverlay(field: StyledEditField) {
        blankOverlayTarget = field
        blankOverlayOpen = true
        blankOverlayField.visible = true
        blankOverlayField.active = true
        blankOverlayField.setValue(field.value)
        blankOverlayField.setFocused(true)
        setFocused(blankOverlayField)
    }

    private fun closeBlankOverlay() {
        blankOverlayOpen = false
        blankOverlayTarget = null
        blankOverlayField.visible = false
        blankOverlayField.active = false
        blankOverlayField.setFocused(false)
        setFocused(null)
    }

    private fun blankEmitter(): HudCelebrationEmitterConfig = currentEmitter()

    private fun blankCategoryLabel(): String = when (selectedCategory) {
        FinishEffectCategory.NEW_RECORD -> "Новый рекорд"
        FinishEffectCategory.AVERAGE -> "Средняк"
        FinishEffectCategory.WORSE -> "Плохо"
    }

    private fun blankSpawnModeLabel(): String = when (blankEmitter().spawnMode) {
        HudCelebrationSpawnMode.SINGLE -> "Одиночный"
        HudCelebrationSpawnMode.CONTINUOUS -> "Постоянный"
    }

    private fun blankRenderTypeLabel(): String = when (blankEmitter().renderType) {
        HudCelebrationRenderType.PARTICLE -> "Color"
        HudCelebrationRenderType.TEXTURE -> "Texture"
        HudCelebrationRenderType.ITEMSTACK -> "ItemStack"
        HudCelebrationRenderType.TEXT -> "Text"
        else -> "Color"
    }

    private fun blankTextureModeLabel(): String = when (blankEmitter().textureMode) {
        HudCelebrationTextureMode.SINGLE -> "Single"
        HudCelebrationTextureMode.SHEET -> "Spritesheet"
        HudCelebrationTextureMode.SEQUENCE -> "Sequence"
    }

    private fun blankTextureInterpolateLabel(): String = if (blankEmitter().textureInterpolate) "Interpolate: on" else "Interpolate: off"

    private fun normalizedBlankLifetimeText(): String {
        val raw = blankEmitter().lifetimeExpression.trim()
        return when {
            raw.isBlank() -> ""
            raw.toIntOrNull() != null -> raw
            raw.toFloatOrNull() != null -> (raw.toFloat() * 20f).toInt().coerceAtLeast(0).toString()
            else -> raw
        }
    }

    private fun blankSectionRowY(sectionTop: Int, index: Int): Int = sectionTop + 18 + index * (fieldHeight + 10)

    private fun blankVisualSectionTop(layout: BlankLayout): Int {
        val generalTop = blankContentRect(layout).top + blankScrollOffset + font.lineHeight + 10
        return generalTop + 18 + 4 * (fieldHeight + 10) + layout.sectionGap
    }

    private fun blankTooltipText(key: String): String = when (key) {
        "emitter_type" -> "Какой пресет финиш-эффекта редактируется сейчас."
        "spawn_mode" -> "Одиночный спавнит один burst, постоянный поддерживает поток частиц."
        "spawn_amount" -> "Если поле пустое, будет использовано 0."
        "lifetime" -> "Время жизни частицы в тиках. Если поле пустое, будет 0."
        "visual_type" -> "Тип визуального представления частицы."
        "visual_color" -> "HEX-цвет. Если пусто, используется #FFFFFF."
        "visual_uv_xy" -> "Левый верхний угол UV-области в пикселях."
        "visual_uv_wh" -> "Размер UV-области в пикселях."
        "visual_texture_mode" -> "Single, Spritesheet или Sequence."
        "visual_texture_id" -> "Identifier текстуры. Если пусто, будет error-texture."
        "visual_frames_xy" -> "Если пусто, позже можно будет подбирать автоматически."
        "visual_frames" -> "Порядок кадров: 0,1,2,3. Если пусто, будет использоваться порядок по умолчанию."
        "visual_interpolate" -> "Плавный переход между кадрами."
        "visual_stub" -> "Этот блок ещё не собран в новом интерфейсе."
        else -> "example_tooltip_info :P"
    }

    private fun previewBounds(): HudRenderBounds {
        val inner = previewRect().inset(10)
        val cx = (inner.centerX + previewPanX).toInt()
        val cy = (inner.centerY + previewPanY).toInt()
        return HudRenderBounds(cx - inner.width / 2, cy - inner.height / 2, cx + inner.width / 2, cy + inner.height / 2)
    }

    private fun renderPanels(guiGraphics: GuiGraphics) {
        val margin = scaledMargin
        val frame = Rect(margin, margin, width - margin * 2, height - margin * 2)
        guiGraphics.fill(frame.left, frame.top, frame.right, frame.bottom, 0x80000000.toInt())
    }

    private fun renderPreviewClipped(guiGraphics: GuiGraphics) {
        val p = previewRect()
        guiGraphics.enableScissor(p.left + 8, p.top + 26, p.right - 8, p.bottom - 18)
        HudCelebrationEffectsClient.render(guiGraphics, previewBounds(), null, preview = true)
        guiGraphics.disableScissor()
    }

    private fun renderFullPreview(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val fullRect = Rect(0, 0, width, height)
        guiGraphics.enableScissor(fullRect.left, fullRect.top, fullRect.right, fullRect.bottom)
        HudCelebrationEffectsClient.render(guiGraphics, previewBoundsFor(fullRect), null, preview = true)
        guiGraphics.disableScissor()
        guiGraphics.drawString(font, "ESC or Back To Editor", 12, height - 18, 0xFFC8D0D8.toInt())
    }

    private fun previewBoundsFor(rect: Rect): HudRenderBounds {
        val cx = (rect.centerX + previewPanX).toInt()
        val cy = (rect.centerY + previewPanY).toInt()
        return HudRenderBounds(cx - rect.width / 2, cy - rect.height / 2, cx + rect.width / 2, cy + rect.height / 2)
    }

    private fun drawSection(guiGraphics: GuiGraphics, title: String, x: Int, y: Int) {
        guiGraphics.drawString(font, title, x, y, 0xFFFFE28A.toInt())
        val lineLeft = x + font.width(title) + 8
        val lineRight = settingsRect().right - scrollbarWidth - scrollbarMargin - 16
        if (lineRight > lineLeft) guiGraphics.fill(lineLeft, y + 5, lineRight, y + 6, 0x55D8E0EA)
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics) {
        val s = settingsRect()
        val trackTop = s.top + 12
        val trackBottom = s.bottom - 12
        val trackHeight = (trackBottom - trackTop).coerceAtLeast(1)
        val trackLeft = s.right - scrollbarMargin - scrollbarWidth
        val trackRight = trackLeft + scrollbarWidth
        guiGraphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0x55343C46)
        val visibleHeight = (s.height - 24).coerceAtLeast(1)
        val contentHeight = (contentBottom(contentTop()) - contentTop()).coerceAtLeast(visibleHeight)
        val thumbHeight = ((visibleHeight.toFloat() / contentHeight.toFloat()) * trackHeight).toInt().coerceIn(24, trackHeight)
        val scrollRange = (-minScrollOffset()).coerceAtLeast(1)
        val progress = (-scrollOffset).toFloat() / scrollRange.toFloat()
        val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0)
        val thumbTop = trackTop + (thumbTravel * progress).toInt()
        guiGraphics.fill(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, 0xFFD7DEE6.toInt())
    }

    private fun isOverScrollbarThumb(mouseX: Double, mouseY: Double): Boolean {
        val s = settingsRect()
        val trackTop = s.top + 12
        val trackBottom = s.bottom - 12
        val trackHeight = (trackBottom - trackTop).coerceAtLeast(1)
        val visibleHeight = (s.height - 24).coerceAtLeast(1)
        val contentHeight = (contentBottom(contentTop()) - contentTop()).coerceAtLeast(visibleHeight)
        val thumbHeight = ((visibleHeight.toFloat() / contentHeight.toFloat()) * trackHeight).toInt().coerceIn(24, trackHeight)
        val scrollRange = (-minScrollOffset()).coerceAtLeast(1)
        val progress = (-scrollOffset).toFloat() / scrollRange.toFloat()
        val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0)
        val thumbTop = trackTop + (thumbTravel * progress).toInt()
        val left = s.right - scrollbarMargin - scrollbarWidth
        return mouseX >= left && mouseX <= left + scrollbarWidth && mouseY >= thumbTop && mouseY <= thumbTop + thumbHeight
    }

    private fun updateScrollFromMouse(mouseY: Double) {
        val s = settingsRect()
        val trackTop = s.top + 12
        val trackBottom = s.bottom - 12
        val trackHeight = (trackBottom - trackTop).coerceAtLeast(1)
        val visibleHeight = (s.height - 24).coerceAtLeast(1)
        val contentHeight = (contentBottom(contentTop()) - contentTop()).coerceAtLeast(visibleHeight)
        val thumbHeight = ((visibleHeight.toFloat() / contentHeight.toFloat()) * trackHeight).toInt().coerceIn(24, trackHeight)
        val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0)
        val thumbCenter = mouseY.toFloat() - thumbHeight / 2f
        val normalized = ((thumbCenter - trackTop) / max(1f, thumbTravel.toFloat())).coerceIn(0f, 1f)
        scrollOffset = -((-minScrollOffset()) * normalized).toInt()
    }

    private fun minScrollOffset(): Int = -(contentBottom(contentTop()) - (settingsRect().bottom - 64)).coerceAtLeast(0)

    private fun layoutWidgets() {
        if (rebuildFromScratch) return
        val s = settingsRect()
        val fx = s.left + 14 + labelWidth
        val fw = s.width - 28 - labelWidth - scrollbarWidth - scrollbarMargin
        val pw = (fw - 8) / 2
        val sx = fx + pw + 8
        val top = contentTop() + scrollOffset
        val textureMode = currentEmitter().textureMode
        val visualY = visualFieldTop(top)
        val motionY = motionFieldTop(top)
        categoryButton.setPosition(fx, top + 28); categoryButton.setWidth(fw)
        renderTypeButton.setPosition(fx, top + 56); renderTypeButton.setWidth(fw)
        autoPreviewField.setPosition(fx, top + 112); autoPreviewField.setWidth(fw)
        originButton.setPosition(fx, top + 140); originButton.setWidth(fw)
        spawnModeButton.setPosition(fx, top + 168); spawnModeButton.setWidth(fw)
        textureModeButton.setPosition(fx, visualY); textureModeButton.setWidth(fw)
        textureIdField.setPosition(fx, visualY + rowStep()); textureIdField.setWidth(fw - 44)
        pickTextureButton.setPosition(fx + fw - 38, visualY + rowStep())
        textureUvXField.setPosition(fx, visualY + rowStep() * 2); textureUvXField.setWidth(pw)
        textureUvYField.setPosition(sx, visualY + rowStep() * 2); textureUvYField.setWidth(pw)
        textureUvWidthField.setPosition(fx, visualY + rowStep() * 3); textureUvWidthField.setWidth(pw)
        textureUvHeightField.setPosition(sx, visualY + rowStep() * 3); textureUvHeightField.setWidth(pw)
        textureTintColorField.setPosition(fx, visualY + rowStep() * 4); textureTintColorField.setWidth(fw)
        textureFramesXField.setPosition(fx, visualY + rowStep() * 5); textureFramesXField.setWidth(if (textureMode == HudCelebrationTextureMode.SEQUENCE) fw else pw)
        textureFramesYField.setPosition(sx, visualY + rowStep() * 5); textureFramesYField.setWidth(pw)
        textureFrameTimeField.setPosition(fx, visualY + rowStep() * 6); textureFrameTimeField.setWidth(fw)
        texturePlaybackModeButton.setPosition(fx, visualY + rowStep() * 7); texturePlaybackModeButton.setWidth(fw)
        textureInterpolateButton.setPosition(fx, visualY + rowStep() * 8); textureInterpolateButton.setWidth(fw)
        textureFrameOrderField.setPosition(fx, visualY + rowStep() * 9); textureFrameOrderField.setWidth(fw)
        itemIdField.setPosition(fx, visualY); itemIdField.setWidth(fw - 44)
        pickItemButton.setPosition(fx + fw - 38, visualY)
        useTargetItemButton.setPosition(fx, visualY + rowStep()); useTargetItemButton.setWidth(fw)
        colorField.setPosition(fx, visualY); colorField.setWidth(fw)
        textContentField.setPosition(fx, visualY); textContentField.setWidth(fw)
        textColorField.setPosition(fx, visualY + rowStep()); textColorField.setWidth(pw)
        textSizeField.setPosition(sx, visualY + rowStep()); textSizeField.setWidth(pw)
        textShadowButton.setPosition(fx, visualY + rowStep() * 2); textShadowButton.setWidth(pw)
        textShadowColorField.setPosition(sx, visualY + rowStep() * 2); textShadowColorField.setWidth(pw)
        offsetXField.setPosition(fx, motionY); offsetXField.setWidth(pw)
        offsetYField.setPosition(sx, motionY); offsetYField.setWidth(pw)
        gravityField.setPosition(fx, motionY + rowStep()); gravityField.setWidth(fw)
        initialVelocityXField.setPosition(fx, motionY + rowStep() * 2); initialVelocityXField.setWidth(pw)
        initialVelocityYField.setPosition(sx, motionY + rowStep() * 2); initialVelocityYField.setWidth(pw)
        positionXField.setPosition(fx, motionY + rowStep() * 3); positionXField.setWidth(pw)
        positionYField.setPosition(sx, motionY + rowStep() * 3); positionYField.setWidth(pw)
        rotationField.setPosition(fx, motionY + rowStep() * 4); rotationField.setWidth(fw)
        sizeField.setPosition(fx, motionY + rowStep() * 5); sizeField.setWidth(pw)
        alphaField.setPosition(sx, motionY + rowStep() * 5); alphaField.setWidth(pw)
        countField.setPosition(fx, motionY + rowStep() * 6); countField.setWidth(pw)
        spawnPeriodField.setPosition(sx, motionY + rowStep() * 6); spawnPeriodField.setWidth(pw)
        lifetimeField.setPosition(fx, motionY + rowStep() * 7); lifetimeField.setWidth(fw)
        saveButton.setPosition(fx, motionY + rowStep() * 12); saveButton.setWidth(pw)
        closeButton.setPosition(sx, motionY + rowStep() * 12); closeButton.setWidth(pw)
        val p = previewRect()
        fullPreviewButton.setPosition(p.right - 122, topSafe - 22)
        fullPreviewButton.setWidth(110)
    }

    private fun contentTop() = settingsRect().top + 8
    private fun parseLongOrNull(raw: String): Long? = raw.toLongOrNull()
    private fun autoPreviewText(): String = autoPreviewIntervalMs.toString()

    private fun makeTextField(x: Int, y: Int, width: Int, value: String, hint: String, onApply: (String) -> Unit): EditBox {
        val field = EditBox(font, x, y, width, fieldHeight, Component.literal(hint))
        field.setValue(value)
        field.setMaxLength(256)
        field.setResponder { if (!syncingFields) { onApply(it); queuePreview(forceNow = true) } }
        addRenderableWidget(field)
        return field
    }

    private fun makeLongField(x: Int, y: Int, width: Int, value: String, onApply: (String) -> Unit): EditBox {
        val field = EditBox(font, x, y, width, fieldHeight, Component.literal("Spawn time"))
        field.setValue(value)
        field.setMaxLength(128)
        field.setResponder { if (!syncingFields) onApply(it) }
        addRenderableWidget(field)
        numericFields += NumericField(field) { autoPreviewText() }
        return field
    }

    private fun makeIntField(
        x: Int,
        y: Int,
        width: Int,
        expressionGetter: () -> String,
        updater: (HudCelebrationEmitterConfig, String, Int) -> HudCelebrationEmitterConfig
    ): EditBox {
        val field = EditBox(font, x, y, width, fieldHeight, Component.literal("int"))
        field.setMaxLength(128)
        field.setResponder { raw -> if (!syncingFields) updateNumericExpression(raw, true) { e, ex, v -> updater(e, ex, v.toInt()) } }
        addRenderableWidget(field)
        numericFields += NumericField(field, expressionGetter)
        return field
    }

    private fun makeFloatField(
        x: Int,
        y: Int,
        width: Int,
        expressionGetter: () -> String,
        updater: (HudCelebrationEmitterConfig, String, Float) -> HudCelebrationEmitterConfig
    ): EditBox {
        val field = EditBox(font, x, y, width, fieldHeight, Component.literal("value"))
        field.setMaxLength(128)
        field.setResponder { raw -> if (!syncingFields) updateNumericExpression(raw, false, updater) }
        addRenderableWidget(field)
        numericFields += NumericField(field, expressionGetter)
        return field
    }

    private fun makeOptionalIntField(
        x: Int,
        y: Int,
        width: Int,
        expressionGetter: () -> String,
        updater: (HudCelebrationEmitterConfig, String, Int) -> HudCelebrationEmitterConfig
    ): EditBox {
        val field = EditBox(font, x, y, width, fieldHeight, Component.literal("auto"))
        field.setMaxLength(128)
        field.setResponder { raw ->
            if (!syncingFields) {
                val expression = raw.trim()
                mutateEmitter { emitter ->
                    updater(emitter, expression, if (expression.isBlank()) 0 else evaluateNumeric(expression, true).toInt())
                }
                queuePreview(forceNow = true)
            }
        }
        addRenderableWidget(field)
        numericFields += NumericField(field, expressionGetter)
        return field
    }

    private fun makeOptionalFloatField(
        x: Int,
        y: Int,
        width: Int,
        expressionGetter: () -> String,
        updater: (HudCelebrationEmitterConfig, String, Float) -> HudCelebrationEmitterConfig
    ): EditBox {
        val field = EditBox(font, x, y, width, fieldHeight, Component.literal("auto"))
        field.setMaxLength(128)
        field.setResponder { raw ->
            if (!syncingFields) {
                val expression = raw.trim()
                mutateEmitter { emitter ->
                    updater(emitter, expression, if (expression.isBlank()) 0f else evaluateNumeric(expression, false))
                }
                queuePreview(forceNow = true)
            }
        }
        addRenderableWidget(field)
        numericFields += NumericField(field, expressionGetter)
        return field
    }

    private fun updateNumericExpression(raw: String, integer: Boolean, updater: (HudCelebrationEmitterConfig, String, Float) -> HudCelebrationEmitterConfig) {
        val expression = raw.trim().ifBlank { "0" }
        val evaluated = evaluateNumeric(expression, integer)
        mutateEmitter { updater(it, expression, evaluated) }
        queuePreview(forceNow = true)
    }

    private fun evaluateNumeric(expression: String, integer: Boolean): Float {
        val evaluated = MathExpression.evaluate(expression.ifBlank { "0" }, expressionVariables())?.toFloat() ?: 0f
        return if (integer) evaluated.toInt().toFloat() else evaluated
    }

    private fun expressionVariables(): Map<String, Double> {
        val e = currentEmitter()
        return mapOf(
            "life_time" to e.lifetimeTicks.toDouble(),
            "life_factor" to 0.0,
            "age_tick" to 0.0,
            "age_frame" to 0.0,
            "age_tick_delta" to 0.0,
            "age_frame_delta" to 0.0,
            "rand" to 0.0,
            "rand_x" to 0.0,
            "rand_y" to 0.0,
            "pos_x" to 0.0,
            "pos_y" to 0.0,
            "start_pos_x" to 0.0,
            "start_pos_y" to 0.0
        )
    }

    private inner class NumericField(val field: EditBox, val expressionGetter: () -> String) {
        fun refreshPresentation() {
            val current = expressionGetter()
            if (field.value != current) {
                syncingFields = true
                field.setValue(current)
                syncingFields = false
                if (field.isFocused) field.moveCursorToEnd(false)
            }
        }
    }
}
