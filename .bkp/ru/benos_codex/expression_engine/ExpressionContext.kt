package ru.benos_codex.expression_engine

class ExpressionContext internal constructor(
    private val values: Map<String, ExpressionValue>,
    val cache: MutableMap<String, Any>? = null,
    private val parent: ExpressionContext? = null
) {
    fun value(name: String): ExpressionValue =
        values[name] ?: parent?.value(name) ?: error("Unknown variable '$name'")

    fun withValue(name: String, value: ExpressionValue): ExpressionContext =
        ExpressionContext(mapOf(name to value), cache, this)

    fun contains(name: String): Boolean =
        values.containsKey(name) || parent?.contains(name) == true

    companion object {
        val EMPTY = ExpressionContext(emptyMap(), null, null)
    }
}

class ExpressionContextBuilder {
    private val values = linkedMapOf<String, ExpressionValue>()
    private var cache: MutableMap<String, Any>? = null

    fun number(name: String, value: Number) {
        values[name] = NumberValue(value.toDouble())
    }

    fun hex(name: String, value: String) {
        values[name] = HexValue(value)
    }

    fun bool(name: String, value: Boolean) {
        values[name] = BoolValue(value)
    }

    fun string(name: String, value: String) {
        values[name] = StringValue(value)
    }

    fun value(name: String, value: ExpressionValue) {
        values[name] = value
    }

    fun any(name: String, value: Any) {
        values[name] = value.toExpressionValue()
    }

    fun applyCache(cache: MutableMap<String, Any>) {
        this.cache = cache
    }

    internal fun build(): ExpressionContext =
        ExpressionContext(values.toMap(), cache, null)
}
