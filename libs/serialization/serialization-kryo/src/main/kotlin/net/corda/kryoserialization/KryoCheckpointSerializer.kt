package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import java.io.ByteArrayInputStream
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast

class KryoCheckpointSerializer(
    private val kryo: Kryo,
) : CheckpointSerializer {

    private companion object {
        val log = contextLogger()
    }

    override fun <T : Any> deserialize(
        bytes: ByteArray,
        clazz: Class<T>,
    ): T {
        return try {
            kryoInput(ByteArrayInputStream(bytes)) {
                uncheckedCast(kryo.readClassAndObject(this))
            }
        } catch (ex: Exception) {
            log.error("Failed to deserialize bytes", ex)
            throw ex
        }
    }

    override fun <T : Any> serialize(obj: T): ByteArray {
        return try {
            kryoOutput {
                kryo.writeClassAndObject(this, obj)
            }
        } catch (ex: Exception) {
            log.error("Failed to serialize: $ex")
            throw ex
        }
    }
}

