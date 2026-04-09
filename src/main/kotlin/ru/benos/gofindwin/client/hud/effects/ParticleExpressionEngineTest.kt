package ru.benos.gofindwin.client.hud.effects

import net.minecraft.util.Mth
import net.minecraft.util.RandomSource

fun main() {
    var successCounter = 0
    var failedCounter  = 0

    val engine = ExpressionEngine().apply {
        addFunction("math.sin(value)") { args, _ ->
            NumberValue(Mth.sin(args.double("value")).toDouble())
        }

        addFunction("math.cos(value)") { args, _ ->
            NumberValue(Mth.cos(args.double("value")).toDouble())
        }

        addFunction("math.rand()") { args, _ ->
            NumberValue(Mth.nextDouble(RandomSource.create(), -1.0, 1.0))
        }

        addFunction("math.rand(value)") { args, _ ->
            NumberValue(Mth.nextDouble(RandomSource.create(), -args.double("value"), args.double("value")))
        }

        addFunction("math.rand(start, end)") { args, _ ->
            NumberValue(Mth.nextDouble(RandomSource.create(), args.double("start"), args.double("end")))
        }

        addNumberVariable("new_era") { _ -> 512.652353 }
    }
    val a = 12
    val b = 15.2
    val c = 8.24

    fun test(expression: String, alwaysSuccess: Boolean = false, code: () -> Any) {
        val exp = engine.evaluate(expression)

        val a =
            if (exp is NumberValue)
                "%.8f".format(exp.asDouble())
            else
                exp.toString()
        val b =
            if (code is Number)
                "%.8f".format((code() as Number).toDouble())
            else
                code().toString()
        val c =
            if (alwaysSuccess)
                true
            else
                if (a == b)
                    { successCounter++; "Success" }
                else
                    { failedCounter++; "Failed" }

        println(
            """
            =========================
            Expression--: $expression,
            Code return-: $b
            Parse return: $a
            Result------: $c
            """.trimIndent()
        )
    }

    test("$a + $b") { a + b }
    test("$a - $b") { a - b }
    test("$a * $b") { a * b }
    test("$a / $b") { a / b }
    test("$a % $b") { a % b }

    test("$a + $b * $c") { a + b * c }
    test("$a - $c / $b") { a - c / b }
    test("($a % $c) * ($b / $a)") { (a % c) * (b / a) }

    test("math.sin($a)") { Mth.sin(a.toDouble()) }
    test("math.cos($a)") { Mth.cos(a.toDouble()) }

    test("math.rand()", true) { Mth.nextDouble(RandomSource.create(), -1.0, 1.0) }
    test("math.rand($a)", true) { Mth.nextDouble(RandomSource.create(), -a.toDouble(), a.toDouble()) }
    test("math.rand($c, $b)", true) { Mth.nextDouble(RandomSource.create(), c, b) }

    test("ramp($a) { $c = $b, $b = $c }") { ramp(a, mapOf(c to b, b to c)) }

    println("===== Variables ======")

    test("a = $a; a") { a }
    test("b = $b; b = b + 8.245; b") { var ba = b; ba = ba + 8.245; ba }

    test("new_era") { 512.652353 }

    println(
        """
        ===== Total results =====
        Success: $successCounter
        Failed-: $failedCounter
        """.trimIndent()
    )
}
