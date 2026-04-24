package ru.benos.gofindwin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.CommonColors

object GoFindWinConst {
    const val MOD_ID  : String = "gofindwin"
    const val MOD_NAME: String = "GoFindWin"

    @Environment(EnvType.CLIENT)
    sealed class ExpressionVariables(val k: String) {
        data object ParticlesCount: ExpressionVariables("particles_count")

        sealed class Particle(k: String): ExpressionVariables("particle.$k") {
            data object Index: Particle("index")

            sealed class SpawnPosition(k: String): Particle("spawn_position.$k") {
                data object X: SpawnPosition("x")
                data object Y: SpawnPosition("y")
            }

            sealed class Position(k: String): Particle("position.$k") {
                data object X: Position("x")
                data object Y: Position("y")
            }

            sealed class Rotation(k: String): Particle("rotation.$k") {
                data object X: Rotation("x")
                data object Y: Rotation("y")
                data object Z: Rotation("z")
            }

            sealed class Scale(k: String): Particle("scale.$k") {
                data object X: Scale("x")
                data object Y: Scale("y")
                data object Z: Scale("z")
            }

            sealed class Life(k: String): Particle("life.$k") {
                data object Age: Life("age")
                data object Max: Life("max")
                data object Factor: Life("factor")
            }

            sealed class Color(k: String): Particle("color.$k") {
                data object R: Color("r")
                data object G: Color("g")
                data object B: Color("b")
                data object A: Color("a")
            }
        }

        companion object {
            val ALL: List<ExpressionVariables> = listOf(
                ParticlesCount,
                Particle.Index,
                Particle.SpawnPosition.X,
                Particle.SpawnPosition.Y,
                Particle.Position.X,
                Particle.Position.Y,
                Particle.Rotation.X,
                Particle.Rotation.Y,
                Particle.Rotation.Z,
                Particle.Scale.X,
                Particle.Scale.Y,
                Particle.Scale.Z,
                Particle.Life.Age,
                Particle.Life.Max,
                Particle.Life.Factor,
                Particle.Color.R,
                Particle.Color.G,
                Particle.Color.B,
                Particle.Color.A
            )
        }
    }

    @Environment(EnvType.CLIENT)
    object Client {
        const val DIRECTORY_CACHE   : String = ".cache"
        const val DIRECTORY_PROFILES: String = "profiles"

        const val CONFIG_FILE_CLIENT: String = "client.json"

        const val EFFECTS_JSON: String = "effects.json"
        const val SCROLLS_JSON: String = "scrolls.json"

        var DEV: Boolean = true

        object IDEHighlightColors {
            var VAR       = ARGB.color(200, 140, 220)
            val VARIABLES = ARGB.color(255, 149, 126)
            val FUNCTIONS = ARGB.color(255, 196, 88)

            val NUMBER    = ARGB.color(54, 190, 255)
            val STRING    = ARGB.color(149, 172, 83)
            val BOOLEAN   = ARGB.color(191, 139, 209)

            val COMMENT   = ARGB.color(119, 126, 133)
        }

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
