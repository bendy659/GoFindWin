package ru.benos_codex.expression_engine

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
class ExpressionEngine {
    private val functions = linkedMapOf<String, List<FunctionDefinition>>()
    private val variables = linkedMapOf<String, (ExpressionContext) -> ExpressionValue>()

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
        functions[definition.name] = functions[definition.name].orEmpty() + definition
    }

    fun addNumberFunction(
        signature: String,
        handler: (arguments: FunctionArguments, context: ExpressionContext) -> Number
    ) {
        addFunction(signature) { arguments, context ->
            NumberValue(handler(arguments, context).toDouble())
        }
    }

    fun addBooleanFunction(
        signature: String,
        handler: (arguments: FunctionArguments, context: ExpressionContext) -> Boolean
    ) {
        addFunction(signature) { arguments, context ->
            BoolValue(handler(arguments, context))
        }
    }

    fun addStringFunction(
        signature: String,
        handler: (arguments: FunctionArguments, context: ExpressionContext) -> String
    ) {
        addFunction(signature) { arguments, context ->
            StringValue(handler(arguments, context))
        }
    }

    fun addHexFunction(
        signature: String,
        handler: (arguments: FunctionArguments, context: ExpressionContext) -> String
    ) {
        addFunction(signature) { arguments, context ->
            HexValue(handler(arguments, context))
        }
    }

    fun addVariable(name: String, resolver: (ExpressionContext) -> ExpressionValue) {
        variables[name] = resolver
    }

    fun addNumberVariable(name: String, resolver: (ExpressionContext) -> Number) {
        addVariable(name) { NumberValue(resolver(it).toDouble()) }
    }

    fun addHexVariable(name: String, resolver: (ExpressionContext) -> String) {
        addVariable(name) { HexValue(resolver(it)) }
    }

    fun addBoolVariable(name: String, resolver: (ExpressionContext) -> Boolean) {
        addVariable(name) { BoolValue(resolver(it)) }
    }

    fun addStringVariable(name: String, resolver: (ExpressionContext) -> String) {
        addVariable(name) { StringValue(resolver(it)) }
    }

    fun addVec2Variable(name: String, resolver: (ExpressionContext) -> Vec2Value) {
        addVariable(name, resolver)
    }

    fun addVec3Variable(name: String, resolver: (ExpressionContext) -> Vec3Value) {
        addVariable(name, resolver)
    }

    fun addColorVariable(name: String, resolver: (ExpressionContext) -> ColorValue) {
        addVariable(name, resolver)
    }

    fun addAnyVariable(name: String, resolver: (ExpressionContext) -> Any) {
        addVariable(name) { resolver(it).toExpressionValue() }
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
