package ru.benos_codex.gofindwin.client.hud

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import ru.benos_codex.gofindwin.client.hud.effects.HudCelebrationEffectsClient
import ru.benos_codex.gofindwin.client.hud.effects.HudRenderBounds
import ru.benos_codex.gofindwin.GoFindWinConstants
import ru.benos_codex.gofindwin.run.RunPhase

object RunTimerHudRenderer {
    private val hudId: Identifier = Identifier.fromNamespaceAndPath(GoFindWinConstants.MOD_ID, "run_timer")
    private val slotSprite: Identifier = Identifier.withDefaultNamespace("container/slot")
    private val tooltipBackgroundSprite: Identifier = Identifier.withDefaultNamespace("tooltip/background")
    private val tooltipFrameSprite: Identifier = Identifier.withDefaultNamespace("tooltip/frame")

    fun initialize() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, hudId) { guiGraphics, deltaTracker ->
            render(guiGraphics, deltaTracker)
        }
    }

    fun render(guiGraphics: GuiGraphics, deltaTracker: net.minecraft.client.DeltaTracker) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.options.hideGui) return
        if (minecraft.player == null) return

        val hudState = RunTimerHudStateStore.state
        if (!hudState.visible) return

        val config = TimerHudConfigManager.config
        val font = minecraft.font
        val timeText = RunTimerFormatter.format(hudState.elapsedMs)
        val timerTextColor = resolveTimerColor(hudState, config)
        val itemStack = if (config.showTargetItem) resolveTargetItemStack(hudState.targetItemId) else null
        val itemName = itemStack?.hoverName?.string

        val slotSize = config.targetItemSlotSize
        val headerText = itemName ?: ""
        val headerWidth = if (headerText.isNotEmpty()) font.width(headerText) else 0
        val tooltipPaddingX = 4
        val tooltipPaddingY = 3
        val tooltipWidth = if (headerText.isNotEmpty()) headerWidth + tooltipPaddingX * 2 else 0
        val tooltipHeight = if (headerText.isNotEmpty()) font.lineHeight + tooltipPaddingY * 2 else 0
        val timerWidth = font.width(timeText)
        val contentWidth = maxOf(timerWidth, tooltipWidth, if (itemStack != null) slotSize else 0)

        val headerHeight = tooltipHeight
        val slotHeight = if (itemStack != null) slotSize else 0
        val timerHeight = font.lineHeight

        val verticalGap = 4
        var contentHeight = timerHeight
        if (slotHeight > 0) contentHeight += slotHeight + verticalGap
        if (headerHeight > 0) contentHeight += headerHeight + verticalGap

        val panelWidth = contentWidth + config.paddingX * 2
        val panelHeight = contentHeight + config.paddingY * 2

        val baseX = guiGraphics.guiWidth() / 2
        val baseY = guiGraphics.guiHeight() - 22
        val originX = baseX + config.offsetX
        val originY = baseY + config.offsetY

        val pose = guiGraphics.pose()
        pose.pushMatrix()
        pose.translate(originX.toFloat(), originY.toFloat())
        if (config.rotationDeg != 0f) {
            pose.rotate(Math.toRadians(config.rotationDeg.toDouble()).toFloat())
        }
        pose.scale(config.scale, config.scale)

        val left = -panelWidth / 2
        val top = -panelHeight
        val right = left + panelWidth
        val bottom = top + panelHeight

        HudCelebrationEffectsClient.render(guiGraphics, deltaTracker, HudRenderBounds(left, top, right, bottom), hudState.targetItemId, preview = false)
        guiGraphics.fill(left, top, right, bottom, config.backgroundColor)
        renderBorder(guiGraphics, config, left, top, right, bottom)

        var currentY = top + config.paddingY

        if (headerText.isNotEmpty()) {
            val tooltipLeft = -(tooltipWidth / 2)
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, tooltipBackgroundSprite, tooltipLeft, currentY, tooltipWidth, tooltipHeight)
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, tooltipFrameSprite, tooltipLeft, currentY, tooltipWidth, tooltipHeight)
            guiGraphics.drawCenteredString(font, headerText, 0, currentY + tooltipPaddingY, config.textColor)
            currentY += headerHeight + verticalGap
        }

        if (itemStack != null) {
            val slotLeft = -(slotSize / 2)
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, slotSprite, slotLeft, currentY, slotSize, slotSize)

            val itemScale = 1.15f
            val scaledItemSize = 16f * itemScale
            val iconOffset = ((slotSize - scaledItemSize) / 2f)
            val iconX = slotLeft + iconOffset
            val iconY = currentY + iconOffset

            val pose = guiGraphics.pose()
            pose.pushMatrix()
            pose.translate(iconX, iconY)
            pose.scale(itemScale, itemScale)
            guiGraphics.renderItem(itemStack, 0, 0)
            pose.popMatrix()

            guiGraphics.renderItemDecorations(font, itemStack, iconX.toInt(), iconY.toInt())

            currentY += slotHeight + verticalGap
        }

        guiGraphics.drawCenteredString(font, timeText, 0, currentY, timerTextColor)
        pose.popMatrix()
    }

    private fun resolveTimerColor(hudState: ru.benos_codex.gofindwin.hud.RunTimerHudState, config: TimerHudConfig): Int {
        val baseColor = when (hudState.phase) {
            RunPhase.FINISHING, RunPhase.POST_FINISH -> config.timerColorBest
            else -> config.timerColorNormal
        }

        if (!config.finishBlinkEnabled) return baseColor
        if (hudState.phase !in setOf(RunPhase.FINISHING, RunPhase.POST_FINISH)) return baseColor
        if (!RunTimerHudStateStore.isFinishBlinkActive(config.finishBlinkDurationMs)) return baseColor

        val startedAtMs = RunTimerHudStateStore.finishBlinkStartedAtMs()
        if (startedAtMs <= 0L) return baseColor

        val elapsedMs = System.currentTimeMillis() - startedAtMs
        val interval = config.finishBlinkIntervalMs.coerceAtLeast(40L)
        val blinkOn = (elapsedMs / interval) % 2L == 0L
        return if (blinkOn) config.timerColorBest else config.textColor
    }

    private fun resolveTargetItemStack(targetItemId: String?): ItemStack? {
        val identifier = targetItemId?.let(Identifier::tryParse) ?: return null
        val item = BuiltInRegistries.ITEM.getValue(identifier)
        return ItemStack(item)
    }

    private fun renderBorder(
        guiGraphics: GuiGraphics,
        config: TimerHudConfig,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        if (!config.borderEnabled || config.borderThickness <= 0) return

        val thickness = config.borderThickness
        guiGraphics.fill(left, top, right, top + thickness, config.borderColor)
        guiGraphics.fill(left, bottom - thickness, right, bottom, config.borderColor)
        guiGraphics.fill(left, top, left + thickness, bottom, config.borderColor)
        guiGraphics.fill(right - thickness, top, right, bottom, config.borderColor)
    }
}
