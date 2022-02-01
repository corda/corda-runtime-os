package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.base.util.uncheckedCast
import java.io.ByteArrayInputStream

class KryoCheckpointSerializer(
    private val kryo: Kryo,
) : CheckpointSerializer {

    override fun <T : Any> deserialize(
        bytes: ByteArray,
        clazz: Class<T>,
    ): T {
        return kryoInput(ByteArrayInputStream(bytes)) {
            uncheckedCast(kryo.readClassAndObject(this))
        }
    }

    override fun <T : Any> serialize(obj: T): ByteArray {
        return kryoOutput {
            kryo.writeClassAndObject(this, obj)
        }
    }
}

