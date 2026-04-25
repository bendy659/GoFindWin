package ru.benos.libs.ui_layout.data

sealed interface UiLength {
    sealed interface UiWeighted : UiLength { val weight: Float }

    data class Fixed    (val value: Int)                  : UiLength
    data class Fill     (override val weight: Float = 1f) : UiWeighted
    data class Available(override val weight: Float = 1f) : UiWeighted
    data class Expand   (override val weight: Float = 1f) : UiWeighted
    data object Wrap                                      : UiLength
}