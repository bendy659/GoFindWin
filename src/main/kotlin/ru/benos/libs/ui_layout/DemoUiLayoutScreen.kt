package ru.benos.libs.ui_layout

import net.minecraft.util.ARGB
import org.joml.Vector2i
import ru.benos.libs.helpers.ComponentHelper.component
import ru.benos.libs.helpers.ComponentHelper.literal
import ru.benos.libs.helpers.ComponentHelper.style
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.UiBoxOutlineColor
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.axis.UiAlign
import ru.benos.libs.ui_layout.data.axis.UiTextAlign
import ru.benos.libs.ui_layout.data.theme.UiBoxTheme

class DemoUiLayoutScreen: AbstractUiLayout("Demo UI Screen".literal) {
    private var mousePosition: Vector2i = Vector2i(0, 0)

    private val mainBoxTheme: UiBoxTheme = UiBoxTheme.DEFAULT
        .hovered(
            background = UiBoxTheme.DEFAULT.background,
            outline = UiBoxOutlineColor(1, 192, 255, 255, 255)
        )
        .clicked(
            background = UiBoxTheme.DEFAULT.background,
            outline = UiBoxOutlineColor(1, 64, 255, 255, 255)
        )
        .released(
            background = UiBoxTheme.DEFAULT.background,
            outline = UiBoxOutlineColor(1, 192, 255, 255, 0)
        )

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
                    .onMouseClicked { _, _, _ -> true }
                    .onMouseReleased { _, _, _ -> true }
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
            }
        }
    }
}