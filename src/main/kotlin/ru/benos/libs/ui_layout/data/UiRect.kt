package ru.benos.libs.ui_layout.data

data class UiRect(
    val x: Int,
    val y: Int,
    val width : Int,
    val height: Int
) {
    val right: Int
        get() = x + width

    val bottom: Int
        get() = y + height

    fun shrink(insets: UiInsets): UiRect =
        UiRect(
            x = x + insets.left,
            y = y + insets.top,
            width = (width - insets.horizontal).coerceAtLeast(0),
            height = (height - insets.vertical).coerceAtLeast(0)
        )

    fun contains(pX: Double, pY: Double): Boolean {
        val c0 = pX >= x.toDouble()
        val c1 = pX <= (x + width)
        val c2 = pY >= y.toDouble()
        val c3 = pY <= (y + height)

        return c0 && c1 && c2 && c3
    }
}