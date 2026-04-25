package ru.benos.libs.ui_layout.data.region

import ru.benos.libs.ui_layout.data.UiRect

data class UiHoverRegion(
    val rect: UiRect,
    val hoverEvent: () -> Unit
)
