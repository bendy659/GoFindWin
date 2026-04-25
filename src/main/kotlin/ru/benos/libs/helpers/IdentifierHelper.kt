package ru.benos.libs.helpers

import net.minecraft.resources.Identifier

object IdentifierHelper {
    val String.ident: Identifier
        get() = Identifier.parse(this@ident)

    fun String.mident(modId: String): Identifier =
        "$modId:${this@mident}".ident
}