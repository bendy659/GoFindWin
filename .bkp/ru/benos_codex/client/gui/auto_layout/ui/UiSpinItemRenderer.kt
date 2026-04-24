package ru.benos_codex.client.gui.auto_layout.ui

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.render.state.BlitRenderState
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix3x2f
import kotlin.math.PI

@Environment(EnvType.CLIENT)
object ItemStackRenderer {
    private const val THREE_D_RENDER_PADDING_MULTIPLIER = 1.75f

    private data class CacheKey(
        val modelIdentity: Any,
        val pixelSize: Int
    )

    private data class RenderTexture(
        val pixelSize: Int,
        val colorTexture: GpuTexture,
        val colorView: GpuTextureView,
        val depthTexture: GpuTexture,
        val depthView: GpuTextureView
    ) {
        fun close() {
            colorView.close()
            colorTexture.close()
            depthView.close()
            depthTexture.close()
        }
    }

    private val projectionBuffer = CachedOrthoProjectionMatrixBuffer("ui_spin_item", -1000.0f, 1000.0f, true)
    private val textureCache = linkedMapOf<CacheKey, RenderTexture>()

    fun render(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        mode: UiItemStackMode,
        drawX: Int,
        drawY: Int,
        drawSize: Int
    ): Boolean =
        when (mode) {
            UiItemStackMode.Flat2D -> renderFlat2D(guiGraphics, stack, drawX, drawY, drawSize)
            UiItemStackMode.Spin3D -> renderSpin3D(guiGraphics, stack, drawX, drawY, drawSize)
        }

    fun renderFlat2D(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        drawX: Int,
        drawY: Int,
        drawSize: Int = 16,
        transform: ItemTransform2D = ItemTransform2D()
    ): Boolean {
        return renderFlat2D(
            guiGraphics = guiGraphics,
            stack = stack,
            drawX = drawX.toFloat(),
            drawY = drawY.toFloat(),
            drawWidth = drawSize.toFloat(),
            drawHeight = drawSize.toFloat(),
            transform = transform
        )
    }

    fun renderFlat2D(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        drawX: Float,
        drawY: Float,
        drawWidth: Float,
        drawHeight: Float,
        transform: ItemTransform2D = ItemTransform2D()
    ): Boolean {
        if (stack.isEmpty || drawWidth <= 0.0f || drawHeight <= 0.0f) return false

        guiGraphics.pose().pushMatrix()
        guiGraphics.pose().translate(
            drawX + transform.offsetX + drawWidth / 2.0f,
            drawY + transform.offsetY + drawHeight / 2.0f
        )
        if (transform.rotation != 0f) {
            guiGraphics.pose().rotate(transform.rotation)
        }
        guiGraphics.pose().scale(
            (drawWidth / 16.0f) * transform.scaleX,
            (drawHeight / 16.0f) * transform.scaleY
        )
        guiGraphics.pose().translate(-8f, -8f)
        guiGraphics.renderItem(stack, 0, 0)
        guiGraphics.pose().popMatrix()
        return true
    }

    fun renderFlat2D(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        drawX: Float,
        drawY: Float,
        drawSize: Float,
        transform: ItemTransform2D = ItemTransform2D()
    ): Boolean {
        if (stack.isEmpty || drawSize <= 0) return false

        return renderFlat2D(guiGraphics, stack, drawX, drawY, drawSize, drawSize, transform)
    }

    fun renderSpin3D(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        drawX: Int,
        drawY: Int,
        drawSize: Int,
        angleRadians: Float = defaultSpinAngleRadians()
    ): Boolean =
        renderTransformed3D(
            guiGraphics = guiGraphics,
            stack = stack,
            drawX = drawX,
            drawY = drawY,
            drawSize = drawSize,
            transform = ItemTransform3D(rotationY = angleRadians)
        )

