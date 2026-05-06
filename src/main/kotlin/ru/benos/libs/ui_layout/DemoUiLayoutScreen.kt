package ru.benos.libs.ui_layout

import net.minecraft.util.ARGB
import org.joml.Vector2i
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.libs.helpers.ComponentHelper.component
import ru.benos.libs.helpers.ComponentHelper.literal
import ru.benos.libs.helpers.ComponentHelper.style
import ru.benos.libs.helpers.IdentifierHelper.mident
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.axis.UiAlign
import ru.benos.libs.ui_layout.data.axis.UiTextAlign
import ru.benos.libs.ui_layout.data.theme.UiBoxColorTheme
import ru.benos.libs.ui_layout.data.theme.UiCanvas

class DemoUiLayoutScreen: AbstractUiLayout("Demo UI Screen".literal) {
    private var mousePosition: Vector2i = Vector2i(0, 0)

    private val mainBoxTheme: UiBoxColorTheme = UiBoxColorTheme.DEFAULT

    private val cogwheel: UiBoxColorTheme = UiBoxColorTheme(
        backgroundNormal = UiCanvas.Texture("textures/gui/oxidized_copper_gear.png".mident(MOD_ID))
    )

    private val cogwheelRotation: Float
        get() = (runtime?.totalTime ?: 0.0f) * 90.0f

    override fun UiBuilder.ui() {
        box(
            modifier = UiModifier
                .fillWidth()
                .fillHeight()
                .padding(horizontal = 64, vertical = 8)
        ) {
            column(
                boxTheme = mainBoxTheme,
                gap = 4,
                modifier = UiModifier
                    .align(UiAlign.Center, UiAlign.Center)
                    .fillHeight()
                    .padding(vertical = 8)
                    .onMouseHovered { mouseX, mouseY -> mousePosition.set(mouseX, mouseY) }
            ) {
                label(
                    component = listOf(
                        "Mouse position".literal
                            .style(textColor = ARGB.color(196, 255, 128, 0)),
                        ": ".literal,
                        "${mousePosition.x} | ${mousePosition.y}".literal
                            .style(textColor = ARGB.color(196, 64, 255, 64), isBold = true)
                    ).component,
                    textAlign = UiTextAlign.Center,
                    modifier = UiModifier
                        .availableWidth()
                )

                label(
                    text = this@DemoUiLayoutScreen::class.java.name ?: "DemoUiLayoutScreen@NaN",
                    textAlign = UiTextAlign.Center,
                    modifier = UiModifier
                        .availableWidth()
                )

                box(
                    boxTheme = cogwheel,
                    modifier = UiModifier
                        .align(UiAlign.End, UiAlign.Start)
                        .fixedWidth(160)
                        .fixedHeight(160)
                        .translate(64f + 16f)
                        .rotationDeg(cogwheelRotation)
                )
                box(
                    boxTheme = cogwheel,
                    modifier = UiModifier
                        .align(UiAlign.End, UiAlign.Start)
                        .fixedWidth(96)
                        .fixedHeight(96)
                        .translate(64f - 16f, -16f)
                        .rotationDeg(-cogwheelRotation / 1.125f * 2f)
                )
            }
        }
    }
}