package ru.benos.gofindwin.client.hud.effects

class ExpressionEngine {
    private val functions = linkedMapOf<String, MutableList<FunctionDefinition>>()
    private val variables = linkedMapOf<String, (ExpressionContext) -> ExpressionValue>()

    init {
        addFunction("math.sin(a)") { args, _ -> NumberValue(kotlin.math.sin(args.double("a"))) }
        addFunction("math.cos(a)") { args, _ -> NumberValue(kotlin.math.cos(args.double("a"))) }
        addFunction("math.rand(max)") { args, _ ->
            val max = args.double("max")
            NumberValue(-max + kotlin.random.Random.nextDouble() * (max * 2.0))
        }
        addFunction("math.rand(min, max)") { args, _ ->
            val min = args.double("min")
            val max = args.double("max")
            NumberValue(min + kotlin.random.Random.nextDouble() * (max - min))
        }
    }

    fun compile(source: String): CompiledExpression =
        CompiledExpression.of(ExpressionParser(source).parse())

    fun evaluate(source: String, build: ExpressionContextBuilder.() -> Unit = {}): ExpressionValue =
        compile(source).evaluate(this, ExpressionContextBuilder().apply(build).build())

    fun evaluateDouble(source: String, build: ExpressionContextBuilder.() -> Unit = {}): Double =
        evaluate(source, build).asDouble()

    fun addFunction(
        signature: String,
        handler: (arguments: FunctionArguments, context: ExpressionContext) -> ExpressionValue
    ) {
        val definition = FunctionDefinition.parse(signature, handler)
        functions.getOrPut(definition.name) { mutableListOf() }.add(definition)
    }

    fun addVariable(
        name: String,
        resolver: (ExpressionContext) -> ExpressionValue
    ) {
        variables[name] = resolver
    }

    fun addNumberVariable(name: String, resolver: (ExpressionContext) -> Number) {
        addVariable(name) { NumberValue(resolver(it).toDouble()) }
    }

    fun addHexVariable(name: String, resolver: (ExpressionContext) -> String) {
        addVariable(name) { HexValue(resolver(it)) }
    }

    internal fun invokeFunction(name: String, arguments: List<ExpressionValue>, context: ExpressionContext): ExpressionValue {
        val overloads = functions[name]
            ?: error("Unknown function '$name'")
        val definition = overloads.firstOrNull { it.parameters.size == arguments.size }
            ?: error("Function '$name' does not support ${arguments.size} argument(s)")
        return definition.invoke(arguments, context)
    }

    internal fun resolveVariable(name: String, context: ExpressionContext): ExpressionValue =
        variables[name]?.invoke(context)
            ?: error("Unknown variable '$name'")
}

fun ramp(factor: Number, points: Map<Number, Number>): Float {
    val newMap = mutableMapOf<Float, Float>()
    points.forEach { (key, value) -> newMap[key.toFloat()] = value.toFloat() }
    return rampF(factor.toFloat(), newMap)
}

fun ramp(factor: Float, points: Map<Float, Float>): Float =
    rampF(factor, points)

fun ramp(
    factor: Float,
    build: RampBuilder.() -> Unit
): Float = rampF(factor, RampBuilder().apply(build).build())

class RampBuilder {
    private val points = linkedMapOf<Float, Float>()

    fun point(key: Float, value: Float) {
        points[key] = value
    }

    infix fun Float.toValue(value: Float) {
        point(this, value)
    }

    fun build(): Map<Float, Float> = points.toMap()
}

private fun rampF(
    factor: Float,
    points: Map<Float, Float>
): Float {
    require(points.isNotEmpty()) { "ramp must contain at least one point" }
    val sorted = points.entries.sortedBy { it.key }

    if (sorted.size == 1) return sorted.first().value
    if (factor <= sorted.first().key) return sorted.first().value
    if (factor >= sorted.last().key) return sorted.last().value

    for (index in 0 until sorted.lastIndex) {
        val left = sorted[index]
        val right = sorted[index + 1]
        if (factor in left.key..right.key) {
            if (left.key == right.key) return right.value
            val delta = (factor - left.key) / (right.key - left.key)
            return left.value + (right.value - left.value) * delta
        }
    }

    return sorted.last().value
}

