package ru.benos_codex.gofindwin.client.hud.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object MathExpressionSelfTest {
    @JvmStatic
    fun main(args: Array<String>) {
        checkNear(
            "sin(age_tick * 360)",
            sin(Math.toRadians(0.25 * 360.0)),
            MathExpression.evaluate("sin(age_tick * 360)", mapOf("age_tick" to 0.25))
        )

        checkNear(
            "math.sin(age_tick * 360)",
            sin(Math.toRadians(0.25 * 360.0)),
            MathExpression.evaluate("math.sin(age_tick * 360)", mapOf("age_tick" to 0.25))
        )

        checkNear(
            "math.sin(math.to_deg(math.pi / 2))",
            1.0,
            MathExpression.evaluate("math.sin(math.to_deg(math.pi / 2))", emptyMap())
        )

        checkEquals(
            "math.pow(age_tick, 2) + age_frame",
            11.0,
            MathExpression.evaluate("math.pow(age_tick, 2) + age_frame", mapOf("age_tick" to 3.0, "age_frame" to 2.0))
        )

        checkEquals(
            "age_tick > 1 ? 16 : 8",
            16.0,
            MathExpression.evaluate("age_tick > 1 ? 16 : 8", mapOf("age_tick" to 2.0))
        )

        checkEquals(
            "age_tick > 1 ? 16 : 8",
            8.0,
            MathExpression.evaluate("age_tick > 1 ? 16 : 8", mapOf("age_tick" to 0.5))
        )

        checkEquals(
            "age_tick + age_frame + age_tick_delta + age_frame_delta",
            44.0,
            MathExpression.evaluate(
                "age_tick + age_frame + age_tick_delta + age_frame_delta",
                mapOf(
                    "age_tick" to 10.0,
                    "age_frame" to 30.0,
                    "age_tick_delta" to 1.0,
                    "age_frame_delta" to 3.0
                )
            )
        )

        checkEquals("pi", PI, MathExpression.evaluate("pi", emptyMap()))
        checkEquals("math.pi", PI, MathExpression.evaluate("math.pi", emptyMap()))
        checkEquals("pos(-12)", 12.0, MathExpression.evaluate("pos(-12)", emptyMap()))
        checkEquals("math.pos(5)", 5.0, MathExpression.evaluate("math.pos(5)", emptyMap()))
        checkEquals("mod(-1, 8)", 7.0, MathExpression.evaluate("mod(-1, 8)", emptyMap()))
        checkEquals("math.mod(-13, 10)", 7.0, MathExpression.evaluate("math.mod(-13, 10)", emptyMap()))
        checkEquals("age_tick mod 360", 10.0, MathExpression.evaluate("age_tick mod 360", mapOf("age_tick" to 370.0)))
        checkEquals("lerp(10, 20, 0.25)", 12.5, MathExpression.evaluate("lerp(10, 20, 0.25)", emptyMap()))
        checkEquals("math.lerp(0, 100, 0.5)", 50.0, MathExpression.evaluate("math.lerp(0, 100, 0.5)", emptyMap()))
        checkEquals("int(12.9)", 12.0, MathExpression.evaluate("int(12.9)", emptyMap()))
        checkEquals("math.int(-5.2)", -5.0, MathExpression.evaluate("math.int(-5.2)", emptyMap()))
        checkNear("a = rand_x; b = age_tick; sin(a * 360) * b", 0.0, MathExpression.evaluate("a = rand_x; b = age_tick; sin(a * 360) * b", mapOf("rand_x" to 0.5, "age_tick" to 1.0)))
        checkEquals("randi(2, 2)", 2.0, MathExpression.evaluate("randi(2, 2)", emptyMap()))
        checkEquals("randf(1.5, 1.5)", 1.5, MathExpression.evaluate("randf(1.5, 1.5)", emptyMap()))

        println("MathExpression self-test passed")
    }

    private fun checkNear(expression: String, expected: Double, actual: Double?, epsilon: Double = 0.000001) {
        println("$expression -> expected=$expected")
        println("$expression -> parsed=$actual")
        require(actual != null) {
            "Expression returned null: $expression"
        }

        require(abs(expected - actual) <= epsilon) {
            """
            Expression mismatch
            expression: $expression
            expected: $expected
            actual: $actual
            """.trimIndent()
        }
    }

    private fun checkEquals(expression: String, expected: Double, actual: Double?) {
        println("$expression -> expected=$expected")
        println("$expression -> parsed=$actual")
        require(actual != null) {
            "Expression returned null: $expression"
        }

        require(expected == actual) {
            """
            Expression mismatch
            expression: $expression
            expected: $expected
            actual: $actual
            """.trimIndent()
        }
    }
}
