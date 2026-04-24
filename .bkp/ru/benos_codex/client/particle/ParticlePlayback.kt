package ru.benos_codex.client.particle

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import ru.benos.gofindwin.client.data.Color
import ru.benos.gofindwin.client.data.ExpressionBakeData
import ru.benos.gofindwin.client.data.ParticleInstance
import ru.benos.gofindwin.client.data.Vec2
import ru.benos.gofindwin.client.data.Vec3

@Environment(EnvType.CLIENT)
class ParticlePlayback {
    private val particlesByIndex = linkedMapOf<Int, ParticleInstance>()

    val particles: List<ParticleInstance>
        get() = particlesByIndex.values.toList()

    fun reset() {
        particlesByIndex.clear()
    }

    fun sync(frame: List<ExpressionBakeData?>) {
        val aliveIndexes = hashSetOf<Int>()

        frame.forEachIndexed { index, data ->
            if (data == null) return@forEachIndexed
            aliveIndexes += index

            val particle = particlesByIndex[index]
            if (particle == null) {
                particlesByIndex[index] = createParticle(index, data)
                return@forEachIndexed
            }

            particle.age = (particle.age + 1).coerceAtMost(data.particleLifetime)
            particle.lifetime = data.particleLifetime
            particle.item = data.item
            particle.texture = data.texture
            particle.block = data.block
            particle.entity = data.entity
            particle.label = data.label

            particle.updatePosition(data.position.x, data.position.y)
            particle.updateRotation(data.rotation.x, data.rotation.y, data.rotation.z)
            particle.updateScale(data.scale.x, data.scale.y, data.scale.z)
            particle.updateColor(
                r = data.color shr 16 and 0xFF,
                g = data.color shr 8 and 0xFF,
                b = data.color and 0xFF,
                a = data.color ushr 24 and 0xFF
            )
        }

        particlesByIndex.keys.retainAll(aliveIndexes)
    }

    private fun createParticle(index: Int, data: ExpressionBakeData): ParticleInstance {
        val position = Vec2(data.position.x, data.position.y)
        val rotation = Vec3(data.rotation.x.toFloat(), data.rotation.y.toFloat(), data.rotation.z.toFloat())
        val scale = Vec3(data.scale.x.toFloat(), data.scale.y.toFloat(), data.scale.z.toFloat())
        val color = Color(
            r = data.color shr 16 and 0xFF,
            g = data.color shr 8 and 0xFF,
            b = data.color and 0xFF,
            a = data.color ushr 24 and 0xFF
        )

        return ParticleInstance(
            index = index,
            lifetime = data.particleLifetime,
            age = 0,
            item = data.item,
            texture = data.texture,
            block = data.block,
            entity = data.entity,
            label = data.label,
            color = color,
            spawnPosition = position.copy(),
            position = position.copy(),
            rotation = rotation.copy(),
            scale = scale.copy(),
            oldPosition = position.copy(),
            oldRotation = rotation.copy(),
            oldScale = scale.copy(),
            oldColor = color.copy()
        )
    }
}
