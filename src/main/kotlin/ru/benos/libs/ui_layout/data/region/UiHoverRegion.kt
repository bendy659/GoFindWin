package ru.benos.libs.ui_layout.data.region

import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiTransform

data class UiHoverRegion(
    val rect: UiRect,
    val transform: UiTransform,
    val hoverEvent: () -> Unit
)
