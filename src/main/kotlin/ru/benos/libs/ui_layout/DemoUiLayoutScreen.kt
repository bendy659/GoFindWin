package ru.benos.libs.ui_layout

import net.minecraft.util.ARGB
import ru.benos.libs.ui_layout.UiUtils.literal
import ru.benos.libs.ui_layout.UiUtils.ui
import ru.benos.libs.ui_layout.data.UiAlign
import ru.benos.libs.ui_layout.data.UiBoxOutlineColor.Companion.uiBoxOutlineColor
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.nodes.UiNode
import ru.benos.libs.ui_layout.nodes.box.UiBoxColorNode.Companion.boxColor
import ru.benos.libs.ui_layout.nodes.label

open class DemoUiLayoutScreen: AbstractUiLayout {
    constructor(): super("Demo UI Screen".literal)

    companion object: DemoUiLayoutScreen()

    override fun buildUi(): UiNode = ui {
        boxColor(
            backgroundColor = ARGB.color(128, 0, 0, 0),
            outline = uiBoxOutlineColor(2, 128, 255, 255, 255),
            modifier = UiModifier
                .padding(16)
        ) {
            label(
                label = this@DemoUiLayoutScreen::class.java.name ?: "DemoUiLayoutScreen@NaN",
                modifier = UiModifier
            )
        }
    }
}