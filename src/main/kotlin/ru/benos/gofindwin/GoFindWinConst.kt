package ru.benos.gofindwin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.CommonColors
import net.minecraft.util.RandomSource
import java.nio.file.Path

object GoFindWinConst {
    const val MOD_ID  : String = "gofindwin"
    const val MOD_NAME: String = "GoFindWin"

    @Environment(EnvType.CLIENT)
    sealed class ExpressionVariables(val k: String) {
        data object ParticlesCount: ExpressionVariables("particles_count")

        sealed class Particle(k: String): ExpressionVariables("particle.$k") {
            data object Index: Particle("particle")

            sealed class SpawnPosition(k: String): Particle("spawn_position.$k") {
                data object X: SpawnPosition("x")
                data object Y: SpawnPosition("y")
            }

            sealed class Position(k: String): Particle("position.$k") {
                data object X: Position("x")
                data object Y: Position("y")
            }

            sealed class Rotation(k: String): Particle("rotation.$k") {
                data object X: Position("x")
                data object Y: Position("y")
                data object Z: Position("z")
            }

            sealed class Scale(k: String): Particle("scale.$k") {
                data object X: Position("x")
                data object Y: Position("y")
                data object Z: Position("z")
            }

            sealed class Life(k: String): Particle("life.$k") {
                data object Age: Position("age")
                data object Max: Position("max")
                data object Factor: Position("factor")
            }

            sealed class Color(k: String): Particle("color.$k") {
                data object R: Position("r")
                data object G: Position("g")
                data object B: Position("b")
                data object A: Position("a")
            }
        }
    }

    lateinit var CONFIG_PATH: Path

    @Environment(EnvType.CLIENT)
    object Client {
        const val DIRECTORY_CACHE   : String = ".cache"
        const val DIRECTORY_PROFILES: String = "profiles"

        const val CONFIG_FILE_CLIENT: String = "client.json"

        var DEV: Boolean = true

        val String.literal: Component
            get() = Component.literal(this@literal)

        val String.translate: Component
            get() = Component.translatable(this@translate)

        val Iterable<Component>.component: Component
            get() {
                val component = Component.empty()
                this@component.forEach { component.append(it) }

                return component
            }

        fun Component.style(
            color: Int = CommonColors.WHITE,
            bold: Boolean = false,
            italic: Boolean = false,
            underlined: Boolean = false
        ): Component {
            val mutable = this@style.copy()
                .withStyle(
                    Style.EMPTY
                        .withColor(color)
                        .withBold(bold)
                        .withItalic(italic)
                        .withUnderlined(underlined)
                )
            return mutable
        }
    }

    const val CONFIG_FILE_COMMON: String = "common.json"

    val String.ident: Identifier get() =
        Identifier.parse(this@ident)

    val String.mident: Identifier get() =
        "$MOD_ID:${this@mident}".ident
}