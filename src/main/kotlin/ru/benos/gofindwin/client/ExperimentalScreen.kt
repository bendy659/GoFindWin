package ru.benos.gofindwin.client

import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import ru.benos.libs.helpers.ComponentHelper.literal
import ru.benos.libs.ui_layout.data.UiRect

data class UiColor(val r: Int, val g: Int, val b: Int, val a: Int)

private val FILL_PIPELINE = RenderPipelines.register(
    RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("yourmod", "pipeline/ui_fill"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .withBlend(BlendFunction.TRANSLUCENT)       // аналог enableBlend + defaultBlendFunc
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .build()
)

private val FILL_RENDER_TYPE: RenderType = RenderType.create("ui_fill", RenderSetup.builder(FILL_PIPELINE).createRenderSetup())

fun renderRaw(block: () -> Unit) {
    // Blend и depth теперь задаются в RenderPipeline, не здесь
    // Но если нужен raw GL — GlStateManager вместо RenderSystem:
    GlStateManager._enableBlend()
    GlStateManager._disableDepthTest()

    block()

    GlStateManager._disableBlend()
    GlStateManager._enableDepthTest()
}

fun fillRect(rect: UiRect, color: UiColor) {
    // Проекция для GUI уже выставлена движком — setProjectionMatrix больше не нужен

    val r = color.r / 255f
    val g = color.g / 255f
    val b = color.b / 255f
    val a = color.a / 255f

    val buf = Tesselator.getInstance()
        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)

    buf.addVertex(rect.x.toFloat(),     rect.y.toFloat(),      0f).setColor(r, g, b, a)
    buf.addVertex(rect.x.toFloat(),     rect.bottom.toFloat(), 0f).setColor(r, g, b, a)
    buf.addVertex(rect.right.toFloat(), rect.bottom.toFloat(), 0f).setColor(r, g, b, a)
    buf.addVertex(rect.right.toFloat(), rect.y.toFloat(),      0f).setColor(r, g, b, a)

    // BufferUploader.drawWithShader → renderType.draw()
    FILL_RENDER_TYPE.draw(buf.buildOrThrow())
}

class ExperimentalScreen: Screen("".literal) {
    override fun render(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        renderRaw { fillRect(UiRect(32, 32, 256, 128), UiColor(255, 0, 0, 164)) }
    }
}