package ru.benos_codex.expression_engine

class FunctionArguments internal constructor(
    private val values: Map<String, ExpressionValue>,
    private val positional: List<ExpressionValue>
) : Iterable<ExpressionValue> by positional {
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

    fun boolean(name: String): Boolean =
        value(name).asBoolean()

    fun string(name: String): String =
        value(name).asString()

    fun vec2(name: String): Vec2Value =
        value(name).asVec2()

    fun vec3(name: String): Vec3Value =
        value(name).asVec3()

    fun color(name: String): ColorValue =
        value(name).asColor()

    val size: Int get() = positional.size

    fun asList(): List<ExpressionValue> = positional.toList()
}

internal data class FunctionDefinition(
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