private class ExpressionParser(private val source: String) {
    private var index = 0

    fun parse(): ExpressionNode {
        val statements = mutableListOf<StatementNode>()
        skipWhitespaceAndSeparators()
        while (index < source.length) {
            statements += parseStatement()
            skipWhitespaceAndSeparators()
        }
        require(statements.isNotEmpty()) { "Expression is empty" }
        return BlockExpression(statements)
    }

    private fun parseStatement(): StatementNode {
        skipWhitespace()
        val checkpoint = index
        val identifier = parseIdentifierTokenOrNull()

        if (identifier != null) {
            if (identifier == "return") {
                skipWhitespace()
                return ReturnStatement(parseAdditive())
            }

            skipWhitespace()
            if (match('=')) {
                val expression = parseAdditive()
                return AssignmentStatement(identifier, expression)
            }
            index = checkpoint
        }

        return ExpressionStatement(parseAdditive())
    }

    private fun parseAdditive(): ExpressionNode {
        var node = parseMultiplicative()
        while (true) {
            skipWhitespace()
            node = when {
                match('+') -> BinaryExpression(node, Operator.ADD, parseMultiplicative())
                match('-') -> BinaryExpression(node, Operator.SUBTRACT, parseMultiplicative())
                else -> return node
            }
        }
    }

    private fun parseMultiplicative(): ExpressionNode {
        var node = parsePrimary()
        while (true) {
            skipWhitespace()
            node = when {
                match('*') -> BinaryExpression(node, Operator.MULTIPLY, parsePrimary())
                match('/') -> BinaryExpression(node, Operator.DIVIDE, parsePrimary())
                match('%') -> BinaryExpression(node, Operator.REMAINDER, parsePrimary())
                else -> return node
            }
        }
    }

    private fun parsePrimary(): ExpressionNode {
        skipWhitespace()

        if (source.startsWith("ramp", index)) {
            val keywordEnd = index + 4
            val next = source.getOrNull(keywordEnd)
            if (next == null || next.isWhitespace() || next == '(') {
                index = keywordEnd
                return parseRamp()
            }
        }

        if (match('(')) {
            val expression = parseAdditive()
            skipWhitespace()
            require(match(')')) { "Expected ')' at position $index" }
            return expression
        }

        if (match('#')) {
            val start = index - 1
            while (index < source.length && source[index].isHexDigit()) {
                index += 1
            }
            val value = source.substring(start, index)
            require(value.length == 7 || value.length == 9) { "Invalid hex literal '$value'" }
            return LiteralNode(HexValue(value.uppercase()))
        }

        if (index < source.length && source[index].isLetter()) {
            val token = parseIdentifierToken()
            skipWhitespace()

            if (match('(')) {
                val arguments = mutableListOf<ExpressionNode>()
                skipWhitespace()

                if (!match(')')) {
                    do {
                        arguments += parseAdditive()
                        skipWhitespace()
                    } while (match(','))

                    require(match(')')) { "Expected ')' at position $index" }
                }

                return FunctionCallNode(token, arguments)
            }

            return VariableNode(token)
        }

        val start = index
        while (index < source.length && !source[index].isWhitespace() && source[index] !in "+-*/%(),{}=;") {
            index += 1
        }
        require(index > start) { "Expected operand at position $index" }
        val token = source.substring(start, index)
        val number = token.toDoubleOrNull() ?: error("Unknown token '$token'")
        return LiteralNode(NumberValue(number))
    }

