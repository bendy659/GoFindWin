package ru.benos_codex.expression_engine

internal class ExpressionParser(source: String) {
    private val tokens = ExpressionTokenizer(source).tokenize()
    private var index = 0

    fun parse(): ExpressionNode {
        val statements = mutableListOf<StatementNode>()
        skipSeparators()

        while (!isAtEnd()) {
            statements += parseStatement()
            skipSeparators()
        }

        require(statements.isNotEmpty()) { "Expression is empty" }
        return BlockExpression(statements)
    }

    private fun parseStatement(): StatementNode {
        skipSeparators()

        if (match(TokenType.RETURN)) {
            return ReturnStatement(parseExpression())
        }

        if (match(TokenType.VAR)) {
            val name = expect(TokenType.IDENTIFIER, "Expected identifier after 'var'").lexeme
            expect(TokenType.EQUAL, "Expected '=' after local variable name")
            return LocalDeclarationStatement(name, parseExpression())
        }

        val checkpoint = index
        val target = parseAssignmentTargetOrNull()
        if (target != null && match(TokenType.EQUAL)) {
            return AssignmentStatement(target, parseExpression())
        }
        index = checkpoint

        return ExpressionStatement(parseExpression())
    }

    private fun parseExpression(): ExpressionNode =
        parseAdditive()

    private fun parseAdditive(): ExpressionNode {
        var node = parseMultiplicative()

        while (true) {
            node = when {
                match(TokenType.PLUS) -> BinaryExpression(node, Operator.ADD, parseMultiplicative())
                match(TokenType.MINUS) -> BinaryExpression(node, Operator.SUBTRACT, parseMultiplicative())
                else -> return node
            }
        }
    }

    private fun parseMultiplicative(): ExpressionNode {
        var node = parseUnary()

        while (true) {
            node = when {
                match(TokenType.STAR) -> BinaryExpression(node, Operator.MULTIPLY, parseUnary())
                match(TokenType.SLASH) -> BinaryExpression(node, Operator.DIVIDE, parseUnary())
                match(TokenType.PERCENT) -> BinaryExpression(node, Operator.REMAINDER, parseUnary())
                else -> return node
            }
        }
    }

    private fun parseUnary(): ExpressionNode =
        when {
            match(TokenType.MINUS) -> UnaryExpression(Operator.NEGATE, parseUnary())
            match(TokenType.PLUS) -> parseUnary()
            else -> parsePrimary()
        }

    private fun parsePrimary(): ExpressionNode {
        val token = peek()

        return when (token.type) {
            TokenType.NUMBER -> {
                advance()
                LiteralNode(NumberValue(token.lexeme.toDouble()))
            }

            TokenType.STRING -> {
                advance()
                LiteralNode(StringValue(token.lexeme))
            }

            TokenType.HEX -> {
                advance()
                LiteralNode(HexValue(token.lexeme.uppercase()))
            }

            TokenType.TRUE -> {
                advance()
                LiteralNode(BoolValue(true))
            }

            TokenType.FALSE -> {
                advance()
                LiteralNode(BoolValue(false))
            }

            TokenType.LPAREN -> {
                advance()
                val expression = parseExpression()
                expect(TokenType.RPAREN, "Expected ')' after expression")
                expression
            }

            TokenType.IDENTIFIER -> parseIdentifierPrimary()
            else -> errorAt(token, "Expected operand")
        }
    }

    private fun parseIdentifierPrimary(): ExpressionNode {
        var node: ExpressionNode = VariableNode(expect(TokenType.IDENTIFIER, "Expected identifier").lexeme)

        while (true) {
            node = when {
                match(TokenType.DOT) -> {
                    val property = expect(TokenType.IDENTIFIER, "Expected identifier after '.'").lexeme
                    PropertyAccessNode(node, property)
                }

                match(TokenType.LPAREN) -> parseFunctionCall(node)
                else -> return node
            }
        }
    }

    private fun parseFunctionCall(callee: ExpressionNode): ExpressionNode {
        val functionName = callee.toFunctionName()
            ?: error("Unsupported function target '$callee'")

        if (functionName == "ramp") {
            val input = parseExpression()
            expect(TokenType.RPAREN, "Expected ')' after ramp input")
            expect(TokenType.LBRACE, "Expected '{' to start ramp body")
            return parseRamp(input)
        }

        val arguments = mutableListOf<ExpressionNode>()
        if (!check(TokenType.RPAREN)) {
            do {
                arguments += parseExpression()
            } while (match(TokenType.COMMA))
        }

        expect(TokenType.RPAREN, "Expected ')' after function arguments")
        return FunctionCallNode(functionName, arguments)
    }

    private fun parseRamp(input: ExpressionNode): ExpressionNode {
        val points = mutableListOf<RampPoint>()
        skipSeparators()

        while (!check(TokenType.RBRACE)) {
            val key = parseExpression()
            expect(TokenType.EQUAL, "Expected '=' in ramp point")
            val value = parseExpression()
            points += RampPoint(key, value)

            if (!match(TokenType.COMMA)) {
                break
            }
            skipSeparators()
        }

        expect(TokenType.RBRACE, "Expected '}' after ramp body")
        require(points.isNotEmpty()) { "ramp must contain at least one point" }
        return RampExpression(input, points)
    }

