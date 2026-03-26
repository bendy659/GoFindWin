package ru.benos_codex.gofindwin.client.hud.effects

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class HudExpandedFieldEditorScreen(
    private val parent: Screen,
    initialValue: String,
    private val fieldLabel: String,
    private val onApply: (String) -> Unit
) : Screen(Component.literal("Expanded Field Editor")) {
    private lateinit var editorField: MultiLineEditBox
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private val startValue = initialValue

    override fun init() {
        val fieldWidth = (width - 120).coerceAtLeast(320)
        val fieldX = (width - fieldWidth) / 2
        val fieldTop = 66
        val fieldHeight = (height - 230).coerceAtLeast(180)
        editorField = addRenderableWidget(
            MultiLineEditBox.builder()
                .setX(fieldX)
                .setY(fieldTop)
                .setPlaceholder(Component.literal("Formula or text"))
                .setShowBackground(true)
                .setShowDecorations(true)
                .build(font, fieldWidth, fieldHeight, Component.literal(fieldLabel))
                .apply {
            setValue(startValue)
            setCharacterLimit(4096)
            setLineLimit(200)
        })
        saveButton = addRenderableWidget(
            Button.builder(Component.literal("Apply")) {
                onApply(editorField.value)
                minecraft.setScreen(parent)
            }.bounds(fieldX, fieldTop + fieldHeight + 20, (fieldWidth - 8) / 2, 24).build()
        )
        cancelButton = addRenderableWidget(
            Button.builder(Component.literal("Cancel")) {
                minecraft.setScreen(parent)
            }.bounds(fieldX + (fieldWidth - 8) / 2 + 8, fieldTop + fieldHeight + 20, (fieldWidth - 8) / 2, 24).build()
        )
        setInitialFocus(editorField)
        editorField.setFocused(true)
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xD010161D.toInt())
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val infoX = width / 2
        val infoTop = saveButton.y + saveButton.height + 12
        guiGraphics.drawCenteredString(font, Component.literal(fieldLabel), width / 2, 34, 0xFFFFFFFF.toInt())
        guiGraphics.drawCenteredString(font, Component.literal("Right click a field to reopen this editor"), infoX, infoTop, 0xFFC8D0D8.toInt())
        guiGraphics.drawCenteredString(font, Component.literal("Functions: sin cos sin_deg cos_deg pow mod pos abs min max"), infoX, infoTop + 12, 0xFFC8D0D8.toInt())
        guiGraphics.drawCenteredString(font, Component.literal("Vars: rand rand_x rand_y pos_x pos_y life_time age_tick age_frame"), infoX, infoTop + 24, 0xFFC8D0D8.toInt())
        guiGraphics.drawCenteredString(font, Component.literal("Also: age_tick_delta age_frame_delta, + - * / % ^, ?:, &&, ||"), infoX, infoTop + 36, 0xFFC8D0D8.toInt())
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }
}
