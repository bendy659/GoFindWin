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
    private val builtinColorConstants: Map<String, Double> = mapOf(
        "black" to 0x000000.toDouble(),
        "dark_blue" to 0x0000AA.toDouble(),
        "dark_green" to 0x00AA00.toDouble(),
        "dark_aqua" to 0x00AAAA.toDouble(),
        "dark_red" to 0xAA0000.toDouble(),
        "dark_purple" to 0xAA00AA.toDouble(),
        "gold" to 0xFFAA00.toDouble(),
        "gray" to 0xAAAAAA.toDouble(),
        "dark_gray" to 0x555555.toDouble(),
        "blue" to 0x5555FF.toDouble(),
        "green" to 0x55FF55.toDouble(),
        "aqua" to 0x55FFFF.toDouble(),
        "red" to 0xFF5555.toDouble(),
        "light_purple" to 0xFF55FF.toDouble(),
        "yellow" to 0xFFFF55.toDouble(),
        "white" to 0xFFFFFF.toDouble()
    )

    fun evaluate(expression: String, variables: Map<String, Double>): Double? {
        return try {
            evaluateStatements(expression, variables.withBuiltins())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun evaluateColor(expression: String, variables: Map<String, Double>): Int? {
        return try {
            evaluateColorStatements(expression, variables.withBuiltins())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun Map<String, Double>.withBuiltins(): Map<String, Double> = buildMap {
        putAll(builtinColorConstants)
        builtinColorConstants.forEach { (key, value) -> put(key.lowercase(), value) }
        putAll(this@withBuiltins)
        this@withBuiltins.forEach { (key, value) -> put(key.lowercase(), value) }
    }

    private fun evaluateStatements(expression: String, variables: Map<String, Double>): Double {
        val scope = variables.toMutableMap()
        var last = 0.0
        splitStatements(expression).forEach { statement ->
            val trimmed = statement.trim()
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.startsWith("keyframe(", ignoreCase = true)) {
                last = evaluateKeyframe(trimmed, scope)
                return@forEach
            }
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

    private fun evaluateColorStatements(expression: String, variables: Map<String, Double>): Int {
        val scope = variables.toMutableMap()
        var last = 0xFFFFFFFF.toInt()
        splitStatements(expression).forEach { statement ->
            val trimmed = statement.trim()
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.startsWith("keyframe(", ignoreCase = true)) {
                last = evaluateColorKeyframe(trimmed, scope)
                return@forEach
            }
            val assignmentIndex = topLevelAssignmentIndex(trimmed)
            if (assignmentIndex != null) {
                val name = trimmed.substring(0, assignmentIndex).trim()
                require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_\\.]*"))) { "Invalid assignment target" }
                val rhs = trimmed.substring(assignmentIndex + 1)
                val value = Parser(rhs, scope).parse()
                scope[name] = value
                scope[name.lowercase()] = value
            } else {
                last = parseColorExpression(trimmed, scope)
            }
        }
        return last
    }

    private fun splitStatements(expression: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0
        var braceDepth = 0
        expression.forEach { ch ->
            when (ch) {
                '(' -> {
                    parenDepth += 1
                    current.append(ch)
                }
                ')' -> {
                    parenDepth = (parenDepth - 1).coerceAtLeast(0)
                    current.append(ch)
                }
                '{' -> {
                    braceDepth += 1
                    current.append(ch)
                }
                '}' -> {
                    braceDepth = (braceDepth - 1).coerceAtLeast(0)
                    current.append(ch)
                }
                ';', '\n', '\r' -> {
                    if (parenDepth == 0 && braceDepth == 0) {
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
        var parenDepth = 0
        var braceDepth = 0
        var index = 0
        while (index < statement.length) {
            val ch = statement[index]
            when (ch) {
                '(' -> parenDepth += 1
                ')' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                '{' -> braceDepth += 1
                '}' -> braceDepth = (braceDepth - 1).coerceAtLeast(0)
                '=' -> {
                    val prev = statement.getOrNull(index - 1)
                    val next = statement.getOrNull(index + 1)
                    val isComparison = prev == '=' || prev == '!' || prev == '<' || prev == '>' || next == '='
                    if (parenDepth == 0 && braceDepth == 0 && !isComparison) return index
                }
            }
            index += 1
        }
        return null
    }

    private data class KeyframeValue(val value: Double, val easing: Double)
    private data class KeyframeEntry(val start: Double, val end: Double, val keyframe: KeyframeValue)
    private data class ColorKeyframeValue(val value: Int, val easing: Double)
    private data class ColorKeyframeEntry(val start: Double, val end: Double, val keyframe: ColorKeyframeValue)

    private fun evaluateKeyframe(statement: String, variables: Map<String, Double>): Double {
        val openParen = statement.indexOf('(')
        require(openParen >= 0) { "keyframe requires '('" }
        val closeParen = findMatching(statement, openParen, '(', ')')
        val openBrace = statement.indexOf('{', closeParen + 1)
        require(openBrace >= 0) { "keyframe requires '{'" }
        val closeBrace = findMatching(statement, openBrace, '{', '}')
        val axisExpression = statement.substring(openParen + 1, closeParen).trim()
        val body = statement.substring(openBrace + 1, closeBrace)
        val axis = Parser(axisExpression, variables).parse()
        val entries = parseKeyframeEntries(body, variables).sortedBy { it.start }
        require(entries.isNotEmpty()) { "keyframe requires at least one entry" }
        if (axis <= entries.first().start) return entries.first().keyframe.value
        for (index in entries.indices) {
            val current = entries[index]
            if (axis in current.start..current.end) return current.keyframe.value
            val next = entries.getOrNull(index + 1) ?: continue
            if (axis > current.end && axis < next.start) {
                val distance = (next.start - current.end).takeIf { it != 0.0 } ?: return next.keyframe.value
                val rawFactor = ((axis - current.end) / distance).coerceIn(0.0, 1.0)
                val easing = next.keyframe.easing.takeIf { it > 0.0 } ?: 1.0
                val factor = rawFactor.pow(easing)
                return current.keyframe.value + (next.keyframe.value - current.keyframe.value) * factor
            }
        }
        return entries.last().keyframe.value
    }

    private fun parseKeyframeEntries(body: String, variables: Map<String, Double>): List<KeyframeEntry> {
        return splitTopLevel(body, ',')
            .mapNotNull { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val assignmentIndex = topLevelAssignmentIndex(trimmed) ?: throw IllegalArgumentException("Invalid keyframe entry")
                val key = trimmed.substring(0, assignmentIndex).trim()
                val valueRaw = trimmed.substring(assignmentIndex + 1).trim()
                val (start, end) = if (".." in key) {
                    val parts = key.split("..", limit = 2)
                    require(parts.size == 2) { "Invalid keyframe range" }
                    Parser(parts[0].trim(), variables).parse() to Parser(parts[1].trim(), variables).parse()
                } else {
                    val point = Parser(key, variables).parse()
                    point to point
                }
                val normalizedStart = min(start, end)
                val normalizedEnd = max(start, end)
                KeyframeEntry(normalizedStart, normalizedEnd, parseKeyframeValue(valueRaw, variables))
            }
    }

    private fun parseKeyframeValue(raw: String, variables: Map<String, Double>): KeyframeValue {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) {
            return KeyframeValue(Parser(trimmed, variables).parse(), 1.0)
        }
        require(trimmed.endsWith("}")) { "Invalid keyframe object" }
        val content = trimmed.substring(1, trimmed.length - 1)
        var value: Double? = null
        var easing = 1.0
        splitTopLevel(content, ',').forEach { part ->
            val entry = part.trim()
            if (entry.isEmpty()) return@forEach
            val assignmentIndex = topLevelAssignmentIndex(entry) ?: throw IllegalArgumentException("Invalid keyframe object field")
            val name = entry.substring(0, assignmentIndex).trim().lowercase()
            val rhs = entry.substring(assignmentIndex + 1).trim()
            when (name) {
                "value" -> value = Parser(rhs, variables).parse()
                "easing" -> easing = Parser(rhs, variables).parse()
                else -> throw IllegalArgumentException("Unknown keyframe field: $name")
            }
        }
        return KeyframeValue(value ?: throw IllegalArgumentException("Keyframe object requires value"), easing)
    }

    private fun evaluateColorKeyframe(statement: String, variables: Map<String, Double>): Int {
        val openParen = statement.indexOf('(')
        require(openParen >= 0) { "keyframe requires '('" }
        val closeParen = findMatching(statement, openParen, '(', ')')
        val openBrace = statement.indexOf('{', closeParen + 1)
        require(openBrace >= 0) { "keyframe requires '{'" }
        val closeBrace = findMatching(statement, openBrace, '{', '}')
        val axisExpression = statement.substring(openParen + 1, closeParen).trim()
        val body = statement.substring(openBrace + 1, closeBrace)
        val axis = Parser(axisExpression, variables).parse()
        val entries = parseColorKeyframeEntries(body, variables).sortedBy { it.start }
        require(entries.isNotEmpty()) { "keyframe requires at least one entry" }
        if (axis <= entries.first().start) return entries.first().keyframe.value
        for (index in entries.indices) {
            val current = entries[index]
            if (axis in current.start..current.end) return current.keyframe.value
            val next = entries.getOrNull(index + 1) ?: continue
            if (axis > current.end && axis < next.start) {
                val distance = (next.start - current.end).takeIf { it != 0.0 } ?: return next.keyframe.value
                val rawFactor = ((axis - current.end) / distance).coerceIn(0.0, 1.0)
                val easing = next.keyframe.easing.takeIf { it > 0.0 } ?: 1.0
                return blendColors(current.keyframe.value, next.keyframe.value, rawFactor.pow(easing))
            }
        }
        return entries.last().keyframe.value
    }

    private fun parseColorKeyframeEntries(body: String, variables: Map<String, Double>): List<ColorKeyframeEntry> {
        return splitTopLevel(body, ',')
            .mapNotNull { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val assignmentIndex = topLevelAssignmentIndex(trimmed) ?: throw IllegalArgumentException("Invalid keyframe entry")
                val key = trimmed.substring(0, assignmentIndex).trim()
                val valueRaw = trimmed.substring(assignmentIndex + 1).trim()
                val (start, end) = if (".." in key) {
                    val parts = key.split("..", limit = 2)
                    require(parts.size == 2) { "Invalid keyframe range" }
                    Parser(parts[0].trim(), variables).parse() to Parser(parts[1].trim(), variables).parse()
                } else {
                    val point = Parser(key, variables).parse()
                    point to point
                }
                val normalizedStart = min(start, end)
                val normalizedEnd = max(start, end)
                ColorKeyframeEntry(normalizedStart, normalizedEnd, parseColorKeyframeValue(valueRaw, variables))
            }
    }

    private fun parseColorKeyframeValue(raw: String, variables: Map<String, Double>): ColorKeyframeValue {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) {
            return ColorKeyframeValue(parseColorExpression(trimmed, variables), 1.0)
        }
        require(trimmed.endsWith("}")) { "Invalid keyframe object" }
        val content = trimmed.substring(1, trimmed.length - 1)
        var value: Int? = null
        var easing = 1.0
        splitTopLevel(content, ',').forEach { part ->
            val entry = part.trim()
            if (entry.isEmpty()) return@forEach
            val assignmentIndex = topLevelAssignmentIndex(entry) ?: throw IllegalArgumentException("Invalid keyframe object field")
            val name = entry.substring(0, assignmentIndex).trim().lowercase()
            val rhs = entry.substring(assignmentIndex + 1).trim()
            when (name) {
                "value" -> value = parseColorExpression(rhs, variables)
                "easing" -> easing = rhs.toDouble()
                else -> throw IllegalArgumentException("Unknown keyframe field: $name")
            }
        }
        return ColorKeyframeValue(value ?: throw IllegalArgumentException("Keyframe object requires value"), easing)
    }

    private fun parseColorExpression(raw: String, variables: Map<String, Double>): Int {
        val trimmed = raw.trim()
        val functionMatch = Regex("([A-Za-z_][A-Za-z0-9_\\.]*)\\((.*)\\)").matchEntire(trimmed)
        if (functionMatch != null) {
            val name = functionMatch.groupValues[1].lowercase()
            val argsRaw = splitTopLevel(functionMatch.groupValues[2], ',').map { it.trim() }.filter { it.isNotEmpty() }
            return when (name) {
                "rand_color", "math.rand_color" -> {
                    require(argsRaw.size == 2) { "rand_color requires 2 arguments" }
                    val from = parseColorExpression(argsRaw[0], variables)
                    val to = parseColorExpression(argsRaw[1], variables)
                    blendColors(from, to, stableUnit(variables, trimmed))
                }
                else -> normalizeColorInt(evaluateStatements(trimmed, variables).toInt())
            }
        }
        variables[trimmed]?.let { return normalizeColorInt(it.toInt()) }
        variables[trimmed.lowercase()]?.let { return normalizeColorInt(it.toInt()) }
        return parseColorLiteralOrNull(trimmed)
            ?: normalizeColorInt(evaluateStatements(trimmed, variables).toInt())
    }

    private fun normalizeColorInt(value: Int): Int =
        if ((value ushr 24) == 0) (0xFF shl 24) or value else value

    private fun parseColorLiteral(raw: String): Int {
        val normalized = raw.trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
        return when (normalized.length) {
            6 -> normalized.toLongOrNull(16)?.toInt()?.let { (0xFF shl 24) or it }
            8 -> normalized.toLongOrNull(16)?.toInt()
            else -> throw IllegalArgumentException("Invalid color literal")
        } ?: throw IllegalArgumentException("Invalid color literal")
    }

    private fun parseColorLiteralOrNull(raw: String): Int? {
        val normalized = raw.trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
        return when (normalized.length) {
            6 -> normalized.toLongOrNull(16)?.toInt()?.let { (0xFF shl 24) or it }
            8 -> normalized.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    private fun blendColors(start: Int, end: Int, factor: Double): Int {
        val progress = factor.coerceIn(0.0, 1.0)
        val sr = (start shr 16) and 0xFF
        val sg = (start shr 8) and 0xFF
        val sb = start and 0xFF
        val er = (end shr 16) and 0xFF
        val eg = (end shr 8) and 0xFF
        val eb = end and 0xFF
        val r = (sr + (er - sr) * progress).toInt().coerceIn(0, 255)
        val g = (sg + (eg - sg) * progress).toInt().coerceIn(0, 255)
        val b = (sb + (eb - sb) * progress).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun stableUnit(variables: Map<String, Double>, salt: String): Double {
        var hash = salt.hashCode().toLong()
        hash = hash * 31 + variables["rand"]?.toBits().orZero()
        hash = hash * 31 + variables["rand_x"]?.toBits().orZero()
        hash = hash * 31 + variables["rand_y"]?.toBits().orZero()
        hash = hash * 31 + variables["start_pos_x"]?.toBits().orZero()
        hash = hash * 31 + variables["start_pos_y"]?.toBits().orZero()
        val normalized = (hash xor (hash ushr 33)).toULong().toDouble() / ULong.MAX_VALUE.toDouble()
        return normalized.coerceIn(0.0, 1.0)
    }

    private fun Double?.toBits(): Long = this?.toBits() ?: 0L
    private fun Long?.orZero(): Long = this ?: 0L

    private fun splitTopLevel(source: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0
        var braceDepth = 0
        source.forEach { ch ->
            when (ch) {
                '(' -> {
                    parenDepth += 1
                    current.append(ch)
                }
                ')' -> {
                    parenDepth = (parenDepth - 1).coerceAtLeast(0)
                    current.append(ch)
                }
                '{' -> {
                    braceDepth += 1
                    current.append(ch)
                }
                '}' -> {
                    braceDepth = (braceDepth - 1).coerceAtLeast(0)
                    current.append(ch)
                }
                delimiter -> {
                    if (parenDepth == 0 && braceDepth == 0) {
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

    private fun findMatching(source: String, openIndex: Int, open: Char, close: Char): Int {
        var depth = 0
        for (index in openIndex until source.length) {
            when (source[index]) {
                open -> depth += 1
                close -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        throw IllegalArgumentException("Unbalanced '$open$close'")
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

            if ((peek() == '0' && (source.getOrNull(index + 1) == 'x' || source.getOrNull(index + 1) == 'X')) || peek()?.isDigit() == true || peek() == '.') {
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
                "rand_value", "math.rand_value" -> {
                    require(args.isNotEmpty()) { "rand_value requires at least 1 argument" }
                    args[stableChoiceIndex(args)]
                }
                else -> throw IllegalArgumentException("Unknown function: $name")
            }
        }

        private fun stableChoiceIndex(args: List<Double>): Int {
            var hash = 17
            listOf("rand", "rand_x", "rand_y", "start_pos_x", "start_pos_y").forEach { key ->
                val value = variables[key] ?: variables[key.lowercase()] ?: 0.0
                val bits = java.lang.Double.doubleToLongBits(value)
                hash = 31 * hash + (bits xor (bits ushr 32)).toInt()
            }
            args.forEach { value ->
                val bits = java.lang.Double.doubleToLongBits(value)
                hash = 31 * hash + (bits xor (bits ushr 32)).toInt()
            }
            return Math.floorMod(hash, args.size)
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
            if (peek() == '0' && (source.getOrNull(index + 1) == 'x' || source.getOrNull(index + 1) == 'X')) {
                index += 2
                val start = index
                while (peek()?.let { it.isDigit() || it.lowercaseChar() in 'a'..'f' } == true) {
                    index += 1
                }
                require(index > start) { "Invalid hex literal" }
                return source.substring(start, index).toLong(16).toDouble()
            }

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
