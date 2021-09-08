package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import net.corda.serialization.CheckpointSerializer
import net.corda.v5.base.util.uncheckedCast
import java.io.ByteArrayInputStream

class KryoCheckpointSerializer(
    private val kryo: Kryo,
) : CheckpointSerializer {

    override fun <T : Any> deserialize(
        bytes: ByteArray,
        clazz: Class<T>,
    ): T {
        val payload = kryoMagic.consume(bytes)
            ?: throw KryoException("Serialized bytes header does not match expected format.")
        return kryoInput(ByteArrayInputStream(payload)) {
            uncheckedCast(kryo.readClassAndObject(this))
        }
    }

    override fun <T : Any> serialize(obj: T): ByteArray {
        return kryoOutput {
            kryoMagic.writeTo(this)
            kryo.writeClassAndObject(this, obj)
        }
    }
}