    fun renderTransformed3D(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        drawX: Int,
        drawY: Int,
        drawSize: Int,
        transform: ItemTransform3D
    ): Boolean {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return false
        if (drawSize <= 0) return false

        val itemEntity = ItemEntity(level, 0.0, 0.0, 0.0, stack.copy()).apply {
            makeFakeItem()
            setNeverPickUp()
        }

        val itemState = TrackingItemStackRenderState()
        minecraft.itemModelResolver.updateForTopItem(
            itemState,
            stack,
            ItemDisplayContext.GROUND,
            level,
            itemEntity,
            0
        )
        if (itemState.isEmpty) return false

        val guiScale = minecraft.window.guiScale
        val pixelSize = (drawSize * guiScale * THREE_D_RENDER_PADDING_MULTIPLIER)
            .toInt()
            .coerceAtLeast(16)
        val texture = textureFor(CacheKey(itemState.modelIdentity, pixelSize))
        renderToTexture(texture, itemState, pixelSize, transform)
        submitBlit(guiGraphics, texture, drawX, drawY, drawSize)
        trimCache()
        return true
    }

    fun currentFrame(
        deltaTracker: DeltaTracker = Minecraft.getInstance().deltaTracker,
        timeMs: Long = System.currentTimeMillis()
    ): ItemRenderFrame {
        val partialTick = deltaTracker.getGameTimeDeltaPartialTick(false)
        val tickCount = Minecraft.getInstance().level?.gameTime ?: 0L
        return ItemRenderFrame(
            deltaTracker = deltaTracker,
            partialTick = partialTick,
            timeMs = timeMs,
            tickCount = tickCount,
            gameTimeWithPartial = tickCount.toFloat() + partialTick
        )
    }

    fun defaultSpinAngleRadians(periodMs: Long = 6000L): Float =
        ((System.currentTimeMillis() % periodMs) / periodMs.toFloat()) * (PI.toFloat() * 2.0f)

    fun defaultSpinTransform(periodMs: Long = 6000L): ItemTransform3D =
        ItemTransform3D(rotationY = defaultSpinAngleRadians(periodMs))

    private fun renderToTexture(
        texture: RenderTexture,
        itemState: TrackingItemStackRenderState,
        pixelSize: Int,
        transform: ItemTransform3D
    ) {
        val minecraft = Minecraft.getInstance()
        val featureDispatcher = minecraft.gameRenderer.featureRenderDispatcher
        val bufferSource = featureDispatcherBufferSource(featureDispatcher)
        val lighting = if (itemState.usesBlockLight()) Lighting.Entry.ITEMS_3D else Lighting.Entry.ITEMS_FLAT

        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(texture.colorTexture, 0, texture.depthTexture, 1.0)
        RenderSystem.outputColorTextureOverride = texture.colorView
        RenderSystem.outputDepthTextureOverride = texture.depthView
        RenderSystem.setProjectionMatrix(
            projectionBuffer.getBuffer(pixelSize.toFloat(), pixelSize.toFloat()),
            ProjectionType.ORTHOGRAPHIC
        )
        minecraft.gameRenderer.lighting.setupFor(lighting)

        val poseStack = PoseStack().apply {
            translate(pixelSize / 2.0f, pixelSize / 2.0f, 0.0f)
            scale(pixelSize.toFloat(), -pixelSize.toFloat(), pixelSize.toFloat())
            translate(transform.offsetX, transform.offsetY, transform.offsetZ)
            mulPose(Axis.XP.rotation(transform.rotationX))
            mulPose(Axis.YP.rotation(transform.rotationY))
            mulPose(Axis.ZP.rotation(transform.rotationZ))
            scale(transform.scaleX, transform.scaleY, transform.scaleZ)
        }

        itemState.submit(
            poseStack,
            featureDispatcher.submitNodeStorage,
            15728880,
            OverlayTexture.NO_OVERLAY,
            0
        )
        featureDispatcher.renderAllFeatures()
        bufferSource?.endBatch()

        RenderSystem.outputColorTextureOverride = null
        RenderSystem.outputDepthTextureOverride = null
    }

