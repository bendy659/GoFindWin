package ru.benos_codex.gofindwin.client.hud.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object MathExpression {
    fun evaluate(expression: String, variables: Map<String, Double>): Double? {
        return try {
            evaluateStatements(expression, variables)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun evaluateStatements(expression: String, variables: Map<String, Double>): Double {
        val scope = variables.toMutableMap()
        var last = 0.0
        splitStatements(expression).forEach { statement ->
            val trimmed = statement.trim()
            if (trimmed.isEmpty()) return@forEach
            val assignmentIndex = topLevelAssignmentIndex(trimmed)
            if (assignmentIndex != null) {
                val name = trimmed.substring(0, assignmentIndex).trim()
                require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_\\.]*"))) { "Invalid assignment target" }
                val rhs = trimmed.substring(assignmentIndex + 1)
                val value = Parser(rhs, scope).parse()
                scope[name] = value
                scope[name.lowercase()] = value
                last = value
            } else {
                last = Parser(trimmed, scope).parse()
            }
        }
        return last
    }

    private fun splitStatements(expression: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        expression.forEach { ch ->
            when (ch) {
                '(' -> {
                    depth += 1
                    current.append(ch)
                }
                ')' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    current.append(ch)
                }
                ';', '\n', '\r' -> {
                    if (depth == 0) {
                        result += current.toString()
                        current.clear()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun topLevelAssignmentIndex(statement: String): Int? {
        var depth = 0
        var index = 0
        while (index < statement.length) {
            val ch = statement[index]
            when (ch) {
                '(' -> depth += 1
                ')' -> depth = (depth - 1).coerceAtLeast(0)
                '=' -> {
                    val prev = statement.getOrNull(index - 1)
                    val next = statement.getOrNull(index + 1)
                    val isComparison = prev == '=' || prev == '!' || prev == '<' || prev == '>' || next == '='
                    if (depth == 0 && !isComparison) return index
                }
            }
            index += 1
        }
        return null
    }

    fun substitute(expression: String, variables: Map<String, String>): String {
        var result = expression

        variables.entries
            .sortedByDescending { it.key.length }
            .forEach { (name, value) ->
                result = result.replace(Regex("(?<![A-Za-z0-9_.])${Regex.escape(name)}(?![A-Za-z0-9_.])"), value)
            }

        return result
    }

    private class Parser(
        private val source: String,
        private val variables: Map<String, Double>
    ) {
        private var index = 0

        fun parse(): Double {
            val value = parseTernary()
            skipWhitespace()
            require(index >= source.length) { "Unexpected trailing input" }
            return value
        }

        private fun parseTernary(): Double {
            val condition = parseOr()
            skipWhitespace()

            if (!match('?')) return condition

            val ifTrue = parseTernary()
            require(match(':')) { "Expected ':' in ternary expression" }
            val ifFalse = parseTernary()

            return if (condition != 0.0) ifTrue else ifFalse
        }

        private fun parseOr(): Double {
            var value = parseAnd()

            while (true) {
                value = when {
                    match("||") -> if (value != 0.0 || parseAnd() != 0.0) 1.0 else 0.0
                    else -> return value
                }
            }
        }

        private fun parseAnd(): Double {
            var value = parseEquality()

            while (true) {
                value = when {
                    match("&&") -> if (value != 0.0 && parseEquality() != 0.0) 1.0 else 0.0
                    else -> return value
                }
            }
        }

        private fun parseEquality(): Double {
            var value = parseComparison()

            while (true) {
                value = when {
                    match("==") -> if (abs(value - parseComparison()) < 0.00001) 1.0 else 0.0
                    match("!=") -> if (abs(value - parseComparison()) >= 0.00001) 1.0 else 0.0
                    else -> return value
                }
            }
        }

        private fun parseComparison(): Double {
            var value = parseAdditive()

            while (true) {
                value = when {
                    match("<=") -> if (value <= parseAdditive()) 1.0 else 0.0
                    match(">=") -> if (value >= parseAdditive()) 1.0 else 0.0
                    match("<") -> if (value < parseAdditive()) 1.0 else 0.0
                    match(">") -> if (value > parseAdditive()) 1.0 else 0.0
                    else -> return value
                }
            }
        }

        private fun parseAdditive(): Double {
            var value = parseMultiplicative()

            while (true) {
                value = when {
                    match('+') -> value + parseMultiplicative()
                    match('-') -> value - parseMultiplicative()
                    else -> return value
                }
            }
        }

        private fun parseMultiplicative(): Double {
            var value = parsePower()

            while (true) {
                value = when {
                    match('*') -> value * parsePower()
                    match('/') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) value else value / divisor
                    }
                    matchWord("mod") -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) value else positiveMod(value, divisor)
                    }
                    match('%') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) value else value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            var value = parseUnary()

            while (match('^')) {
                value = value.pow(parseUnary())
            }

            return value
        }

        private fun parseUnary(): Double {
            return when {
                match('+') -> parseUnary()
                match('-') -> -parseUnary()
                match('!') -> if (parseUnary() == 0.0) 1.0 else 0.0
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Double {
            skipWhitespace()

            if (match('(')) {
                val value = parseTernary()
                require(match(')')) { "Expected ')'" }
                return value
            }

            if (peek()?.isDigit() == true || peek() == '.') {
                return parseNumber()
            }

            val identifier = parseIdentifier()
            skipWhitespace()

            if (match('(')) {
                return parseFunction(identifier, parseArguments())
            }

            return resolveIdentifier(identifier)
        }

        private fun parseArguments(): List<Double> {
            skipWhitespace()

            if (match(')')) return emptyList()

            val args = mutableListOf<Double>()

            while (true) {
                args += parseTernary()
                skipWhitespace()

                when {
                    match(',') -> continue
                    match(')') -> return args
                    else -> throw IllegalArgumentException("Expected ',' or ')'")
                }
            }
        }

        private fun parseFunction(name: String, args: List<Double>): Double {
            return when (name.lowercase()) {
                "sin", "math.sin", "sin_deg" -> {
                    require(args.size == 1) { "sin requires 1 argument" }
                    sin(Math.toRadians(args[0]))
                }
                "cos", "math.cos", "cos_deg" -> {
                    require(args.size == 1) { "cos requires 1 argument" }
                    cos(Math.toRadians(args[0]))
                }
                "pow", "math.pow" -> {
                    require(args.size == 2) { "pow requires 2 arguments" }
                    args[0].pow(args[1])
                }
                "sqrt", "math.sqrt" -> {
                    require(args.size == 1) { "sqrt requires 1 argument" }
                    sqrt(args[0])
                }
                "abs", "math.abs" -> {
                    require(args.size == 1) { "abs requires 1 argument" }
                    abs(args[0])
                }
                "pos", "math.pos" -> {
                    require(args.size == 1) { "pos requires 1 argument" }
                    if (args[0] < 0.0) -args[0] else args[0]
                }
                "min", "math.min" -> {
                    require(args.size == 2) { "min requires 2 arguments" }
                    min(args[0], args[1])
                }
                "max", "math.max" -> {
                    require(args.size == 2) { "max requires 2 arguments" }
                    max(args[0], args[1])
                }
                "lerp", "math.lerp" -> {
                    require(args.size == 3) { "lerp requires 3 arguments" }
                    args[0] + (args[1] - args[0]) * args[2]
                }
                "int", "math.int" -> {
                    require(args.size == 1) { "int requires 1 argument" }
                    args[0].toInt().toDouble()
                }
                "math.to_rad" -> {
                    require(args.size == 1) { "math.to_rad requires 1 argument" }
                    Math.toRadians(args[0])
                }
                "math.to_deg" -> {
                    require(args.size == 1) { "math.to_deg requires 1 argument" }
                    Math.toDegrees(args[0])
                }
                "math.clamp" -> {
                    require(args.size == 3) { "math.clamp requires 3 arguments" }
                    args[0].coerceIn(args[1], args[2])
                }
                "mod", "math.mod" -> {
                    require(args.size == 2) { "mod requires 2 arguments" }
                    if (args[1] == 0.0) args[0] else positiveMod(args[0], args[1])
                }
                "randi", "math.randi" -> {
                    require(args.size == 2) { "randi requires 2 arguments" }
                    val from = args[0].toInt()
                    val to = args[1].toInt()
                    if (from <= to) Random.nextInt(from, to + 1).toDouble() else Random.nextInt(to, from + 1).toDouble()
                }
                "randf", "math.randf" -> {
                    require(args.size == 2) { "randf requires 2 arguments" }
                    val from = args[0]
                    val to = args[1]
                    if (from == to) from else Random.nextDouble(min(from, to), max(from, to))
                }
                else -> throw IllegalArgumentException("Unknown function: $name")
            }
        }

        private fun positiveMod(value: Double, divisor: Double): Double {
            val result = value % divisor
            return if (result < 0.0) result + abs(divisor) else result
        }

        private fun resolveIdentifier(identifier: String): Double {
            val normalized = identifier.lowercase()

            return variables[identifier]
                ?: variables[normalized]
                ?: when (normalized) {
                    "pi", "math.pi" -> PI
                    "e" -> Math.E
                    else -> throw IllegalArgumentException("Unknown identifier: $identifier")
                }
        }

        private fun parseNumber(): Double {
            val start = index

            while (peek()?.isDigit() == true || peek() == '.') {
                index += 1
            }

            if (peek() == 'e' || peek() == 'E') {
                index += 1

                if (peek() == '+' || peek() == '-') {
                    index += 1
                }

                while (peek()?.isDigit() == true) {
                    index += 1
                }
            }

            return source.substring(start, index).toDouble()
        }

        private fun parseIdentifier(): String {
            skipWhitespace()
            val start = index

            while (peek()?.let { it.isLetterOrDigit() || it == '_' || it == '.' } == true) {
                index += 1
            }

            require(index > start) { "Expected identifier" }
            return source.substring(start, index)
        }

        private fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) {
                index += 1
            }
        }

        private fun match(expected: Char): Boolean {
            skipWhitespace()

            if (peek() != expected) return false

            index += 1
            return true
        }

        private fun match(expected: String): Boolean {
            skipWhitespace()

            if (!source.regionMatches(index, expected, 0, expected.length, ignoreCase = true)) {
                return false
            }

            index += expected.length
            return true
        }

        private fun matchWord(expected: String): Boolean {
            skipWhitespace()
            if (!source.regionMatches(index, expected, 0, expected.length, ignoreCase = true)) {
                return false
            }
            val before = source.getOrNull(index - 1)
            val after = source.getOrNull(index + expected.length)
            val validBefore = before == null || !(before.isLetterOrDigit() || before == '_' || before == '.')
            val validAfter = after == null || !(after.isLetterOrDigit() || after == '_' || after == '.')
            if (!validBefore || !validAfter) return false
            index += expected.length
            return true
        }

        private fun peek(): Char? = source.getOrNull(index)
    }
}
