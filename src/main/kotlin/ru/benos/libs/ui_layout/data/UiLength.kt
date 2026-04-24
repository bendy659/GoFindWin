package ru.benos.libs.ui_layout.data

sealed interface UiLength {
    data class Fixed    (val value: Int)        : UiLength
    data class Fill     (val weight: Float = 1f): UiLength
    data class Available(val weight: Float = 1f): UiLength
    data class Expand   (val weight: Float = 1f): UiLength
    data object Wrap                            : UiLength
}