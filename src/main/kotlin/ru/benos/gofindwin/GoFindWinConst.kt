package ru.benos.gofindwin

import net.minecraft.resources.Identifier

object GoFindWinConst {
    const val MOD_ID  : String = "gofindwin"
    const val MOD_NAME: String = "GoFindWin"

    var DEV: Boolean = false

    fun Boolean.invoke(block: () -> Unit) {
        if (this@invoke)
            block()
    }
}