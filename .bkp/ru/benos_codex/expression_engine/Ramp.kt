package ru.benos_codex.expression_engine

fun ramp(factor: Number, points: Map<Number, Number>): Float {
    val newMap = buildMap<Float, Float> {
        points.forEach { (key, value) ->
            put(key.toFloat(), value.toFloat())
        }
    }
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

internal fun rampF(
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