    private fun parseQualifiedName(): String =
        parseQualifiedNameOrNull() ?: errorAt(peek(), "Expected identifier")

    private fun parseQualifiedNameOrNull(): String? {
        if (!check(TokenType.IDENTIFIER)) return null

        val parts = mutableListOf<String>()
        parts += advance().lexeme

        while (match(TokenType.DOT)) {
            val next = expect(TokenType.IDENTIFIER, "Expected identifier after '.'")
            parts += next.lexeme
        }

        return parts.joinToString(".")
    }

    private fun parseAssignmentTargetOrNull(): AssignmentTarget? {
        val qualifiedName = parseQualifiedNameOrNull() ?: return null
        val parts = qualifiedName.split('.')
        return AssignmentTarget(parts.first(), parts.drop(1))
    }

    private fun skipSeparators() {
        while (match(TokenType.SEMICOLON) || match(TokenType.NEWLINE)) {
            // skip
        }
    }

    private fun match(type: TokenType): Boolean {
        if (!check(type)) return false
        advance()
        return true
    }

    private fun check(type: TokenType): Boolean =
        peek().type == type

    private fun expect(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        errorAt(peek(), message)
    }

    private fun advance(): Token {
        if (!isAtEnd()) index += 1
        return previous()
    }

    private fun previous(): Token =
        tokens[index - 1]

    private fun peek(): Token =
        tokens[index]

    private fun isAtEnd(): Boolean =
        peek().type == TokenType.EOF

    private fun errorAt(token: Token, message: String): Nothing =
        throw IllegalArgumentException("$message at position ${token.position}")
}

private class ExpressionTokenizer(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var index = 0

    fun tokenize(): List<Token> {
        while (!isAtEnd()) {
            val start = index
            when (val char = advance()) {
                ' ', '\t' -> Unit
                '\r' -> {
                    if (peek() == '\n') advance()
                    tokens += Token(TokenType.NEWLINE, "\n", start)
                }

                '\n' -> tokens += Token(TokenType.NEWLINE, "\n", start)
                ';' -> tokens += Token(TokenType.SEMICOLON, ";", start)
                ',' -> tokens += Token(TokenType.COMMA, ",", start)
                '.' -> tokens += Token(TokenType.DOT, ".", start)
                '(' -> tokens += Token(TokenType.LPAREN, "(", start)
                ')' -> tokens += Token(TokenType.RPAREN, ")", start)
                '{' -> tokens += Token(TokenType.LBRACE, "{", start)
                '}' -> tokens += Token(TokenType.RBRACE, "}", start)
                '=' -> tokens += Token(TokenType.EQUAL, "=", start)
                '+' -> tokens += Token(TokenType.PLUS, "+", start)
                '-' -> tokens += Token(TokenType.MINUS, "-", start)
                '*' -> tokens += Token(TokenType.STAR, "*", start)
                '/' -> tokens += Token(TokenType.SLASH, "/", start)
                '%' -> tokens += Token(TokenType.PERCENT, "%", start)
                '"' -> tokens += readString(start)
                '#' -> {
                    val hexToken = readHexOrNull(start)
                    if (hexToken != null) {
                        tokens += hexToken
                    } else {
                        skipLineComment()
                    }
                }
                else -> when {
                    char.isDigit() -> tokens += readNumber(start)
                    char.isIdentifierStart() -> tokens += readIdentifier(start)
                    else -> throw IllegalArgumentException("Unexpected character '$char' at position $start")
                }
            }
        }

        tokens += Token(TokenType.EOF, "", source.length)
        return tokens
    }

    private fun readNumber(start: Int): Token {
        while (peek().isDigit()) advance()
        if (peek() == '.' && peekNext().isDigit()) {
            advance()
            while (peek().isDigit()) advance()
        }
        return Token(TokenType.NUMBER, source.substring(start, index), start)
    }

    private fun readIdentifier(start: Int): Token {
        while (peek().isIdentifierPart()) advance()
        val lexeme = source.substring(start, index)
        val type = when (lexeme) {
            "return" -> TokenType.RETURN
            "var" -> TokenType.VAR
            "true" -> TokenType.TRUE
            "false" -> TokenType.FALSE
            else -> TokenType.IDENTIFIER
        }
        return Token(type, lexeme, start)
    }

    private fun readString(start: Int): Token {
        val builder = StringBuilder()

        while (!isAtEnd()) {
            val char = advance()
            when (char) {
                '"' -> return Token(TokenType.STRING, builder.toString(), start)
                '\\' -> {
                    check(!isAtEnd()) { "Unterminated string literal" }
                    val escaped = advance()
                    builder.append(
                        when (escaped) {
                            'n' -> '\n'
                            't' -> '\t'
                            '"' -> '"'
                            '\\' -> '\\'
                            else -> escaped
                        }
                    )
                }

                else -> builder.append(char)
            }
        }

        error("Unterminated string literal")
    }

    private fun readHexOrNull(start: Int): Token? {
        val checkpoint = index
        while (peek().isHexDigit()) advance()
        val lexeme = source.substring(start, index)

        val isValidLength = lexeme.length == 7 || lexeme.length == 9
        val next = peek()
        val hasValidBoundary = next == '\u0000' || next.isWhitespace() || next in ",;(){}+-*/%="

        if (isValidLength && hasValidBoundary) {
            return Token(TokenType.HEX, lexeme, start)
        }

        index = checkpoint
        return null
    }

    private fun skipLineComment() {
        while (!isAtEnd()) {
            val char = peek()
            if (char == '\n' || char == '\r') {
                return
            }
            advance()
        }
    }

    private fun advance(): Char =
        source[index++]

    private fun peek(): Char =
        if (isAtEnd()) '\u0000' else source[index]

    private fun peekNext(): Char =
        if (index + 1 >= source.length) '\u0000' else source[index + 1]

    private fun isAtEnd(): Boolean =
        index >= source.length
}

