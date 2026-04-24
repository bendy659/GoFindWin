package ru.benos_codex.expression_engine

import ru.benos.gofindwin.client.data.Color
import ru.benos.gofindwin.client.data.ParticleInstance
import ru.benos.gofindwin.client.data.Vec2
import ru.benos.gofindwin.client.data.Vec3

sealed interface ExpressionValue {
    fun asDouble(): Double = error("Value '$this' is not numeric")
    fun asHex(): String = error("Value '$this' is not hex")
    fun asBoolean(): Boolean = error("Value '$this' is not boolean")
    fun asString(): String = error("Value '$this' is not string")
    fun asVec2(): Vec2Value = error("Value '$this' is not vec2")
    fun asVec3(): Vec3Value = error("Value '$this' is not vec3")
    fun asColor(): ColorValue = error("Value '$this' is not color")
}

internal interface PropertyValue : ExpressionValue {
    fun getProperty(name: String): ExpressionValue
    fun setProperty(name: String, value: ExpressionValue)
}

data class NumberValue(val value: Double) : ExpressionValue {
    override fun asDouble(): Double = value
}

data class HexValue(val value: String) : ExpressionValue {
    override fun asHex(): String = value
}

data class BoolValue(val value: Boolean) : ExpressionValue {
    override fun asBoolean(): Boolean = value
}

data class StringValue(val value: String) : ExpressionValue {
    override fun asString(): String = value
}

data class Vec2Value(
    val x: Double,
    val y: Double
) : ExpressionValue {
    override fun asVec2(): Vec2Value = this
}

data class Vec3Value(
    val x: Double,
    val y: Double,
    val z: Double
) : ExpressionValue {
    override fun asVec3(): Vec3Value = this
}

data class ColorValue(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int = 255
) : ExpressionValue {
    override fun asColor(): ColorValue = this
}

data class ParticleObjectValue(val value: ParticleInstance) : PropertyValue {
    override fun getProperty(name: String): ExpressionValue =
        when (name) {
            "index" -> NumberValue(value.index.toDouble())
            "lifetime" -> NumberValue(value.lifetime.toDouble())
            "age" -> NumberValue(value.age.toDouble())
            "lifeFactor" -> NumberValue(value.lifeFactor.toDouble())
            "position" -> Vec2ObjectValue(value.position)
            "rotation" -> Vec3ObjectValue(value.rotation)
            "scale" -> Vec3ObjectValue(value.scale)
            "color" -> ColorObjectValue(value.color)
            else -> error("Unknown particle property '$name'")
        }

    override fun setProperty(name: String, value: ExpressionValue) {
        when (name) {
            "index" -> this.value.index = value.asDouble().toInt()
            "lifetime" -> this.value.lifetime = value.asDouble().toInt()
            "age" -> this.value.age = value.asDouble().toInt()
            "position" -> {
                val vec = value.asVec2()
                this.value.position.x = vec.x.toFloat()
                this.value.position.y = vec.y.toFloat()
            }
            "rotation" -> {
                val vec = value.asVec3()
                this.value.rotation.x = vec.x.toFloat()
                this.value.rotation.y = vec.y.toFloat()
                this.value.rotation.z = vec.z.toFloat()
            }
            "scale" -> {
                val vec = value.asVec3()
                this.value.scale.x = vec.x.toFloat()
                this.value.scale.y = vec.y.toFloat()
                this.value.scale.z = vec.z.toFloat()
            }
            "color" -> {
                val color = value.asColor()
                this.value.color.r = color.r
                this.value.color.g = color.g
                this.value.color.b = color.b
                this.value.color.a = color.a
            }
            else -> error("Unknown particle property '$name'")
        }
    }
}

data class Vec2ObjectValue(val value: Vec2) : PropertyValue {
    override fun asVec2(): Vec2Value =
        Vec2Value(value.x.toDouble(), value.y.toDouble())

    override fun getProperty(name: String): ExpressionValue =
        when (name) {
            "x" -> NumberValue(value.x.toDouble())
            "y" -> NumberValue(value.y.toDouble())
            else -> error("Unknown vec2 property '$name'")
        }

    override fun setProperty(name: String, value: ExpressionValue) {
        when (name) {
            "x" -> this.value.x = value.asDouble().toFloat()
            "y" -> this.value.y = value.asDouble().toFloat()
            else -> error("Unknown vec2 property '$name'")
        }
    }
}

data class Vec3ObjectValue(val value: Vec3) : PropertyValue {
    override fun asVec3(): Vec3Value =
        Vec3Value(value.x.toDouble(), value.y.toDouble(), value.z.toDouble())

    override fun getProperty(name: String): ExpressionValue =
        when (name) {
            "x" -> NumberValue(value.x.toDouble())
            "y" -> NumberValue(value.y.toDouble())
            "z" -> NumberValue(value.z.toDouble())
            else -> error("Unknown vec3 property '$name'")
        }

    override fun setProperty(name: String, value: ExpressionValue) {
        when (name) {
            "x" -> this.value.x = value.asDouble().toFloat()
            "y" -> this.value.y = value.asDouble().toFloat()
            "z" -> this.value.z = value.asDouble().toFloat()
            else -> error("Unknown vec3 property '$name'")
        }
    }
}

data class ColorObjectValue(val value: Color) : PropertyValue {
    override fun asColor(): ColorValue =
        ColorValue(value.r, value.g, value.b, value.a)

    override fun getProperty(name: String): ExpressionValue =
        when (name) {
            "r" -> NumberValue(value.r.toDouble())
            "g" -> NumberValue(value.g.toDouble())
            "b" -> NumberValue(value.b.toDouble())
            "a" -> NumberValue(value.a.toDouble())
            else -> error("Unknown color property '$name'")
        }

    override fun setProperty(name: String, value: ExpressionValue) {
        when (name) {
            "r" -> this.value.r = value.asDouble().toInt()
            "g" -> this.value.g = value.asDouble().toInt()
            "b" -> this.value.b = value.asDouble().toInt()
            "a" -> this.value.a = value.asDouble().toInt()
            else -> error("Unknown color property '$name'")
        }
    }
}

internal fun Any.toExpressionValue(): ExpressionValue =
    when (this) {
        is ExpressionValue -> this
        is Number -> NumberValue(this.toDouble())
        is Boolean -> BoolValue(this)
        is String -> if (startsWith("#")) HexValue(this) else StringValue(this)
        is ParticleInstance -> ParticleObjectValue(this)
        is Vec2 -> Vec2ObjectValue(this)
        is Vec3 -> Vec3ObjectValue(this)
        is Color -> ColorObjectValue(this)
        is Pair<*, *> -> {
            val x = (first as? Number)?.toDouble() ?: error("Unsupported Pair first value '$first'")
            val y = (second as? Number)?.toDouble() ?: error("Unsupported Pair second value '$second'")
            Vec2Value(x, y)
        }
        else -> error("Unsupported variable type '${this::class.simpleName}'")
    }