    private fun parseRamp(): ExpressionNode {
        skipWhitespace()
        require(match('(')) { "Expected '(' after ramp at position $index" }
        val input = parseAdditive()
        skipWhitespace()
        require(match(')')) { "Expected ')' after ramp input at position $index" }
        skipWhitespace()
        require(match('{')) { "Expected '{' to start ramp body at position $index" }

        val points = mutableListOf<RampPoint>()
        while (true) {
            skipWhitespace()
            if (match('}')) break

            val key = parseAdditive()
            skipWhitespace()
            require(match('=')) { "Expected '=' in ramp point at position $index" }
            val value = parseAdditive()
            points += RampPoint(key, value)

            skipWhitespace()
            if (match('}')) break
            require(match(',')) { "Expected ',' or '}' in ramp body at position $index" }
        }

        require(points.isNotEmpty()) { "ramp must contain at least one point" }
        return RampExpression(input, points)
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index += 1
        }
    }

    private fun skipWhitespaceAndSeparators() {
        while (index < source.length) {
            val char = source[index]
            if (char.isWhitespace() || char == ';') {
                index += 1
                continue
            }
            break
        }
    }

    private fun parseIdentifierToken(): String =
        parseIdentifierTokenOrNull() ?: error("Expected identifier at position $index")

    private fun parseIdentifierTokenOrNull(): String? {
        if (index >= source.length || !source[index].isLetter()) return null
        val start = index
        while (index < source.length && !source[index].isWhitespace() && source[index] !in "+-*/%(),{}=;") {
            index += 1
        }
        return source.substring(start, index)
    }

    private fun match(char: Char): Boolean {
        if (index < source.length && source[index] == char) {
            index += 1
            return true
        }
        return false
    }
}

class CompiledExpression private constructor(
    private val expression: ExpressionNode
) {
    fun evaluate(context: ExpressionContext = ExpressionContext.EMPTY): ExpressionValue =
        evaluate(ExpressionEngine(), context)

    fun evaluate(engine: ExpressionEngine, context: ExpressionContext = ExpressionContext.EMPTY): ExpressionValue =
        expression.evaluate(engine, context)

    companion object {
        internal fun of(expression: ExpressionNode): CompiledExpression =
            CompiledExpression(expression)
    }
}

class ExpressionContext internal constructor(
    private val values: Map<String, ExpressionValue>,
    private val parent: ExpressionContext? = null
) {
    fun value(name: String): ExpressionValue =
        values[name] ?: parent?.value(name) ?: error("Unknown variable '$name'")

    fun withValue(name: String, value: ExpressionValue): ExpressionContext =
        ExpressionContext(mapOf(name to value), this)

    fun contains(name: String): Boolean =
        values.containsKey(name) || parent?.contains(name) == true

    companion object {
        val EMPTY = ExpressionContext(emptyMap(), null)
    }
}

class ExpressionContextBuilder {
    private val values = linkedMapOf<String, ExpressionValue>()

    fun number(name: String, value: Number) {
        values[name] = NumberValue(value.toDouble())
    }

    fun hex(name: String, value: String) {
        values[name] = HexValue(value)
    }

    fun value(name: String, value: ExpressionValue) {
        values[name] = value
    }

    internal fun build(): ExpressionContext =
        ExpressionContext(values.toMap(), null)
}

sealed interface ExpressionValue {
    fun asDouble(): Double = error("Value '$this' is not numeric")
    fun asHex(): String = error("Value '$this' is not hex")
}

data class NumberValue(val value: Double) : ExpressionValue {
    override fun asDouble(): Double = value
}

data class HexValue(val value: String) : ExpressionValue {
    override fun asHex(): String = value
}

internal sealed interface ExpressionNode {
    fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue
}

internal sealed interface StatementNode {
    fun evaluate(engine: ExpressionEngine, context: ExpressionContext): StatementResult
}

internal data class StatementResult(
    val context: ExpressionContext,
    val value: ExpressionValue,
    val returned: Boolean = false
)

internal data class BlockExpression(
    val statements: List<StatementNode>
) : ExpressionNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue {
        var currentContext = context
        var currentValue: ExpressionValue = NumberValue(0.0)

        statements.forEach { statement ->
            val result = statement.evaluate(engine, currentContext)
            currentContext = result.context
            currentValue = result.value
            if (result.returned) return currentValue
        }

        return currentValue
    }
}

internal data class ExpressionStatement(
    val expression: ExpressionNode
) : StatementNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): StatementResult =
        StatementResult(context, expression.evaluate(engine, context))
}

