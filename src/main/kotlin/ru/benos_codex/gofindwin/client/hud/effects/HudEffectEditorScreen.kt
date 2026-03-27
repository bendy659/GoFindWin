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
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
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
    private var blankPendingTooltipText: String? = null
    private var blankPendingTooltipX: Int = 0
    private var blankPendingTooltipY: Int = 0

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
        var palettePreview = false

        init {
            setBordered(true)
            setMaxLength(1024)
            setTextColor(0xFFF7F8FA.toInt())
            setTextColorUneditable(0xFFD4D9E0.toInt())
        }

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val fill = if (isFocused) 0x2612161D else 0x180E1117
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, fill)
            val hideVanillaText = palettePreview && !isFocused
            val normalTextColor = 0xFFF7F8FA.toInt()
            val normalUneditableColor = 0xFFD4D9E0.toInt()
            if (hideVanillaText) {
                setTextColor(0x00FFFFFF)
                setTextColorUneditable(0x00FFFFFF)
            }
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick)
            if (hideVanillaText) {
                setTextColor(normalTextColor)
                setTextColorUneditable(normalUneditableColor)
                val content = value
                val drawX = getX() + 5
                val drawY = getY() + (height - Minecraft.getInstance().font.lineHeight) / 2
                renderPalettePreview(guiGraphics, drawX, drawY, content)
            }
        }

        private fun renderPalettePreview(guiGraphics: GuiGraphics, x: Int, y: Int, raw: String) {
            val font = Minecraft.getInstance().font
            var drawX = x
            val segments = raw.split(",")
            segments.forEachIndexed { index, segment ->
                val text = segment.trim()
                if (text.isNotEmpty()) {
                    val color = parsePreviewColor(text)
                    guiGraphics.drawString(font, text, drawX, y, color)
                    drawX += font.width(text)
                }
                if (index != segments.lastIndex) {
                    guiGraphics.drawString(font, ",", drawX, y, 0xFFF7F8FA.toInt())
                    drawX += font.width(",")
                }
            }
        }

        private fun parsePreviewColor(raw: String): Int {
            val value = raw.removePrefix("#")
            return when (value.length) {
                6 -> value.toLongOrNull(16)?.toInt()?.let { 0xFF000000.toInt() or it }
                8 -> value.toLongOrNull(16)?.toInt()
                else -> null
            } ?: 0xFFF7F8FA.toInt()
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
        private var textScale = 1.0f
        private var responder: (String) -> Unit = {}
        private var selectionAnchor = 0
        private var selectionCursor = 0
        private var draggingSelection = false
        private val builtinVariables = setOf(
            "rand", "rand_x", "rand_y", "life_time", "life_factor",
            "pos_x", "pos_y", "start_pos_x", "start_pos_y",
            "hud_left", "hud_right", "hud_top", "hud_bottom", "hud_center_x", "hud_center_y",
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
        )
        private val builtinFunctions = setOf(
            "sin", "cos", "pow", "lerp", "int", "mod", "pos",
            "randi", "randf", "rand_value", "keyframe"
        )

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
            selectionAnchor = cursor
            selectionCursor = cursor
            ensureCursorVisible()
        }

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) = Unit

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val fill = if (isFocused) 0xD010141B.toInt() else 0xC80D1117.toInt()
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, fill)
            val gutterWidth = lineNumberGutterWidth()
            val textLeft = getX() + gutterWidth + 10
            val textTop = getY() + 10
            val availableLines = visibleLineCount()
            val lines = value.split('\n')
            val visibleLines = lines.drop(scrollLine).take(availableLines)
            val selection = selectionRange()
            guiGraphics.fill(getX(), getY(), getX() + gutterWidth, getY() + height, 0x1813171E)
            guiGraphics.fill(getX() + gutterWidth, getY() + 6, getX() + gutterWidth + 1, getY() + height - 6, 0x22FFFFFF)
            visibleLines.forEachIndexed { index, line ->
                val absoluteLine = scrollLine + index
                val lineStart = absoluteIndexForLine(absoluteLine)
                val lineEnd = lineStart + line.length
                val lineSelectionStart = selection?.first?.coerceIn(lineStart, lineEnd)
                val lineSelectionEnd = selection?.second?.coerceIn(lineStart, lineEnd)
                val drawY = textTop + index * textFont.lineHeight
                val lineNumberText = (absoluteLine + 1).toString()
                drawScaledString(
                    guiGraphics,
                    lineNumberText,
                    getX() + gutterWidth - 6 - scaledTextWidth(lineNumberText),
                    drawY,
                    0x7FA0A9B6
                )
                if (lineSelectionStart != null && lineSelectionEnd != null && lineSelectionStart < lineSelectionEnd) {
                    val startColumn = lineSelectionStart - lineStart
                    val endColumn = lineSelectionEnd - lineStart
                    val selectionX = textLeft + scaledTextWidth(line.take(startColumn))
                    val selectionRight = textLeft + scaledTextWidth(line.take(endColumn))
                    guiGraphics.fill(selectionX, drawY, selectionRight, drawY + scaledLineHeight(), 0x664B6EA8)
                }
                renderHighlightedLine(guiGraphics, line, textLeft, drawY)
            }
            if (isFocused) {
                val cursorPos = cursorPosition()
                val visibleLine = cursorPos.first - scrollLine
                if (visibleLine in 0 until availableLines) {
                    val lineText = lines.getOrElse(cursorPos.first) { "" }
                    val beforeCursor = lineText.take(cursorPos.second.coerceAtMost(lineText.length))
                    val cursorX = textLeft + scaledTextWidth(beforeCursor)
                    val cursorY = textTop + visibleLine * scaledLineHeight()
                    guiGraphics.fill(cursorX, cursorY, cursorX + 1, cursorY + scaledLineHeight(), 0xFFFFFFFF.toInt())
                }
            }
            renderScrollbar(guiGraphics)
            val border = if (isFocused) 0x88FFFFFF.toInt() else 0x44FFFFFF
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, border)
            guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border)
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, border)
            guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border)
            renderSuggestions(guiGraphics)
        }

        override fun onClick(mouseButtonEvent: MouseButtonEvent, bl: Boolean) {
            setFocused(true)
            cursor = nearestCursor(mouseButtonEvent.x(), mouseButtonEvent.y())
            selectionAnchor = cursor
            selectionCursor = cursor
            ensureCursorVisible()
        }

        override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
            if (!isFocused || mouseButtonEvent.button() != 0) return false
            if (!draggingSelection) {
                draggingSelection = true
                if (selectionAnchor == selectionCursor) {
                    selectionAnchor = cursor
                }
            }
            cursor = nearestCursor(mouseButtonEvent.x(), mouseButtonEvent.y())
            selectionCursor = cursor
            ensureCursorVisible()
            return true
        }

        override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
            if (mouseButtonEvent.button() == 0) {
                draggingSelection = false
            }
            return super.mouseReleased(mouseButtonEvent)
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            if (!isMouseOver(mouseX, mouseY)) return false
            if (isControlPressed()) {
                textScale = (textScale + if (scrollY > 0) 0.1f else -0.1f).coerceIn(0.75f, 2.0f)
                ensureCursorVisible()
                return true
            }
            val lines = value.split('\n').size
            val availableLines = visibleLineCount()
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
                258 -> {
                    if (applySuggestionIfVisible()) return true
                    insert("    ")
                    return true
                }
                259 -> {
                    if (deleteSelection()) return true
                    if (cursor > 0) {
                        value = value.removeRange(cursor - 1, cursor)
                        cursor -= 1
                        selectionAnchor = cursor
                        selectionCursor = cursor
                        notifyResponder()
                    }
                    return true
                }
                261 -> {
                    if (deleteSelection()) return true
                    if (cursor < value.length) {
                        value = value.removeRange(cursor, cursor + 1)
                        selectionAnchor = cursor
                        selectionCursor = cursor
                        notifyResponder()
                    }
                    return true
                }
                262 -> {
                    cursor = (cursor + 1).coerceAtMost(value.length)
                    updateSelectionFromKeyboard()
                    ensureCursorVisible()
                    return true
                }
                263 -> {
                    cursor = (cursor - 1).coerceAtLeast(0)
                    updateSelectionFromKeyboard()
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
                    updateSelectionFromKeyboard()
                    ensureCursorVisible()
                    return true
                }
                269 -> {
                    cursor = lineEnd(cursor)
                    updateSelectionFromKeyboard()
                    ensureCursorVisible()
                    return true
                }
            }
            return false
        }

        private fun insert(text: String) {
            if (text.isEmpty()) return
            deleteSelection()
            if (value.length + text.length > maxLength) return
            value = value.substring(0, cursor) + text + value.substring(cursor)
            cursor += text.length
            selectionAnchor = cursor
            selectionCursor = cursor
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
            updateSelectionFromKeyboard()
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
            val targetLine = (relativeY / scaledLineHeight() + scrollLine).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            val lineText = lines.getOrElse(targetLine) { "" }
            val relativeX = (mouseX.toInt() - getX() - lineNumberGutterWidth() - 10).coerceAtLeast(0)
            var bestColumn = 0
            for (column in 0..lineText.length) {
                if (scaledTextWidth(lineText.take(column)) > relativeX) break
                bestColumn = column
            }
            var index = 0
            for (i in 0 until targetLine) index += lines[i].length + 1
            return index + bestColumn
        }

        private fun ensureCursorVisible() {
            val line = cursorPosition().first
            val availableLines = visibleLineCount()
            val maxScroll = (value.split('\n').size - availableLines).coerceAtLeast(0)
            when {
                line < scrollLine -> scrollLine = line
                line >= scrollLine + availableLines -> scrollLine = line - availableLines + 1
            }
            scrollLine = scrollLine.coerceIn(0, maxScroll)
        }

        private fun selectionRange(): Pair<Int, Int>? {
            val start = minOf(selectionAnchor, selectionCursor)
            val end = maxOf(selectionAnchor, selectionCursor)
            return if (start == end) null else start to end
        }

        private fun deleteSelection(): Boolean {
            val selection = selectionRange() ?: return false
            value = value.removeRange(selection.first, selection.second)
            cursor = selection.first
            selectionAnchor = cursor
            selectionCursor = cursor
            notifyResponder()
            return true
        }

        private fun updateSelectionFromKeyboard() {
            selectionAnchor = cursor
            selectionCursor = cursor
        }

        private fun absoluteIndexForLine(line: Int): Int {
            val lines = value.split('\n')
            val clamped = line.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            var index = 0
            for (i in 0 until clamped) index += lines[i].length + 1
            return index
        }

        private fun renderHighlightedLine(guiGraphics: GuiGraphics, line: String, x: Int, y: Int) {
            var drawX = x
            val regex = Regex("0x[0-9A-Fa-f]{6,8}|#[0-9A-Fa-f]{6,8}|\\b(keyframe|value|easing|rand|rand_x|rand_y|life_time|life_factor|pos_x|pos_y|start_pos_x|start_pos_y|hud_left|hud_right|hud_top|hud_bottom|hud_center_x|hud_center_y|sin|cos|pow|lerp|int|mod|pos|randi|randf|rand_value|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)\\b|\\b\\d+(?:\\.\\d+)?\\b|[=+\\-*/{}(),.:]")
            val specialVariables = builtinVariables - setOf(
                "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
                "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
            )
            val colorConstants = builtinVariables intersect setOf(
                "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
                "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
            )
            val functions = builtinFunctions - setOf("keyframe")
            val localVariables = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=").findAll(value)
                .map { it.groupValues[1] }
                .filterNot { it in specialVariables || it in functions || it in setOf("keyframe", "value", "easing") }
                .toSet()
            var lastIndex = 0
            regex.findAll(line).forEach { match ->
                if (match.range.first > lastIndex) {
                    val plain = line.substring(lastIndex, match.range.first)
                    drawScaledString(guiGraphics, plain, drawX, y, 0xFFF7F8FA.toInt())
                    drawX += scaledTextWidth(plain)
                }
                val token = match.value
                val color = when {
                    token.startsWith("#") || token.startsWith("0x") || token.startsWith("0X") -> parsePreviewColor(token)
                    token.matches(Regex("\\d+(?:\\.\\d+)?")) -> 0xFF8BD5FF.toInt()
                    token in setOf("keyframe", "value", "easing") -> 0xFFFFE28A.toInt()
                    token.matches(Regex("[=+\\-*/{}(),.]")) -> 0xFFB8C0CC.toInt()
                    token in functions -> 0xFFA6E3A1.toInt()
                    token in colorConstants -> 0xFFF38BA8.toInt()
                    token in specialVariables -> 0xFF89B4FA.toInt()
                    token in localVariables -> 0xFFF5C2E7.toInt()
                    else -> 0xFFF7F8FA.toInt()
                }
                drawScaledString(guiGraphics, token, drawX, y, color)
                drawX += scaledTextWidth(token)
                lastIndex = match.range.last + 1
            }
            if (lastIndex < line.length) {
                val plain = line.substring(lastIndex)
                drawScaledString(guiGraphics, plain, drawX, y, 0xFFF7F8FA.toInt())
            }
        }

        private fun parsePreviewColor(raw: String): Int {
            val value = raw.removePrefix("#").removePrefix("0x").removePrefix("0X")
            return when (value.length) {
                6 -> value.toLongOrNull(16)?.toInt()?.let { 0xFF000000.toInt() or it }
                8 -> value.toLongOrNull(16)?.toInt()
                else -> null
            } ?: 0xFFF7F8FA.toInt()
        }

        private fun visibleLineCount(): Int = ((height - 20) / scaledLineHeight()).coerceAtLeast(1)

        private fun lineNumberGutterWidth(): Int {
            val totalLines = value.split('\n').size.coerceAtLeast(1)
            return (scaledTextWidth(totalLines.toString()) + 14).coerceAtLeast(28)
        }

        private fun currentTokenRange(): IntRange? {
            if (value.isEmpty()) return null
            val safeCursor = cursor.coerceIn(0, value.length)
            var start = safeCursor
            while (start > 0 && value[start - 1].isIdentifierChar()) start--
            var end = safeCursor
            while (end < value.length && value[end].isIdentifierChar()) end++
            return if (start == end) null else start until end
        }

        private fun currentTokenPrefix(): String? {
            val range = currentTokenRange() ?: return null
            val safeCursor = cursor.coerceIn(range.first, range.last + 1)
            return value.substring(range.first, safeCursor)
                .takeIf { it.isNotBlank() && it.any(Char::isLetter) }
        }

        private fun suggestionItems(): List<String> {
            val prefix = currentTokenPrefix()?.lowercase() ?: return emptyList()
            val locals = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=").findAll(value)
                .map { it.groupValues[1] }
                .toSet()
            return (builtinVariables + builtinFunctions + locals + setOf("value", "easing"))
                .filter { it.lowercase().startsWith(prefix) && it.lowercase() != prefix }
                .sorted()
                .take(8)
        }

        private fun applySuggestionIfVisible(): Boolean {
            val suggestions = suggestionItems()
            val replacement = suggestions.firstOrNull() ?: return false
            val range = currentTokenRange() ?: return false
            value = value.replaceRange(range, replacement)
            cursor = range.first + replacement.length
            selectionAnchor = cursor
            selectionCursor = cursor
            notifyResponder()
            return true
        }

        private fun renderScrollbar(guiGraphics: GuiGraphics) {
            val lines = value.split('\n').size
            val visibleLines = visibleLineCount()
            val maxScroll = (lines - visibleLines).coerceAtLeast(0)
            if (maxScroll <= 0) return
            val trackLeft = getX() + width - 8
            val trackTop = getY() + 8
            val trackBottom = getY() + height - 8
            guiGraphics.fill(trackLeft, trackTop, trackLeft + 3, trackBottom, 0x22FFFFFF)
            val trackHeight = (trackBottom - trackTop).coerceAtLeast(1)
            val thumbHeight = max((trackHeight * visibleLines / lines.coerceAtLeast(1)), 18)
            val travel = (trackHeight - thumbHeight).coerceAtLeast(0)
            val thumbTop = trackTop + if (maxScroll == 0) 0 else (travel * scrollLine / maxScroll)
            guiGraphics.fill(trackLeft - 1, thumbTop, trackLeft + 4, thumbTop + thumbHeight, 0x88FFFFFF.toInt())
        }

        private fun renderSuggestions(guiGraphics: GuiGraphics) {
            if (!isFocused) return
            val suggestions = suggestionItems()
            if (suggestions.isEmpty()) return
            val (lineIndex, columnIndex) = cursorPosition()
            val visibleLine = lineIndex - scrollLine
            if (visibleLine !in 0 until visibleLineCount()) return
            val lineText = value.split('\n').getOrElse(lineIndex) { "" }
            val prefix = currentTokenPrefix() ?: return
            val beforeCursor = lineText.take(columnIndex)
            val popupX = (getX() + lineNumberGutterWidth() + 10 + scaledTextWidth(beforeCursor) - scaledTextWidth(prefix)).coerceAtLeast(getX() + 8)
            val popupY = getY() + 10 + (visibleLine + 1) * scaledLineHeight() + 4
            val popupWidth = suggestions.maxOf { scaledTextWidth(it) }.coerceAtLeast(80) + 12
            val popupHeight = suggestions.size * scaledLineHeight() + 8
            guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xE010141B.toInt())
            guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + 1, 0x66FFFFFF)
            guiGraphics.fill(popupX, popupY + popupHeight - 1, popupX + popupWidth, popupY + popupHeight, 0x66FFFFFF)
            guiGraphics.fill(popupX, popupY, popupX + 1, popupY + popupHeight, 0x66FFFFFF)
            guiGraphics.fill(popupX + popupWidth - 1, popupY, popupX + popupWidth, popupY + popupHeight, 0x66FFFFFF)
            suggestions.forEachIndexed { index, item ->
                drawScaledString(guiGraphics, item, popupX + 6, popupY + 4 + index * scaledLineHeight(), 0xFFF7F8FA.toInt())
            }
        }

        private fun Char.isIdentifierChar(): Boolean = isLetterOrDigit() || this == '_' || this == '.'

        private fun isControlPressed(): Boolean {
            val windowHandle = resolveWindowHandle() ?: return false
            return GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        }

        private fun resolveWindowHandle(): Long? {
            val window = Minecraft.getInstance().window
            return try {
                val getter = window.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
                when (val value = getter?.invoke(window)) {
                    is Long -> value
                    is Number -> value.toLong()
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun scaledLineHeight(): Int = (textFont.lineHeight * textScale).toInt().coerceAtLeast(1)

        private fun scaledTextWidth(text: String): Int = (textFont.width(text) * textScale).toInt()

        private fun drawScaledString(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int) {
            if (text.isEmpty()) return
            val pose = guiGraphics.pose()
            pose.pushMatrix()
            pose.translate(x.toFloat(), y.toFloat())
            pose.scale(textScale, textScale)
            guiGraphics.drawString(textFont, text, 0, 0, color)
            pose.popMatrix()
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
    private var autoPreviewIntervalMs = 0L
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
    private var blankMiniPreviewRect: Rect? = null
    private var blankOverlayOpen = false
    private var blankOverlayTarget: StyledEditField? = null
    private var blankOverlayTitle = "variable"
    private var blankOverlayInfoScrollOffset = 0
    private var blankOverlayInfoContentHeight = 0
    private var draggingBlankOverlayInfoScrollbar = false
    private var draggingBlankOverlaySplit = false
    private var draggingBlankOverlayColumnSplit = false
    private var blankOverlaySplitRatio = 0.22f
    private var blankOverlayPreviewRatio = 0.34f
    private lateinit var blankOverlayField: OverlayTextArea
    private var blankOverlayLeftRect = Rect(0, 0, 0, 0)
    private var blankOverlayRightRect = Rect(0, 0, 0, 0)
    private var blankOverlayPreviewRect = Rect(0, 0, 0, 0)
    private var blankOverlayRect = Rect(0, 0, 0, 0)

    private lateinit var blankCategoryButton: StyledActionButton
    private lateinit var blankSpawnModeButton: StyledActionButton
    private lateinit var blankRenderTypeButton: StyledActionButton
    private lateinit var blankTextureModeButton: StyledActionButton
    private lateinit var blankTextureInterpolateButton: StyledActionButton
    private lateinit var blankCountField: StyledEditField
    private lateinit var blankRateField: StyledEditField
    private lateinit var blankLifetimeField: StyledEditField
    private lateinit var blankOriginXField: StyledEditField
    private lateinit var blankOriginYField: StyledEditField
    private lateinit var blankColorField: StyledEditField
    private lateinit var blankTextureIdField: StyledEditField
    private lateinit var blankPickTextureButton: StyledActionButton
    private lateinit var blankUvXField: StyledEditField
    private lateinit var blankUvYField: StyledEditField
    private lateinit var blankUvWidthField: StyledEditField
    private lateinit var blankUvHeightField: StyledEditField
    private lateinit var blankTextureTintField: StyledEditField
    private lateinit var blankFramesXField: StyledEditField
    private lateinit var blankFramesYField: StyledEditField
    private lateinit var blankFramesField: StyledEditField
    private lateinit var blankFrameTimeField: StyledEditField
    private lateinit var blankItemIdField: StyledEditField
    private lateinit var blankPickItemButton: StyledActionButton
    private lateinit var blankUseTargetItemButton: StyledActionButton
    private lateinit var blankTextContentField: StyledEditField
    private lateinit var blankTextColorField: StyledEditField
    private lateinit var blankTextSizeField: StyledEditField
    private lateinit var blankTextShadowButton: StyledActionButton
    private lateinit var blankTextShadowColorField: StyledEditField
    private lateinit var blankPositionXField: StyledEditField
    private lateinit var blankPositionYField: StyledEditField
    private lateinit var blankRotationField: StyledEditField
    private lateinit var blankScaleField: StyledEditField
    private lateinit var blankAlphaField: StyledEditField

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
            maybeSpawnAutoPreview()
            blankPendingTooltipText = null
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
            if (!blankOverlayOpen) {
                renderPreviewClipped(guiGraphics)
            }
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
            if (!blankOverlayOpen) {
                val blankContent = blankContentRect(layout)
                guiGraphics.enableScissor(blankContent.left, blankContent.top, blankContent.right, blankContent.bottom)
                super.render(guiGraphics, mouseX, mouseY, partialTick)
                renderBlankProps(guiGraphics, layout, mouseX, mouseY)
                guiGraphics.disableScissor()
                renderBlankScrollbar(guiGraphics, layout)
            }
            if (blankMiniPreviewRect?.contains(mouseX.toDouble(), mouseY.toDouble()) == true) {
                renderBlankTextureMiniTooltip(guiGraphics, mouseX + 14, mouseY + 14)
            }
            if (blankOverlayOpen) renderBlankOverlay(guiGraphics, mouseX, mouseY)
            blankPendingTooltipText?.let { renderBlankTooltip(guiGraphics, blankPendingTooltipX, blankPendingTooltipY, it) }
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
                if (mouseButtonEvent.button() == 0 && blankOverlaySplitHandleRect().contains(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                    draggingBlankOverlaySplit = true
                    return true
                }
                if (mouseButtonEvent.button() == 0 && blankOverlayColumnHandleRect().contains(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                    draggingBlankOverlayColumnSplit = true
                    return true
                }
                if (mouseButtonEvent.button() == 0 && blankOverlayInfoScrollbarThumbRect().contains(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                    draggingBlankOverlayInfoScrollbar = true
                    updateBlankOverlayInfoScrollFromMouse(mouseButtonEvent.y())
                    return true
                }
                val handled = blankOverlayField.mouseClicked(mouseButtonEvent, bl)
                if (!handled && !blankOverlayRect.contains(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                    closeBlankOverlay()
                    return true
                }
                return handled
            }
            if (mouseButtonEvent.button() == 0) {
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
                        blankPickItemButton.isMouseOver(x, y) ||
                        blankUseTargetItemButton.isMouseOver(x, y) ||
                        blankTextShadowButton.isMouseOver(x, y) ||
                        blankCountField.isMouseOver(x, y) ||
                        blankRateField.isMouseOver(x, y) ||
                        blankLifetimeField.isMouseOver(x, y) ||
                        blankOriginXField.isMouseOver(x, y) ||
                        blankOriginYField.isMouseOver(x, y) ||
                        blankColorField.isMouseOver(x, y) ||
                        blankTextureIdField.isMouseOver(x, y) ||
                        blankTextureTintField.isMouseOver(x, y) ||
                        blankPickTextureButton.isMouseOver(x, y) ||
                        blankUvXField.isMouseOver(x, y) ||
                        blankUvYField.isMouseOver(x, y) ||
                        blankUvWidthField.isMouseOver(x, y) ||
                        blankUvHeightField.isMouseOver(x, y) ||
                        blankFramesXField.isMouseOver(x, y) ||
                        blankFramesYField.isMouseOver(x, y) ||
                        blankFramesField.isMouseOver(x, y) ||
                        blankFrameTimeField.isMouseOver(x, y) ||
                        blankItemIdField.isMouseOver(x, y) ||
                        blankTextContentField.isMouseOver(x, y) ||
                        blankTextColorField.isMouseOver(x, y) ||
                        blankTextSizeField.isMouseOver(x, y) ||
                        blankTextShadowColorField.isMouseOver(x, y) ||
                        blankPositionXField.isMouseOver(x, y) ||
                        blankPositionYField.isMouseOver(x, y) ||
                        blankRotationField.isMouseOver(x, y) ||
                        blankScaleField.isMouseOver(x, y) ||
                        blankAlphaField.isMouseOver(x, y)

                if (!clickedInteractive) {
                    listOf(
                        blankCountField, blankRateField, blankLifetimeField, blankOriginXField, blankOriginYField, blankColorField, blankTextureIdField,
                        blankTextureTintField,
                        blankUvXField, blankUvYField, blankUvWidthField, blankUvHeightField, blankFramesXField, blankFramesYField, blankFramesField, blankFrameTimeField,
                        blankItemIdField, blankTextContentField, blankTextColorField, blankTextSizeField, blankTextShadowColorField,
                        blankPositionXField, blankPositionYField, blankRotationField, blankScaleField, blankAlphaField
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
        if (button == 0) {
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
        if (button == 0) return handled
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

    private data class BlankTexturePreviewFrame(
        val textureId: Identifier,
        val uvX: Int,
        val uvY: Int,
        val uvWidth: Int,
        val uvHeight: Int,
        val textureWidth: Int,
        val textureHeight: Int
    )

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
        if (rebuildFromScratch) {
            if (blankOverlayOpen) {
                if (draggingBlankOverlayInfoScrollbar && mouseButtonEvent.button() == 0) {
                    updateBlankOverlayInfoScrollFromMouse(mouseButtonEvent.y())
                    return true
                }
                if (draggingBlankOverlaySplit && mouseButtonEvent.button() == 0) {
                    updateBlankOverlaySplit(mouseButtonEvent.x())
                    return true
                }
                if (draggingBlankOverlayColumnSplit && mouseButtonEvent.button() == 0) {
                    updateBlankOverlayColumnSplit(mouseButtonEvent.y())
                    return true
                }
                return blankOverlayField.mouseDragged(mouseButtonEvent, d, e)
            }
            if (draggingBlankScrollbar && mouseButtonEvent.button() == 0) {
                updateBlankScrollFromMouse(mouseButtonEvent.y())
                layoutBlankWidgets()
                return true
            }
            if (draggingBodySplit && mouseButtonEvent.button() == 0) {
                updateBodySplit(mouseButtonEvent.x())
                return true
            }
            if (draggingPreview && mouseButtonEvent.button() == 2) {
                previewPanX += d.toFloat()
                previewPanY += e.toFloat()
                HudCelebrationEffectsClient.clear()
                queuePreview(forceNow = true)
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
            if (mouseButtonEvent.button() == 0) draggingBlankOverlayInfoScrollbar = false
            if (mouseButtonEvent.button() == 0) draggingBlankOverlaySplit = false
            if (mouseButtonEvent.button() == 0) draggingBlankOverlayColumnSplit = false
            return super.mouseReleased(mouseButtonEvent)
        }
        if (mouseButtonEvent.button() == 0) draggingScrollbar = false
        if (mouseButtonEvent.button() == 2) draggingPreview = false
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (rebuildFromScratch && blankOverlayOpen) {
            if (blankOverlayField.isMouseOver(mouseX, mouseY) && blankOverlayField.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true
            }
            if (blankOverlayLeftRect.contains(mouseX, mouseY)) {
                val prev = blankOverlayInfoScrollOffset
                blankOverlayInfoScrollOffset =
                    (blankOverlayInfoScrollOffset + scrollY.toInt() * SCROLL_STEP).coerceIn(blankOverlayInfoMinScrollOffset(), 0)
                return blankOverlayInfoScrollOffset != prev
            }
            return true
        }
        if (rebuildFromScratch) {
            val layout = blankLayout()
            if (blankContentRect(layout).contains(mouseX, mouseY) || layout.props.contains(mouseX, mouseY)) {
                val prev = blankScrollOffset
                blankScrollOffset = (blankScrollOffset + scrollY.toInt() * SCROLL_STEP).coerceIn(blankMinScrollOffset(layout), 0)
                if (blankScrollOffset != prev) {
                    layoutBlankWidgets()
                }
                return true
            }
            return false
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

    private fun cycleOrigin() = Unit

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
        val previewDelayMs = previewCycleDelayMs()
        if (forceNow || previewDelayMs <= 0L || now >= nextAutoPreviewAtMs) {
            HudCelebrationEffectsClient.requestPreview(currentPreset())
            nextAutoPreviewAtMs = now + previewDelayMs
        }
    }

    private fun maybeSpawnAutoPreview() {
        val previewDelayMs = previewCycleDelayMs()
        if (previewDelayMs > 0L && System.currentTimeMillis() >= nextAutoPreviewAtMs) {
            HudCelebrationEffectsClient.requestPreview(currentPreset())
            nextAutoPreviewAtMs = System.currentTimeMillis() + previewDelayMs
        }
    }

    private fun previewCycleDelayMs(): Long {
        if (autoPreviewIntervalMs > 0L) return autoPreviewIntervalMs
        val emitter = currentEmitter()
        val parsedLifetime = emitter.lifetimeExpression.trim().toIntOrNull()
            ?: emitter.lifetimeTicks.coerceAtLeast(1)
        return ((parsedLifetime.coerceAtLeast(1) / 20f) * 1000f).toLong().coerceAtLeast(50L)
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
        HudCelebrationConfigManager.savePreset(currentPresetId(), workingPresets.getValue(selectedCategory))
    }

    private fun currentPresetId(): String = TimerHudConfigManager.config.finishEffects.profileId(selectedCategory)
    private fun categoryLabel(): String = selectedCategory.name.lowercase()
    private fun originLabel(): String = "custom"
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
        blankTextureIdField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("minecraft:particle/flash")))
        blankTextureTintField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("#FFFFFF")))
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
        blankFrameTimeField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("100")))
        blankItemIdField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("minecraft:item")))
        blankPickItemButton = addRenderableWidget(StyledActionButton(0, 0, 36, fieldHeight, Component.literal("...")) {
            minecraft?.setScreen(HudItemPickerScreen(this, blankEmitter().itemId) { itemId ->
                mutateEmitter { it.copy(itemId = itemId, useTargetItem = false) }
                blankItemIdField.setValue(itemId)
                blankUseTargetItemButton.setLabel(Component.literal(blankUseTargetItemLabel()))
            })
        })
        blankUseTargetItemButton = addRenderableWidget(
            StyledActionButton(0, 0, 100, fieldHeight, Component.literal(blankUseTargetItemLabel())) { toggleBlankUseTargetItem() }
        )
        blankOriginXField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("hud_center_x")))
        blankOriginYField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("hud_center_y")))
        blankTextContentField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("GO\\nFIND\\nWIN")))
        blankTextColorField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("#FFFFFF")))
        blankTextSizeField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("1")))
        blankTextShadowButton = addRenderableWidget(
            StyledActionButton(0, 0, 100, fieldHeight, Component.literal(blankTextShadowLabel())) { toggleBlankTextShadow() }
        )
        blankTextShadowColorField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("#202020")))
        blankPositionXField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0.0")))
        blankPositionYField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0.0")))
        blankRotationField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("0.0")))
        blankScaleField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("1.0")))
        blankAlphaField = addRenderableWidget(StyledEditField(font, 0, 0, 100, fieldHeight, Component.literal("1.0")))
        blankColorField.palettePreview = true
        blankTextureTintField.palettePreview = true
        blankTextColorField.palettePreview = true
        blankTextShadowColorField.palettePreview = true

        blankCountField.setValue(blankEmitter().countExpression.takeIf { it.isNotBlank() } ?: "")
        blankRateField.setValue(blankEmitter().spawnPeriodExpression.takeIf { it.isNotBlank() } ?: "")
        blankLifetimeField.setValue(normalizedBlankLifetimeText())
        blankOriginXField.setValue(blankEmitter().originXExpression.takeIf { it.isNotBlank() } ?: blankEmitter().originX.toString())
        blankOriginYField.setValue(blankEmitter().originYExpression.takeIf { it.isNotBlank() } ?: blankEmitter().originY.toString())
        blankColorField.setValue(blankEmitter().colorPalette.joinToString(",").ifBlank { "#FFFFFF" })
        blankTextureIdField.setValue(blankEmitter().textureId)
        blankUvXField.setValue(blankEmitter().textureUvXExpression.takeIf { it.isNotBlank() && it != "var" } ?: blankEmitter().textureUvX.toString())
        blankUvYField.setValue(blankEmitter().textureUvYExpression.takeIf { it.isNotBlank() && it != "var" } ?: blankEmitter().textureUvY.toString())
        blankUvWidthField.setValue(blankEmitter().textureUvWidthExpression.takeIf { it.isNotBlank() && it != "var" } ?: blankEmitter().textureUvWidth.toString())
        blankUvHeightField.setValue(blankEmitter().textureUvHeightExpression.takeIf { it.isNotBlank() && it != "var" } ?: blankEmitter().textureUvHeight.toString())
        blankFramesXField.setValue(blankEmitter().textureFramesXExpression.takeIf { it.isNotBlank() } ?: "")
        blankFramesYField.setValue(blankEmitter().textureFramesYExpression.takeIf { it.isNotBlank() } ?: "")
        blankFramesField.setValue(blankEmitter().textureFrameOrder)
        blankFrameTimeField.setValue(blankEmitter().textureFrameTimeMsExpression.takeIf { it.isNotBlank() } ?: "")
        blankTextureTintField.setValue(blankEmitter().textureTintColor)
        blankItemIdField.setValue(blankEmitter().itemId)
        blankTextContentField.setValue(blankEmitter().textContent)
        blankTextColorField.setValue(blankEmitter().textColor)
        blankTextSizeField.setValue(blankEmitter().textScaleExpression.takeIf { it.isNotBlank() } ?: blankEmitter().textScale.toString())
        blankTextShadowColorField.setValue(blankEmitter().textShadowColor)
        blankPositionXField.setValue(blankEmitter().positionXExpression.takeIf { it.isNotBlank() } ?: blankEmitter().positionX.toString())
        blankPositionYField.setValue(blankEmitter().positionYExpression.takeIf { it.isNotBlank() } ?: blankEmitter().positionY.toString())
        blankRotationField.setValue(blankEmitter().initialRotationExpression.takeIf { it.isNotBlank() } ?: blankEmitter().initialRotation.toString())
        blankScaleField.setValue(blankEmitter().sizeExpression.takeIf { it.isNotBlank() } ?: blankEmitter().size.toString())
        blankAlphaField.setValue(blankEmitter().alphaExpression.takeIf { it.isNotBlank() } ?: blankEmitter().alpha.toString())
        blankColorField.setTextColor(previewTextColor(blankColorField.value))
        blankTextureTintField.setTextColor(previewTextColor(blankTextureTintField.value))
        blankTextColorField.setTextColor(previewTextColor(blankTextColorField.value))
        blankTextShadowColorField.setTextColor(previewTextColor(blankTextShadowColorField.value))

        blankCountField.setResponder { raw ->
            mutateEmitter {
                val expression = raw.trim()
                val evaluated = if (expression.isBlank()) 0 else evaluateNumeric(expression, true).toInt().coerceAtLeast(0)
                it.copy(countExpression = raw, count = evaluated)
            }
        }
        blankRateField.setResponder { raw ->
            mutateEmitter {
                val expression = raw.trim()
                val evaluated = if (expression.isBlank()) 0f else evaluateNumeric(expression, false).coerceAtLeast(0f)
                it.copy(spawnPeriodExpression = raw, spawnPeriodMs = evaluated)
            }
        }
        blankLifetimeField.setResponder { raw ->
            mutateEmitter {
                val expression = raw.trim()
                val evaluated = if (expression.isBlank()) 0 else evaluateNumeric(expression, true).toInt().coerceAtLeast(0)
                it.copy(lifetimeExpression = raw, lifetimeTicks = evaluated)
            }
        }
        blankOriginXField.setResponder { raw ->
            mutateEmitter { it.copy(originXExpression = raw.trim(), originX = raw.toFloatOrNull() ?: 0f) }
        }
        blankOriginYField.setResponder { raw ->
            mutateEmitter { it.copy(originYExpression = raw.trim(), originY = raw.toFloatOrNull() ?: 0f) }
        }
        blankColorField.setResponder { raw ->
            mutateEmitter { it.copy(colorPalette = parseColorList(raw).ifEmpty { listOf("#FFFFFF") }) }
            blankColorField.setTextColor(previewTextColor(raw))
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
        blankFrameTimeField.setResponder { raw ->
            mutateEmitter { it.copy(textureFrameTimeMsExpression = raw, textureFrameTimeMs = raw.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f) }
        }
        blankTextureTintField.setResponder { raw ->
            mutateEmitter { it.copy(textureTintColor = raw.ifBlank { "#FFFFFF" }) }
            blankTextureTintField.setTextColor(previewTextColor(raw))
        }
        blankItemIdField.setResponder { raw ->
            mutateEmitter { it.copy(itemId = raw.ifBlank { "minecraft:barrier" }) }
        }
        blankTextContentField.setResponder { raw ->
            mutateEmitter { it.copy(textContent = raw) }
        }
        blankTextColorField.setResponder { raw ->
            mutateEmitter { it.copy(textColor = raw.ifBlank { "#FFFFFF" }) }
            blankTextColorField.setTextColor(previewTextColor(raw))
        }
        blankTextSizeField.setResponder { raw ->
            mutateEmitter { it.copy(textScaleExpression = raw, textScale = raw.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f) }
        }
        blankTextShadowColorField.setResponder { raw ->
            mutateEmitter { it.copy(textShadowColor = raw.ifBlank { "#202020" }) }
            blankTextShadowColorField.setTextColor(previewTextColor(raw))
        }
        blankPositionXField.setResponder { raw ->
            mutateEmitter { it.copy(positionXExpression = raw.trim(), positionX = raw.toFloatOrNull() ?: 0f) }
        }
        blankPositionYField.setResponder { raw ->
            mutateEmitter { it.copy(positionYExpression = raw.trim(), positionY = raw.toFloatOrNull() ?: 0f) }
        }
        blankRotationField.setResponder { raw ->
            mutateEmitter { it.copy(initialRotationExpression = raw.trim(), initialRotation = raw.toFloatOrNull() ?: 0f) }
        }
        blankScaleField.setResponder { raw ->
            mutateEmitter { it.copy(sizeExpression = raw.trim(), size = raw.toFloatOrNull() ?: 1f) }
        }
        blankAlphaField.setResponder { raw ->
            mutateEmitter { it.copy(alphaExpression = raw.trim(), alpha = raw.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f) }
        }
        blankOverlayField = addRenderableWidget(OverlayTextArea(font, 0, 0, 100, 180))
        blankOverlayField.visible = false
        blankOverlayField.active = false
        blankOverlayField.setMaxLength(4096)
        blankOverlayField.setResponder { value ->
            blankOverlayTarget?.setValue(value)
            queuePreview(forceNow = true)
        }

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
        val bottom = layout.props.bottom - scaledBodyInnerMargin - max(24, fieldHeight + 8)
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
        val row4 = layout.rowY(sectionTop, 4)
        val columnGap = 8
        val pairWidth = ((layout.controlWidth - columnGap) / 2).coerceAtLeast(64)
        val secondX = layout.controlX + pairWidth + columnGap

        blankCategoryButton.setPosition(layout.controlX, row0 - 2)
        blankCategoryButton.setWidth(layout.controlWidth)
        blankSpawnModeButton.setPosition(layout.controlX, row1 - 2)
        blankSpawnModeButton.setWidth(layout.controlWidth)
        blankOriginXField.setPosition(layout.controlX, row2 - 2)
        blankOriginXField.setWidth(pairWidth)
        blankOriginYField.setPosition(secondX, row2 - 2)
        blankOriginYField.setWidth(pairWidth)
        blankCountField.setPosition(layout.controlX, row3 - 2)
        blankCountField.setWidth(layout.controlWidth)
        blankRateField.setPosition(layout.controlX, row3 - 2)
        blankRateField.setWidth(layout.controlWidth)
        blankLifetimeField.setPosition(layout.controlX, row4 - 2)
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
                blankTextureIdField.setPosition(layout.controlX, visualRow - 2)
                blankTextureIdField.setWidth(layout.controlWidth - 44)
                blankPickTextureButton.setPosition(layout.controlX + layout.controlWidth - 36, visualRow - 2)
                visualRow = blankSectionRowY(visualTop, 2)
                blankTextureTintField.setPosition(layout.controlX, visualRow - 2)
                blankTextureTintField.setWidth(layout.controlWidth)
                visualRow = blankSectionRowY(visualTop, 3)
                val quadGap = 8
                val quadWidth = ((layout.controlWidth - quadGap * 3) / 4).coerceAtLeast(44)
                blankUvXField.setPosition(layout.controlX, visualRow - 2)
                blankUvXField.setWidth(quadWidth)
                blankUvYField.setPosition(layout.controlX + quadWidth + quadGap, visualRow - 2)
                blankUvYField.setWidth(quadWidth)
                blankUvWidthField.setPosition(layout.controlX + (quadWidth + quadGap) * 2, visualRow - 2)
                blankUvWidthField.setWidth(quadWidth)
                blankUvHeightField.setPosition(layout.controlX + (quadWidth + quadGap) * 3, visualRow - 2)
                blankUvHeightField.setWidth(quadWidth)
                visualRow = blankSectionRowY(visualTop, 4)
                blankTextureModeButton.setPosition(layout.controlX, visualRow - 2)
                blankTextureModeButton.setWidth(layout.controlWidth)
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
                        val miniPreviewWidth = max(fieldHeight + 2, 28)
                        val miniPreviewGap = 10
                        blankFrameTimeField.setPosition(layout.controlX, visualRow - 2)
                        blankFrameTimeField.setWidth((layout.controlWidth - miniPreviewWidth - miniPreviewGap).coerceAtLeast(96))
                        visualRow = blankSectionRowY(visualTop, 8)
                        blankTextureInterpolateButton.setPosition(layout.controlX, visualRow - 2)
                        blankTextureInterpolateButton.setWidth(layout.controlWidth)
                    }
                    HudCelebrationTextureMode.SEQUENCE -> {
                        visualRow = blankSectionRowY(visualTop, 5)
                        blankFramesField.setPosition(layout.controlX, visualRow - 2)
                        blankFramesField.setWidth(layout.controlWidth)
                        visualRow = blankSectionRowY(visualTop, 6)
                        val miniPreviewWidth = max(fieldHeight + 2, 28)
                        val miniPreviewGap = 10
                        blankFrameTimeField.setPosition(layout.controlX, visualRow - 2)
                        blankFrameTimeField.setWidth((layout.controlWidth - miniPreviewWidth - miniPreviewGap).coerceAtLeast(96))
                        visualRow = blankSectionRowY(visualTop, 7)
                        blankTextureInterpolateButton.setPosition(layout.controlX, visualRow - 2)
                        blankTextureInterpolateButton.setWidth(layout.controlWidth)
                    }
                    HudCelebrationTextureMode.SINGLE -> Unit
                }
            }
            HudCelebrationRenderType.ITEMSTACK -> {
                blankItemIdField.setPosition(layout.controlX, visualRow - 2)
                blankItemIdField.setWidth(layout.controlWidth - 44)
                blankPickItemButton.setPosition(layout.controlX + layout.controlWidth - 36, visualRow - 2)
                visualRow = blankSectionRowY(visualTop, 2)
                blankUseTargetItemButton.setPosition(layout.controlX, visualRow - 2)
                blankUseTargetItemButton.setWidth(layout.controlWidth)
            }
            HudCelebrationRenderType.TEXT -> {
                blankTextContentField.setPosition(layout.controlX, visualRow - 2)
                blankTextContentField.setWidth(layout.controlWidth)
                visualRow = blankSectionRowY(visualTop, 2)
                blankTextColorField.setPosition(layout.controlX, visualRow - 2)
                blankTextColorField.setWidth(pairWidth)
                blankTextSizeField.setPosition(secondX, visualRow - 2)
                blankTextSizeField.setWidth(pairWidth)
                visualRow = blankSectionRowY(visualTop, 3)
                blankTextShadowButton.setPosition(layout.controlX, visualRow - 2)
                blankTextShadowButton.setWidth(pairWidth)
                blankTextShadowColorField.setPosition(secondX, visualRow - 2)
                blankTextShadowColorField.setWidth(pairWidth)
            }
            else -> Unit
        }
        val controlTop = blankControlSectionTop(layout)
        val controlRow0 = blankSectionRowY(controlTop, 0)
        val controlRow1 = blankSectionRowY(controlTop, 1)
        val controlRow2 = blankSectionRowY(controlTop, 2)
        val controlRow3 = blankSectionRowY(controlTop, 3)
        blankPositionXField.setPosition(layout.controlX, controlRow0 - 2)
        blankPositionXField.setWidth(pairWidth)
        blankPositionYField.setPosition(secondX, controlRow0 - 2)
        blankPositionYField.setWidth(pairWidth)
        blankRotationField.setPosition(layout.controlX, controlRow1 - 2)
        blankRotationField.setWidth(layout.controlWidth)
        blankScaleField.setPosition(layout.controlX, controlRow2 - 2)
        blankScaleField.setWidth(layout.controlWidth)
        blankAlphaField.setPosition(layout.controlX, controlRow3 - 2)
        blankAlphaField.setWidth(layout.controlWidth)
        refreshBlankVisualVisibility()
    }

    private fun blankContentBottom(layout: BlankLayout): Int {
        val visibleBottom = blankInteractiveWidgets()
            .filter { it.visible }
            .maxOfOrNull { it.y + it.height - blankScrollOffset }
            ?: blankContentRect(layout).top
        return visibleBottom + scaledBodyInnerMargin + max(120, fieldHeight + 56)
    }

    private fun blankInteractiveWidgets(): List<AbstractWidget> = listOf(
        blankCategoryButton,
        blankSpawnModeButton,
        blankRenderTypeButton,
        blankTextureModeButton,
        blankTextureInterpolateButton,
        blankCountField,
        blankRateField,
        blankLifetimeField,
        blankOriginXField,
        blankOriginYField,
        blankColorField,
        blankTextureIdField,
        blankPickTextureButton,
        blankUvXField,
        blankUvYField,
        blankUvWidthField,
        blankUvHeightField,
        blankTextureTintField,
        blankFramesXField,
        blankFramesYField,
        blankFramesField,
        blankFrameTimeField,
        blankItemIdField,
        blankPickItemButton,
        blankUseTargetItemButton,
        blankTextContentField,
        blankTextColorField,
        blankTextSizeField,
        blankTextShadowButton,
        blankTextShadowColorField,
        blankPositionXField,
        blankPositionYField,
        blankRotationField,
        blankScaleField,
        blankAlphaField
    )

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
        blankMiniPreviewRect = null
        val top = blankContentRect(layout).top + blankScrollOffset
        val sectionTop = top + font.lineHeight + 10
        val lineLeft = layout.labelX + font.width("General") + 8
        val lineRight = blankContentRect(layout).right - 8

        guiGraphics.drawString(font, "General", layout.labelX, sectionTop, 0xFFFFE28A.toInt())
        if (lineRight > lineLeft) guiGraphics.fill(lineLeft, sectionTop + 5, lineRight, sectionTop + 6, 0x55D8E0EA)

        renderBlankLabel(guiGraphics, "Тип эмитора", layout.labelX, layout.rowY(sectionTop, 0) + 6, "emitter_type")
        renderBlankLabel(guiGraphics, "Режим спавна частиц", layout.labelX, layout.rowY(sectionTop, 1) + 6, "spawn_mode")
        renderBlankLabel(guiGraphics, "Origin", layout.labelX, layout.rowY(sectionTop, 2) + 6, "origin")
        renderBlankLabel(
            guiGraphics,
            if (blankEmitter().spawnMode == HudCelebrationSpawnMode.SINGLE) "Кол-во частиц" else "Частиц в мин",
            layout.labelX,
            layout.rowY(sectionTop, 3) + 6,
            "spawn_amount"
        )
        renderBlankLabel(guiGraphics, "Время жизни частицы", layout.labelX, layout.rowY(sectionTop, 4) + 6, "lifetime")
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
                renderBlankLabel(guiGraphics, "Texture ID", layout.labelX, blankSectionRowY(visualTop, 1) + 6, "visual_texture_id")
                renderBlankLabel(guiGraphics, "Multiply color", layout.labelX, blankSectionRowY(visualTop, 2) + 6, "visual_texture_tint")
                renderBlankLabel(guiGraphics, "UV", layout.labelX, blankSectionRowY(visualTop, 3) + 6, "visual_uv")
                renderBlankLabel(guiGraphics, "Mode", layout.labelX, blankSectionRowY(visualTop, 4) + 6, "visual_texture_mode")
                when (blankEmitter().textureMode) {
                    HudCelebrationTextureMode.SHEET -> {
                        renderBlankLabel(guiGraphics, "Frames X / Y", layout.labelX, blankSectionRowY(visualTop, 5) + 6, "visual_frames_xy")
                        renderBlankLabel(guiGraphics, "Frames", layout.labelX, blankSectionRowY(visualTop, 6) + 6, "visual_frames")
                        renderBlankLabel(guiGraphics, "Frame time ms", layout.labelX, blankSectionRowY(visualTop, 7) + 6, "visual_frame_time")
                        renderBlankLabel(guiGraphics, "Interpolate / Random", layout.labelX, blankSectionRowY(visualTop, 8) + 6, "visual_interpolate")
                        blankMiniPreviewRect = renderBlankTextureMiniPreview(guiGraphics, layout, blankSectionRowY(visualTop, 7) - 2)
                    }
                    HudCelebrationTextureMode.SEQUENCE -> {
                        renderBlankLabel(guiGraphics, "Frames", layout.labelX, blankSectionRowY(visualTop, 5) + 6, "visual_frames")
                        renderBlankLabel(guiGraphics, "Frame time ms", layout.labelX, blankSectionRowY(visualTop, 6) + 6, "visual_frame_time")
                        renderBlankLabel(guiGraphics, "Interpolate / Random", layout.labelX, blankSectionRowY(visualTop, 7) + 6, "visual_interpolate")
                        blankMiniPreviewRect = renderBlankTextureMiniPreview(guiGraphics, layout, blankSectionRowY(visualTop, 6) - 2)
                    }
                    HudCelebrationTextureMode.SINGLE -> Unit
                }
            }
            HudCelebrationRenderType.ITEMSTACK -> {
                renderBlankLabel(guiGraphics, "Item ID", layout.labelX, blankSectionRowY(visualTop, 1) + 6, "visual_item_id")
                renderBlankLabel(guiGraphics, "Use target item", layout.labelX, blankSectionRowY(visualTop, 2) + 6, "visual_use_target_item")
            }
            HudCelebrationRenderType.TEXT -> {
                renderBlankLabel(guiGraphics, "Text", layout.labelX, blankSectionRowY(visualTop, 1) + 6, "visual_text_content")
                renderBlankLabel(guiGraphics, "Text color / Size", layout.labelX, blankSectionRowY(visualTop, 2) + 6, "visual_text_style")
                renderBlankLabel(guiGraphics, "Shadow / Shadow color", layout.labelX, blankSectionRowY(visualTop, 3) + 6, "visual_text_shadow")
            }
            else -> Unit
        }
        val controlTop = blankControlSectionTop(layout)
        val controlLineLeft = layout.labelX + font.width("Control") + 8
        guiGraphics.drawString(font, "Control", layout.labelX, controlTop, 0xFFFFE28A.toInt())
        if (lineRight > controlLineLeft) guiGraphics.fill(controlLineLeft, controlTop + 5, lineRight, controlTop + 6, 0x55D8E0EA)
        renderBlankLabel(guiGraphics, "Position", layout.labelX, blankSectionRowY(controlTop, 0) + 6, "control_position")
        renderBlankLabel(guiGraphics, "Rotation", layout.labelX, blankSectionRowY(controlTop, 1) + 6, "control_rotation")
        renderBlankLabel(guiGraphics, "Scale", layout.labelX, blankSectionRowY(controlTop, 2) + 6, "control_scale")
        renderBlankLabel(guiGraphics, "Alpha", layout.labelX, blankSectionRowY(controlTop, 3) + 6, "control_alpha")

        val fieldError = blankHoveredFieldError(mouseX.toDouble(), mouseY.toDouble())
        val hoveredTooltip = blankTooltipTargets.entries.firstOrNull { it.value.contains(mouseX.toDouble(), mouseY.toDouble()) }?.key
        blankPendingTooltipText = when {
            fieldError != null -> fieldError
            hoveredTooltip != null -> blankTooltipText(hoveredTooltip)
            else -> null
        }
        blankPendingTooltipX = mouseX + 12
        blankPendingTooltipY = mouseY + 12
    }

    private fun renderBlankTextureMiniPreview(guiGraphics: GuiGraphics, layout: BlankLayout, rowTop: Int): Rect? {
        val emitter = blankEmitter()
        val totalFrames = blankTextureFrameSequence(emitter).size.coerceAtLeast(1)
        val frameTimeMs = emitter.textureFrameTimeMsExpression.toFloatOrNull()?.coerceAtLeast(10f)
            ?: emitter.textureFrameTimeMs.coerceAtLeast(10f)
        val stepFloat = ((System.currentTimeMillis() % 1_000_000L).toFloat() / frameTimeMs).coerceAtLeast(0f)
        val stepBase = stepFloat.toInt()
        val currentFrame = resolveBlankTexturePreviewFrame(stepBase) ?: return null
        val nextFrame = if (emitter.textureInterpolate && totalFrames > 1) resolveBlankTexturePreviewFrame(stepBase + 1) else null
        val interpolation = if (emitter.textureInterpolate) (stepFloat - stepBase).coerceIn(0f, 1f) else 0f
        val previewSize = max(fieldHeight + 2, 28)
        val previewRect = Rect(
            layout.controlX + layout.controlWidth - previewSize,
            rowTop + (fieldHeight - previewSize) / 2,
            previewSize,
            previewSize
        )
        guiGraphics.fill(previewRect.left, previewRect.top, previewRect.right, previewRect.bottom, 0x180E1117)
        drawOutline(guiGraphics, previewRect, 0x44FFFFFF)

        val progress = ((System.currentTimeMillis() % 1000L) / 1000f).coerceIn(0f, 1f)
        val tint = previewTextColor(blankTextureTintField.value)
        val argb = (0xFF shl 24) or (tint and 0x00FFFFFF)
        val drawSize = (previewRect.height - 8).coerceAtLeast(12)
        val drawX = previewRect.left + (previewRect.width - drawSize) / 2
        val drawY = previewRect.top + (previewRect.height - drawSize) / 2
        renderBlankTextureMiniFrame(guiGraphics, currentFrame, drawX, drawY, drawSize, argb, 1f - interpolation)
        if (nextFrame != null && interpolation > 0.001f) {
            renderBlankTextureMiniFrame(guiGraphics, nextFrame, drawX, drawY, drawSize, argb, interpolation)
        }
        return previewRect
    }

    private fun renderBlankTextureMiniTooltip(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val emitter = blankEmitter()
        val totalFrames = blankTextureFrameSequence(emitter).size.coerceAtLeast(1)
        val frameTimeMs = emitter.textureFrameTimeMsExpression.toFloatOrNull()?.coerceAtLeast(10f)
            ?: emitter.textureFrameTimeMs.coerceAtLeast(10f)
        val stepFloat = ((System.currentTimeMillis() % 1_000_000L).toFloat() / frameTimeMs).coerceAtLeast(0f)
        val stepBase = stepFloat.toInt()
        val currentFrame = resolveBlankTexturePreviewFrame(stepBase) ?: return
        val nextFrame = if (emitter.textureInterpolate && totalFrames > 1) resolveBlankTexturePreviewFrame(stepBase + 1) else null
        val interpolation = if (emitter.textureInterpolate) (stepFloat - stepBase).coerceIn(0f, 1f) else 0f
        val rect = Rect(x, y, 76, 76)
        guiGraphics.fill(rect.left, rect.top, rect.right, rect.bottom, 0xD010141B.toInt())
        drawOutline(guiGraphics, rect, 0x66FFFFFF)

        val progress = ((System.currentTimeMillis() % 1000L) / 1000f).coerceIn(0f, 1f)
        val tint = previewTextColor(blankTextureTintField.value)
        val argb = (0xFF shl 24) or (tint and 0x00FFFFFF)
        val drawSize = rect.height - 12
        val drawX = rect.left + (rect.width - drawSize) / 2
        val drawY = rect.top + (rect.height - drawSize) / 2
        renderBlankTextureMiniFrame(guiGraphics, currentFrame, drawX, drawY, drawSize, argb, 1f - interpolation)
        if (nextFrame != null && interpolation > 0.001f) {
            renderBlankTextureMiniFrame(guiGraphics, nextFrame, drawX, drawY, drawSize, argb, interpolation)
        }
    }

    private fun renderBlankTextureMiniFrame(
        guiGraphics: GuiGraphics,
        frame: BlankTexturePreviewFrame,
        x: Int,
        y: Int,
        size: Int,
        tint: Int,
        alphaFactor: Float
    ) {
        val a = (((tint ushr 24) and 0xFF) * alphaFactor).toInt().coerceIn(0, 255)
        val modulatedTint = (a shl 24) or (tint and 0x00FFFFFF)
        guiGraphics.blit(
            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            frame.textureId,
            x,
            y,
            frame.uvX.toFloat(),
            frame.uvY.toFloat(),
            size,
            size,
            frame.uvWidth,
            frame.uvHeight,
            frame.textureWidth,
            frame.textureHeight,
            modulatedTint
        )
    }

    private fun blendPreviewColors(start: Int, end: Int, progress: Float): Int {
        val p = progress.coerceIn(0f, 1f)
        val sr = (start shr 16) and 0xFF
        val sg = (start shr 8) and 0xFF
        val sb = start and 0xFF
        val er = (end shr 16) and 0xFF
        val eg = (end shr 8) and 0xFF
        val eb = end and 0xFF
        val r = (sr + (er - sr) * p).toInt().coerceIn(0, 255)
        val g = (sg + (eg - sg) * p).toInt().coerceIn(0, 255)
        val b = (sb + (eb - sb) * p).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun resolveBlankTexturePreviewFrame(step: Int): BlankTexturePreviewFrame? {
        val emitter = blankEmitter()
        if (emitter.renderType != HudCelebrationRenderType.TEXTURE) return null
        val frames = blankTextureFrameSequence(emitter)
        val sequenceTokens = blankTextureFrameTokens(emitter)
        val frameCount = when (emitter.textureMode) {
            HudCelebrationTextureMode.SEQUENCE -> sequenceTokens.size.coerceAtLeast(1)
            else -> frames.size.coerceAtLeast(1)
        }
        val currentFrame = when (emitter.textureMode) {
            HudCelebrationTextureMode.SINGLE -> 0
            HudCelebrationTextureMode.SHEET, HudCelebrationTextureMode.SEQUENCE -> {
                step.wrapIndex(frameCount)
            }
        }
        val textureId = when (emitter.textureMode) {
            HudCelebrationTextureMode.SEQUENCE -> resolveBlankSequenceTextureIdentifier(emitter.textureId, sequenceTokens.getOrElse(currentFrame) { "0" })
                ?: resolveBlankSequenceTextureIdentifier(emitter.textureId, "0")
                ?: resolveBlankTextureIdentifier(emitter.textureId)
            else -> resolveBlankTextureIdentifier(emitter.textureId)
        } ?: return null
        val textureSize = resolveBlankTextureSize(textureId) ?: return null
        val uvX = when (emitter.textureMode) {
            HudCelebrationTextureMode.SHEET -> {
                val frameValue = frames.getOrElse(currentFrame) { 0 }
                emitter.textureUvX + (frameValue % emitter.textureFramesX.coerceAtLeast(1)) * emitter.textureUvWidth
            }
            else -> emitter.textureUvX
        }
        val uvY = when (emitter.textureMode) {
            HudCelebrationTextureMode.SHEET -> {
                val frameValue = frames.getOrElse(currentFrame) { 0 }
                emitter.textureUvY + (frameValue / emitter.textureFramesX.coerceAtLeast(1)) * emitter.textureUvHeight
            }
            else -> emitter.textureUvY
        }
        return BlankTexturePreviewFrame(
            textureId = textureId,
            uvX = uvX,
            uvY = uvY,
            uvWidth = emitter.textureUvWidth.coerceAtLeast(1),
            uvHeight = emitter.textureUvHeight.coerceAtLeast(1),
            textureWidth = textureSize.first,
            textureHeight = textureSize.second
        )
    }

    private fun blankTextureFrameSequence(emitter: HudCelebrationEmitterConfig): List<Int> {
        val fallbackCount = when (emitter.textureMode) {
            HudCelebrationTextureMode.SINGLE -> 1
            HudCelebrationTextureMode.SHEET -> (emitter.textureFramesX.coerceAtLeast(1) * emitter.textureFramesY.coerceAtLeast(1)).coerceAtLeast(1)
            HudCelebrationTextureMode.SEQUENCE -> max(1, expandBlankFrameTokens(emitter.textureFrameOrder).size)
        }
        val custom = expandBlankFrameOrder(emitter.textureFrameOrder)
        return if (custom.isEmpty()) (0 until fallbackCount).toList() else custom
    }

    private fun blankTextureFrameTokens(emitter: HudCelebrationEmitterConfig): List<String> {
        val custom = expandBlankFrameTokens(emitter.textureFrameOrder)
        return if (custom.isEmpty()) listOf("0") else custom
    }

    private fun expandBlankFrameOrder(raw: String): List<Int> =
        raw.split(',', ';', ' ', '\n', '\r', '\t')
            .flatMap { token ->
                val trimmed = token.trim()
                when {
                    trimmed.isEmpty() -> emptyList()
                    ".." in trimmed -> {
                        val parts = trimmed.split("..", limit = 2)
                        val from = parts.getOrNull(0)?.trim()?.toIntOrNull()
                        val to = parts.getOrNull(1)?.trim()?.toIntOrNull()
                        if (from == null || to == null) emptyList()
                        else if (from <= to) (from..to).toList()
                        else (from downTo to).toList()
                    }
                    else -> trimmed.toIntOrNull()?.let(::listOf) ?: emptyList()
                }
            }

    private fun expandBlankFrameTokens(raw: String): List<String> =
        raw.split(',', ';', ' ', '\n', '\r', '\t')
            .flatMap { token ->
                val trimmed = token.trim()
                when {
                    trimmed.isEmpty() -> emptyList()
                    ".." in trimmed -> {
                        val parts = trimmed.split("..", limit = 2)
                        val from = parts.getOrNull(0)?.trim()?.toIntOrNull()
                        val to = parts.getOrNull(1)?.trim()?.toIntOrNull()
                        if (from == null || to == null) listOf(trimmed)
                        else if (from <= to) (from..to).map(Int::toString)
                        else (from downTo to).map(Int::toString)
                    }
                    else -> listOf(trimmed)
                }
            }

    private fun resolveBlankTextureIdentifier(textureId: String): Identifier? {
        val parsed = Identifier.tryParse(textureId) ?: return null
        return if (parsed.path.startsWith("textures/") || parsed.path.endsWith(".png")) {
            parsed
        } else {
            Identifier.fromNamespaceAndPath(parsed.namespace, "textures/${parsed.path}.png")
        }
    }

    private fun resolveBlankSequenceTextureIdentifier(pattern: String, frameToken: String): Identifier? {
        val candidates = buildList {
            if (pattern.contains("%d")) {
                add(pattern.replace("%d", frameToken))
            } else {
                add(pattern)
            }
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            resolveBlankTextureIdentifier(candidate)?.takeIf { resolveBlankTextureSize(it) != null }
        }
    }

    private fun resolveBlankTextureSize(textureId: Identifier): Pair<Int, Int>? {
        val resource = minecraft?.resourceManager?.getResource(textureId)?.orElse(null) ?: return null
        return resource.open().use { stream ->
            com.mojang.blaze3d.platform.NativeImage.read(stream).use { image ->
                image.width to image.height
            }
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
        blankOverlayRect = overlay
        val titleX = overlay.left + scaledBodyInnerMargin
        val titleY = overlay.top + scaledBodyInnerMargin
        val contentTop = titleY + font.lineHeight + 12
        val gap = scaledPanelGap
        val leftWidth = (overlay.width * blankOverlaySplitRatio).toInt().coerceIn(110, overlay.width / 2)

        val leftHeight = overlay.bottom - contentTop - scaledBodyInnerMargin
        val previewHeight = (leftHeight * blankOverlayPreviewRatio).toInt().coerceIn(110, leftHeight - 90)
        blankOverlayLeftRect = Rect(overlay.left + scaledBodyInnerMargin, contentTop, leftWidth, (leftHeight - previewHeight - gap).coerceAtLeast(80))
        blankOverlayPreviewRect = Rect(blankOverlayLeftRect.left, blankOverlayLeftRect.bottom + gap, leftWidth, previewHeight)
        val rightLeft = blankOverlayLeftRect.right + gap
        blankOverlayRightRect = Rect(rightLeft, contentTop, overlay.right - rightLeft - scaledBodyInnerMargin, overlay.bottom - contentTop - scaledBodyInnerMargin)

        guiGraphics.fill(body.left, body.top, body.right, body.bottom, 0xAA000000.toInt())
        guiGraphics.fill(overlay.left, overlay.top, overlay.right, overlay.bottom, 0xE010141B.toInt())
        drawOutline(guiGraphics, overlay, 0x66FFFFFF)
        guiGraphics.drawString(font, "GoFindWin settings > effects > edit variable > $blankOverlayTitle", titleX, titleY, 0xFFFFFFFF.toInt())

        guiGraphics.fill(blankOverlayLeftRect.left, blankOverlayLeftRect.top, blankOverlayLeftRect.right, blankOverlayLeftRect.bottom, 0x1AFFFFFF)
        guiGraphics.fill(blankOverlayPreviewRect.left, blankOverlayPreviewRect.top, blankOverlayPreviewRect.right, blankOverlayPreviewRect.bottom, 0x1AFFFFFF)
        guiGraphics.fill(blankOverlayRightRect.left, blankOverlayRightRect.top, blankOverlayRightRect.right, blankOverlayRightRect.bottom, 0x1AFFFFFF)
        drawOutline(guiGraphics, blankOverlayLeftRect, 0x40FFFFFF)
        drawOutline(guiGraphics, blankOverlayPreviewRect, 0x40FFFFFF)
        drawOutline(guiGraphics, blankOverlayRightRect, 0x40FFFFFF)
        val splitHandle = blankOverlaySplitHandleRect()
        val columnHandle = blankOverlayColumnHandleRect()
        guiGraphics.fill(
            splitHandle.left,
            splitHandle.top,
            splitHandle.right,
            splitHandle.bottom,
            when {
                draggingBlankOverlaySplit -> 0xCCFFFFFF.toInt()
                splitHandle.contains(mouseX.toDouble(), mouseY.toDouble()) -> 0xAAFFFFFF.toInt()
                else -> 0x66FFFFFF.toInt()
            }
        )
        guiGraphics.fill(
            columnHandle.left,
            columnHandle.top,
            columnHandle.right,
            columnHandle.bottom,
            when {
                draggingBlankOverlayColumnSplit -> 0xCCFFFFFF.toInt()
                columnHandle.contains(mouseX.toDouble(), mouseY.toDouble()) -> 0xAAFFFFFF.toInt()
                else -> 0x66FFFFFF.toInt()
            }
        )

        blankOverlayInfoTargets.clear()
        val infoX = blankOverlayLeftRect.left + 10
        var y = blankOverlayLeftRect.top + 10 + blankOverlayInfoScrollOffset
        val visibleTop = blankOverlayLeftRect.top + 6
        val visibleBottom = blankOverlayLeftRect.bottom - 6
        guiGraphics.enableScissor(
            blankOverlayLeftRect.left + 4,
            blankOverlayLeftRect.top + 4,
            blankOverlayLeftRect.right - 4,
            blankOverlayLeftRect.bottom - 4
        )
        fun drawInfoEntry(label: String, color: Int, tooltip: String? = null, extraGap: Int = 0) {
            val rowTop = y
            val rowBottom = y + font.lineHeight
            if (rowBottom >= visibleTop && rowTop <= visibleBottom) {
                guiGraphics.drawString(font, label, infoX, y, color)
                if (tooltip != null) {
                    blankOverlayInfoTargets[tooltip] = Rect(infoX - 2, y - 1, font.width(label) + 4, font.lineHeight + 2)
                }
            }
            y += font.lineHeight + extraGap
        }
        drawInfoEntry("Variables", 0xFFFFE28A.toInt(), extraGap = 8)
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
            drawInfoEntry(label, 0xFFFFFFFF.toInt(), tooltip, 4)
        }
        y += 8
        drawInfoEntry("Functions", 0xFFFFE28A.toInt(), extraGap = 8)
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
            drawInfoEntry(label, 0xFFFFFFFF.toInt(), tooltip, 4)
        }
        y += 8
        drawInfoEntry("Vanilla colors", 0xFFFFE28A.toInt(), extraGap = 8)
        val colorEntries = listOf(
            "black, dark_blue, dark_green, dark_aqua" to "Vanilla palette constants",
            "dark_red, dark_purple, gold, gray" to "Vanilla palette constants",
            "dark_gray, blue, green, aqua" to "Vanilla palette constants",
            "red, light_purple, yellow, white" to "Vanilla palette constants"
        )
        colorEntries.forEach { (label, tooltip) ->
            drawInfoEntry(label, 0xFFFFFFFF.toInt(), tooltip, 4)
        }
        y += 8
        drawInfoEntry("Keyframes", 0xFFFFE28A.toInt(), extraGap = 8)
        val keyframeEntries = listOf(
            "keyframe(t) { ... }" to "keyframe(axis) supports points, ranges and { value = ..., easing = ... }",
            "0.0 = 1" to "Point keyframe: exact value at time 0.0",
            "0.5..0.75 = 0.5" to "Range keyframe: hold this value from 0.5 to 0.75",
            "1.0 = { value = 0, easing = 2 }" to "Object keyframe with easing. easing = 1 means linear",
            "0.52 = { value = pos_x + 10, easing = 0.375 }" to "Procedural keyframe value. Any normal numeric expression is allowed inside value"
        )
        keyframeEntries.forEach { (label, tooltip) ->
            drawInfoEntry(label, 0xFFFFFFFF.toInt(), tooltip, 4)
        }
        y += 8
        drawInfoEntry("Info", 0xFFFFE28A.toInt(), extraGap = 8)
        drawInfoEntry("a = rand_x; b = age_tick;", 0xFFFFFFFF.toInt(), extraGap = 4)
        drawInfoEntry("sin(a * 360) * b;", 0xFFFFFFFF.toInt())
        y += 8
        drawInfoEntry("Alpha example", 0xFFFFE28A.toInt(), extraGap = 6)
        drawInfoEntry("t = life_factor;", 0xFFFFFFFF.toInt(), extraGap = 4)
        drawInfoEntry("keyframe(t) { 0.0 = 1, 0.8 = { value = 1, easing = 1 }, 1.0 = { value = 0, easing = 2 } }", 0xFFFFFFFF.toInt(), extraGap = 8)
        drawInfoEntry("Scale example", 0xFFFFE28A.toInt(), extraGap = 6)
        drawInfoEntry("t = life_factor;", 0xFFFFFFFF.toInt(), extraGap = 4)
        drawInfoEntry("keyframe(t) { 0.0 = 0.5, 0.2 = 1.0, 1.0 = 0.2 }", 0xFFFFFFFF.toInt(), extraGap = 8)
        drawInfoEntry("Color example", 0xFFFFE28A.toInt(), extraGap = 6)
        drawInfoEntry("t = life_factor;", 0xFFFFFFFF.toInt(), extraGap = 4)
        drawInfoEntry("keyframe(t) { 0.0 = #FF0000, 0.5 = { value = #00FF00, easing = 1 }, 1.0 = #0000FF }", 0xFFFFFFFF.toInt(), extraGap = 8)
        drawInfoEntry("Multiply color example", 0xFFFFE28A.toInt(), extraGap = 6)
        drawInfoEntry("t = life_factor;", 0xFFFFFFFF.toInt(), extraGap = 4)
        drawInfoEntry("keyframe(t) { 0.0 = #FFFFFF, 1.0 = #66AAFF }", 0xFFFFFFFF.toInt())
        guiGraphics.disableScissor()
        blankOverlayInfoContentHeight = (y - blankOverlayInfoScrollOffset) - blankOverlayLeftRect.top + 10
        renderBlankOverlayInfoScrollbar(guiGraphics)

        guiGraphics.drawString(font, "Preview", blankOverlayPreviewRect.left + 10, blankOverlayPreviewRect.top + 10, 0xFFFFE28A.toInt())
        guiGraphics.enableScissor(
            blankOverlayPreviewRect.left + 8,
            blankOverlayPreviewRect.top + 26,
            blankOverlayPreviewRect.right - 8,
            blankOverlayPreviewRect.bottom - 8
        )
        HudCelebrationEffectsClient.render(guiGraphics, previewBoundsFor(blankOverlayPreviewRect.inset(14)), null, preview = true)
        guiGraphics.disableScissor()

        blankOverlayField.visible = true
        blankOverlayField.active = true
        blankOverlayField.setPosition(blankOverlayRightRect.left + 10, blankOverlayRightRect.top + 10)
        blankOverlayField.setWidth(blankOverlayRightRect.width - 20)
        blankOverlayField.height = blankOverlayRightRect.height - 20
        blankOverlayField.render(guiGraphics, mouseX, mouseY, 0f)

        val hoveredInfo = blankOverlayInfoTargets.entries.firstOrNull {
            it.value.contains(mouseX.toDouble(), mouseY.toDouble()) && blankOverlayLeftRect.contains(mouseX.toDouble(), mouseY.toDouble())
        }?.key
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
        val item = blankEmitter().renderType == HudCelebrationRenderType.ITEMSTACK
        val text = blankEmitter().renderType == HudCelebrationRenderType.TEXT
        blankRenderTypeButton.visible = true
        blankRenderTypeButton.active = true
        blankColorField.visible = color
        blankColorField.active = color
        listOf(
            blankTextureModeButton,
            blankTextureIdField,
            blankPickTextureButton,
            blankTextureTintField,
            blankUvXField,
            blankUvYField,
            blankUvWidthField,
            blankUvHeightField
        ).forEach {
            it.visible = texture
            it.active = texture
        }
        blankFramesXField.visible = sheet
        blankFramesXField.active = sheet
        blankFramesYField.visible = sheet
        blankFramesYField.active = sheet
        blankFramesField.visible = sheet || sequence
        blankFramesField.active = sheet || sequence
        blankFrameTimeField.visible = sheet || sequence
        blankFrameTimeField.active = sheet || sequence
        blankTextureInterpolateButton.visible = sheet || sequence
        blankTextureInterpolateButton.active = sheet || sequence
        blankItemIdField.visible = item
        blankItemIdField.active = item
        blankPickItemButton.visible = item
        blankPickItemButton.active = item
        blankUseTargetItemButton.visible = item
        blankUseTargetItemButton.active = item
        blankTextContentField.visible = text
        blankTextContentField.active = text
        blankTextColorField.visible = text
        blankTextColorField.active = text
        blankTextSizeField.visible = text
        blankTextSizeField.active = text
        blankTextShadowButton.visible = text
        blankTextShadowButton.active = text
        blankTextShadowColorField.visible = text
        blankTextShadowColorField.active = text
    }

    private fun hoveredBlankEditableField(mouseX: Double, mouseY: Double): StyledEditField? {
        return listOf(
            blankCountField,
            blankRateField,
            blankLifetimeField,
            blankOriginXField,
            blankOriginYField,
            blankColorField,
            blankTextureIdField,
            blankTextureTintField,
            blankUvXField,
            blankUvYField,
            blankUvWidthField,
            blankUvHeightField,
            blankFramesXField,
            blankFramesYField,
            blankFramesField,
            blankFrameTimeField,
            blankItemIdField,
            blankTextContentField,
            blankTextColorField,
            blankTextSizeField,
            blankTextShadowColorField,
            blankPositionXField,
            blankPositionYField,
            blankRotationField,
            blankScaleField,
            blankAlphaField
        ).firstOrNull { it.visible && it.isMouseOver(mouseX, mouseY) }
    }

    private fun openBlankOverlay(field: StyledEditField) {
        blankOverlayTarget = field
        blankOverlayTitle = blankOverlayTitleFor(field)
        blankOverlayOpen = true
        blankOverlayInfoScrollOffset = 0
        blankOverlayField.visible = true
        blankOverlayField.active = true
        blankOverlayField.setValue(field.value)
        blankOverlayField.setFocused(true)
        setFocused(blankOverlayField)
        queuePreview(forceNow = true)
    }

    private fun closeBlankOverlay() {
        blankOverlayOpen = false
        blankOverlayTarget = null
        blankOverlayTitle = "variable"
        blankOverlayInfoTargets.clear()
        blankOverlayField.visible = false
        blankOverlayField.active = false
        blankOverlayField.setFocused(false)
        setFocused(null)
        queuePreview(forceNow = true)
    }

    private fun blankOverlayTitleFor(field: StyledEditField): String = when (field) {
        blankCountField -> "Count"
        blankRateField -> "Particles per minute"
        blankLifetimeField -> "Lifetime"
        blankOriginXField, blankOriginYField -> "Origin"
        blankColorField -> "Color"
        blankTextureIdField -> "Texture ID"
        blankTextureTintField -> "Multiply color"
        blankUvXField, blankUvYField, blankUvWidthField, blankUvHeightField -> "UV"
        blankFramesXField, blankFramesYField -> "Frames X / Y"
        blankFramesField -> "Frames"
        blankFrameTimeField -> "Frame time ms"
        blankItemIdField -> "Item ID"
        blankTextContentField -> "Text"
        blankTextColorField -> "Text color"
        blankTextSizeField -> "Text size"
        blankTextShadowColorField -> "Shadow color"
        blankPositionXField, blankPositionYField -> "Position"
        blankRotationField -> "Rotation"
        blankScaleField -> "Scale"
        blankAlphaField -> "Alpha"
        else -> "variable"
    }

    private fun blankOverlaySplitHandleRect(): Rect {
        val centerX = blankOverlayLeftRect.right + scaledPanelGap / 2
        return Rect(centerX - 2, blankOverlayRightRect.centerY - 18, 4, 36)
    }

    private fun blankOverlayColumnHandleRect(): Rect {
        val centerY = blankOverlayLeftRect.bottom + scaledPanelGap / 2
        return Rect(blankOverlayLeftRect.centerX - 18, centerY - 2, 36, 4)
    }

    private fun blankOverlayInfoMinScrollOffset(): Int {
        val visibleHeight = (blankOverlayLeftRect.height - 12).coerceAtLeast(1)
        return -(blankOverlayInfoContentHeight - visibleHeight).coerceAtLeast(0)
    }

    private fun blankOverlayInfoScrollbarTrackRect(): Rect {
        val left = blankOverlayLeftRect.right - 10
        return Rect(left, blankOverlayLeftRect.top + 8, 4, (blankOverlayLeftRect.height - 16).coerceAtLeast(1))
    }

    private fun blankOverlayInfoScrollbarThumbRect(): Rect {
        val track = blankOverlayInfoScrollbarTrackRect()
        val visibleHeight = (blankOverlayLeftRect.height - 12).coerceAtLeast(1)
        val contentHeight = blankOverlayInfoContentHeight.coerceAtLeast(visibleHeight)
        val thumbHeight = ((visibleHeight.toFloat() / contentHeight.toFloat()) * track.height).toInt().coerceIn(18, track.height)
        val scrollRange = (-blankOverlayInfoMinScrollOffset()).coerceAtLeast(1)
        val progress = (-blankOverlayInfoScrollOffset).toFloat() / scrollRange.toFloat()
        val thumbTravel = (track.height - thumbHeight).coerceAtLeast(0)
        val thumbTop = track.top + (thumbTravel * progress).toInt()
        return Rect(track.left, thumbTop, track.width, thumbHeight)
    }

    private fun renderBlankOverlayInfoScrollbar(guiGraphics: GuiGraphics) {
        val track = blankOverlayInfoScrollbarTrackRect()
        val thumb = blankOverlayInfoScrollbarThumbRect()
        guiGraphics.fill(track.left, track.top, track.right, track.bottom, 0x40343C46)
        guiGraphics.fill(track.left, thumb.top, track.right, thumb.bottom, 0xFFD7DEE6.toInt())
    }

    private fun updateBlankOverlayInfoScrollFromMouse(mouseY: Double) {
        val track = blankOverlayInfoScrollbarTrackRect()
        val thumb = blankOverlayInfoScrollbarThumbRect()
        val thumbTravel = (track.height - thumb.height).coerceAtLeast(0)
        val thumbCenter = mouseY.toFloat() - thumb.height / 2f
        val normalized = ((thumbCenter - track.top) / max(1f, thumbTravel.toFloat())).coerceIn(0f, 1f)
        blankOverlayInfoScrollOffset = -((-blankOverlayInfoMinScrollOffset()) * normalized).toInt()
    }

    private fun updateBlankOverlaySplit(mouseX: Double) {
        val body = bodyRect()
        val inset = max(28, scaledBodyInnerMargin * 2)
        val overlay = body.inset(inset)
        val contentLeft = overlay.left + scaledBodyInnerMargin
        val contentWidth = overlay.width - scaledBodyInnerMargin * 2
        val ratio = ((mouseX - contentLeft) / contentWidth.toDouble()).toFloat()
        blankOverlaySplitRatio = ratio.coerceIn(0.16f, 0.42f)
    }

    private fun updateBlankOverlayColumnSplit(mouseY: Double) {
        val body = bodyRect()
        val inset = max(28, scaledBodyInnerMargin * 2)
        val overlay = body.inset(inset)
        val contentTop = overlay.top + scaledBodyInnerMargin + font.lineHeight + 12
        val totalHeight = overlay.bottom - contentTop - scaledBodyInnerMargin
        val previewHeight = (overlay.bottom - scaledBodyInnerMargin - mouseY).toFloat()
        val ratio = (previewHeight / totalHeight.toFloat()).coerceIn(0.18f, 0.58f)
        blankOverlayPreviewRatio = ratio
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
    private fun blankUseTargetItemLabel(): String = if (blankEmitter().useTargetItem) "Use target item: on" else "Use target item: off"

    private fun blankTextShadowLabel(): String = if (blankEmitter().textShadowEnabled) "Shadow: on" else "Shadow: off"

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
        return generalTop + 18 + 5 * (fieldHeight + 10) + layout.sectionGap
    }

    private fun blankVisualRowCount(): Int = when (blankEmitter().renderType) {
        HudCelebrationRenderType.PARTICLE -> 4
        HudCelebrationRenderType.TEXTURE -> when (blankEmitter().textureMode) {
            HudCelebrationTextureMode.SINGLE -> 7
            HudCelebrationTextureMode.SHEET -> 11
            HudCelebrationTextureMode.SEQUENCE -> 10
        }
        HudCelebrationRenderType.ITEMSTACK -> 3
        HudCelebrationRenderType.TEXT -> 4
        else -> 2
    }

    private fun blankControlSectionTop(layout: BlankLayout): Int {
        val visualTop = blankVisualSectionTop(layout)
        return visualTop + 18 + blankVisualRowCount() * (fieldHeight + 10) + layout.sectionGap
    }

    private fun blankTooltipText(key: String): String = when (key) {
        "emitter_type" -> "Какой пресет финиш-эффекта редактируется сейчас."
        "spawn_mode" -> "Одиночный спавнит один burst, постоянный поддерживает поток частиц."
        "origin" -> "Базовая точка спавна частицы. Поддерживаются формулы и HUD-переменные: hud_left, hud_right, hud_top, hud_bottom, hud_center_x, hud_center_y."
        "spawn_amount" -> "Если поле пустое, будет использовано 0."
        "lifetime" -> "Время жизни частицы в тиках. Если поле пустое, будет 0."
        "visual_type" -> "Тип визуального представления частицы."
        "visual_color" -> "HEX-цвет или палитра через запятую: #FF0000,#00FF00,#0000FF. Если пусто, используется #FFFFFF."
        "visual_lerp_color" -> "Цвет или палитра, в которую частица будет переходить. Если пусто, перехода нет."
        "visual_lerp_factor" -> "Фактор перехода. Если пусто, используется life_factor."
        "visual_uv" -> "UV: X, Y, W, H в пикселях."
        "visual_texture_mode" -> "Single, Spritesheet или Sequence."
        "visual_texture_id" -> "Identifier текстуры. Если пусто, будет error-texture."
        "visual_texture_tint" -> "Multiply color для текстуры. Можно указывать палитру через запятую."
        "visual_texture_tint_lerp" -> "Цвет или палитра, в которую будет переходить multiply color."
        "visual_texture_tint_factor" -> "Фактор перехода multiply color. Если пусто, используется life_factor."
        "visual_frames_xy" -> "Если пусто, позже можно будет подбирать автоматически."
        "visual_frames" -> "Порядок кадров: 0,1,2,3 или диапазон 0..11. Для Sequence можно указывать токены: a,b,c,01,fx_a."
        "visual_frame_time" -> "Скорость проигрывания анимации в миллисекундах на кадр."
        "visual_interpolate" -> "Плавный переход между кадрами и выбор случайного кадра на частицу."
        "visual_item_id" -> "Identifier предмета. Если пусто, будет использован minecraft:barrier."
        "visual_use_target_item" -> "Использовать предмет текущей цели вместо фиксированного item id."
        "visual_text_content" -> "Текст частицы. Поддерживаются переносы через \\n."
        "visual_text_style" -> "Цвет текста и размер."
        "visual_text_shadow" -> "Тень текста и цвет тени."
        "control_position" -> "Положение частицы: X и Y."
        "control_rotation" -> "Поворот частицы в градусах."
        "control_scale" -> "Размер частицы. 1.0 = базовый размер."
        "control_alpha" -> "Прозрачность частицы. 0..1."
        else -> "example_tooltip_info :P"
    }

    private fun parseColorList(raw: String): List<String> =
        if (isDynamicColorExpression(raw)) {
            listOf(raw.trim()).filter { it.isNotEmpty() }
        } else {
            raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    private fun isDynamicColorExpression(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.contains("keyframe(", ignoreCase = true) ||
            trimmed.contains(';') ||
            trimmed.contains('{') ||
            trimmed.contains('(') ||
            trimmed.contains(')') ||
            trimmed.contains('+') ||
            trimmed.contains('*') ||
            trimmed.contains('/') ||
            Regex("(?<!^)-(?!$)").containsMatchIn(trimmed)
    }

    private fun blankHoveredFieldError(mouseX: Double, mouseY: Double): String? {
        val field = hoveredBlankEditableField(mouseX, mouseY) ?: return null
        return when (field) {
            blankColorField,
            blankTextureTintField,
            blankTextColorField,
            blankTextShadowColorField -> validateColorInput(field.value)
            else -> null
        }
    }

    private fun validateColorInput(raw: String): String? {
        if (raw.isBlank() || isDynamicColorExpression(raw)) return null
        val invalid = raw.split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && parseColorLiteralOrNull(it) == null }
        return invalid?.let { "Invalid color: $it" }
    }

    private fun previewTextColor(raw: String): Int {
        val first = if (isDynamicColorExpression(raw)) {
            Regex("(#|0x)[0-9A-Fa-f]{6,8}").find(raw)?.value ?: raw.trim()
        } else {
            parseColorList(raw).firstOrNull() ?: raw.trim()
        }
        return parseColorLiteralOrNull(first) ?: 0xFFF7F8FA.toInt()
    }

    private fun parseColorLiteralOrNull(raw: String): Int? {
        val value = raw.trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
        return when (value.length) {
            6 -> value.toLongOrNull(16)?.toInt()?.let { 0xFF000000.toInt() or it }
            8 -> value.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    private fun Int.wrapIndex(size: Int): Int {
        if (size <= 0) return 0
        val mod = this % size
        return if (mod < 0) mod + size else mod
    }


    private fun toggleBlankUseTargetItem() {
        mutateEmitter { it.copy(useTargetItem = !it.useTargetItem) }
        blankUseTargetItemButton.setLabel(Component.literal(blankUseTargetItemLabel()))
    }

    private fun toggleBlankTextShadow() {
        mutateEmitter { it.copy(textShadowEnabled = !it.textShadowEnabled) }
        blankTextShadowButton.setLabel(Component.literal(blankTextShadowLabel()))
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
        val bounds = previewBounds()
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
            "start_pos_y" to 0.0,
            "hud_left" to bounds.left.toDouble(),
            "hud_right" to bounds.right.toDouble(),
            "hud_top" to bounds.top.toDouble(),
            "hud_bottom" to bounds.bottom.toDouble(),
            "hud_center_x" to bounds.centerX.toDouble(),
            "hud_center_y" to bounds.centerY.toDouble()
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
