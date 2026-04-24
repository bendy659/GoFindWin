package ru.benos.libs.ui_layout.data

open class UiInsets(
    val left  : Int = 0,
    val top   : Int = 0,
    val right : Int = 0,
    val bottom: Int = 0
) {
    companion object {
        val ZERO: UiInsets = UiInsets(
            left = 0,
            top = 0,
            right = 0,
            bottom = 0
        )
    }

    constructor(all: Int): this(
        left = all,
        top = all,
        right = all,
        bottom = all
    )
    constructor(horizontal: Int, vertical: Int): this(
        left = horizontal,
        top = vertical,
        right = horizontal,
        bottom = vertical
    )

    val horizontal: Int
        get() = left + right

    val vertical: Int
        get() = top + bottom
}