internal data class AssignmentStatement(
    val name: String,
    val expression: ExpressionNode
) : StatementNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): StatementResult {
        val value = expression.evaluate(engine, context)
        return StatementResult(context.withValue(name, value), value)
    }
}

internal data class ReturnStatement(
    val expression: ExpressionNode
) : StatementNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): StatementResult =
        StatementResult(context, expression.evaluate(engine, context), returned = true)
}

internal data class LiteralNode(val value: ExpressionValue) : ExpressionNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue = value
}

internal data class VariableNode(val name: String) : ExpressionNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue =
        if (context.contains(name)) context.value(name) else engine.resolveVariable(name, context)
}

internal data class FunctionCallNode(
    val name: String,
    val arguments: List<ExpressionNode>
) : ExpressionNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue =
        engine.invokeFunction(name, arguments.map { it.evaluate(engine, context) }, context)
}

internal data class BinaryExpression(
    val left: ExpressionNode,
    val operator: Operator,
    val right: ExpressionNode
) : ExpressionNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue {
        val leftValue = left.evaluate(engine, context).asDouble()
        val rightValue = right.evaluate(engine, context).asDouble()

        return NumberValue(
            when (operator) {
                Operator.ADD -> leftValue + rightValue
                Operator.SUBTRACT -> leftValue - rightValue
                Operator.MULTIPLY -> leftValue * rightValue
                Operator.DIVIDE -> leftValue / rightValue
                Operator.REMAINDER -> leftValue % rightValue
            }
        )
    }
}

internal data class RampPoint(
    val key: ExpressionNode,
    val value: ExpressionNode
)

internal data class RampExpression(
    val input: ExpressionNode,
    val points: List<RampPoint>
) : ExpressionNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue {
        val factor = input.evaluate(engine, context).asDouble().toFloat()
        val evaluatedPoints = points.associate {
            it.key.evaluate(engine, context).asDouble().toFloat() to
                it.value.evaluate(engine, context).asDouble().toFloat()
        }

        return NumberValue(rampF(factor, evaluatedPoints).toDouble())
    }
}

internal enum class Operator {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER
}

class FunctionArguments internal constructor(
    private val values: Map<String, ExpressionValue>,
    private val positional: List<ExpressionValue>
) : Iterable<ExpressionValue> {
    fun value(name: String): ExpressionValue =
        values[name] ?: error("Unknown argument '$name'")

    fun value(index: Int): ExpressionValue =
        positional[index]

    fun double(name: String): Double =
        value(name).asDouble()

    fun double(index: Int): Double =
        value(index).asDouble()

    fun hex(name: String): String =
        value(name).asHex()

    val size: Int get() = positional.size

    fun asList(): List<ExpressionValue> = positional.toList()

    override fun iterator(): Iterator<ExpressionValue> =
        positional.iterator()

    fun forEach(action: (ExpressionValue) -> Unit) {
        positional.forEach(action)
    }
}

private data class FunctionDefinition(
    val name: String,
    val parameters: List<String>,
    val handler: (arguments: FunctionArguments, context: ExpressionContext) -> ExpressionValue
) {
    fun invoke(arguments: List<ExpressionValue>, context: ExpressionContext): ExpressionValue =
        handler(
            FunctionArguments(parameters.zip(arguments).toMap(), arguments),
            context
        )

    companion object {
        fun parse(
            signature: String,
            handler: (arguments: FunctionArguments, context: ExpressionContext) -> ExpressionValue
        ): FunctionDefinition {
            val trimmed = signature.trim()
            val openIndex = trimmed.indexOf('(')
            val closeIndex = trimmed.lastIndexOf(')')
            require(openIndex > 0 && closeIndex == trimmed.lastIndex && closeIndex > openIndex) {
                "Invalid function signature '$signature'"
            }

            val name = trimmed.substring(0, openIndex).trim()
            val params = trimmed.substring(openIndex + 1, closeIndex)
                .split(',')
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }

            return FunctionDefinition(name, params, handler)
        }
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

typealias ParticleExpressionEngine = ExpressionEngine
typealias ParticleValue = ExpressionValue
typealias CompiledParticleExpression = CompiledExpression