private data class Token(
    val type: TokenType,
    val lexeme: String,
    val position: Int
)

private enum class TokenType {
    IDENTIFIER,
    NUMBER,
    STRING,
    HEX,
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    COMMA,
    SEMICOLON,
    NEWLINE,
    DOT,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    EQUAL,
    RETURN,
    VAR,
    TRUE,
    FALSE,
    EOF
}

class CompiledExpression private constructor(
    private val expression: ExpressionNode
) {
    fun evaluate(engine: ExpressionEngine, context: ExpressionContext = ExpressionContext.EMPTY): ExpressionValue =
        expression.evaluate(engine, context)

    companion object {
        internal fun of(expression: ExpressionNode): CompiledExpression =
            CompiledExpression(expression)
    }
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

internal data class LocalDeclarationStatement(
    val name: String,
    val expression: ExpressionNode
) : StatementNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): StatementResult {
        val value = expression.evaluate(engine, context)
        return StatementResult(context.withValue(name, value), value)
    }
}

internal data class AssignmentStatement(
    val target: AssignmentTarget,
    val expression: ExpressionNode
) : StatementNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): StatementResult {
        val value = expression.evaluate(engine, context)
        return StatementResult(target.assign(engine, context, value), value)
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

internal data class PropertyAccessNode(
    val target: ExpressionNode,
    val property: String
) : ExpressionNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue {
        val qualifiedName = toQualifiedName()
        if (qualifiedName != null) {
            if (context.contains(qualifiedName)) return context.value(qualifiedName)
            runCatching { engine.resolveVariable(qualifiedName, context) }.getOrNull()?.let { return it }
        }

        val base = target.evaluate(engine, context)
        return (base as? PropertyValue)?.getProperty(property)
            ?: error("Value '$base' has no property '$property'")
    }

    fun toQualifiedName(): String? =
        when (target) {
            is VariableNode -> "${target.name}.$property"
            is PropertyAccessNode -> "${target.toQualifiedName() ?: return null}.$property"
            else -> null
        }
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
                Operator.NEGATE -> error("Unary operator '$operator' is not supported in BinaryExpression")
            }
        )
    }
}

internal data class UnaryExpression(
    val operator: Operator,
    val expression: ExpressionNode
) : ExpressionNode {
    override fun evaluate(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue {
        val value = expression.evaluate(engine, context).asDouble()

        return NumberValue(
            when (operator) {
                Operator.NEGATE -> -value
                else -> error("Unsupported unary operator '$operator'")
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
    REMAINDER,
    NEGATE
}

internal data class AssignmentTarget(
    val root: String,
    val properties: List<String>
) {
    fun assign(engine: ExpressionEngine, context: ExpressionContext, value: ExpressionValue): ExpressionContext {
        if (properties.isEmpty()) {
            return context.withValue(root, value)
        }

        val baseValue = resolveBase(engine, context)
        if (baseValue is PropertyValue) {
            setNestedProperty(baseValue, properties, value)
            return context
        }

        return context.withValue("$root.${properties.joinToString(".")}", value)
    }

    private fun resolveBase(engine: ExpressionEngine, context: ExpressionContext): ExpressionValue? =
        when {
            context.contains(root) -> context.value(root)
            else -> runCatching { engine.resolveVariable(root, context) }.getOrNull()
        }

    private fun setNestedProperty(target: PropertyValue, path: List<String>, value: ExpressionValue) {
        if (path.size == 1) {
            target.setProperty(path.first(), value)
            return
        }

        val next = target.getProperty(path.first())
        val nextTarget = next as? PropertyValue
            ?: error("Property '${path.first()}' is not writable")
        setNestedProperty(nextTarget, path.drop(1), value)
    }
}

private fun ExpressionNode.toFunctionName(): String? =
    when (this) {
        is VariableNode -> name
        is PropertyAccessNode -> toQualifiedName()
        else -> null
    }

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun Char.isIdentifierStart(): Boolean =
    this == '_' || this.isLetter()

private fun Char.isIdentifierPart(): Boolean =
    this == '_' || this.isLetterOrDigit()