    private fun submitBlit(guiGraphics: GuiGraphics, texture: RenderTexture, drawX: Int, drawY: Int, drawSize: Int) {
        guiGraphics.guiRenderState.submitGuiElement(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(
                    texture.colorView,
                    RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
                ),
                Matrix3x2f(),
                drawX,
                drawY,
                drawX + drawSize,
                drawY + drawSize,
                0.0f,
                1.0f,
                1.0f,
                0.0f,
                -1,
                guiGraphics.scissorStack.peek()
            )
        )
    }

    private fun textureFor(key: CacheKey): RenderTexture {
        val cached = textureCache.remove(key)
        if (cached != null) {
            textureCache[key] = cached
            return cached
        }

        val device = RenderSystem.getDevice()
        val colorTexture = device.createTexture(
            { "UI spin item color" },
            12,
            TextureFormat.RGBA8,
            key.pixelSize,
            key.pixelSize,
            1,
            1
        )
        val depthTexture = device.createTexture(
            { "UI spin item depth" },
            8,
            TextureFormat.DEPTH32,
            key.pixelSize,
            key.pixelSize,
            1,
            1
        )
        return RenderTexture(
            key.pixelSize,
            colorTexture,
            device.createTextureView(colorTexture),
            depthTexture,
            device.createTextureView(depthTexture)
        ).also { textureCache[key] = it }
    }

    private fun trimCache(maxEntries: Int = 24) {
        while (textureCache.size > maxEntries) {
            val eldestKey = textureCache.entries.firstOrNull()?.key ?: return
            textureCache.remove(eldestKey)?.close()
        }
    }

    private fun featureDispatcherBufferSource(featureDispatcher: FeatureRenderDispatcher): MultiBufferSource.BufferSource? =
        runCatching {
            val field = FeatureRenderDispatcher::class.java.getDeclaredField("bufferSource")
            field.isAccessible = true
            field.get(featureDispatcher) as? MultiBufferSource.BufferSource
        }.getOrNull()
}

@Environment(EnvType.CLIENT)
object UiItemStackRenderer {
    fun render(guiGraphics: GuiGraphics, stack: ItemStack, mode: UiItemStackMode, drawX: Int, drawY: Int, drawSize: Int): Boolean =
        ItemStackRenderer.render(guiGraphics, stack, mode, drawX, drawY, drawSize)

    fun renderFlat2D(guiGraphics: GuiGraphics, stack: ItemStack, drawX: Int, drawY: Int, drawSize: Int = 16): Boolean =
        ItemStackRenderer.renderFlat2D(guiGraphics, stack, drawX, drawY, drawSize)

    fun renderFlat2D(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        drawX: Float,
        drawY: Float,
        drawWidth: Float,
        drawHeight: Float
    ): Boolean = ItemStackRenderer.renderFlat2D(guiGraphics, stack, drawX, drawY, drawWidth, drawHeight)

    fun renderFlat2D(guiGraphics: GuiGraphics, stack: ItemStack, drawX: Float, drawY: Float, drawSize: Float): Boolean =
        ItemStackRenderer.renderFlat2D(guiGraphics, stack, drawX, drawY, drawSize)

    fun renderSpin3D(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        drawX: Int,
        drawY: Int,
        drawSize: Int,
        angleRadians: Float = ItemStackRenderer.defaultSpinAngleRadians()
    ): Boolean = ItemStackRenderer.renderSpin3D(guiGraphics, stack, drawX, drawY, drawSize, angleRadians)

    fun renderTransformed3D(
        guiGraphics: GuiGraphics,
        stack: ItemStack,
        drawX: Int,
        drawY: Int,
        drawSize: Int,
        transform: UiItemStackTransform
    ): Boolean = ItemStackRenderer.renderTransformed3D(guiGraphics, stack, drawX, drawY, drawSize, transform)

    fun currentFrame(deltaTracker: DeltaTracker = Minecraft.getInstance().deltaTracker, timeMs: Long = System.currentTimeMillis()): UiItemStackFrame =
        ItemStackRenderer.currentFrame(deltaTracker, timeMs)

    fun defaultSpinAngleRadians(periodMs: Long = 6000L): Float =
        ItemStackRenderer.defaultSpinAngleRadians(periodMs)

    fun defaultSpinTransform(periodMs: Long = 6000L): UiItemStackTransform =
        ItemStackRenderer.defaultSpinTransform(periodMs)
}
