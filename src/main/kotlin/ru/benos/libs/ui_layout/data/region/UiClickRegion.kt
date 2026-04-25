package ru.benos.libs.ui_layout.data.region

import ru.benos.libs.ui_layout.data.UiRect

data class UiClickRegion(
    val rect: UiRect,
    val clickEvent: (Int, Int, Int) -> Boolean
)
