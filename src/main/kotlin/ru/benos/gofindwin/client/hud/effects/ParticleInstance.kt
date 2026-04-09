package ru.benos.gofindwin.client.hud.effects

data class ParticleInstance(
    var x     : Int,
    var oldX  : Int = x,
    val startX: Int = x,

    var y     : Int,
    var oldY  : Int = y,
    val startY: Int = x,

    var rotation   : Float,
    var oldRotation: Float = rotation,

    var width   : Float = 1.0f,
    var oldWidth: Float = width,

    var height   : Float = 1.0f,
    var oldHeight: Float = height,

    var age     : Int = 0,

    val lifeTime: Int,

    var color   : Int   = 0xFFFFFFFF.toInt(),
    var oldColor: Int = color,

    var alpha   : Float = 1.0f,
    var oldAlpha: Float = alpha
) {
    val updateAge: Unit get() { age += 1; }

    val lifeFactor: Float get() = age.toFloat() / lifeTime.toFloat()

    val drawable: Boolean get() = age >= 0

    fun stX(n: Int) { oldX = x; x = n }
    fun stY(n: Int) { oldY = y; y = n }
    fun stPosition(nX: Int, nY: Int) { stX(nX); stY(nY) }

    fun stRotation(n: Float) { oldRotation = rotation; rotation = n }

    fun stWidth(n: Float) { oldWidth = width; width = n }
    fun stHeight(n: Float) { oldHeight = height; height = n }
    fun stScale(nX: Float, nY: Float) { stWidth(nX); stHeight(nY) }

    fun stColor(n: Int) { oldColor = color; color = n }
    fun stAlpha(n: Float) { oldAlpha = alpha; alpha = n }
}